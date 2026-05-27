package com.example.mystore.config;

import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RSAPublicKeyNotificationConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "wechat.pay.mch-id")
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