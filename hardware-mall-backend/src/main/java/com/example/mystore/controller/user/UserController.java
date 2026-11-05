package com.example.mystore.controller.user;

import com.example.mystore.annotation.RateLimit;
import com.example.mystore.common.result.Result;
import com.example.mystore.entity.db.User;
import com.example.mystore.service.UserService;
import com.example.mystore.util.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/login")
    @RateLimit(key = "user:login", count = 10, time = 60)
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> params) {
        String code = params.get("code");
        User user = userService.login(code);
        String token = userService.issueLoginToken(user.getId());
        User userInfo = userService.getUserInfo(user.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userInfo", userInfo);
        return Result.success(data);
    }

    @GetMapping("/info")
    public Result<User> getUserInfo() {
        Long userId = UserContext.getUserId();
        return Result.success(userService.getUserInfo(userId));
    }

    @PutMapping("/info")
    public Result<User> updateUserInfo(@Valid @RequestBody User user) {
        Long userId = UserContext.getUserId();
        user.setId(userId);
        return Result.success(userService.updateUserInfo(user));
    }

    @PostMapping("/refresh")
    public Result<String> refreshToken(@RequestHeader("Authorization") String authHeader) {
        Long userId = UserContext.getUserId();
        String oldToken = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        return Result.success(userService.refreshToken(userId, oldToken));
    }

    @PostMapping("/phone")
    @RateLimit(key = "user:phone", count = 10, time = 60)
    public Result<User> bindPhone(@RequestBody Map<String, String> params) {
        Long userId = UserContext.getUserId();
        String phoneCode = params.get("code");
        return Result.success(userService.updatePhone(userId, phoneCode));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader) {
        Long userId = UserContext.getUserId();
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        userService.logout(userId, token);
        return Result.success(null);
    }

    @PutMapping("/region")
    public Result<Void> updateRegion(@RequestBody Map<String, String> params) {
        Long userId = UserContext.getUserId();
        userService.updateUserRegion(userId, params.get("province"), params.get("city"));
        return Result.success(null);
    }
}
