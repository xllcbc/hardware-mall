package com.example.mystore.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.result.Result;
import com.example.mystore.entity.db.Category;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.service.CategoryService;
import com.example.mystore.service.SpuService;
import com.example.mystore.annotation.RequireAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/spu")
@RequiredArgsConstructor
@RequireAdmin
public class AdminSpuController {

    private final CategoryService categoryService;
    private final SpuService spuService;

    @GetMapping("/category/list")
    public Result<Page<Category>> getCategoryList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status) {
        return Result.success(categoryService.getCategoryPage(page, limit, name, status));
    }

    @GetMapping("/category/{id}")
    public Result<Category> getCategoryById(@PathVariable Long id) {
        Category category = categoryService.getCategoryById(id);
        if (category == null) {
            return Result.error("分类不存在");
        }
        return Result.success(category);
    }

    @PostMapping("/category")
    public Result<Category> createCategory(@RequestBody Category category) {
        return Result.success(categoryService.createCategory(category));
    }

    @PutMapping("/category/{id}")
    public Result<Category> updateCategory(@PathVariable Long id, @RequestBody Category category) {
        category.setId(id);
        return Result.success(categoryService.updateCategory(category));
    }

    @DeleteMapping("/category/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Result.success();
    }

    @GetMapping("/list")
    public Result<Page<Spu>> getSpuList(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(spuService.getSpuPage(categoryId, keyword, page, limit, status));
    }

    @GetMapping("/{id}")
    public Result<Spu> getSpuById(@PathVariable Long id) {
        return Result.success(spuService.getSpuById(id));
    }

    @PostMapping
    public Result<Spu> createSpu(@RequestBody Spu spu) {
        return Result.success(spuService.createSpu(spu));
    }

    @PutMapping("/{id}")
    public Result<Spu> updateSpu(@PathVariable Long id, @RequestBody Spu spu) {
        spu.setId(id);
        return Result.success(spuService.updateSpu(spu));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteSpu(@PathVariable Long id) {
        spuService.deleteSpu(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateSpuStatus(@PathVariable Long id, @RequestBody java.util.Map<String, Integer> body) {
        Integer status = body.get("status");
        spuService.updateStatus(id, status);
        return Result.success();
    }
}
