package com.example.mystore.service;

import com.example.mystore.entity.dto.LevelDiscountItemRequest;
import com.example.mystore.entity.vo.LevelDiscountVO;

import java.math.BigDecimal;
import java.util.List;

public interface PricingRuleService {

    BigDecimal rateFor(Long levelId, Long categoryId, boolean excludeDiscount);

    Long resolveRetailLevelId();

    List<LevelDiscountVO> getDiscounts(Long levelId);

    void saveDiscounts(Long levelId, List<LevelDiscountItemRequest> items);

    void fillDiscounts(Long levelId, BigDecimal rate);

    void copyDiscounts(Long levelId, Long sourceLevelId);

    boolean isComplete(Long levelId);

    void evictLevelCache(Long levelId);

    void evictRetailLevelIdCache();
}
