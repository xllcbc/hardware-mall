package com.example.mystore.controller.admin;

import com.example.mystore.annotation.RateLimit;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.result.Result;
import com.example.mystore.util.JwtUtil;
import com.example.mystore.util.RedisUtil;
import com.example.mystore.annotation.RequireAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@RequireAdmin
public class AdminAuthController {

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    @Value("${admin.username:admin}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    @PostMapping("/login")
    @RateLimit(key = "admin:login", count = 5, time = 60)
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");

        if (!adminUsername.equals(username) || !adminPassword.equals(password)) {
            return Result.error("用户名或密码错误");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", 0L);
        claims.put("role", 2);
        claims.put("username", username);
        String token = jwtUtil.generateToken(claims);

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", 0);
        userInfo.put("username", username);
        userInfo.put("role", 2);
        data.put("userInfo", userInfo);

        return Result.success(data);
    }

    @PostMapping("/refresh")
    public Result<String> refresh(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", 0L);
            claims.put("role", 2);
            claims.put("username", adminUsername);
            String newToken = jwtUtil.generateToken(claims);
            return Result.success(newToken);
        }
        return Result.error("无效的认证信息");
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            long ttl = (jwtUtil.getExpirationFromToken(token) - System.currentTimeMillis()) / 1000;
            if (ttl > 0) {
                redisUtil.set(RedisConstants.PREFIX_TOKEN_BLACKLIST + token, "1", ttl, TimeUnit.SECONDS);
            }
        }
        return Result.success(null);
    }
}
