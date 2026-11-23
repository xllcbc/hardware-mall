package com.example.mystore.service.impl;

import com.example.mystore.entity.dto.LevelPricing;
import com.example.mystore.entity.dto.LevelRateTable;
import com.example.mystore.entity.dto.ProductPricingFacts;
import com.example.mystore.service.LevelPricingService;
import com.example.mystore.service.PriceService;
import com.example.mystore.service.PricingRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 等级定价模块实现。取整仍委托唯一的纯定价函数，倍率解析委托定价缓存适配器；
 * 模块本身只负责把商品事实与整张倍率表匹配成可售性与等级结算价。
 */
@Service
@RequiredArgsConstructor
public class LevelPricingServiceImpl implements LevelPricingService {

    private final PricingRuleService pricingRuleService;
    private final PriceService priceService;

    @Override
    public LevelPricing price(Long levelId, ProductPricingFacts facts) {
        if (facts == null) {
            throw new IllegalArgumentException("商品事实不能为空");
        }
        return priceAll(levelId, List.of(facts)).get(0);
    }

    @Override
    public List<LevelPricing> priceAll(Long levelId, List<ProductPricingFacts> facts) {
        if (facts == null || facts.isEmpty()) {
            return List.of();
        }
        boolean usesRetail = facts.stream().anyMatch(ProductPricingFacts::excludeDiscount);
        boolean usesLevel = facts.stream().anyMatch(fact -> !fact.excludeDiscount());

        LevelRateTable levelTable = usesLevel ? pricingRuleService.rateTable(levelId) : null;
        LevelRateTable retailTable = usesRetail
                ? pricingRuleService.rateTable(pricingRuleService.resolveRetailLevelId())
                : null;

        List<LevelPricing> result = new ArrayList<>(facts.size());
        for (ProductPricingFacts fact : facts) {
            result.add(resolve(fact.excludeDiscount() ? retailTable : levelTable, fact));
        }
        return result;
    }

    private LevelPricing resolve(LevelRateTable rateTable, ProductPricingFacts facts) {
        BigDecimal rate = rateTable.rateFor(facts.categoryId());
        if (rate == null) {
            return LevelPricing.missing(rateTable.levelId());
        }
        return LevelPricing.priced(
                rateTable.levelId(),
                rate,
                resolvePrice(facts.basePriceMin(), rate),
                resolvePrice(facts.basePriceMax(), rate));
    }

    private BigDecimal resolvePrice(BigDecimal basePrice, BigDecimal rate) {
        return basePrice == null ? null : priceService.resolveUnitPrice(basePrice, rate);
    }
}
