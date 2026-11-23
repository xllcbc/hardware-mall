package com.example.mystore.entity.dto;

import java.math.BigDecimal;

/**
 * 一次等级定价计算所需的商品事实：分类、批发基价（或基价区间）与是否不参与折扣。
 * 只承载输入，不包含任何等级结论。
 */
public record ProductPricingFacts(
        Long categoryId,
        BigDecimal basePriceMin,
        BigDecimal basePriceMax,
        boolean excludeDiscount) {

    public static ProductPricingFacts of(
            Long categoryId, BigDecimal basePrice, boolean excludeDiscount) {
        return new ProductPricingFacts(categoryId, basePrice, basePrice, excludeDiscount);
    }

    public static ProductPricingFacts range(
            Long categoryId, BigDecimal basePriceMin, BigDecimal basePriceMax, boolean excludeDiscount) {
        return new ProductPricingFacts(categoryId, basePriceMin, basePriceMax, excludeDiscount);
    }
}
