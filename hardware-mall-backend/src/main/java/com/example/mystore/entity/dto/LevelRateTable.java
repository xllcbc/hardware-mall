package com.example.mystore.entity.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 某一生效等级的整张分类倍率表。批量入口只取一次该表，再按分类匹配。
 */
public record LevelRateTable(Long levelId, Map<Long, BigDecimal> rates) {

    public LevelRateTable {
        rates = rates == null ? Map.of() : rates;
    }

    public BigDecimal rateFor(Long categoryId) {
        return categoryId == null ? null : rates.get(categoryId);
    }
}
