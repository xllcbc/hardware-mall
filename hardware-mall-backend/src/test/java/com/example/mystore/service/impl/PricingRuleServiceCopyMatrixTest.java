package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.constant.UserLevelStatusConstants;
import com.example.mystore.entity.db.Category;
import com.example.mystore.entity.db.LevelCategoryRate;
import com.example.mystore.entity.db.UserLevel;
import com.example.mystore.entity.vo.LevelRateVO;
import com.example.mystore.mapper.CategoryMapper;
import com.example.mystore.mapper.LevelCategoryRateMapper;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@org.junit.jupiter.api.Tag("h2")
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
    private LevelCategoryRateMapper levelCategoryRateMapper;

    @Test
    void copyRates_replacesTargetMatrixAndPreservesSourceGap() {
        Long targetLevelId = createLevel("复制目标等级");
        Long sourceLevelId = createLevel("复制来源等级");
        Long configuredCategoryId = createCategory("来源已配置分类");
        Long sourceGapCategoryId = createCategory("来源缺失分类");
        Long targetOnlyCategoryId = createCategory("目标旧配置分类");

        insertRate(sourceLevelId, configuredCategoryId, "0.6000");
        insertRate(targetLevelId, configuredCategoryId, "0.9000");
        insertRate(targetLevelId, targetOnlyCategoryId, "0.8000");

        pricingRuleService.copyRates(targetLevelId, sourceLevelId);

        List<LevelCategoryRate> targetRows = levelCategoryRateMapper.selectList(
                new LambdaQueryWrapper<LevelCategoryRate>()
                        .eq(LevelCategoryRate::getLevelId, targetLevelId));
        assertThat(targetRows)
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getCategoryId()).isEqualTo(configuredCategoryId);
                    assertThat(row.getRate()).isEqualByComparingTo("0.6000");
                });

        List<LevelRateVO> matrix = pricingRuleService.getRates(targetLevelId);
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

    @Test
    void copyRates_rejectsIncompleteSourceForEnabledTargetWithoutChangingTarget() {
        Long targetLevelId = createLevel("启用复制目标等级");
        Long sourceLevelId = createLevel("不完整复制来源等级");
        Long configuredCategoryId = createCategory("已配置分类");
        Long gapCategoryId = createCategory("缺口分类");

        setLevelStatus(targetLevelId, UserLevelStatusConstants.STATUS_ENABLED);
        insertRate(targetLevelId, configuredCategoryId, "0.9000");
        insertRate(targetLevelId, gapCategoryId, "0.8500");
        insertRate(sourceLevelId, configuredCategoryId, "0.6000");

        assertThatThrownBy(() -> pricingRuleService.copyRates(targetLevelId, sourceLevelId))
                .hasMessage("来源等级倍率矩阵未填满，不能复制到已启用等级");

        List<LevelCategoryRate> targetRows = levelCategoryRateMapper.selectList(
                new LambdaQueryWrapper<LevelCategoryRate>()
                        .eq(LevelCategoryRate::getLevelId, targetLevelId)
                        .orderByAsc(LevelCategoryRate::getCategoryId));
        assertThat(targetRows)
                .extracting(LevelCategoryRate::getRate)
                .containsExactly(new BigDecimal("0.9000"), new BigDecimal("0.8500"));
    }

    @Test
    void explicitOneRateStaysConfiguredWhenNewCategoryCreatesGap() {
        Long levelId = createLevel("显式一比一等级");
        Long originalCategoryId = createCategory("原分类");
        insertRate(levelId, originalCategoryId, "1.0000");

        assertThat(pricingRuleService.getRates(levelId))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.isMissing()).isFalse();
                    assertThat(item.getRate()).isEqualByComparingTo("1.0000");
                });

        Long newCategoryId = createCategory("新增分类");
        List<LevelRateVO> matrix = pricingRuleService.getRates(levelId);

        assertThat(matrix)
                .filteredOn(item -> item.getCategoryId().equals(originalCategoryId))
                .singleElement()
                .satisfies(item -> assertThat(item.isMissing()).isFalse());
        assertThat(matrix)
                .filteredOn(item -> item.getCategoryId().equals(newCategoryId))
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

    private void setLevelStatus(Long levelId, int status) {
        UserLevel level = userLevelMapper.selectById(levelId);
        level.setStatus(status);
        userLevelMapper.updateById(level);
    }

    private void insertRate(Long levelId, Long categoryId, String rateValue) {
        LevelCategoryRate row = new LevelCategoryRate();
        row.setLevelId(levelId);
        row.setCategoryId(categoryId);
        row.setRate(new BigDecimal(rateValue));
        levelCategoryRateMapper.insert(row);
    }
}
