package com.example.mystore.entity.dto;

import java.math.BigDecimal;

/**
 * 管理端等级定价结果：批发基价、视角等级与零售等级两份报价，以及最终生效报价。
 *
 * <p>管理入口额外返回批发基价与零售等级结算价，用于组装管理端价格视角；
 * 顾客侧 {@link LevelPricing} 在类型上不包含这些字段。缺口时
 * {@link #effective()} 不可售且 {@link #rateMissing()} 为 true，与显式 1.0000 严格区分。
 */
public record AdminLevelPricing(
        BigDecimal basePriceMin,
        BigDecimal basePriceMax,
        LevelPricing viewLevel,
        LevelPricing retailLevel,
        LevelPricing effective) {

    /**
     * 缺口标记：生效报价不可售，表示该分类在生效等级下缺少倍率。
     */
    public boolean rateMissing() {
        return effective == null || !effective.sellable();
    }
}
