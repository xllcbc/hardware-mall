package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.constant.UserLevelStatusConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.Category;
import com.example.mystore.entity.db.LevelCategoryDiscount;
import com.example.mystore.entity.db.UserLevel;
import com.example.mystore.entity.dto.LevelDiscountItemRequest;
import com.example.mystore.entity.vo.LevelDiscountVO;
import com.example.mystore.entity.vo.PricingLevelCache;
import com.example.mystore.event.PricingCacheInvalidationEvent;
import com.example.mystore.mapper.CategoryMapper;
import com.example.mystore.mapper.LevelCategoryDiscountMapper;
import com.example.mystore.mapper.UserLevelMapper;
import com.example.mystore.service.PricingRuleService;
import com.example.mystore.util.RedisUtil;
import lombok.RequiredArgsConstructor;
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
    private final LevelCategoryDiscountMapper levelCategoryDiscountMapper;
    private final CategoryMapper categoryMapper;
    private final RedisUtil redisUtil;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public BigDecimal rateFor(Long levelId, Long categoryId, boolean excludeDiscount) {
        if (excludeDiscount || levelId == null || categoryId == null) {
            return UserLevelStatusConstants.DEFAULT_RATE;
        }
        Long retailLevelId = resolveRetailLevelId();
        if (retailLevelId != null && retailLevelId.equals(levelId)) {
            return UserLevelStatusConstants.DEFAULT_RATE;
        }
        PricingLevelCache cache = getLevelCache(levelId);
        if (cache == null || cache.getRates() == null) {
            return UserLevelStatusConstants.DEFAULT_RATE;
        }
        return cache.getRates().getOrDefault(categoryId, UserLevelStatusConstants.DEFAULT_RATE);
    }

    @Override
    public Long resolveRetailLevelId() {
        return redisUtil.queryWithCache(
                RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID,
                Long.class,
                RedisConstants.CACHE_TTL_HOUR,
                () -> {
                    UserLevel retail = userLevelMapper.selectOne(new LambdaQueryWrapper<UserLevel>()
                            .eq(UserLevel::getIsRetail, UserLevelStatusConstants.IS_RETAIL)
                            .eq(UserLevel::getStatus, UserLevelStatusConstants.STATUS_ENABLED)
                            .last("LIMIT 1"));
                    return retail == null ? UserLevelStatusConstants.RETAIL_LEVEL_ID : retail.getId();
                });
    }

    @Override
    public List<LevelDiscountVO> getDiscounts(Long levelId) {
        UserLevel level = requireLevel(levelId);
        if (Integer.valueOf(UserLevelStatusConstants.IS_RETAIL).equals(level.getIsRetail())) {
            return List.of();
        }
        List<Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getDeleteTime, 0)
                .orderByDesc(Category::getSortOrder)
                .orderByAsc(Category::getId));
        Map<Long, BigDecimal> rates = loadRates(levelId);
        List<LevelDiscountVO> result = new ArrayList<>(categories.size());
        for (Category category : categories) {
            BigDecimal rate = rates.get(category.getId());
            LevelDiscountVO vo = new LevelDiscountVO();
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
    public void saveDiscounts(Long levelId, List<LevelDiscountItemRequest> items) {
        UserLevel level = requireLevel(levelId);
        rejectRetail(level);
        if (items == null || items.isEmpty()) {
            throw new BusinessException("倍率明细不能为空");
        }
        Set<Long> seen = new HashSet<>();
        for (LevelDiscountItemRequest item : items) {
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
    public void fillDiscounts(Long levelId, BigDecimal rate) {
        UserLevel level = requireLevel(levelId);
        rejectRetail(level);
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
    public void copyDiscounts(Long levelId, Long sourceLevelId) {
        UserLevel target = requireLevel(levelId);
        rejectRetail(target);
        if (sourceLevelId == null) {
            throw new BusinessException("来源等级不能为空");
        }
        if (levelId.equals(sourceLevelId)) {
            throw new BusinessException("不能复制到自身");
        }
        UserLevel source = requireLevel(sourceLevelId);
        if (Integer.valueOf(UserLevelStatusConstants.IS_RETAIL).equals(source.getIsRetail())) {
            throw new BusinessException("零售等级没有可复制的倍率矩阵");
        }
        Map<Long, BigDecimal> sourceRates = loadRates(sourceLevelId);
        for (Map.Entry<Long, BigDecimal> entry : sourceRates.entrySet()) {
            upsertRate(levelId, entry.getKey(), entry.getValue());
        }
        eventPublisher.publishEvent(new PricingCacheInvalidationEvent(
                PricingCacheInvalidationEvent.Type.MATRIX_CHANGED, levelId));
    }

    @Override
    public boolean isComplete(Long levelId) {
        UserLevel level = requireLevel(levelId);
        if (Integer.valueOf(UserLevelStatusConstants.IS_RETAIL).equals(level.getIsRetail())) {
            return true;
        }
        List<Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getDeleteTime, 0));
        if (categories.isEmpty()) {
            return true;
        }
        Set<Long> configured = loadRates(levelId).keySet();
        return categories.stream().allMatch(category -> configured.contains(category.getId()));
    }

    @Override
    public void evictLevelCache(Long levelId) {
        if (levelId != null) {
            redisUtil.evictWithLock(RedisConstants.PREFIX_PRICING_LEVEL + levelId);
        }
    }

    @Override
    public void evictRetailLevelIdCache() {
        redisUtil.evictWithLock(RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID);
    }

    private PricingLevelCache getLevelCache(Long levelId) {
        return redisUtil.queryWithCache(
                RedisConstants.PREFIX_PRICING_LEVEL + levelId,
                PricingLevelCache.class,
                RedisConstants.CACHE_TTL_HOUR,
                () -> {
                    PricingLevelCache cache = new PricingLevelCache();
                    cache.setRates(loadRates(levelId));
                    return cache;
                });
    }

    private Map<Long, BigDecimal> loadRates(Long levelId) {
        List<LevelCategoryDiscount> rows = levelCategoryDiscountMapper.selectList(
                new LambdaQueryWrapper<LevelCategoryDiscount>()
                        .eq(LevelCategoryDiscount::getLevelId, levelId));
        Map<Long, BigDecimal> rates = new HashMap<>();
        for (LevelCategoryDiscount row : rows) {
            if (row.getCategoryId() != null && row.getRate() != null) {
                rates.put(row.getCategoryId(), row.getRate());
            }
        }
        return rates;
    }

    private void upsertRate(Long levelId, Long categoryId, BigDecimal rate) {
        LevelCategoryDiscount existing = levelCategoryDiscountMapper.selectOne(
                new LambdaQueryWrapper<LevelCategoryDiscount>()
                        .eq(LevelCategoryDiscount::getLevelId, levelId)
                        .eq(LevelCategoryDiscount::getCategoryId, categoryId)
                        .last("LIMIT 1"));
        if (existing == null) {
            LevelCategoryDiscount row = new LevelCategoryDiscount();
            row.setLevelId(levelId);
            row.setCategoryId(categoryId);
            row.setRate(rate);
            row.setCreateTime(LocalDateTime.now());
            row.setUpdateTime(LocalDateTime.now());
            levelCategoryDiscountMapper.insert(row);
            return;
        }
        existing.setRate(rate);
        existing.setUpdateTime(LocalDateTime.now());
        levelCategoryDiscountMapper.updateById(existing);
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

    private void rejectRetail(UserLevel level) {
        if (Integer.valueOf(UserLevelStatusConstants.IS_RETAIL).equals(level.getIsRetail())) {
            throw new BusinessException("零售等级不维护倍率矩阵");
        }
    }

    private void validateRate(BigDecimal rate) {
        if (rate == null
                || rate.compareTo(UserLevelStatusConstants.MIN_EXCLUSIVE_RATE) <= 0
                || rate.compareTo(UserLevelStatusConstants.MAX_INCLUSIVE_RATE) > 0) {
            throw new BusinessException("倍率必须在 (0, 1] 之间");
        }
        if (rate.scale() > 4) {
            throw new BusinessException("倍率最多保留四位小数");
        }
    }
}
