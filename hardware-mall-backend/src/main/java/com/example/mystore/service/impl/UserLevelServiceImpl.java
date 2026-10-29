package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mystore.common.constant.UserLevelStatusConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.LevelCategoryDiscount;
import com.example.mystore.entity.db.User;
import com.example.mystore.entity.db.UserLevel;
import com.example.mystore.entity.vo.UserLevelVO;
import com.example.mystore.event.PricingCacheInvalidationEvent;
import com.example.mystore.mapper.LevelCategoryDiscountMapper;
import com.example.mystore.mapper.UserLevelMapper;
import com.example.mystore.mapper.UserMapper;
import com.example.mystore.service.PricingRuleService;
import com.example.mystore.service.UserLevelService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserLevelServiceImpl implements UserLevelService {

    private final UserLevelMapper userLevelMapper;
    private final LevelCategoryDiscountMapper levelCategoryDiscountMapper;
    private final UserMapper userMapper;
    private final PricingRuleService pricingRuleService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<UserLevelVO> list() {
        List<UserLevel> levels = userLevelMapper.selectList(new LambdaQueryWrapper<UserLevel>()
                .orderByDesc(UserLevel::getSortOrder)
                .orderByAsc(UserLevel::getId));
        List<UserLevelVO> result = new ArrayList<>(levels.size());
        for (UserLevel level : levels) {
            UserLevelVO vo = new UserLevelVO();
            vo.setId(level.getId());
            vo.setName(level.getName());
            vo.setIsRetail(level.getIsRetail());
            vo.setSortOrder(level.getSortOrder());
            vo.setStatus(level.getStatus());
            vo.setUserCount(userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getLevelId, level.getId())));
            result.add(vo);
        }
        return result;
    }

    @Override
    public UserLevel getById(Long id) {
        UserLevel level = userLevelMapper.selectById(id);
        if (level == null) {
            throw new BusinessException("用户等级不存在");
        }
        return level;
    }

    @Override
    public UserLevel create(UserLevel level) {
        if (level == null || !StringUtils.hasText(level.getName())) {
            throw new BusinessException("等级名称不能为空");
        }
        String name = level.getName().trim();
        Long count = userLevelMapper.selectCount(new LambdaQueryWrapper<UserLevel>()
                .eq(UserLevel::getName, name));
        if (count != null && count > 0) {
            throw new BusinessException("等级名称已存在");
        }
        UserLevel target = new UserLevel();
        target.setName(name);
        target.setIsRetail(UserLevelStatusConstants.NOT_RETAIL);
        target.setSortOrder(level.getSortOrder() == null ? 0 : level.getSortOrder());
        target.setStatus(UserLevelStatusConstants.STATUS_DISABLED);
        target.setCreateTime(LocalDateTime.now());
        target.setUpdateTime(LocalDateTime.now());
        userLevelMapper.insert(target);
        return target;
    }

    @Override
    public UserLevel update(Long id, UserLevel level) {
        UserLevel exist = getById(id);
        boolean retail = Integer.valueOf(UserLevelStatusConstants.IS_RETAIL).equals(exist.getIsRetail());
        if (retail && StringUtils.hasText(level.getName()) && !level.getName().trim().equals(exist.getName())) {
            throw new BusinessException("零售等级不可改名");
        }
        if (!retail && StringUtils.hasText(level.getName())) {
            String name = level.getName().trim();
            Long count = userLevelMapper.selectCount(new LambdaQueryWrapper<UserLevel>()
                    .eq(UserLevel::getName, name)
                    .ne(UserLevel::getId, id));
            if (count != null && count > 0) {
                throw new BusinessException("等级名称已存在");
            }
            exist.setName(name);
        }
        if (level.getSortOrder() != null) {
            exist.setSortOrder(level.getSortOrder());
        }
        exist.setUpdateTime(LocalDateTime.now());
        userLevelMapper.updateById(exist);
        pricingRuleService.evictLevelCache(id);
        return exist;
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        UserLevel level = getById(id);
        if (Integer.valueOf(UserLevelStatusConstants.IS_RETAIL).equals(level.getIsRetail())) {
            throw new BusinessException("零售等级固定启用，不可停用");
        }
        if (status == null
                || (status != UserLevelStatusConstants.STATUS_ENABLED
                && status != UserLevelStatusConstants.STATUS_DISABLED)) {
            throw new BusinessException("无效的等级状态");
        }
        if (status == UserLevelStatusConstants.STATUS_ENABLED && !pricingRuleService.isComplete(id)) {
            throw new BusinessException("等级倍率矩阵未填满，不能启用");
        }
        level.setStatus(status);
        level.setUpdateTime(LocalDateTime.now());
        userLevelMapper.updateById(level);
        eventPublisher.publishEvent(new PricingCacheInvalidationEvent(
                PricingCacheInvalidationEvent.Type.LEVEL_AVAILABILITY_CHANGED, id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        UserLevel level = getById(id);
        if (Integer.valueOf(UserLevelStatusConstants.IS_RETAIL).equals(level.getIsRetail())) {
            throw new BusinessException("零售等级不可删除");
        }
        Long users = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getLevelId, id));
        if (users != null && users > 0) {
            throw new BusinessException("该等级仍有关联用户，不能删除");
        }
        levelCategoryDiscountMapper.delete(new LambdaQueryWrapper<LevelCategoryDiscount>()
                .eq(LevelCategoryDiscount::getLevelId, id));
        userLevelMapper.deleteById(id);
        eventPublisher.publishEvent(new PricingCacheInvalidationEvent(
                PricingCacheInvalidationEvent.Type.LEVEL_AVAILABILITY_CHANGED, id));
    }

    @Override
    public String getName(Long id) {
        if (id == null) {
            return null;
        }
        UserLevel level = userLevelMapper.selectById(id);
        return level == null ? null : level.getName();
    }
}
