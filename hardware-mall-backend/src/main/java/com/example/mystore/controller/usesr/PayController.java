package com.example.mystore.controller.usesr;

import com.example.mystore.annotation.RateLimit;
import com.example.mystore.common.result.Result;
import com.example.mystore.service.PayService;
import com.example.mystore.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user/pay")
@RequiredArgsConstructor
public class PayController {

    private final PayService payService;
    private final JwtUtil jwtUtil;

    @PostMapping("/mock")
    @RateLimit(key = "pay", count = 10, time = 60)
    public Result<Void> mockPay(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Long> params) {
        Long userId = extractUserId(authHeader);
        Long orderId = params.get("orderId");
        payService.mockPay(userId, orderId);
        return Result.success();
    }

    private Long extractUserId(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtUtil.getUserIdFromToken(token);
        }
        throw new RuntimeException("无效的认证信息");
    }
}