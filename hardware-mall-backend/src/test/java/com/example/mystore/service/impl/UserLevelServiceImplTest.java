package com.example.mystore.service.impl;

import com.example.mystore.common.constant.UserLevelStatusConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.UserLevel;
import com.example.mystore.event.PricingCacheInvalidationEvent;
import com.example.mystore.mapper.LevelCategoryDiscountMapper;
import com.example.mystore.mapper.UserLevelMapper;
import com.example.mystore.mapper.UserMapper;
import com.example.mystore.service.PricingRuleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserLevelServiceImplTest {

    @Mock
    private UserLevelMapper userLevelMapper;
    @Mock
    private LevelCategoryDiscountMapper levelCategoryDiscountMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PricingRuleService pricingRuleService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserLevelServiceImpl userLevelService;

    @Test
    void retailLevelCannotBeRenamedOrDeleted() {
        UserLevel retail = new UserLevel();
        retail.setId(1L);
        retail.setName("零售");
        retail.setIsRetail(UserLevelStatusConstants.IS_RETAIL);
        retail.setStatus(UserLevelStatusConstants.STATUS_ENABLED);
        when(userLevelMapper.selectById(1L)).thenReturn(retail);

        UserLevel rename = new UserLevel();
        rename.setName("新零售");
        assertThatThrownBy(() -> userLevelService.update(1L, rename))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> userLevelService.delete(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void enableRequiresCompleteMatrix() {
        UserLevel level = new UserLevel();
        level.setId(2L);
        level.setIsRetail(UserLevelStatusConstants.NOT_RETAIL);
        level.setStatus(UserLevelStatusConstants.STATUS_DISABLED);
        when(userLevelMapper.selectById(2L)).thenReturn(level);
        when(pricingRuleService.isComplete(2L)).thenReturn(false);

        assertThatThrownBy(() -> userLevelService.updateStatus(2L, UserLevelStatusConstants.STATUS_ENABLED))
                .isInstanceOf(BusinessException.class);
        verify(userLevelMapper, never()).updateById(any(UserLevel.class));
    }

    @Test
    void statusChange_publishesPricingOnlyInvalidation() {
        UserLevel level = customLevel(2L);
        when(userLevelMapper.selectById(2L)).thenReturn(level);
        when(pricingRuleService.isComplete(2L)).thenReturn(true);

        userLevelService.updateStatus(2L, UserLevelStatusConstants.STATUS_ENABLED);

        verify(eventPublisher).publishEvent(new PricingCacheInvalidationEvent(
                PricingCacheInvalidationEvent.Type.LEVEL_AVAILABILITY_CHANGED, 2L));
        verify(pricingRuleService, never()).evictLevelCache(any());
        verify(pricingRuleService, never()).evictRetailLevelIdCache();
    }

    @Test
    void delete_publishesPricingOnlyInvalidation() {
        when(userLevelMapper.selectById(2L)).thenReturn(customLevel(2L));
        when(userMapper.selectCount(any())).thenReturn(0L);

        userLevelService.delete(2L);

        verify(eventPublisher).publishEvent(new PricingCacheInvalidationEvent(
                PricingCacheInvalidationEvent.Type.LEVEL_AVAILABILITY_CHANGED, 2L));
        verify(pricingRuleService, never()).evictLevelCache(any());
        verify(pricingRuleService, never()).evictRetailLevelIdCache();
    }

    private UserLevel customLevel(Long id) {
        UserLevel level = new UserLevel();
        level.setId(id);
        level.setIsRetail(UserLevelStatusConstants.NOT_RETAIL);
        level.setStatus(UserLevelStatusConstants.STATUS_DISABLED);
        return level;
    }
}
