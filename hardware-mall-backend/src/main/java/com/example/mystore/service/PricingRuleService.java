package com.example.mystore.service;

import com.example.mystore.entity.dto.LevelRateItemRequest;
import com.example.mystore.entity.dto.LevelRateTable;
import com.example.mystore.entity.vo.LevelRateVO;
import com.example.mystore.entity.dto.PricingRateResolution;

import java.math.BigDecimal;
import java.util.List;

public interface PricingRuleService {

    PricingRateResolution rateFor(Long levelId, Long categoryId, boolean excludeDiscount);

    /**
     * 一次取齐生效等级的整张分类倍率表；等级为空或不存在时回落到零售等级。
     */
    LevelRateTable rateTable(Long levelId);

    Long resolveRetailLevelId();

    List<LevelRateVO> getRates(Long levelId);

    void saveRates(Long levelId, List<LevelRateItemRequest> items);

    void fillRates(Long levelId, BigDecimal rate);

    void copyRates(Long levelId, Long sourceLevelId);

    boolean isComplete(Long levelId);
}
