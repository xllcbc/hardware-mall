package com.example.mystore.config;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class OssClientConfig {

    private final OssProperties ossProperties;

    @Bean(destroyMethod = "shutdown")
    public OSS ossClient() {
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setConnectionTimeout(ossProperties.getConnectionTimeoutMs());
        configuration.setSocketTimeout(ossProperties.getSocketTimeoutMs());
        configuration.setConnectionRequestTimeout(ossProperties.getConnectionRequestTimeoutMs());
        configuration.setRequestTimeoutEnabled(true);
        configuration.setRequestTimeout(ossProperties.getRequestTimeoutMs());
        configuration.setMaxErrorRetry(ossProperties.getMaxErrorRetry());

        return new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret(),
                configuration
        );
    }
}
