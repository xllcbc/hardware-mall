package com.example.mystore.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mystore.common.constant.DiscountConstants;
import com.example.mystore.common.constant.RedisConstants;
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
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.AfterEach;
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

/**
 * 列表、推荐与等级预览三个读取面共用等级定价批量入口的 HTTP 契约测试。
 * 缺口商品在三个面上的可售标记与批发基价遮罩必须一致。
 */
@SpringBootTest
@org.junit.jupiter.api.Tag("h2")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductPricingFacesHttpTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private RedisUtil redisUtil;
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

    private String adminToken;
    private UserLevel level;
    private Category pricedCategory;
    private Spu priced;

    @BeforeEach
    void setUp() {
        clearCaches();
        adminToken = jwtUtil.generateToken(Map.of(
                "userId", 1L,
                "role", StatusConstants.USER_ROLE_ADMIN));

        pricedCategory = category("批量报价分类");
        Category gapCategory = category("批量报价缺口分类");

        UserLevel retailLevel = userLevelMapper.selectOne(new LambdaQueryWrapper<UserLevel>()
                .eq(UserLevel::getIsRetail, UserLevelStatusConstants.IS_RETAIL)
                .last("LIMIT 1"));
        rate(retailLevel.getId(), pricedCategory.getId(), "1.0000");

        level = new UserLevel();
        level.setName("批量报价等级");
        level.setIsRetail(UserLevelStatusConstants.NOT_RETAIL);
        level.setSortOrder(20);
        level.setStatus(UserLevelStatusConstants.STATUS_ENABLED);
        userLevelMapper.insert(level);
        rate(level.getId(), pricedCategory.getId(), "0.8000");

        priced = spu(pricedCategory.getId(), "批量报价正常商品", 20);
        sku(priced.getId(), "10.00");
        sku(priced.getId(), "20.00");

        Spu gap = spu(gapCategory.getId(), "批量报价缺口商品", 10);
        sku(gap.getId(), "7.50");
    }

    @AfterEach
    void tearDown() {
        clearCaches();
    }

    @Test
    void productListMarksGapRowsUnsellableAndHidesBasePrice() throws Exception {
        mvc.perform(get("/api/user/product/list").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.records[0].sellable").value(true))
                .andExpect(jsonPath("$.data.records[0].minPrice").value(10.0))
                .andExpect(jsonPath("$.data.records[0].maxPrice").value(20.0))
                .andExpect(jsonPath("$.data.records[0].originalPrice").value(99.0))
                .andExpect(jsonPath("$.data.records[1].sellable").value(false))
                .andExpect(jsonPath("$.data.records[1].minPrice").doesNotExist())
                .andExpect(jsonPath("$.data.records[1].maxPrice").doesNotExist())
                .andExpect(jsonPath("$.data.records[1].originalPrice").doesNotExist());
    }

    @Test
    void recommendMarksGapRowsUnsellableAndHidesBasePrice() throws Exception {
        mvc.perform(get("/api/user/product/recommend").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].sellable").value(true))
                .andExpect(jsonPath("$.data[0].minPrice").value(10.0))
                .andExpect(jsonPath("$.data[0].maxPrice").value(20.0))
                .andExpect(jsonPath("$.data[0].originalPrice").value(99.0))
                .andExpect(jsonPath("$.data[1].sellable").value(false))
                .andExpect(jsonPath("$.data[1].minPrice").doesNotExist())
                .andExpect(jsonPath("$.data[1].maxPrice").doesNotExist())
                .andExpect(jsonPath("$.data[1].originalPrice").doesNotExist());
    }

    @Test
    void productListRangeMatchesDetailForSameLevelViewer() throws Exception {
        String shopperToken = jwtUtil.generateToken(Map.of(
                "userId", 99L,
                "role", StatusConstants.USER_ROLE_REGULAR,
                "levelId", level.getId()));

        mvc.perform(get("/api/user/product/list")
                        .header("Authorization", "Bearer " + shopperToken)
                        .param("categoryId", String.valueOf(pricedCategory.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].minPrice").value(8.0))
                .andExpect(jsonPath("$.data.records[0].maxPrice").value(16.0));

        mvc.perform(get("/api/user/product/{id}", priced.getId())
                        .header("Authorization", "Bearer " + shopperToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skus.length()").value(2))
                .andExpect(jsonPath("$.data.minPrice").value(8.0))
                .andExpect(jsonPath("$.data.maxPrice").value(16.0))
                .andExpect(jsonPath("$.data.sellable").value(true));
    }

    @Test
    void levelPreviewMarksGapRowsUnsellableAndHidesBasePrice() throws Exception {
        mvc.perform(get("/api/admin/user-level/{id}/product-preview", level.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.records[0].sellable").value(true))
                .andExpect(jsonPath("$.data.records[0].pricingRate").value(0.8))
                .andExpect(jsonPath("$.data.records[0].minPrice").value(8.0))
                .andExpect(jsonPath("$.data.records[0].maxPrice").value(16.0))
                .andExpect(jsonPath("$.data.records[0].retailMinPrice").value(10.0))
                .andExpect(jsonPath("$.data.records[0].retailMaxPrice").value(20.0))
                .andExpect(jsonPath("$.data.records[0].originalPrice").value(99.0))
                .andExpect(jsonPath("$.data.records[1].sellable").value(false))
                .andExpect(jsonPath("$.data.records[1].pricingRate").doesNotExist())
                .andExpect(jsonPath("$.data.records[1].minPrice").doesNotExist())
                .andExpect(jsonPath("$.data.records[1].maxPrice").doesNotExist())
                .andExpect(jsonPath("$.data.records[1].retailMinPrice").doesNotExist())
                .andExpect(jsonPath("$.data.records[1].retailMaxPrice").doesNotExist())
                .andExpect(jsonPath("$.data.records[1].originalPrice").doesNotExist());
    }

    private void clearCaches() {
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRODUCT_LIST + "*");
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRODUCT_RECOMMEND + "*");
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRICING_LEVEL + "*");
        redisUtil.delete(RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID);
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

    private Spu spu(Long categoryId, String name, int salesCount) {
        Spu item = new Spu();
        item.setCategoryId(categoryId);
        item.setName(name);
        item.setSubtitle("批量报价测试");
        item.setImages(List.of());
        item.setOriginalPrice(new BigDecimal("99.00"));
        item.setSalesCount(salesCount);
        item.setStatus(StatusConstants.PRODUCT_STATUS_ON_SHELF);
        item.setIsRecommend(1);
        item.setExcludeDiscount(DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
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
