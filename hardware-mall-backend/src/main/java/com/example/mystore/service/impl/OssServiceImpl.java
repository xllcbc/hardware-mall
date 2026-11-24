package com.example.mystore.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.config.OssProperties;
import com.example.mystore.service.OssService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OssServiceImpl implements OssService {

    /** 30 天公共缓存：对象键含时间戳与 UUID，不会覆盖，长缓存安全。 */
    private static final String CACHE_CONTROL_30_DAYS = "public, max-age=2592000";

    private final OSS ossClient;
    private final OssProperties ossProperties;

    /**
     * 上传图片并返回公网地址。写入 30 天公共缓存头，因此只适用于公开可访问的图片目录
     * （products/、avatars/、banners/），不可用于私有文件。
     */
    @Override
    public String uploadFile(MultipartFile file, String dir) {
        long startedAt = System.nanoTime();
        String extension = "";
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String newFilename = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 8)
                + extension;

        String objectName = dir + newFilename;
        log.info("OSS 上传开始: bucket={}, object={}, size={}B",
                ossProperties.getBucketName(), objectName, file.getSize());

        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            if (file.getContentType() != null && !file.getContentType().isBlank()) {
                metadata.setContentType(file.getContentType());
            }
            metadata.setCacheControl(CACHE_CONTROL_30_DAYS);
            ossClient.putObject(new PutObjectRequest(
                    ossProperties.getBucketName(),
                    objectName,
                    inputStream,
                    metadata
            ));
            log.info("OSS 上传完成: object={}, elapsedMs={}",
                    objectName, elapsedMillis(startedAt));
        } catch (Exception e) {
            log.error("OSS 上传失败: bucket={}, object={}, size={}B, elapsedMs={}",
                    ossProperties.getBucketName(), objectName, file.getSize(), elapsedMillis(startedAt), e);
            throw new BusinessException("图片上传失败，请稍后重试");
        }

        return ossProperties.getDomain() + "/" + objectName;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
