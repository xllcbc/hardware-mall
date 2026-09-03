package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.Category;
import com.example.mystore.mapper.CategoryMapper;
import com.example.mystore.service.CategoryService;
import com.example.mystore.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final RedisUtil redisUtil;

    @Override
    public List<Category> getCategoryList() {
        Object cached = redisUtil.get(RedisConstants.PREFIX_CATEGORY_LIST);
        if (cached != null) {
            return (List<Category>) cached;
        }
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Category::getStatus, 1)
               .eq(Category::getDeleteTime, 0)
               .orderByAsc(Category::getSortOrder);
        List<Category> list = categoryMapper.selectList(wrapper);
        redisUtil.setWithJitter(RedisConstants.PREFIX_CATEGORY_LIST, list, RedisConstants.CACHE_TTL_HOUR, TimeUnit.SECONDS, RedisConstants.CACHE_JITTER_MAX);
        return list;
    }

    @Override
    public Page<Category> getCategoryPage(Integer page, Integer limit, String name, Integer status) {
        Page<Category> pageParam = new Page<>(page, limit);
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Category::getDeleteTime, 0);
        if (StringUtils.hasText(name)) {
            wrapper.like(Category::getName, name);
        }
        if (status != null) {
            wrapper.eq(Category::getStatus, status);
        }
        wrapper.orderByDesc(Category::getSortOrder);
        return categoryMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public Category getCategoryById(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null || category.getDeleteTime() != 0) {
            return null;
        }
        return category;
    }

    @Override
    public Category createCategory(Category category) {
        category.setCreateTime(LocalDateTime.now());
        category.setUpdateTime(LocalDateTime.now());
        category.setDeleteTime(0L);
        categoryMapper.insert(category);
        redisUtil.delete(RedisConstants.PREFIX_CATEGORY_LIST);
        return category;
    }

    @Override
    public Category updateCategory(Category category) {
        Category exist = categoryMapper.selectById(category.getId());
        if (exist == null) {
            throw new BusinessException("分类不存在");
        }

        if (StringUtils.hasText(category.getName())) {
            exist.setName(category.getName());
        }
        if (category.getIcon() != null) {
            exist.setIcon(category.getIcon());
        }
        if (category.getSortOrder() != null) {
            exist.setSortOrder(category.getSortOrder());
        }
        if (category.getStatus() != null) {
            exist.setStatus(category.getStatus());
        }
        exist.setUpdateTime(LocalDateTime.now());

        categoryMapper.updateById(exist);
        redisUtil.delete(RedisConstants.PREFIX_CATEGORY_LIST);
        return exist;
    }

    @Override
    public void deleteCategory(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException("分类不存在");
        }
        category.setDeleteTime(System.currentTimeMillis());
        categoryMapper.updateById(category);
        redisUtil.delete(RedisConstants.PREFIX_CATEGORY_LIST);
    }
}
