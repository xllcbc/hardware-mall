package com.example.mystore.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.result.Result;
import com.example.mystore.entity.db.SpecItem;
import com.example.mystore.entity.db.SpecTemplate;
import com.example.mystore.service.SpecItemService;
import com.example.mystore.service.SpecTemplateService;
import com.example.mystore.annotation.RequireAdmin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/spec")
@RequiredArgsConstructor
@RequireAdmin
public class AdminSpecController {

    private final SpecTemplateService specTemplateService;
    private final SpecItemService specItemService;

    @GetMapping("/template/list")
    public Result<Page<SpecTemplate>> getTemplateList(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(specTemplateService.getTemplatePage(categoryId, name, page, limit));
    }

    @GetMapping("/template/{id}")
    public Result<SpecTemplate> getTemplateById(@PathVariable Long id) {
        return Result.success(specTemplateService.getTemplateById(id));
    }

    @GetMapping("/template/category/{categoryId}")
    public Result<List<SpecTemplate>> getTemplatesByCategory(@PathVariable Long categoryId) {
        return Result.success(specTemplateService.getTemplatesByCategory(categoryId));
    }

    @PostMapping("/template")
    public Result<SpecTemplate> createTemplate(@Valid @RequestBody SpecTemplate template) {
        return Result.success(specTemplateService.createTemplate(template));
    }

    @PutMapping("/template/{id}")
    public Result<SpecTemplate> updateTemplate(@PathVariable Long id, @Valid @RequestBody SpecTemplate template) {
        template.setId(id);
        return Result.success(specTemplateService.updateTemplate(template));
    }

    @DeleteMapping("/template/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        specTemplateService.deleteTemplate(id);
        return Result.success();
    }

    @GetMapping("/item/list")
    public Result<List<SpecItem>> getItemList(@RequestParam Long templateId) {
        return Result.success(specItemService.getItemsByTemplate(templateId));
    }

    @GetMapping("/item/category/{categoryId}")
    public Result<List<SpecItem>> getItemsByCategory(@PathVariable Long categoryId) {
        return Result.success(specItemService.getItemsByCategory(categoryId));
    }

    @PostMapping("/item")
    public Result<SpecItem> createItem(@RequestBody SpecItem item) {
        return Result.success(specItemService.createItem(item));
    }

    @PutMapping("/item/{id}")
    public Result<SpecItem> updateItem(@PathVariable Long id, @RequestBody SpecItem item) {
        item.setId(id);
        return Result.success(specItemService.updateItem(item));
    }

    @DeleteMapping("/item/{id}")
    public Result<Void> deleteItem(@PathVariable Long id) {
        specItemService.deleteItem(id);
        return Result.success();
    }
}
