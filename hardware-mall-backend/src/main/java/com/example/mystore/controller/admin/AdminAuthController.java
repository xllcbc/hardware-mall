package com.example.mystore.controller.admin;

import com.example.mystore.annotation.RateLimit;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.result.Result;
import com.example.mystore.entity.db.User;
import com.example.mystore.service.UserService;
import com.example.mystore.util.JwtUtil;
import com.example.mystore.util.RedisUtil;
import com.example.mystore.util.UserContext;
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
    private final UserService userService;

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

        User admin = userService.getUserByOpenid("admin");
        if (admin == null) {
            return Result.error("管理员账号不存在");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", admin.getId());
        claims.put("role", admin.getRole());
        claims.put("username", username);
        String token = jwtUtil.generateToken(claims);

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", admin.getId());
        userInfo.put("username", username);
        userInfo.put("role", admin.getRole());
        data.put("userInfo", userInfo);

        return Result.success(data);
    }

    @PostMapping("/refresh")
    public Result<String> refresh() {
        Long userId = UserContext.getUserId();
        User admin = userService.getUserInfo(userId);
        if (admin == null) {
            return Result.error("管理员账号不存在");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", admin.getId());
        claims.put("role", admin.getRole());
        claims.put("username", admin.getNickname());
        String newToken = jwtUtil.generateToken(claims);
        return Result.success(newToken);
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
