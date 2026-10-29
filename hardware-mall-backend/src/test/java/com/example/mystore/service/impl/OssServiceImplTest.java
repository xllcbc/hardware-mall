package com.example.mystore.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.PutObjectRequest;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.config.OssProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OssServiceImplTest {

    @Mock
    private OSS ossClient;

    private OssServiceImpl ossService;

    @BeforeEach
    void setUp() {
        OssProperties properties = new OssProperties();
        properties.setBucketName("test-bucket");
        properties.setRegion("cn-beijing");
        properties.setDomain("https://cdn.example.com");
        ossService = new OssServiceImpl(ossClient, properties);
    }

    @Test
    void uploadFile_streamsContentWithMetadataAndReturnsPublicUrl() {
        byte[] content = "image-content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "product.jpg", "image/jpeg", content);

        String url = ossService.uploadFile(file, "products/");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(ossClient).putObject(requestCaptor.capture());
        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.getBucketName()).isEqualTo("test-bucket");
        assertThat(request.getKey()).startsWith("products/").endsWith(".jpg");
        assertThat(request.getInputStream()).isNotNull();
        assertThat(request.getMetadata().getContentLength()).isEqualTo(content.length);
        assertThat(request.getMetadata().getContentType()).isEqualTo("image/jpeg");
        assertThat(url).startsWith("https://cdn.example.com/products/");
    }

    @Test
    void uploadFile_whenOssFails_returnsStableBusinessError() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "product.jpg", "image/jpeg", new byte[]{1, 2, 3});
        doThrow(new RuntimeException("network unavailable"))
                .when(ossClient).putObject(any(PutObjectRequest.class));

        assertThatThrownBy(() -> ossService.uploadFile(file, "products/"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("图片上传失败，请稍后重试");
    }
}
