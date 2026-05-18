package com.example.mystore.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.entity.db.Category;

import java.util.List;

public interface CategoryService {
    List<Category> getCategoryList();
    Page<Category> getCategoryPage(Integer page, Integer limit, String name, Integer status);
    Category getCategoryById(Long id);
    Category createCategory(Category category);
    Category updateCategory(Category category);
    void deleteCategory(Long id);
}
