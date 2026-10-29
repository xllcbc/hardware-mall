package com.example.mystore.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssProperties {
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;
    private String region;
    private String domain;
    private String endpoint;
    private int connectionTimeoutMs = 5000;
    private int socketTimeoutMs = 30000;
    private int connectionRequestTimeoutMs = 5000;
    private int requestTimeoutMs = 45000;
    private int maxErrorRetry = 1;

    public String getEndpoint() {
        if (endpoint != null && !endpoint.isBlank()) {
            return endpoint;
        }
        return "https://oss-" + region + ".aliyuncs.com";
    }
}
