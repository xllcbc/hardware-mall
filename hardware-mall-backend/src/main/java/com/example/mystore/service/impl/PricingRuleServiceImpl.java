package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mystore.common.constant.PricingCacheConstants;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.constant.UserLevelStatusConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.Category;
import com.example.mystore.entity.db.LevelCategoryRate;
import com.example.mystore.entity.db.UserLevel;
import com.example.mystore.entity.dto.LevelRateItemRequest;
import com.example.mystore.entity.vo.LevelRateVO;
import com.example.mystore.entity.vo.PricingLevelCache;
import com.example.mystore.entity.dto.PricingRateResolution;
import com.example.mystore.event.PricingCacheInvalidationEvent;
import com.example.mystore.mapper.CategoryMapper;
import com.example.mystore.mapper.LevelCategoryRateMapper;
import com.example.mystore.mapper.UserLevelMapper;
import com.example.mystore.service.PricingRuleService;
import com.example.mystore.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PricingRuleServiceImpl implements PricingRuleService {

    private final UserLevelMapper userLevelMapper;
    private final LevelCategoryRateMapper levelCategoryRateMapper;
    private final CategoryMapper categoryMapper;
    private final RedisUtil redisUtil;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${pricing.cache.lock-wait-ms:500}")
    private long cacheLockWaitMs = 500L;

    @Override
    public PricingRateResolution rateFor(Long levelId, Long categoryId, boolean excludeDiscount) {
        PricingLevelCache cache;
        Long effectiveLevelId;
        if (excludeDiscount || levelId == null) {
            effectiveLevelId = resolveRetailLevelId();
            cache = getLevelCache(effectiveLevelId);
        } else {
            cache = getLevelCache(levelId);
            if (levelExists(cache)) {
                effectiveLevelId = levelId;
            } else {
                effectiveLevelId = resolveRetailLevelId();
                cache = getLevelCache(effectiveLevelId);
            }
        }
        BigDecimal rate = categoryId == null || cache == null || cache.getRates() == null
                ? null : cache.getRates().get(categoryId);
        return rate == null
                ? PricingRateResolution.missing(effectiveLevelId)
                : PricingRateResolution.resolved(effectiveLevelId, rate);
    }

    private boolean levelExists(PricingLevelCache cache) {
        return cache != null && (cache.getLevelExists() == null || cache.getLevelExists());
    }

    @Override
    public Long resolveRetailLevelId() {
        return redisUtil.queryWithCache(
                RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID,
                Long.class,
                PricingCacheConstants.TTL_SECONDS,
                RedisConstants.CACHE_NULL_TTL,
                PricingCacheConstants.JITTER_SECONDS,
                cacheLockWaitMs,
                () -> {
                    UserLevel retail = userLevelMapper.selectOne(new LambdaQueryWrapper<UserLevel>()
                            .eq(UserLevel::getIsRetail, UserLevelStatusConstants.IS_RETAIL)
                            .eq(UserLevel::getStatus, UserLevelStatusConstants.STATUS_ENABLED)
                            .last("LIMIT 1"));
                    return retail == null ? UserLevelStatusConstants.RETAIL_LEVEL_ID : retail.getId();
                });
    }

    @Override
    public List<LevelRateVO> getRates(Long levelId) {
        requireLevel(levelId);
        List<Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getDeleteTime, 0)
                .orderByDesc(Category::getSortOrder)
                .orderByAsc(Category::getId));
        Map<Long, BigDecimal> rates = loadRates(levelId);
        List<LevelRateVO> result = new ArrayList<>(categories.size());
        for (Category category : categories) {
            BigDecimal rate = rates.get(category.getId());
            LevelRateVO vo = new LevelRateVO();
            vo.setCategoryId(category.getId());
            vo.setCategoryName(category.getName());
            vo.setRate(rate);
            vo.setMissing(rate == null);
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional
    public void saveRates(Long levelId, List<LevelRateItemRequest> items) {
        UserLevel level = requireLevel(levelId);
        if (items == null || items.isEmpty()) {
            throw new BusinessException("倍率明细不能为空");
        }
        Set<Long> seen = new HashSet<>();
        for (LevelRateItemRequest item : items) {
            if (item == null || item.getCategoryId() == null) {
                throw new BusinessException("分类不能为空");
            }
            if (!seen.add(item.getCategoryId())) {
                throw new BusinessException("同一分类不能重复提交");
            }
            requireCategory(item.getCategoryId());
            validateRate(item.getRate());
            upsertRate(levelId, item.getCategoryId(), item.getRate());
        }
        eventPublisher.publishEvent(new PricingCacheInvalidationEvent(
                PricingCacheInvalidationEvent.Type.MATRIX_CHANGED, levelId));
    }

    @Override
    @Transactional
    public void fillRates(Long levelId, BigDecimal rate) {
        UserLevel level = requireLevel(levelId);
        validateRate(rate);
        List<Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getDeleteTime, 0));
        for (Category category : categories) {
            upsertRate(levelId, category.getId(), rate);
        }
        eventPublisher.publishEvent(new PricingCacheInvalidationEvent(
                PricingCacheInvalidationEvent.Type.MATRIX_CHANGED, levelId));
    }

    @Override
    @Transactional
    public void copyRates(Long levelId, Long sourceLevelId) {
        UserLevel target = requireLevel(levelId);
        if (sourceLevelId == null) {
            throw new BusinessException("来源等级不能为空");
        }
        if (levelId.equals(sourceLevelId)) {
            throw new BusinessException("不能复制到自身");
        }
        UserLevel source = requireLevel(sourceLevelId);
        if (Integer.valueOf(UserLevelStatusConstants.STATUS_ENABLED).equals(target.getStatus())
                && !isComplete(sourceLevelId)) {
            throw new BusinessException("来源等级倍率矩阵未填满，不能复制到已启用等级");
        }
        Map<Long, BigDecimal> sourceRates = loadRates(sourceLevelId);
        levelCategoryRateMapper.delete(new LambdaQueryWrapper<LevelCategoryRate>()
                .eq(LevelCategoryRate::getLevelId, levelId));
        for (Map.Entry<Long, BigDecimal> entry : sourceRates.entrySet()) {
            insertRate(levelId, entry.getKey(), entry.getValue());
        }
        eventPublisher.publishEvent(new PricingCacheInvalidationEvent(
                PricingCacheInvalidationEvent.Type.MATRIX_CHANGED, levelId));
    }

    @Override
    public boolean isComplete(Long levelId) {
        UserLevel level = requireLevel(levelId);
        List<Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getDeleteTime, 0));
        if (categories.isEmpty()) {
            return true;
        }
        Set<Long> configured = loadRates(levelId).keySet();
        return categories.stream().allMatch(category -> configured.contains(category.getId()));
    }

    private PricingLevelCache getLevelCache(Long levelId) {
        return redisUtil.queryWithCache(
                RedisConstants.PREFIX_PRICING_LEVEL + levelId,
                PricingLevelCache.class,
                PricingCacheConstants.TTL_SECONDS,
                RedisConstants.CACHE_NULL_TTL,
                PricingCacheConstants.JITTER_SECONDS,
                cacheLockWaitMs,
                () -> {
                    PricingLevelCache cache = new PricingLevelCache();
                    UserLevel level = userLevelMapper.selectById(levelId);
                    cache.setLevelExists(level != null);
                    cache.setRates(level == null ? new HashMap<>() : loadRates(levelId));
                    return cache;
                });
    }

    private Map<Long, BigDecimal> loadRates(Long levelId) {
        List<LevelCategoryRate> rows = levelCategoryRateMapper.selectList(
                new LambdaQueryWrapper<LevelCategoryRate>()
                        .eq(LevelCategoryRate::getLevelId, levelId));
        Map<Long, BigDecimal> rates = new HashMap<>();
        for (LevelCategoryRate row : rows) {
            if (row.getCategoryId() != null && row.getRate() != null) {
                rates.put(row.getCategoryId(), row.getRate());
            }
        }
        return rates;
    }

    private void upsertRate(Long levelId, Long categoryId, BigDecimal rate) {
        LevelCategoryRate existing = levelCategoryRateMapper.selectOne(
                new LambdaQueryWrapper<LevelCategoryRate>()
                        .eq(LevelCategoryRate::getLevelId, levelId)
                        .eq(LevelCategoryRate::getCategoryId, categoryId)
                        .last("LIMIT 1"));
        if (existing == null) {
            insertRate(levelId, categoryId, rate);
            return;
        }
        existing.setRate(rate);
        existing.setUpdateTime(LocalDateTime.now());
        levelCategoryRateMapper.updateById(existing);
    }

    private void insertRate(Long levelId, Long categoryId, BigDecimal rate) {
        LevelCategoryRate row = new LevelCategoryRate();
        row.setLevelId(levelId);
        row.setCategoryId(categoryId);
        row.setRate(rate);
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        levelCategoryRateMapper.insert(row);
    }

    private UserLevel requireLevel(Long levelId) {
        if (levelId == null) {
            throw new BusinessException("用户等级不存在");
        }
        UserLevel level = userLevelMapper.selectById(levelId);
        if (level == null) {
            throw new BusinessException("用户等级不存在");
        }
        return level;
    }

    private void requireCategory(Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null || category.getDeleteTime() != null && category.getDeleteTime() != 0L) {
            throw new BusinessException("商品分类不存在");
        }
    }

    private void validateRate(BigDecimal rate) {
        if (rate == null
                || rate.compareTo(UserLevelStatusConstants.MIN_EXCLUSIVE_RATE) <= 0
                || rate.compareTo(UserLevelStatusConstants.MAX_INCLUSIVE_RATE) > 0) {
            throw new BusinessException("倍率必须在 (0, 3] 之间");
        }
        if (rate.scale() > 4) {
            throw new BusinessException("倍率最多保留四位小数");
        }
    }
}
