package com.example.mystore.controller.user;

import com.example.mystore.annotation.RateLimit;
import com.example.mystore.common.result.Result;
import com.example.mystore.entity.db.PaymentRecord;
import com.example.mystore.service.PayService;
import com.example.mystore.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/user/pay")
@RequiredArgsConstructor
@ConditionalOnBean(PayService.class)
public class PayController {

    private final PayService payService;
    private final JwtUtil jwtUtil;

    @PostMapping("/prepay")
    @RateLimit(key = "pay", count = 10, time = 60)
    public Result<Map<String, String>> prepay(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Long> params) {
        Long userId = extractUserId(authHeader);
        Long orderId = params.get("orderId");
        Map<String, String> payParams = payService.prepay(userId, orderId);
        return Result.success(payParams);
    }

    @PostMapping(value = "/callback", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> callback(
            @RequestBody String body,
            @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
            @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
            @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "Wechatpay-Serial", required = false) String serial) {
        log.info("收到微信支付回调通知");
        return payService.callback(body, signature, nonce, timestamp, serial);
    }

    @GetMapping("/query/{orderId}")
    public Result<PaymentRecord> queryPayStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long orderId) {
        PaymentRecord record = payService.queryByOrderId(orderId);
        return Result.success(record);
    }

    private Long extractUserId(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtUtil.getUserIdFromToken(token);
        }
        throw new RuntimeException("无效的认证信息");
    }
}