package com.example.mystore.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.result.Result;
import com.example.mystore.entity.db.User;
import com.example.mystore.service.UserService;
import com.example.mystore.annotation.RequireAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
@RequireAdmin
public class AdminUserController {

    private final UserService userService;

    @GetMapping("/list")
    public Result<Page<User>> getUserList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Integer status) {
        return Result.success(userService.getUserPage(page, limit, province, city, status));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateUserStatus(id, status);
        return Result.success();
    }

    @PutMapping("/{id}/region")
    public Result<Void> updateUserRegion(
            @PathVariable Long id,
            @RequestBody Map<String, String> params) {
        String province = params.get("province");
        String city = params.get("city");
        userService.updateUserRegion(id, province, city);
        return Result.success();
    }
}