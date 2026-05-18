package com.example.mystore.service.impl;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.entity.db.Category;
import com.example.mystore.mapper.CategoryMapper;
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private Category category1;
    private Category category2;

    @BeforeEach
    void setUp() {
        category1 = new Category();
        category1.setId(1L);
        category1.setName("锁具");
        category1.setSortOrder(100);
        category1.setStatus(1);
        category1.setDeleteTime(0L);

        category2 = new Category();
        category2.setId(2L);
        category2.setName("胶类");
        category2.setSortOrder(90);
        category2.setStatus(1);
        category2.setDeleteTime(0L);
    }

    @Test
    void testGetCategoryList_CacheHit() {
        when(redisUtil.get(RedisConstants.PREFIX_CATEGORY_LIST)).thenReturn(Arrays.asList(category1, category2));

        List<Category> result = categoryService.getCategoryList();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("锁具");
        verify(categoryMapper, never()).selectList(any());
    }

    @Test
    void testGetCategoryList_CacheMiss() {
        when(redisUtil.get(RedisConstants.PREFIX_CATEGORY_LIST)).thenReturn(null);
        when(categoryMapper.selectList(any())).thenReturn(Arrays.asList(category1, category2));

        List<Category> result = categoryService.getCategoryList();

        assertThat(result).hasSize(2);
        verify(redisUtil).setWithJitter(eq(RedisConstants.PREFIX_CATEGORY_LIST), eq(result), anyLong(), any(), anyLong());
    }

    @Test
    void testCreateCategory_InvalidatesCache() {
        Category newCategory = new Category();
        newCategory.setName("工具");
        newCategory.setSortOrder(70);

        categoryService.createCategory(newCategory);

        verify(redisUtil).delete(RedisConstants.PREFIX_CATEGORY_LIST);
        verify(categoryMapper).insert(any(Category.class));
    }

    @Test
    void testGetCategoryById_NotFound() {
        when(categoryMapper.selectById(999L)).thenReturn(null);

        Category result = categoryService.getCategoryById(999L);

        assertThat(result).isNull();
    }

    @Test
    void testGetCategoryById_Deleted() {
        Category deleted = new Category();
        deleted.setId(3L);
        deleted.setDeleteTime(System.currentTimeMillis());
        when(categoryMapper.selectById(3L)).thenReturn(deleted);

        Category result = categoryService.getCategoryById(3L);

        assertThat(result).isNull();
    }

    @Test
    void testDeleteCategory_SoftDelete() {
        when(categoryMapper.selectById(1L)).thenReturn(category1);

        categoryService.deleteCategory(1L);

        verify(categoryMapper).updateById(argThat(cat -> cat.getDeleteTime() > 0));
        verify(redisUtil).delete(RedisConstants.PREFIX_CATEGORY_LIST);
    }

    @Test
    void testDeleteCategory_NotFound() {
        when(categoryMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> categoryService.deleteCategory(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("分类不存在");
    }
}
