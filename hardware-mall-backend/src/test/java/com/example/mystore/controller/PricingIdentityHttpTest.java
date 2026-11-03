package com.example.mystore.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mystore.common.constant.DiscountConstants;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.constant.UserLevelStatusConstants;
import com.example.mystore.entity.db.Category;
import com.example.mystore.entity.db.LevelCategoryDiscount;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.db.User;
import com.example.mystore.entity.db.UserLevel;
import com.example.mystore.mapper.CategoryMapper;
import com.example.mystore.mapper.LevelCategoryDiscountMapper;
import com.example.mystore.mapper.SkuMapper;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.mapper.UserMapper;
import com.example.mystore.mapper.UserLevelMapper;
import com.example.mystore.util.JwtUtil;
import com.example.mystore.util.RedisUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "wechat.mock-code-enabled=true")
class PricingIdentityHttpTest {

    private static final long CUSTOM_LEVEL_ID = 2002L;
    private static final long PRODUCT_ID = 9201L;
    private static final long CATEGORY_ID = 82L;
    private static final String LOGIN_CODE = "test_pricing_identity_user";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private UserLevelMapper userLevelMapper;
    @Autowired
    private LevelCategoryDiscountMapper levelCategoryDiscountMapper;
    @Autowired
    private SpuMapper spuMapper;
    @Autowired
    private SkuMapper skuMapper;
    @Autowired
    private UserMapper userMapper;

    private String adminToken;

    @BeforeEach
    void setUp() {
        cleanupDatabase();
        clearCachesAndTokens();
        adminToken = jwtUtil.generateToken(Map.of(
                "userId", 1L,
                "role", StatusConstants.USER_ROLE_ADMIN));

        Category category = new Category();
        category.setId(CATEGORY_ID);
        category.setName("身份链测试分类");
        category.setSortOrder(10);
        category.setStatus(1);
        category.setDeleteTime(0L);
        categoryMapper.insert(category);

        UserLevel level = new UserLevel();
        level.setId(CUSTOM_LEVEL_ID);
        level.setName("身份链工程客户");
        level.setIsRetail(UserLevelStatusConstants.NOT_RETAIL);
        level.setSortOrder(60);
        level.setStatus(UserLevelStatusConstants.STATUS_ENABLED);
        userLevelMapper.insert(level);

        LevelCategoryDiscount discount = new LevelCategoryDiscount();
        discount.setLevelId(CUSTOM_LEVEL_ID);
        discount.setCategoryId(CATEGORY_ID);
        discount.setRate(new BigDecimal("0.8000"));
        levelCategoryDiscountMapper.insert(discount);

        Spu spu = new Spu();
        spu.setId(PRODUCT_ID);
        spu.setCategoryId(CATEGORY_ID);
        spu.setName("身份链测试商品");
        spu.setOriginalPrice(new BigDecimal("30.00"));
        spu.setSalesCount(0);
        spu.setStatus(StatusConstants.PRODUCT_STATUS_ON_SHELF);
        spu.setIsRecommend(0);
        spu.setExcludeDiscount(DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        spu.setDeleteTime(0L);
        spuMapper.insert(spu);

        Sku sku = new Sku();
        sku.setId(9301L);
        sku.setSpuId(PRODUCT_ID);
        sku.setSpecs(Collections.emptyList());
        sku.setPrice(new BigDecimal("25.00"));
        sku.setStock(10);
        sku.setStatus(1);
        sku.setSpecHash("identity-chain");
        sku.setDeleteTime(0L);
        skuMapper.insert(sku);
    }

    @AfterEach
    void tearDown() {
        cleanupDatabase();
        clearCachesAndTokens();
    }

    @Test
    void levelChangeInvalidatesAllTokensAndNextLoginReadsNewLevelPrice() {
        LoginSession firstLogin = login(1L);
        LoginSession secondLogin = login(1L);

        JsonNode retailProduct = getProductList(firstLogin.token());
        assertThat(retailProduct.path("minPrice").decimalValue())
                .isEqualByComparingTo("25.00");

        assignLevel(firstLogin.userId());

        assertTokenInvalidated(firstLogin.token());
        assertTokenInvalidated(secondLogin.token());

        assignLevel(firstLogin.userId());

        LoginSession nextLogin = login(CUSTOM_LEVEL_ID);
        JsonNode levelProduct = getProductList(nextLogin.token());
        assertThat(levelProduct.path("minPrice").decimalValue())
                .isEqualByComparingTo("20.00");
        assertThat(levelProduct.path("maxPrice").decimalValue())
                .isEqualByComparingTo("20.00");
        assertThat(levelProduct.has("levelName")).isFalse();
        assertThat(levelProduct.toString()).doesNotContain("wholesale");
    }

    private LoginSession login(long expectedLevelId) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "/api/user/login",
                jsonEntity(Map.of("code", LOGIN_CODE), null),
                JsonNode.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode data = response.getBody().path("data");
        assertThat(data.path("token").asText()).isNotBlank();
        assertThat(data.path("userInfo").path("levelId").asLong()).isEqualTo(expectedLevelId);
        return new LoginSession(
                data.path("userInfo").path("id").asLong(),
                data.path("token").asText());
    }

    private JsonNode getProductList(String token) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/user/product/list",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)),
                JsonNode.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody().path("data").path("records").path(0);
    }

    private void assignLevel(long userId) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/admin/user/" + userId + "/level",
                HttpMethod.PUT,
                jsonEntity(Map.of("levelId", CUSTOM_LEVEL_ID), adminToken),
                JsonNode.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().path("code").asInt()).isEqualTo(200);
    }

    private void assertTokenInvalidated(String token) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/user/info",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)),
                JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody().path("message").asText()).contains("登录已失效");
    }

    private HttpEntity<Map<String, Object>> jsonEntity(Map<String, Object> body, String token) {
        HttpHeaders headers = token == null ? new HttpHeaders() : bearerHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private void clearCachesAndTokens() {
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRODUCT_LIST + "*");
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRODUCT_DETAIL + "*");
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRODUCT_RECOMMEND + "*");
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRICING_LEVEL + "*");
        redisUtil.deleteByPattern(RedisConstants.PREFIX_USER_TOKENS + "*");
        redisUtil.deleteByPattern(RedisConstants.PREFIX_TOKEN_BLACKLIST + "*");
        redisUtil.deleteByPattern(RedisConstants.PREFIX_WECHAT_SESSION + "*");
        redisUtil.deleteByPattern(RedisConstants.PREFIX_RATE_LIMIT + "user:login*");
        redisUtil.delete(RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID);
    }

    private void cleanupDatabase() {
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getOpenid, LOGIN_CODE));
        if (existing != null) {
            userMapper.deleteById(existing.getId());
        }
        skuMapper.deleteById(9301L);
        spuMapper.deleteById(PRODUCT_ID);
        levelCategoryDiscountMapper.delete(new LambdaQueryWrapper<LevelCategoryDiscount>()
                .eq(LevelCategoryDiscount::getLevelId, CUSTOM_LEVEL_ID));
        userLevelMapper.deleteById(CUSTOM_LEVEL_ID);
        categoryMapper.deleteById(CATEGORY_ID);
    }

    private record LoginSession(long userId, String token) {
    }
}
