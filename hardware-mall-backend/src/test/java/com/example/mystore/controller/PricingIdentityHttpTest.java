package com.example.mystore.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mystore.common.constant.DiscountConstants;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.constant.UserLevelStatusConstants;
import com.example.mystore.entity.db.Address;
import com.example.mystore.entity.db.Cart;
import com.example.mystore.entity.db.Category;
import com.example.mystore.entity.db.LevelCategoryRate;
import com.example.mystore.entity.db.Logistics;
import com.example.mystore.entity.db.Order;
import com.example.mystore.entity.db.OrderItem;
import com.example.mystore.entity.db.PaymentRecord;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.db.User;
import com.example.mystore.entity.db.UserLevel;
import com.example.mystore.mapper.CategoryMapper;
import com.example.mystore.mapper.AddressMapper;
import com.example.mystore.mapper.CartMapper;
import com.example.mystore.mapper.LevelCategoryRateMapper;
import com.example.mystore.mapper.LogisticsMapper;
import com.example.mystore.mapper.OrderItemMapper;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.mapper.PaymentRecordMapper;
import com.example.mystore.mapper.SkuMapper;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.mapper.UserMapper;
import com.example.mystore.mapper.UserLevelMapper;
import com.example.mystore.service.SkuService;
import com.example.mystore.util.JwtUtil;
import com.example.mystore.util.RedisUtil;
import com.example.mystore.util.WechatUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.junit.jupiter.api.Tag("h2")
@ActiveProfiles("test")
class PricingIdentityHttpTest {

    private static final long CUSTOM_LEVEL_ID = 2002L;
    private static final long PRODUCT_ID = 9201L;
    private static final long CATEGORY_ID = 82L;
    private static final long ADDRESS_ID = 9401L;
    private static final long LOGISTICS_ID = 9501L;
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
    private AddressMapper addressMapper;
    @Autowired
    private CartMapper cartMapper;
    @Autowired
    private UserLevelMapper userLevelMapper;
    @Autowired
    private LevelCategoryRateMapper levelCategoryRateMapper;
    @Autowired
    private LogisticsMapper logisticsMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private PaymentRecordMapper paymentRecordMapper;
    @Autowired
    private SpuMapper spuMapper;
    @Autowired
    private SkuMapper skuMapper;
    @Autowired
    private UserMapper userMapper;
    @SpyBean
    private SkuService skuService;
    @SpyBean
    private WechatUtil wechatUtil;

    private String adminToken;

