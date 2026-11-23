package com.example.mystore.service.impl;

import com.example.mystore.common.constant.UserLevelStatusConstants;
import com.example.mystore.entity.dto.AdminLevelPricing;
import com.example.mystore.entity.dto.LevelPricing;
import com.example.mystore.entity.dto.LevelRateTable;
import com.example.mystore.entity.dto.ProductPricingFacts;
import com.example.mystore.service.LevelPricingService;
import com.example.mystore.service.PricingRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 等级定价模块实现。倍率解析委托定价缓存适配器；等级结算价按批发基价乘以倍率、
 * 人民币两位小数四舍五入，这是全链路唯一的取整位置。
 */
@Service
@RequiredArgsConstructor
public class LevelPricingServiceImpl implements LevelPricingService {

    private final PricingRuleService pricingRuleService;

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

    @Override
    public List<AdminLevelPricing> priceAllForAdmin(
            Long viewLevelId, List<ProductPricingFacts> facts) {
        if (facts == null || facts.isEmpty()) {
            return List.of();
        }
        Long retailLevelId = pricingRuleService.resolveRetailLevelId();
        LevelRateTable viewTable = pricingRuleService.rateTable(viewLevelId);
        LevelRateTable retailTable = Objects.equals(viewTable.levelId(), retailLevelId)
                ? viewTable
                : pricingRuleService.rateTable(retailLevelId);

        List<AdminLevelPricing> result = new ArrayList<>(facts.size());
        for (ProductPricingFacts fact : facts) {
            LevelPricing view = resolve(viewTable, fact);
            LevelPricing retail = resolve(retailTable, fact);
            LevelPricing effective = fact.excludeDiscount() ? retail : view;
            result.add(new AdminLevelPricing(
                    fact.basePriceMin(), fact.basePriceMax(), view, retail, effective));
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
        if (basePrice == null) {
            return null;
        }
        if (basePrice.signum() < 0) {
            throw new IllegalArgumentException("基础价格不能为负");
        }
        if (rate == null
                || rate.compareTo(UserLevelStatusConstants.MIN_EXCLUSIVE_RATE) <= 0
                || rate.compareTo(UserLevelStatusConstants.MAX_INCLUSIVE_RATE) > 0) {
            throw new IllegalArgumentException("定价倍率必须在 (0, 3] 之间");
        }
        return basePrice.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}
