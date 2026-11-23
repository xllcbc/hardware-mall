package com.example.mystore.entity.dto;

import java.math.BigDecimal;

/**
 * 顾客侧等级定价结果：生效等级、适用倍率、可售性与等级结算价（单价或区间）。
 *
 * <p>对应 CONTEXT.md 的「等级结算价」「倍率缺口」与「订单等级快照」：
 * 缺口时 {@code sellable} 为 false，倍率与结算价均为空；显式配置的 1.0000
 * 是合法倍率，与缺口严格区分。顾客侧产物不包含批发基价。
 */
public record LevelPricing(
        Long levelId,
        BigDecimal rate,
        boolean sellable,
        BigDecimal minPrice,
        BigDecimal maxPrice) {

    public static LevelPricing missing(Long levelId) {
        return new LevelPricing(levelId, null, false, null, null);
    }

    public static LevelPricing priced(
            Long levelId, BigDecimal rate, BigDecimal minPrice, BigDecimal maxPrice) {
        return new LevelPricing(levelId, rate, true, minPrice, maxPrice);
    }
}
