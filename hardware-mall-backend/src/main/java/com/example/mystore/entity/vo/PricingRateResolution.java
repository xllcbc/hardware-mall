package com.example.mystore.entity.vo;

import java.math.BigDecimal;

/**
 * 等级分类倍率解析结果。rate 为 null 表示缺口（暂不可售），与显式配置的 1.0000 区分。
 */
public record PricingRateResolution(Long levelId, BigDecimal rate) {

    public static PricingRateResolution resolved(Long levelId, BigDecimal rate) {
        return new PricingRateResolution(levelId, rate);
    }

    public static PricingRateResolution missing(Long levelId) {
        return new PricingRateResolution(levelId, null);
    }

    public boolean isMissing() {
        return rate == null;
    }
}
