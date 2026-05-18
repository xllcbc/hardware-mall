package com.example.mystore.controller.admin;

import com.example.mystore.common.result.Result;
import com.example.mystore.service.OssService;
import com.example.mystore.annotation.RequireAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@RequireAdmin
public class UploadController {

    private final OssService ossService;

    @PostMapping("/upload/product")
    public Result<String> uploadProductImage(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("上传文件不能为空");
        }
        String url = ossService.uploadFile(file, "products/");
        return Result.success(url);
    }

    @PostMapping("/upload/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("上传文件不能为空");
        }
        String url = ossService.uploadFile(file, "avatars/");
        return Result.success(url);
    }

    @PostMapping("/upload/banner")
    public Result<String> uploadBanner(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("上传文件不能为空");
        }
        String url = ossService.uploadFile(file, "banners/");
        return Result.success(url);
    }
}
