package com.example.mystore.controller.user;

import com.example.mystore.common.result.Result;
import com.example.mystore.entity.db.Category;
import com.example.mystore.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserCategoryController {

    private final CategoryService categoryService;


    @GetMapping("/category/list")
    public Result<List<Category>> getCategoryList() {
        return Result.success(categoryService.getCategoryList());
    }
    @GetMapping("/category/{id}")
    public Result<Category> getCategoryById(@PathVariable Long id) {
        return Result.success(categoryService.getCategoryById(id));
    }



}