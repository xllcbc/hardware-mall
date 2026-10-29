package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.constant.UserLevelStatusConstants;
import com.example.mystore.entity.db.Category;
import com.example.mystore.entity.db.LevelCategoryDiscount;
import com.example.mystore.entity.db.UserLevel;
import com.example.mystore.entity.vo.LevelDiscountVO;
import com.example.mystore.mapper.CategoryMapper;
import com.example.mystore.mapper.LevelCategoryDiscountMapper;
import com.example.mystore.mapper.UserLevelMapper;
import com.example.mystore.service.PricingRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/schema-test.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Transactional
class PricingRuleServiceCopyMatrixTest {

    @Autowired
    private PricingRuleService pricingRuleService;

    @Autowired
    private UserLevelMapper userLevelMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private LevelCategoryDiscountMapper levelCategoryDiscountMapper;

    @Test
    void copyDiscounts_replacesTargetMatrixAndPreservesSourceGap() {
        Long targetLevelId = createLevel("复制目标等级");
        Long sourceLevelId = createLevel("复制来源等级");
        Long configuredCategoryId = createCategory("来源已配置分类");
        Long sourceGapCategoryId = createCategory("来源缺失分类");
        Long targetOnlyCategoryId = createCategory("目标旧配置分类");

        insertDiscount(sourceLevelId, configuredCategoryId, "0.6000");
        insertDiscount(targetLevelId, configuredCategoryId, "0.9000");
        insertDiscount(targetLevelId, targetOnlyCategoryId, "0.8000");

        pricingRuleService.copyDiscounts(targetLevelId, sourceLevelId);

        List<LevelCategoryDiscount> targetRows = levelCategoryDiscountMapper.selectList(
                new LambdaQueryWrapper<LevelCategoryDiscount>()
                        .eq(LevelCategoryDiscount::getLevelId, targetLevelId));
        assertThat(targetRows)
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getCategoryId()).isEqualTo(configuredCategoryId);
                    assertThat(row.getRate()).isEqualByComparingTo("0.6000");
                });

        List<LevelDiscountVO> matrix = pricingRuleService.getDiscounts(targetLevelId);
        assertThat(matrix)
                .filteredOn(item -> item.getCategoryId().equals(configuredCategoryId))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.isMissing()).isFalse();
                    assertThat(item.getRate()).isEqualByComparingTo("0.6000");
                });
        assertThat(matrix)
                .filteredOn(item -> item.getCategoryId().equals(sourceGapCategoryId))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.isMissing()).isTrue();
                    assertThat(item.getRate()).isNull();
                });
        assertThat(matrix)
                .filteredOn(item -> item.getCategoryId().equals(targetOnlyCategoryId))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.isMissing()).isTrue();
                    assertThat(item.getRate()).isNull();
                });
    }

    private Long createLevel(String name) {
        UserLevel level = new UserLevel();
        level.setName(name);
        level.setIsRetail(UserLevelStatusConstants.NOT_RETAIL);
        level.setSortOrder(0);
        level.setStatus(UserLevelStatusConstants.STATUS_DISABLED);
        userLevelMapper.insert(level);
        return level.getId();
    }

    private Long createCategory(String name) {
        Category category = new Category();
        category.setParentId(0L);
        category.setName(name);
        category.setSortOrder(0);
        category.setStatus(StatusConstants.CATEGORY_STATUS_ENABLED);
        category.setDeleteTime(0L);
        categoryMapper.insert(category);
        return category.getId();
    }

    private void insertDiscount(Long levelId, Long categoryId, String rate) {
        LevelCategoryDiscount discount = new LevelCategoryDiscount();
        discount.setLevelId(levelId);
        discount.setCategoryId(categoryId);
        discount.setRate(new BigDecimal(rate));
        levelCategoryDiscountMapper.insert(discount);
    }
}
