package com.example.mystore.mapper;

import com.example.mystore.entity.db.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/schema-test.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Transactional
class CategoryMapperTest {

    @Autowired
    private CategoryMapper categoryMapper;

    @Test
    void testInsertAndSelect() {
        Category category = new Category();
        category.setName("测试分类");
        category.setIcon("🔧");
        category.setSortOrder(100);
        category.setStatus(1);
        category.setDeleteTime(0L);

        int rows = categoryMapper.insert(category);
        assertThat(rows).isEqualTo(1);
        assertThat(category.getId()).isNotNull();

        Category found = categoryMapper.selectById(category.getId());
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("测试分类");
        assertThat(found.getIcon()).isEqualTo("🔧");
    }

    @Test
    void testSoftDelete() {
        Category category = new Category();
        category.setName("待删除分类");
        category.setSortOrder(50);
        category.setStatus(1);
        category.setDeleteTime(0L);
        categoryMapper.insert(category);

        Long id = category.getId();

        // 模拟软删除
        Category update = new Category();
        update.setId(id);
        update.setDeleteTime(System.currentTimeMillis());
        categoryMapper.updateById(update);

        Category found = categoryMapper.selectById(id);
        assertThat(found).isNotNull();
        assertThat(found.getDeleteTime()).isGreaterThan(0);
    }

    @Test
    void testUpdateById() {
        Category category = new Category();
        category.setName("原名称");
        category.setSortOrder(10);
        category.setStatus(1);
        category.setDeleteTime(0L);
        categoryMapper.insert(category);

        Category update = new Category();
        update.setId(category.getId());
        update.setName("新名称");
        update.setSortOrder(20);
        categoryMapper.updateById(update);

        Category found = categoryMapper.selectById(category.getId());
        assertThat(found.getName()).isEqualTo("新名称");
        assertThat(found.getSortOrder()).isEqualTo(20);
        // 其他字段应保持不变
        assertThat(found.getStatus()).isEqualTo(1);
    }
}
