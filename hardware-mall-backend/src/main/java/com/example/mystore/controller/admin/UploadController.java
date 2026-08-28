package com.example.mystore.controller.admin;

import com.example.mystore.common.result.Result;
import com.example.mystore.entity.db.Category;
import com.example.mystore.service.CategoryService;
import com.example.mystore.service.OssService;
import com.example.mystore.annotation.RequireAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@RequireAdmin
public class UploadController {

    private final OssService ossService;
    private final CategoryService categoryService;

    @PostMapping("/upload/product")
    public Result<Map<String, String>> uploadProductImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long categoryId) {
        if (file == null || file.isEmpty()) {
            return Result.error("上传文件不能为空");
        }
        String dir = "products/";
        if (categoryId != null) {
            Category category = categoryService.getCategoryById(categoryId);
            if (category != null && category.getName() != null && !category.getName().isBlank()) {
                dir = "products/" + sanitize(category.getName()) + "/";
            }
        }
        String url = ossService.uploadFile(file, dir);
        return Result.success(Map.of("url", url));
    }

    @PostMapping("/upload/avatar")
    public Result<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("上传文件不能为空");
        }
        String url = ossService.uploadFile(file, "avatars/");
        return Result.success(Map.of("url", url));
    }

    @PostMapping("/upload/banner")
    public Result<Map<String, String>> uploadBanner(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("上传文件不能为空");
        }
        String url = ossService.uploadFile(file, "banners/");
        return Result.success(Map.of("url", url));
    }

    private String sanitize(String name) {
        String cleaned = name.replaceAll("[^\\w\\u4e00-\\u9fff-]", "");
        return cleaned.isEmpty() ? "default" : cleaned;
    }
}
