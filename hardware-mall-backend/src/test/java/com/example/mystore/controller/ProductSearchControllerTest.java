package com.example.mystore.controller;

import com.example.mystore.common.constant.DiscountConstants;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.constant.UserLevelStatusConstants;
import com.example.mystore.entity.db.Category;
import com.example.mystore.entity.db.LevelCategoryRate;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.mapper.CategoryMapper;
import com.example.mystore.mapper.LevelCategoryRateMapper;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.util.JwtUtil;
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Tag("h2")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductSearchControllerTest {

    private static final long CATEGORY_ID = 7301L;
    private static final long PRODUCT_ID = 730101L;
    private static final String PRODUCT_NAME = "乾程 B08不锈钢";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private LevelCategoryRateMapper rateMapper;
    @Autowired
    private SpuMapper spuMapper;
    @Autowired
    private RedisUtil redisUtil;

    private String adminToken;

    @BeforeEach
    void setUp() {
        clearCaches();
        adminToken = jwtUtil.generateToken(Map.of(
                "userId", 1L,
                "role", StatusConstants.USER_ROLE_ADMIN));

        Category category = new Category();
        category.setId(CATEGORY_ID);
        category.setName("搜索测试分类");
        category.setSortOrder(10);
        category.setStatus(1);
        category.setDeleteTime(0L);
        categoryMapper.insert(category);

        LevelCategoryRate retailRate = new LevelCategoryRate();
        retailRate.setLevelId(UserLevelStatusConstants.RETAIL_LEVEL_ID);
        retailRate.setCategoryId(CATEGORY_ID);
        retailRate.setRate(BigDecimal.ONE);
        rateMapper.insert(retailRate);

        insertProduct(PRODUCT_ID, PRODUCT_NAME, LocalDateTime.now().minusDays(1));
    }

    @AfterEach
    void tearDown() {
        clearCaches();
    }

    @ParameterizedTest
    @ValueSource(strings = {"乾程B08", "乾程 B08", "乾程", "B08"})
    void adminSearchMatchesBrandAndModelWithOrWithoutSpace(String keyword) throws Exception {
        mvc.perform(get("/api/admin/spu/pricing-list")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("categoryId", String.valueOf(CATEGORY_ID))
                        .param("keyword", keyword)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value((int) PRODUCT_ID))
                .andExpect(jsonPath("$.data.records[0].retailMinPrice").value(23.0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"乾程B08", "乾程 B08", "乾程", "B08"})
    void userSearchMatchesBrandAndModelWithOrWithoutSpace(String keyword) throws Exception {
        mvc.perform(get("/api/user/product/list")
                        .param("categoryId", String.valueOf(CATEGORY_ID))
                        .param("keyword", keyword)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value((int) PRODUCT_ID))
                .andExpect(jsonPath("$.data.records[0].minPrice").value(23.0))
                .andExpect(jsonPath("$.data.records[0].sellable").value(true));
    }

    private void insertProduct(long id, String name, LocalDateTime createTime) {
        Spu spu = new Spu();
        spu.setId(id);
        spu.setCategoryId(CATEGORY_ID);
        spu.setName(name);
        spu.setOriginalPrice(new BigDecimal("23.00"));
        spu.setSalesCount(0);
        spu.setStatus(StatusConstants.PRODUCT_STATUS_ON_SHELF);
        spu.setIsRecommend(StatusConstants.PRODUCT_RECOMMEND_NO);
        spu.setExcludeDiscount(DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        spu.setCreateTime(createTime);
        spu.setUpdateTime(createTime);
        spu.setDeleteTime(0L);
        spuMapper.insert(spu);
    }

    private void clearCaches() {
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRODUCT_LIST + "*");
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRODUCT_SEARCH + "*");
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRICING_LEVEL + "*");
        redisUtil.delete(RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID);
    }
}
