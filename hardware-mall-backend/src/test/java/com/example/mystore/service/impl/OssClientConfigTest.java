package com.example.mystore.service.impl;

import com.aliyun.oss.OSS;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.config.OssClientConfig;
import com.example.mystore.config.OssProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OssClientConfigTest {

    @Test
    void uploadFile_whenOssDoesNotRespond_failsWithinConfiguredRequestTimeout() throws Exception {
        CountDownLatch releaseServer = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            try {
                releaseServer.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();

        OssProperties properties = new OssProperties();
        properties.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setAccessKeyId("test-access-key");
        properties.setAccessKeySecret("test-secret-key");
        properties.setBucketName("test-bucket");
        properties.setDomain("https://cdn.example.com");
        properties.setConnectionTimeoutMs(200);
        properties.setConnectionRequestTimeoutMs(200);
        properties.setSocketTimeoutMs(250);
        properties.setRequestTimeoutMs(250);
        properties.setMaxErrorRetry(0);

        OSS ossClient = new OssClientConfig(properties).ossClient();
        OssServiceImpl ossService = new OssServiceImpl(ossClient, properties);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "product.jpg",
                "image/jpeg",
                "image-content".getBytes(StandardCharsets.UTF_8)
        );

        long startedAt = System.nanoTime();
        try {
            assertThatThrownBy(() -> ossService.uploadFile(file, "products/"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("图片上传失败，请稍后重试");
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
            assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
        } finally {
            releaseServer.countDown();
            ossClient.shutdown();
            server.stop(0);
        }
    }
}
