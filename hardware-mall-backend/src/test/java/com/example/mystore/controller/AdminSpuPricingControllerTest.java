package com.example.mystore.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mystore.common.constant.DiscountConstants;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.constant.UserLevelStatusConstants;
import com.example.mystore.entity.db.Category;
import com.example.mystore.entity.db.LevelCategoryRate;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.db.UserLevel;
import com.example.mystore.mapper.CategoryMapper;
import com.example.mystore.mapper.LevelCategoryRateMapper;
import com.example.mystore.mapper.SkuMapper;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.mapper.UserLevelMapper;
import com.example.mystore.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.junit.jupiter.api.Tag("h2")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminSpuPricingControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private UserLevelMapper userLevelMapper;
    @Autowired
    private LevelCategoryRateMapper rateMapper;
    @Autowired
    private SpuMapper spuMapper;
    @Autowired
    private SkuMapper skuMapper;

    private String token;
    private Category category;
    private Category gapCategory;
    private Category excludedGapCategory;
    private UserLevel level;
    private UserLevel retailLevel;

    @BeforeEach
    void setUp() {
        token = jwtUtil.generateToken(Map.of(
                "userId", 1L,
                "role", StatusConstants.USER_ROLE_ADMIN));

        category = category("价格视角分类");
        gapCategory = category("价格缺口分类");
        excludedGapCategory = category("控价缺口分类");

        retailLevel = userLevelMapper.selectOne(new LambdaQueryWrapper<UserLevel>()
                .eq(UserLevel::getIsRetail, UserLevelStatusConstants.IS_RETAIL)
                .last("LIMIT 1"));
        rate(retailLevel.getId(), category.getId(), "1.2000");

        level = new UserLevel();
        level.setName("价格视角等级");
        level.setIsRetail(UserLevelStatusConstants.NOT_RETAIL);
        level.setSortOrder(20);
        level.setStatus(UserLevelStatusConstants.STATUS_ENABLED);
        userLevelMapper.insert(level);

        rate(level.getId(), category.getId(), "0.8000");
        rate(level.getId(), excludedGapCategory.getId(), "0.9000");

        Spu visible = spu(category.getId(), "八折锁具", 1, DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        sku(visible.getId(), "10.00");
        sku(visible.getId(), "20.00");

        spu(category.getId(), "已下架锁具", 0, DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        Spu gap = spu(gapCategory.getId(), "缺口胶类", 1, DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        sku(gap.getId(), "7.50");

        Spu excluded = spu(category.getId(), "控价锁具", 1, DiscountConstants.DISCOUNT_PARTICIPATION_DISABLED);
        sku(excluded.getId(), "30.00");

        Spu excludedGap = spu(excludedGapCategory.getId(), "控价缺口胶类", 1,
                DiscountConstants.DISCOUNT_PARTICIPATION_DISABLED);
        sku(excludedGap.getId(), "50.00");
    }

    @Test
    void pricingListReturnsLevelSettlementRateAndFilteredTotal() throws Exception {
        mvc.perform(get("/api/admin/spu/pricing-list")
                        .header("Authorization", "Bearer " + token)
                        .param("levelId", String.valueOf(level.getId()))
                        .param("categoryId", String.valueOf(category.getId()))
                        .param("keyword", "锁具")
                        .param("status", "1")
                        .param("page", "1")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].retailMinPrice").value(10.0))
                .andExpect(jsonPath("$.data.records[0].retailMaxPrice").value(20.0))
                .andExpect(jsonPath("$.data.records[0].pricingRate").value(0.8))
                .andExpect(jsonPath("$.data.records[0].effectiveRate").value(0.8))
                .andExpect(jsonPath("$.data.records[0].settlementMinPrice").value(8.0))
                .andExpect(jsonPath("$.data.records[0].settlementMaxPrice").value(16.0))
                .andExpect(jsonPath("$.data.records[0].rateMissing").value(false))
                .andExpect(jsonPath("$.data.records[0].excludeDiscount")
                        .value(DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED))
                // 控价商品固定按零售结算价：30.00 × 零售倍率 1.2000 = 36.00
                .andExpect(jsonPath("$.data.records[1].pricingRate").value(0.8))
                .andExpect(jsonPath("$.data.records[1].effectiveRate").value(1.2))
                .andExpect(jsonPath("$.data.records[1].settlementMinPrice").value(36.0))
                .andExpect(jsonPath("$.data.records[1].settlementMaxPrice").value(36.0))
                .andExpect(jsonPath("$.data.records[1].rateMissing").value(false))
                .andExpect(jsonPath("$.data.records[1].excludeDiscount")
                        .value(DiscountConstants.DISCOUNT_PARTICIPATION_DISABLED));
    }

    @Test
    void pricingListMarksMissingRateAsUnsellable() throws Exception {
        mvc.perform(get("/api/admin/spu/pricing-list")
                        .header("Authorization", "Bearer " + token)
                        .param("levelId", String.valueOf(level.getId()))
                        .param("categoryId", String.valueOf(gapCategory.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                // 管理端仍可见批发基价，但缺口不得给出结算价或兜底倍率
                .andExpect(jsonPath("$.data.records[0].retailMinPrice").value(7.5))
                .andExpect(jsonPath("$.data.records[0].pricingRate").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].effectiveRate").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].settlementMinPrice").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].settlementMaxPrice").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].rateMissing").value(true));
    }

    @Test
    void pricingListWithoutLevelUsesRetailMatrix() throws Exception {
        mvc.perform(get("/api/admin/spu/pricing-list")
                        .header("Authorization", "Bearer " + token)
                        .param("categoryId", String.valueOf(category.getId()))
                        .param("keyword", "八折锁具"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].pricingRate").value(1.2))
                .andExpect(jsonPath("$.data.records[0].effectiveRate").value(1.2))
                .andExpect(jsonPath("$.data.records[0].settlementMinPrice").value(12.0))
                .andExpect(jsonPath("$.data.records[0].settlementMaxPrice").value(24.0))
                .andExpect(jsonPath("$.data.records[0].rateMissing").value(false));
    }

    @Test
    void pricingListRetailViewGapIsUnsellable() throws Exception {
        mvc.perform(get("/api/admin/spu/pricing-list")
                        .header("Authorization", "Bearer " + token)
                        .param("categoryId", String.valueOf(gapCategory.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].pricingRate").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].effectiveRate").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].settlementMinPrice").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].settlementMaxPrice").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].rateMissing").value(true));
    }

    @Test
    void excludedProductWithRetailGapIsUnsellable() throws Exception {
        mvc.perform(get("/api/admin/spu/pricing-list")
                        .header("Authorization", "Bearer " + token)
                        .param("levelId", String.valueOf(level.getId()))
                        .param("categoryId", String.valueOf(excludedGapCategory.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                // 视角等级已配置 0.9，但控价商品按零售结算且零售缺口，仍然不可售
                .andExpect(jsonPath("$.data.records[0].pricingRate").value(0.9))
                .andExpect(jsonPath("$.data.records[0].effectiveRate").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].settlementMinPrice").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].rateMissing").value(true));
    }

    @Test
    void disabledLevelCannotBeUsedAsPriceView() throws Exception {
        UserLevel disabled = new UserLevel();
        disabled.setName("停用价格视角");
        disabled.setIsRetail(UserLevelStatusConstants.NOT_RETAIL);
        disabled.setSortOrder(10);
        disabled.setStatus(UserLevelStatusConstants.STATUS_DISABLED);
        userLevelMapper.insert(disabled);

        mvc.perform(get("/api/admin/spu/pricing-list")
                        .header("Authorization", "Bearer " + token)
                        .param("levelId", String.valueOf(disabled.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("价格视角等级未启用"));
    }

    @Test
    void productWithoutSkuUsesOriginalPriceAsBase() throws Exception {
        spu(category.getId(), "无规格商品", 1, DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);

        mvc.perform(get("/api/admin/spu/pricing-list")
                        .header("Authorization", "Bearer " + token)
                        .param("levelId", String.valueOf(level.getId()))
                        .param("keyword", "无规格商品"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].retailMinPrice").value(99.0))
                .andExpect(jsonPath("$.data.records[0].retailMaxPrice").value(99.0))
                .andExpect(jsonPath("$.data.records[0].settlementMinPrice").value(79.2))
                .andExpect(jsonPath("$.data.records[0].settlementMaxPrice").value(79.2));
    }

    private Category category(String name) {
        Category item = new Category();
        item.setName(name);
        item.setSortOrder(0);
        item.setStatus(1);
        item.setDeleteTime(0L);
        categoryMapper.insert(item);
        return item;
    }

    private Spu spu(Long categoryId, String name, int status, int excludeDiscount) {
        Spu item = new Spu();
        item.setCategoryId(categoryId);
        item.setName(name);
        item.setSubtitle("价格视角测试");
        item.setImages(List.of());
        item.setOriginalPrice(new BigDecimal("99.00"));
        item.setSalesCount(excludeDiscount == DiscountConstants.DISCOUNT_PARTICIPATION_DISABLED ? 10 : 20);
        item.setStatus(status);
        item.setIsRecommend(1);
        item.setExcludeDiscount(excludeDiscount);
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        item.setDeleteTime(0L);
        spuMapper.insert(item);
        return item;
    }

    private void sku(Long spuId, String price) {
        Sku item = new Sku();
        item.setSpuId(spuId);
        item.setSpecs(List.of());
        item.setPrice(new BigDecimal(price));
        item.setStock(10);
        item.setStatus(1);
        item.setSpecHash(spuId + ":" + price);
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        item.setDeleteTime(0L);
        skuMapper.insert(item);
    }

    private void rate(Long levelId, Long categoryId, String rate) {
        LevelCategoryRate item = new LevelCategoryRate();
        item.setLevelId(levelId);
        item.setCategoryId(categoryId);
        item.setRate(new BigDecimal(rate));
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        rateMapper.insert(item);
    }
}
