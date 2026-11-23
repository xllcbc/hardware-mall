package com.example.mystore.service;

import com.example.mystore.entity.dto.LevelPricing;
import com.example.mystore.entity.dto.ProductPricingFacts;

import java.util.List;

/**
 * 等级定价模块：一次调用得到某等级下某商品的可售性与等级结算价（单价或区间）。
 *
 * <p>等级身份由调用方显式传入，模块不读取请求线程上下文。结果对应 CONTEXT.md 的
 * 「等级结算价」「倍率缺口」与「订单等级快照」，不新增领域词。
 */
public interface LevelPricingService {

    /**
     * 单商品入口：计算一个商品事实在该等级下的等级结算价与可售性。
     */
    LevelPricing price(Long levelId, ProductPricingFacts facts);

    /**
     * 批量入口：一次取齐该等级的倍率表，按分类为每个商品事实匹配倍率。
     * 返回顺序与入参一致。
     */
    List<LevelPricing> priceAll(Long levelId, List<ProductPricingFacts> facts);
}
