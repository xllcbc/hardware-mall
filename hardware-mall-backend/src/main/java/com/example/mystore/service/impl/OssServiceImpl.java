package com.example.mystore.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.PutObjectRequest;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.config.OssProperties;
import com.example.mystore.service.OssService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OssServiceImpl implements OssService {

    private final OssProperties ossProperties;

    @Override
    public String uploadFile(MultipartFile file, String dir) {
        String extension = "";
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String newFilename = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 8)
                + extension;

        String objectName = dir + newFilename;

        OSS ossClient = new OSSClientBuilder().build(
                "https://oss-" + ossProperties.getRegion() + ".aliyuncs.com",
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret()
        );

        try {
            ossClient.putObject(new PutObjectRequest(
                    ossProperties.getBucketName(),
                    objectName,
                    new ByteArrayInputStream(file.getBytes())
            ));
        } catch (Exception e) {
            throw new BusinessException("文件上传失败: " + e.getMessage());
        } finally {
            ossClient.shutdown();
        }

        return ossProperties.getDomain() + "/" + objectName;
    }

    @Override
    public void deleteFile(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        String prefix = ossProperties.getDomain() + "/";
        if (!url.startsWith(prefix)) {
            log.warn("OSS 删除跳过：URL 不属于当前域名, url={}", url);
            return;
        }
        String objectName = url.substring(prefix.length());
        OSS ossClient = new OSSClientBuilder().build(
                "https://oss-" + ossProperties.getRegion() + ".aliyuncs.com",
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret()
        );
        try {
            ossClient.deleteObject(ossProperties.getBucketName(), objectName);
        } catch (Exception e) {
            log.warn("OSS 删除失败, objectName={}, error={}", objectName, e.getMessage());
        } finally {
            ossClient.shutdown();
        }
    }
}
