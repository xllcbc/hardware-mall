package com.example.mystore.service.impl;

import com.example.mystore.util.HttpUtil;
import com.example.mystore.util.RedisUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

/**
 * 钉钉机器人告警服务
 * 使用「自定义机器人 + 加签」模式, 用于支付相关异常告警(回调失败/自动退款失败/退款确认失败等)
 *
 * 设计要点(O2 优化):
 *  1. webhook 未配置时直接 return, 本地开发/未配置环境零成本, 绝不抛异常
 *  2. Redis 防抖: 同一 type 5 分钟最多发一次, 防止告警刷屏
 *  3. CompletableFuture.runAsync 异步发送, 不阻塞支付回调主线程
 *  4. 全程 try-catch, 失败只 log, 绝不影响支付主流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DingTalkAlertService {

    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${dingtalk.webhook:}")
    private String webhook;

    @Value("${dingtalk.secret:}")
    private String secret;

    /**
     * 发送一条钉钉告警(异步, 防抖)
     *
     * @param type    告警类型(用作防抖 key), 如 PAY_CALLBACK_FAIL / AUTO_REFUND_FAIL / REFUND_CONFIRM_FAIL
     * @param message 告警内容(订单号/原因等)
     */
    public void alert(String type, String message) {
        send(type, message, "alert:dingtalk:" + type, "告警");
    }

    /**
     * 发送一条业务提醒(异步, 按调用方拼装的完整 type 防抖)
     * 与 alert() 的区别: type 可携带业务标识(如 "REFUND_REQUEST:订单ID"),
     * 保证不同订单的提醒互不吞单; 文案前缀为 [提醒-], 与异常告警 [告警-] 区分
     *
     * @param type    提醒类型+业务标识(用作防抖 key)
     * @param message 提醒内容
     */
    public void notify(String type, String message) {
        send(type, message, "notify:dingtalk:" + type, "提醒");
    }

    /**
     * 共用发送通道: 未配置 webhook 静默降级, Redis 防抖, 异步发送, 全程兜底不影响主流程
     */
    private void send(String type, String message, String debounceKey, String label) {
        try {
            if (webhook == null || webhook.isEmpty()) {
                // O2 静默降级: 本地未配置 webhook 时直接返回, 不影响主流程
                return;
            }

            // 防抖: 同一防抖 key 5 分钟内只发一次, 已存在说明近来已发过, 直接跳过
            boolean got = redisUtil.setIfAbsent(debounceKey, "1", 5, java.util.concurrent.TimeUnit.MINUTES);
            if (!got) {
                log.debug("钉钉消息防抖跳过, type={}", type);
                return;
            }

            String timestamp = String.valueOf(System.currentTimeMillis());
            String sign = sign(timestamp, secret);
            String url = webhook + "&timestamp=" + timestamp + "&sign="
                    + URLEncoder.encode(sign, StandardCharsets.UTF_8);

            String content = String.format("[%s-%s]\n时间: %s\n%s",
                    label,
                    type,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    message);

            String body = objectMapper.writeValueAsString(new DingTextMessage(content));

            CompletableFuture.runAsync(() -> {
                try {
                    String resp = HttpUtil.post(url, body);
                    log.info("钉钉消息发送完成, type={}, resp={}", type, resp);
                } catch (Exception ex) {
                    log.error("钉钉消息发送失败, type={}", type, ex);
                }
            });
        } catch (Exception e) {
            // 兜底: 消息本身绝不能影响主流程
            log.error("钉钉消息准备发送时异常(忽略), type={}", type, e);
        }
    }

    /**
     * 钉钉自定义机器人加签: sign = Base64( HMAC-SHA256(timestamp + "\n" + secret, secret) )
     */
    private String sign(String timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signData);
    }

    /** 钉钉文本消息体 */
    private static class DingTextMessage {
        public String msgtype = "text";
        public Text text;

        DingTextMessage(String content) {
            this.text = new Text(content);
        }
    }

    private static class Text {
        public String content;

        Text(String content) {
            this.content = content;
        }
    }
}