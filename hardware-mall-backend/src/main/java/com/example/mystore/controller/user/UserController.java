package com.example.mystore.controller.user;

import com.example.mystore.annotation.RateLimit;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.result.Result;
import com.example.mystore.entity.db.User;
import com.example.mystore.service.UserService;
import com.example.mystore.util.JwtUtil;
import com.example.mystore.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    @PostMapping("/login")
    @RateLimit(key = "user:login", count = 5, time = 60)
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> params) {
        String code = params.get("code");
        User user = userService.login(code);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("role", user.getRole());
        String token = jwtUtil.generateToken(claims);

        redisUtil.sAdd(RedisConstants.PREFIX_USER_TOKENS + user.getId(), token);

        if (user.getOpenid() != null) {
            user.setOpenid(null);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userInfo", user);
        return Result.success(data);
    }

    @GetMapping("/info")
    public Result<User> getUserInfo(@RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        return Result.success(userService.getUserInfo(userId));
    }

    @PutMapping("/info")
    public Result<User> updateUserInfo(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody User user) {
        Long userId = extractUserId(authHeader);
        user.setId(userId);
        return Result.success(userService.updateUserInfo(user));
    }

    @PostMapping("/refresh")
    public Result<String> refreshToken(@RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        return Result.success(userService.refreshToken(userId));
    }

    @PostMapping("/phone")
    @RateLimit(key = "user:phone", count = 5, time = 60)
    public Result<User> bindPhone(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> params) {
        Long userId = extractUserId(authHeader);
        String phoneCode = params.get("code");
        return Result.success(userService.updatePhone(userId, phoneCode));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        userService.logout(userId, token);
        return Result.success(null);
    }

    @PutMapping("/region")
    public Result<Void> updateRegion(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> params) {
        Long userId = extractUserId(authHeader);
        userService.updateUserRegion(userId, params.get("province"), params.get("city"));
        return Result.success(null);
    }

    private Long extractUserId(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtUtil.getUserIdFromToken(token);
        }
        throw new RuntimeException("无效的认证信息");
    }
}
