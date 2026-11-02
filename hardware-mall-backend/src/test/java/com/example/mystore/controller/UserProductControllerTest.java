package com.example.mystore.controller;

import com.example.mystore.common.constant.DiscountConstants;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.constant.UserLevelStatusConstants;
import com.example.mystore.entity.db.LevelCategoryDiscount;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.db.UserLevel;
import com.example.mystore.mapper.LevelCategoryDiscountMapper;
import com.example.mystore.mapper.SkuMapper;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.mapper.UserLevelMapper;
import com.example.mystore.util.JwtUtil;
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserProductControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private SpuMapper spuMapper;
    @Autowired
    private SkuMapper skuMapper;
    @Autowired
    private UserLevelMapper userLevelMapper;
    @Autowired
    private LevelCategoryDiscountMapper levelCategoryDiscountMapper;
    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUpProduct() {
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRODUCT_LIST + "*");
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRODUCT_DETAIL + "*");
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRODUCT_RECOMMEND + "*");
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRICING_LEVEL + "*");
        redisUtil.delete(RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID);

        UserLevel level = new UserLevel();
        level.setId(2L);
        level.setName("批发A");
        level.setIsRetail(UserLevelStatusConstants.NOT_RETAIL);
        level.setSortOrder(90);
        level.setStatus(UserLevelStatusConstants.STATUS_ENABLED);
        userLevelMapper.insert(level);

        LevelCategoryDiscount discount = new LevelCategoryDiscount();
        discount.setLevelId(2L);
        discount.setCategoryId(1L);
        discount.setRate(new BigDecimal("0.8000"));
        levelCategoryDiscountMapper.insert(discount);

        Spu spu = new Spu();
        spu.setId(9001L);
        spu.setCategoryId(1L);
        spu.setName("多级定价测试商品");
        spu.setOriginalPrice(new BigDecimal("30.00"));
        spu.setSalesCount(0);
        spu.setStatus(StatusConstants.PRODUCT_STATUS_ON_SHELF);
        spu.setIsRecommend(1);
        spu.setExcludeDiscount(DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        spu.setDeleteTime(0L);
        spuMapper.insert(spu);

        skuMapper.insert(sku(9101L, "25.00", "spec-a"));
        skuMapper.insert(sku(9102L, "30.00", "spec-b"));
    }

    @Test
    void invalidTokenReturns401() throws Exception {
        mvc.perform(get("/api/user/product/list")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyRegularTokenWithoutLevelIdReturns401() throws Exception {
        String token = jwtUtil.generateToken(Map.of(
                "userId", 9999L,
                "role", StatusConstants.USER_ROLE_REGULAR,
                "userGroup", 2));

        mvc.perform(get("/api/user/product/list")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminTokenWithoutLevelIdCanBrowseAsRetail() throws Exception {
        String token = jwtUtil.generateToken(Map.of(
                "userId", 1L,
                "role", StatusConstants.USER_ROLE_ADMIN));

        mvc.perform(get("/api/user/product/list")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].minPrice").value(25.00));
    }

    @Test
    void optionsPreflightRemainsPublic() throws Exception {
        mvc.perform(options("/api/user/product/list")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousListUsesRetailPriceAndOmitsPricingType() throws Exception {
        mvc.perform(get("/api/user/product/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].minPrice").value(25.00))
                .andExpect(jsonPath("$.data.records[0].maxPrice").value(30.00))
                .andExpect(jsonPath("$.data.records[0].priceType").doesNotExist())
                .andExpect(content().string(not(containsString("wholesale"))));
    }

    @Test
    void customLevelAppliesRateOnCacheHit() throws Exception {
        mvc.perform(get("/api/user/product/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].minPrice").value(25.00));

        String token = userToken(2L, "level-cache-token");
        mvc.perform(get("/api/user/product/list")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].minPrice").value(20.00))
                .andExpect(jsonPath("$.data.records[0].maxPrice").value(24.00));

        mvc.perform(get("/api/user/product/list")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].minPrice").value(20.00));
    }

    @Test
    void detailUsesLevelPriceAndHidesInternalSkuFields() throws Exception {
        String token = userToken(2L, "level-detail-token");
        mvc.perform(get("/api/user/product/9001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skus[0].price").value(20.00))
                .andExpect(jsonPath("$.data.minPrice").value(20.00))
                .andExpect(jsonPath("$.data.maxPrice").value(24.00))
                .andExpect(jsonPath("$.data.skus[0].specHash").doesNotExist())
                .andExpect(jsonPath("$.data.skus[0].wholesalePrice").doesNotExist())
                .andExpect(jsonPath("$.data.skus[0].priceType").doesNotExist());
    }

    @Test
    void skusEndpointReusesDetailPricing() throws Exception {
        String token = userToken(2L, "level-skus-token");
        mvc.perform(get("/api/user/product/9001/skus")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].price").value(20.00))
                .andExpect(content().string(not(containsString("wholesale"))));
    }

    @Test
    void blacklistedTokenReturns401() throws Exception {
        String token = userToken(2L, "blacklisted-level-token");
        String blacklistKey = RedisConstants.PREFIX_TOKEN_BLACKLIST + token;
        redisUtil.set(blacklistKey, "1", 60, TimeUnit.SECONDS);
        try {
            mvc.perform(get("/api/user/product/list")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        } finally {
            redisUtil.delete(blacklistKey);
        }
    }

    private String userToken(Long levelId, String jti) {
        return jwtUtil.generateToken(Map.of(
                "userId", 9002L,
                "role", StatusConstants.USER_ROLE_REGULAR,
                "levelId", levelId,
                "jti", jti));
    }

    private Sku sku(Long id, String price, String specHash) {
        Sku sku = new Sku();
        sku.setId(id);
        sku.setSpuId(9001L);
        sku.setSpecs(Collections.emptyList());
        sku.setPrice(new BigDecimal(price));
        sku.setStock(10);
        sku.setStatus(1);
        sku.setSpecHash(specHash);
        sku.setDeleteTime(0L);
        return sku;
    }
}