    @BeforeEach
    void setUp() {
        cleanupDatabase();
        clearCachesAndTokens();
        doReturn(Map.of("openid", LOGIN_CODE, "session_key", "test-session-key"))
                .when(wechatUtil).getSessionKey(LOGIN_CODE);
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

        LevelCategoryRate rate = new LevelCategoryRate();
        rate.setLevelId(CUSTOM_LEVEL_ID);
        rate.setCategoryId(CATEGORY_ID);
        rate.setRate(new BigDecimal("0.8000"));
        levelCategoryRateMapper.insert(rate);

        LevelCategoryRate retailRate = new LevelCategoryRate();
        retailRate.setLevelId(UserLevelStatusConstants.RETAIL_LEVEL_ID);
        retailRate.setCategoryId(CATEGORY_ID);
        retailRate.setRate(new BigDecimal("1.0000"));
        levelCategoryRateMapper.insert(retailRate);

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

    @Test
    void checkoutPreviewAndCreateOrderUseOneHttpPricingChainAndRejectChangedAmount() {
        LoginSession session = login(1L);
        assignLevel(session.userId());
        session = login(CUSTOM_LEVEL_ID);
        insertAddressAndLogistics(session.userId());

        JsonNode previewResponse = postJson(
                "/api/user/order/preview",
                Map.of("items", List.of(Map.of("skuId", 9301L, "quantity", 2))),
                session.token(), null);
        assertThat(previewResponse.path("code").asInt()).isEqualTo(200);
        JsonNode preview = previewResponse.path("data");
        assertThat(preview.path("items").path(0).path("price").decimalValue())
                .isEqualByComparingTo("20.00");
        assertThat(preview.path("items").path(0).path("subtotal").decimalValue())
                .isEqualByComparingTo("40.00");
        assertThat(preview.path("goodsAmount").decimalValue()).isEqualByComparingTo("40.00");
        assertThat(preview.path("freightAmount").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(preview.path("payAmount").decimalValue()).isEqualByComparingTo("40.00");

        JsonNode createdResponse = postJson(
                "/api/user/order/create",
                Map.of(
                        "items", List.of(Map.of("skuId", 9301L, "quantity", 2)),
                        "addressId", ADDRESS_ID,
                        "logisticsId", LOGISTICS_ID,
                        "expectedPayAmount", new BigDecimal("40.00")),
                session.token(), "http-price-ok");
        assertThat(createdResponse.path("code").asInt()).isEqualTo(200);
        assertThat(createdResponse.path("data").path("payAmount").decimalValue())
                .isEqualByComparingTo("40.00");

        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, session.userId())
                .orderByDesc(Order::getId)
                .last("LIMIT 1"));
        assertThat(order).isNotNull();
        assertThat(order.getPayAmount()).isEqualByComparingTo("40.00");
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, order.getId()));
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getLevelId()).isEqualTo(CUSTOM_LEVEL_ID);
        assertThat(items.get(0).getLevelName()).isEqualTo("身份链工程客户");
        assertThat(items.get(0).getPricingRate()).isEqualByComparingTo("0.8000");

        Cart cart = new Cart();
        cart.setUserId(session.userId());
        cart.setSkuId(9301L);
        cart.setQuantity(2);
        cart.setDeleteTime(0L);
        cartMapper.insert(cart);

        LevelCategoryRate changed = levelCategoryRateMapper.selectOne(
                new LambdaQueryWrapper<LevelCategoryRate>()
                        .eq(LevelCategoryRate::getLevelId, CUSTOM_LEVEL_ID)
                        .eq(LevelCategoryRate::getCategoryId, CATEGORY_ID));
        changed.setRate(new BigDecimal("0.7000"));
        levelCategoryRateMapper.updateById(changed);
        redisUtil.delete(RedisConstants.PREFIX_PRICING_LEVEL + CUSTOM_LEVEL_ID);

        JsonNode changedResponse = postJson(
                "/api/user/order/create",
                Map.of(
                        "items", List.of(Map.of("skuId", 9301L, "quantity", 2)),
                        "addressId", ADDRESS_ID,
                        "logisticsId", LOGISTICS_ID,
                        "expectedPayAmount", new BigDecimal("40.00")),
                session.token(), "http-price-changed");
        assertThat(changedResponse.path("code").asInt()).isEqualTo(40901);
        assertThat(changedResponse.path("data").path("expectedPayAmount").decimalValue())
                .isEqualByComparingTo("40.00");
        assertThat(changedResponse.path("data").path("preview").path("payAmount").decimalValue())
                .isEqualByComparingTo("35.00");
        assertThat(changedResponse.path("data").path("preview").path("items").path(0).path("price").decimalValue())
                .isEqualByComparingTo("17.50");
        assertThat(orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, session.userId()))).isEqualTo(1L);
        assertThat(cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, session.userId())
                .eq(Cart::getSkuId, 9301L)
                .eq(Cart::getDeleteTime, 0L))).isNotNull();
        assertThat(redisUtil.get(RedisConstants.PREFIX_ORDER_IDEMPOTENCY + "http-price-changed")).isNull();
        assertThat(paymentRecordMapper.selectCount(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderId, order.getId()))).isZero();
    }

    @Test
    void orderTransactionRollsBackEarlierStockDeductionAndReleasesIdempotencyKey() {
        LoginSession session = login(1L);
        assignLevel(session.userId());
        session = login(CUSTOM_LEVEL_ID);
        insertAddressAndLogistics(session.userId());

        Sku orderedSku = skuMapper.selectById(9301L);
        orderedSku.setStock(2);
        skuMapper.updateById(orderedSku);
        Long paymentCountBefore = paymentRecordMapper.selectCount(new LambdaQueryWrapper<>());

        AtomicInteger deductCalls = new AtomicInteger();
        doAnswer(invocation -> {
            if (deductCalls.incrementAndGet() == 2) {
                return false;
            }
            return invocation.callRealMethod();
        }).when(skuService).deductStock(eq(9301L), eq(1));

        JsonNode response = postJson(
                "/api/user/order/create",
                Map.of(
                        "items", List.of(
                                Map.of("skuId", 9301L, "quantity", 1),
                                Map.of("skuId", 9301L, "quantity", 1)),
                        "addressId", ADDRESS_ID,
                        "logisticsId", LOGISTICS_ID,
                        "expectedPayAmount", new BigDecimal("40.00")),
                session.token(), "http-rollback");

        assertThat(response.path("code").asInt()).isEqualTo(500);
        assertThat(skuMapper.selectById(9301L).getStock()).isEqualTo(2);
        assertThat(orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, session.userId()))).isZero();
        assertThat(paymentRecordMapper.selectCount(new LambdaQueryWrapper<>()))
                .isEqualTo(paymentCountBefore);
        assertThat(redisUtil.get(RedisConstants.PREFIX_ORDER_IDEMPOTENCY + "http-rollback")).isNull();
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

    private JsonNode postJson(
            String path, Map<String, Object> body, String token, String idempotencyKey) {
        HttpHeaders headers = bearerHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("X-Idempotency-Key", idempotencyKey);
        }
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                path, new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody();
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
        redisUtil.deleteByPattern(RedisConstants.PREFIX_ORDER_IDEMPOTENCY + "*");
    }

    private void cleanupDatabase() {
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getOpenid, LOGIN_CODE));
        if (existing != null) {
            List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                    .eq(Order::getUserId, existing.getId()));
            for (Order order : orders) {
                orderItemMapper.delete(new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, order.getId()));
            }
            orderMapper.delete(new LambdaQueryWrapper<Order>()
                    .eq(Order::getUserId, existing.getId()));
            cartMapper.delete(new LambdaQueryWrapper<Cart>()
                    .eq(Cart::getUserId, existing.getId()));
            addressMapper.delete(new LambdaQueryWrapper<Address>()
                    .eq(Address::getUserId, existing.getId()));
            userMapper.deleteById(existing.getId());
        }
        logisticsMapper.deleteById(LOGISTICS_ID);
        skuMapper.deleteById(9301L);
        spuMapper.deleteById(PRODUCT_ID);
        levelCategoryRateMapper.delete(new LambdaQueryWrapper<LevelCategoryRate>()
                .eq(LevelCategoryRate::getLevelId, CUSTOM_LEVEL_ID));
        levelCategoryRateMapper.delete(new LambdaQueryWrapper<LevelCategoryRate>()
                .eq(LevelCategoryRate::getLevelId, UserLevelStatusConstants.RETAIL_LEVEL_ID)
                .eq(LevelCategoryRate::getCategoryId, CATEGORY_ID));
        userLevelMapper.deleteById(CUSTOM_LEVEL_ID);
        categoryMapper.deleteById(CATEGORY_ID);
    }

    private void insertAddressAndLogistics(long userId) {
        Address address = new Address();
        address.setId(ADDRESS_ID);
        address.setUserId(userId);
        address.setConsignee("价格链路测试");
        address.setPhone("13800000000");
        address.setProvince("广东省");
        address.setCity("深圳市");
        address.setDistrict("南山区");
        address.setDetail("测试路1号");
        address.setIsDefault(1);
        address.setDeleteTime(0L);
        addressMapper.insert(address);

        Logistics logistics = new Logistics();
        logistics.setId(LOGISTICS_ID);
        logistics.setName("价格链路物流");
        logistics.setCode("pricing-http-test");
        logistics.setPhones(Collections.emptyList());
        logistics.setSortOrder(1);
        logistics.setStatus(1);
        logistics.setDeleteTime(0L);
        logisticsMapper.insert(logistics);
    }

    private record LoginSession(long userId, String token) {
    }
}
