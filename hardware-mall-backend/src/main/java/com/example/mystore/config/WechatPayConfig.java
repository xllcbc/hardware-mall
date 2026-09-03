package com.example.mystore.config;

import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RSAPublicKeyNotificationConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 微信支付配置：商户参数为硬性要求，缺失时启动即失败（fail-fast），
 * 不允许带空商户号带病运行。本地开发也必须配齐环境变量（占位值即可）。
 */
@Slf4j
@Configuration
public class WechatPayConfig {

    @Value("${wechat.pay.mch-id}")
    private String mchId;

    @Value("${wechat.pay.private-key}")
    private String privateKey;

    @Value("${wechat.pay.public-key}")
    private String publicKey;

    @Value("${wechat.pay.public-key-id}")
    private String publicKeyId;

    @Value("${wechat.pay.mch-serial-no}")
    private String mchSerialNo;

    @Value("${wechat.pay.api-v3-key}")
    private String apiV3Key;

    @PostConstruct
    void validate() {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(mchId)) {
            missing.add("wechat.pay.mch-id");
        }
        if (!StringUtils.hasText(privateKey)) {
            missing.add("wechat.pay.private-key");
        }
        if (!StringUtils.hasText(publicKey)) {
            missing.add("wechat.pay.public-key");
        }
        if (!StringUtils.hasText(publicKeyId)) {
            missing.add("wechat.pay.public-key-id");
        }
        if (!StringUtils.hasText(mchSerialNo)) {
            missing.add("wechat.pay.mch-serial-no");
        }
        if (!StringUtils.hasText(apiV3Key)) {
            missing.add("wechat.pay.api-v3-key");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "微信支付配置不完整，缺少: " + String.join(", ", missing)
                            + "。支付为商城核心能力，必须配齐对应环境变量后才能启动");
        }
        log.info("微信支付配置校验通过, mchId={}", mchId);
    }

    @Bean
    public Config rsaPublicKeyConfig() {
        return new RSAPublicKeyConfig.Builder()
                .merchantId(mchId)
                .privateKey(privateKey)
                .publicKey(publicKey)
                .publicKeyId(publicKeyId)
                .merchantSerialNumber(mchSerialNo)
                .apiV3Key(apiV3Key)
                .build();
    }

    @Bean
    public NotificationParser notificationParser() {
        NotificationConfig config = new RSAPublicKeyNotificationConfig.Builder()
                .publicKey(publicKey)
                .publicKeyId(publicKeyId)
                .apiV3Key(apiV3Key)
                .build();
        return new NotificationParser(config);
    }
}