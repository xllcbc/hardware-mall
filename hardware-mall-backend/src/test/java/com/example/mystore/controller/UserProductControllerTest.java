package com.example.mystore.controller;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.mapper.SkuMapper;
import com.example.mystore.mapper.SpuMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
    private RedisUtil redisUtil;

    @Autowired
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUpProduct() {
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRODUCT_LIST + "*");
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRODUCT_DETAIL + "*");
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRODUCT_RECOMMEND + "*");

        Spu spu = new Spu();
        spu.setId(9001L);
        spu.setCategoryId(1L);
        spu.setName("定价隔离测试商品");
        spu.setOriginalPrice(new BigDecimal("30.00"));
        spu.setSalesCount(0);
        spu.setStatus(StatusConstants.PRODUCT_STATUS_ON_SHELF);
        spu.setIsRecommend(1);
        spu.setDeleteTime(0L);
        spuMapper.insert(spu);

        skuMapper.insert(sku(9101L, "25.00", "18.00", "spec-a"));
        skuMapper.insert(sku(9102L, "30.00", null, "spec-b"));
    }

    @Test
    void productEndpoint_withInvalidToken_returns401() throws Exception {
        mvc.perform(get("/api/user/product/list")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void productEndpoint_optionsPreflight_remainsPublic() throws Exception {
        mvc.perform(options("/api/user/product/list")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk());
    }

    @Test
    void productEndpoint_withBlacklistedToken_returns401() throws Exception {
        String token = jwtUtil.generateToken(Map.of(
                "userId", 9999L,
                "role", StatusConstants.USER_ROLE_REGULAR,
                "userGroup", StatusConstants.USER_GROUP_WHOLESALE,
                "jti", "blacklisted-product-token"));
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

    @Test
    void productList_withoutToken_returnsRetailEffectivePriceOnly() throws Exception {
        mvc.perform(get("/api/user/product/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].minPrice").value(25.00))
                .andExpect(jsonPath("$.data.records[0].maxPrice").value(30.00))
                .andExpect(jsonPath("$.data.records[0].priceType").value(StatusConstants.PRICE_TYPE_RETAIL))
                .andExpect(jsonPath("$.data.records[0].wholesaleMinPrice").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].wholesaleMaxPrice").doesNotExist());
    }

    @Test
    void recommend_withoutToken_returnsRetailEffectivePriceOnly() throws Exception {
        mvc.perform(get("/api/user/product/recommend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].minPrice").value(25.00))
                .andExpect(jsonPath("$.data[0].maxPrice").value(30.00))
                .andExpect(jsonPath("$.data[0].priceType").value(StatusConstants.PRICE_TYPE_RETAIL))
                .andExpect(jsonPath("$.data[0].wholesaleMinPrice").doesNotExist())
                .andExpect(jsonPath("$.data[0].wholesaleMaxPrice").doesNotExist());
    }

    @Test
    void productList_cacheHit_stillAppliesWholesaleView() throws Exception {
        mvc.perform(get("/api/user/product/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].minPrice").value(25.00));

        String token = jwtUtil.generateToken(Map.of(
                "userId", 9002L,
                "role", StatusConstants.USER_ROLE_REGULAR,
                "userGroup", StatusConstants.USER_GROUP_WHOLESALE,
                "jti", "wholesale-cache-product-token"));

        mvc.perform(get("/api/user/product/list")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].minPrice").value(18.00))
                .andExpect(jsonPath("$.data.records[0].maxPrice").value(30.00))
                .andExpect(jsonPath("$.data.records[0].priceType").value(StatusConstants.PRICE_TYPE_WHOLESALE))
                .andExpect(jsonPath("$.data.records[0].wholesaleMinPrice").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].wholesaleMaxPrice").doesNotExist());
    }

    @Test
    void productDetail_withoutToken_returnsRetailSkuViewOnly() throws Exception {
        mvc.perform(get("/api/user/product/9001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minPrice").value(25.00))
                .andExpect(jsonPath("$.data.maxPrice").value(30.00))
                .andExpect(jsonPath("$.data.skus[0].price").value(25.00))
                .andExpect(jsonPath("$.data.skus[0].priceType").value(StatusConstants.PRICE_TYPE_RETAIL))
                .andExpect(jsonPath("$.data.skus[0].wholesalePrice").doesNotExist())
                .andExpect(jsonPath("$.data.skus[0].specHash").doesNotExist())
                .andExpect(content().string(not(containsString("wholesalePrice"))));
    }

    @Test
    void productSkus_withoutToken_returnsRetailSkuViewOnly() throws Exception {
        mvc.perform(get("/api/user/product/9001/skus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].price").value(25.00))
                .andExpect(jsonPath("$.data[0].priceType").value(StatusConstants.PRICE_TYPE_RETAIL))
                .andExpect(jsonPath("$.data[0].wholesalePrice").doesNotExist())
                .andExpect(jsonPath("$.data[0].specHash").doesNotExist())
                .andExpect(content().string(not(containsString("wholesalePrice"))));
    }

    @Test
    void productDetail_withWholesaleToken_appliesPerSkuFallback() throws Exception {
        String token = jwtUtil.generateToken(Map.of(
                "userId", 9003L,
                "role", StatusConstants.USER_ROLE_REGULAR,
                "userGroup", StatusConstants.USER_GROUP_WHOLESALE,
                "jti", "wholesale-detail-product-token"));

        mvc.perform(get("/api/user/product/9001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minPrice").value(18.00))
                .andExpect(jsonPath("$.data.maxPrice").value(30.00))
                .andExpect(jsonPath("$.data.skus[0].price").value(18.00))
                .andExpect(jsonPath("$.data.skus[0].priceType").value(StatusConstants.PRICE_TYPE_WHOLESALE))
                .andExpect(jsonPath("$.data.skus[1].price").value(30.00))
                .andExpect(jsonPath("$.data.skus[1].priceType").value(StatusConstants.PRICE_TYPE_RETAIL))
                .andExpect(content().string(not(containsString("wholesalePrice"))));
    }

    @Test
    void productDetail_cacheHit_stillAppliesWholesaleView() throws Exception {
        mvc.perform(get("/api/user/product/9001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minPrice").value(25.00));

        String token = jwtUtil.generateToken(Map.of(
                "userId", 9004L,
                "role", StatusConstants.USER_ROLE_REGULAR,
                "userGroup", StatusConstants.USER_GROUP_WHOLESALE,
                "jti", "wholesale-detail-cache-token"));

        mvc.perform(get("/api/user/product/9001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minPrice").value(18.00))
                .andExpect(jsonPath("$.data.maxPrice").value(30.00))
                .andExpect(jsonPath("$.data.skus[0].price").value(18.00))
                .andExpect(jsonPath("$.data.skus[0].priceType").value(StatusConstants.PRICE_TYPE_WHOLESALE))
                .andExpect(content().string(not(containsString("wholesalePrice"))));
    }

    private Sku sku(Long id, String price, String wholesalePrice, String specHash) {
        Sku sku = new Sku();
        sku.setId(id);
        sku.setSpuId(9001L);
        sku.setSpecs(Collections.emptyList());
        sku.setPrice(new BigDecimal(price));
        sku.setWholesalePrice(wholesalePrice == null ? null : new BigDecimal(wholesalePrice));
        sku.setStock(10);
        sku.setStatus(1);
        sku.setSpecHash(specHash);
        sku.setDeleteTime(0L);
        return sku;
    }
}
