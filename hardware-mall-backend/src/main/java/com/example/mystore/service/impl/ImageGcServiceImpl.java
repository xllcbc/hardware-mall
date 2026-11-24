package com.example.mystore.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.example.mystore.config.ImageGcProperties;
import com.example.mystore.config.OssProperties;
import com.example.mystore.entity.db.OrderItem;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.db.User;
import com.example.mystore.entity.vo.ImageGcReport;
import com.example.mystore.mapper.OrderItemMapper;
import com.example.mystore.mapper.SkuMapper;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.mapper.UserMapper;
import com.example.mystore.service.ImageGcService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 图片回收服务：先列 OSS 对象，再读取数据库引用快照，最后计算孤儿候选。
 * 报告模式只读，不复制、不删除任何对象。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageGcServiceImpl implements ImageGcService {

    /** 白名单前缀：只有这三个目录下的对象参与扫描，trash/ 与其他前缀永不扫描。 */
    static final List<String> SCAN_PREFIXES = List.of("products/", "avatars/", "banners/");

    private final OSS ossClient;
    private final OssProperties ossProperties;
    private final SpuMapper spuMapper;
    private final SkuMapper skuMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserMapper userMapper;
    private final ImageGcProperties properties;

    @Override
    public ImageGcReport runOnce() {
        String mode = properties.getMode().name().toLowerCase();
        if (properties.getMode() == ImageGcProperties.Mode.OFF) {
            log.info("图片回收任务已关闭(mode=off)，跳过本轮");
            return ImageGcReport.notExecuted(mode);
        }
        if (properties.getMode() == ImageGcProperties.Mode.TRASH) {
            log.warn("图片回收 trash 模式尚未启用（本版本只提供只读报告），本轮不复制、不删除任何对象");
        }

        List<OSSObjectSummary> objects;
        try {
            objects = listWhitelistedObjects();
        } catch (RuntimeException e) {
            String reason = "列目录失败，已中止: " + e.getMessage();
            log.error("图片回收中止: {}", reason, e);
            return abortedReport(mode, reason, 0, 0);
        }

        ReferenceSnapshot references;
        try {
            references = loadReferences();
        } catch (RuntimeException e) {
            String reason = "读取图片引用失败，已中止: " + e.getMessage();
            log.error("图片回收中止: {}", reason, e);
            return abortedReport(mode, reason, objects.size(), 0);
        }

        if (references.mappedReferenceCount() == 0) {
            String reason = "本轮映射到的图片引用数为零，已中止（请检查图片域名与数据库引用）";
            log.error("图片回收中止: reason={}, scanned={}", reason, objects.size());
            return abortedReport(mode, reason, objects.size(), 0);
        }

        Instant cutoff = Instant.now().minus(Duration.ofDays(properties.getGraceDays()));
        int referencedObjects = 0;
        int unreferencedObjects = 0;
        int candidateCount = 0;
        long reclaimableBytes = 0;
        List<String> candidateKeys = new ArrayList<>();

        for (OSSObjectSummary object : objects) {
            String key = object.getKey();
            if (references.objectKeys().contains(key)) {
                referencedObjects++;
                continue;
            }
            unreferencedObjects++;
            Date lastModified = object.getLastModified();
            if (lastModified != null && lastModified.toInstant().isBefore(cutoff)) {
                candidateCount++;
                reclaimableBytes += object.getSize();
                candidateKeys.add(key);
            }
        }

        candidateKeys.sort(Comparator.naturalOrder());
        List<String> samples = candidateKeys.stream()
                .limit(Math.max(0, properties.getSampleLimit()))
                .toList();

        return ImageGcReport.builder()
                .executed(true)
                .mode(mode)
                .aborted(false)
                .scannedObjectCount(objects.size())
                .mappedReferenceCount(references.mappedReferenceCount())
                .referencedObjectCount(referencedObjects)
                .unreferencedObjectCount(unreferencedObjects)
                .candidateCount(candidateCount)
                .reclaimableBytes(reclaimableBytes)
                .sampleObjectKeys(samples)
                .build();
    }

    private static ImageGcReport abortedReport(String mode, String reason, int scanned, int mappedReferences) {
        return ImageGcReport.builder()
                .executed(true)
                .mode(mode)
                .aborted(true)
                .abortReason(reason)
                .scannedObjectCount(scanned)
                .mappedReferenceCount(mappedReferences)
                .sampleObjectKeys(List.of())
                .build();
    }

    private List<OSSObjectSummary> listWhitelistedObjects() {        List<OSSObjectSummary> objects = new ArrayList<>();
        for (String prefix : SCAN_PREFIXES) {
            String marker = null;
            do {
                ListObjectsRequest request = new ListObjectsRequest(ossProperties.getBucketName());
                request.setPrefix(prefix);
                request.setMaxKeys(properties.getListPageSize());
                if (marker != null) {
                    request.setMarker(marker);
                }
                ObjectListing listing = ossClient.listObjects(request);
                for (OSSObjectSummary summary : listing.getObjectSummaries()) {
                    if (isWhitelisted(summary.getKey())) {
                        objects.add(summary);
                    }
                }
                marker = listing.isTruncated() ? listing.getNextMarker() : null;
            } while (marker != null);
        }
        return objects;
    }

    private static boolean isWhitelisted(String key) {
        if (key == null) {
            return false;
        }
        return SCAN_PREFIXES.stream().anyMatch(key::startsWith);
    }

    /**
     * 取全表、不排除软删除记录：软删除的商品/规格图片仍可能被历史订单展示，且保守保留不会造成误删。
     */
    private ReferenceSnapshot loadReferences() {
        Set<String> objectKeys = new HashSet<>();
        int mapped = 0;
        int unmapped = 0;

        for (Spu spu : spuMapper.selectList(null)) {
            if (spu.getImages() == null) {
                continue;
            }
            for (String url : spu.getImages()) {
                if (addReference(url, objectKeys)) {
                    mapped++;
                } else if (hasText(url)) {
                    unmapped++;
                }
            }
        }

        for (Sku sku : skuMapper.selectList(null)) {
            if (addReference(sku.getImage(), objectKeys)) {
                mapped++;
            } else if (hasText(sku.getImage())) {
                unmapped++;
            }
        }

        for (OrderItem item : orderItemMapper.selectList(null)) {
            if (addReference(item.getProductImage(), objectKeys)) {
                mapped++;
            } else if (hasText(item.getProductImage())) {
                unmapped++;
            }
        }

        for (User user : userMapper.selectList(null)) {
            if (addReference(user.getAvatarUrl(), objectKeys)) {
                mapped++;
            } else if (hasText(user.getAvatarUrl())) {
                unmapped++;
            }
        }

        if (unmapped > 0) {
            log.warn("图片回收：{} 条图片 URL 无法换算为对象键，已忽略", unmapped);
        }
        return new ReferenceSnapshot(Set.copyOf(objectKeys), mapped);
    }

    private static boolean addReference(String url, Set<String> objectKeys) {
        return toObjectKey(url).map(objectKeys::add).orElse(false);
    }

    /**
     * 只取 URL 路径部分换算对象键，不校验域名；data: 等非 http(s) URL、空值返回 empty。
     */
    private static Optional<String> toObjectKey(String url) {
        if (!hasText(url)) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (scheme != null && !"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return Optional.empty();
            }
            String path = uri.getRawPath();
            if (path == null) {
                return Optional.empty();
            }
            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            return path.isBlank() ? Optional.empty() : Optional.of(path);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ReferenceSnapshot(Set<String> objectKeys, int mappedReferenceCount) {
    }
}
