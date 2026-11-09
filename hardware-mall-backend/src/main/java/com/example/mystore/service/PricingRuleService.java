package com.example.mystore.service;

import com.example.mystore.entity.dto.LevelRateItemRequest;
import com.example.mystore.entity.vo.LevelRateVO;

import java.math.BigDecimal;
import java.util.List;

public interface PricingRuleService {

    BigDecimal rateFor(Long levelId, Long categoryId, boolean excludeDiscount);

    Long resolveRetailLevelId();

    List<LevelRateVO> getRates(Long levelId);

    void saveRates(Long levelId, List<LevelRateItemRequest> items);

    void fillRates(Long levelId, BigDecimal rate);

    void copyRates(Long levelId, Long sourceLevelId);

    boolean isComplete(Long levelId);
}
