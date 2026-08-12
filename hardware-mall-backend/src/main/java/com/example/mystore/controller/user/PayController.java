package com.example.mystore.controller.user;

import com.example.mystore.annotation.RateLimit;
import com.example.mystore.common.result.Result;
import com.example.mystore.entity.db.PaymentRecord;
import com.example.mystore.service.PayService;
import com.example.mystore.util.UserContext;
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

    @PostMapping("/prepay")
    @RateLimit(key = "pay", count = 10, time = 60)
    public Result<Map<String, String>> prepay(@RequestBody Map<String, Long> params) {
        Long userId = UserContext.getUserId();
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

    @PostMapping(value = "/callback/refund", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> refundCallback(
            @RequestBody String body,
            @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
            @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
            @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "Wechatpay-Serial", required = false) String serial) {
        log.info("收到微信退款回调通知");
        return payService.refundCallback(body, signature, nonce, timestamp, serial);
    }

    @GetMapping("/query/{orderId}")
    public Result<PaymentRecord> queryPayStatus(@PathVariable Long orderId) {
        Long userId = UserContext.getUserId();
        PaymentRecord record = payService.queryByOrderIdAndUserId(orderId, userId);
        return Result.success(record);
    }
}