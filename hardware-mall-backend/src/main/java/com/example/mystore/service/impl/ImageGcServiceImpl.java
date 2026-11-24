package com.example.mystore.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.example.mystore.common.constant.ImageGcConstants;
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
 * 图片回收服务：先列 OSS 对象，再读取数据库引用快照，最后用最新引用快照再确认一次，
 * 降低扫描期间新增引用的竞态。报告模式只读，不复制、不删除任何对象。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageGcServiceImpl implements ImageGcService {

    /** 每页列目录大小。 */
    private static final int LIST_PAGE_SIZE = 1000;

    /** 报告中样例对象键的最大数量。 */
    private static final int SAMPLE_LIMIT = 20;

    private final OSS ossClient;
    private final OssProperties ossProperties;
    private final SpuMapper spuMapper;
    private final SkuMapper skuMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserMapper userMapper;
    private final ImageGcProperties properties;

    @Override
    public ImageGcReport runOnce() {
        ImageGcConstants.Mode mode = properties.getMode();
        if (mode == ImageGcConstants.Mode.OFF) {
            log.info("图片回收任务已关闭(mode=off)，跳过本轮");
            return ImageGcReport.notExecuted(mode);
        }

        List<OSSObjectSummary> objects;
        try {
            objects = listWhitelistedObjects();
        } catch (RuntimeException e) {
            return abort(mode, "列目录失败，已中止: " + e.getMessage(), 0, 0, e);
        }

        ReferenceSnapshot references;
        try {
            references = loadReferences();
        } catch (RuntimeException e) {
            return abort(mode, "读取图片引用失败，已中止: " + e.getMessage(), objects.size(), 0, e);
        }
        if (references.mappedReferenceCount() == 0) {
            return abort(mode, "本轮映射到的图片引用数为零，已中止（请检查数据库中的图片引用）",
                    objects.size(), 0, null);
        }

        ReferenceSnapshot confirmed;
        try {
            confirmed = loadReferences();
        } catch (RuntimeException e) {
            return abort(mode, "再次确认图片引用失败，已中止: " + e.getMessage(), objects.size(), 0, e);
        }
        if (confirmed.mappedReferenceCount() == 0) {
            return abort(mode, "再次确认时映射到的图片引用数为零，已中止（请检查数据库中的图片引用）",
                    objects.size(), 0, null);
        }

        Instant cutoff = Instant.now().minus(Duration.ofDays(properties.getGraceDays()));
        int referencedObjects = 0;
        int unreferencedObjects = 0;
        List<String> candidateKeys = new ArrayList<>();

        for (OSSObjectSummary object : objects) {
            String key = object.getKey();
            if (confirmed.objectKeys().contains(key)) {
                referencedObjects++;
                continue;
            }
            unreferencedObjects++;
            Date lastModified = object.getLastModified();
            if (lastModified != null && lastModified.toInstant().isBefore(cutoff)) {
                candidateKeys.add(key);
            }
        }

        candidateKeys.sort(Comparator.naturalOrder());
        Set<String> candidateKeySet = Set.copyOf(candidateKeys);
        long reclaimableBytes = objects.stream()
                .filter(object -> candidateKeySet.contains(object.getKey()))
                .mapToLong(OSSObjectSummary::getSize)
                .sum();
        List<String> samples = candidateKeys.stream()
                .limit(SAMPLE_LIMIT)
                .toList();

        return ImageGcReport.builder()
                .executed(true)
                .mode(mode)
                .aborted(false)
                .scannedObjectCount(objects.size())
                .mappedReferenceCount(confirmed.mappedReferenceCount())
                .referencedObjectCount(referencedObjects)
                .unreferencedObjectCount(unreferencedObjects)
                .candidateCount(candidateKeys.size())
                .reclaimableBytes(reclaimableBytes)
                .sampleObjectKeys(samples)
                .build();
    }

    private ImageGcReport abort(ImageGcConstants.Mode mode, String reason, int scanned,
                                int mappedReferences, RuntimeException cause) {
        if (cause == null) {
            log.error("图片回收中止: reason={}, scanned={}, mappedReferences={}",
                    reason, scanned, mappedReferences);
        } else {
            log.error("图片回收中止: reason={}, scanned={}, mappedReferences={}",
                    reason, scanned, mappedReferences, cause);
        }
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

    private List<OSSObjectSummary> listWhitelistedObjects() {
        List<OSSObjectSummary> objects = new ArrayList<>();
        for (String prefix : ImageGcConstants.Prefix.SCAN) {
            String marker = null;
            do {
                ListObjectsRequest request = new ListObjectsRequest(ossProperties.getBucketName());
                request.setPrefix(prefix);
                request.setMaxKeys(LIST_PAGE_SIZE);
                if (marker != null) {
                    request.setMarker(marker);
                }
                ObjectListing listing = ossClient.listObjects(request);
                for (OSSObjectSummary summary : listing.getObjectSummaries()) {
                    // 防御性过滤：即便 OSS 返回白名单前缀外的对象，也不纳入统计。
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
        return ImageGcConstants.Prefix.SCAN.stream().anyMatch(key::startsWith);
    }

    /**
     * 取全表、不排除软删除记录：软删除的商品/规格图片仍可能被历史订单展示，且保守保留不会造成误删。
     */
    private ReferenceSnapshot loadReferences() {
        ReferenceAccumulator accumulator = new ReferenceAccumulator();

        for (Spu spu : spuMapper.selectList(null)) {
            if (spu.getImages() != null) {
                spu.getImages().forEach(accumulator::add);
            }
        }
        for (Sku sku : skuMapper.selectList(null)) {
            accumulator.add(sku.getImage());
        }
        for (OrderItem item : orderItemMapper.selectList(null)) {
            accumulator.add(item.getProductImage());
        }
        for (User user : userMapper.selectList(null)) {
            accumulator.add(user.getAvatarUrl());
        }

        if (accumulator.unmapped > 0) {
            log.warn("图片回收：{} 条图片 URL 无法换算为对象键，已忽略", accumulator.unmapped);
        }
        return new ReferenceSnapshot(Set.copyOf(accumulator.objectKeys), accumulator.mapped);
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

    private static final class ReferenceAccumulator {

        private final Set<String> objectKeys = new HashSet<>();
        private int mapped;
        private int unmapped;

        private void add(String url) {
            Optional<String> key = toObjectKey(url);
            if (key.isPresent()) {
                objectKeys.add(key.get());
                mapped++;
            } else if (hasText(url)) {
                unmapped++;
            }
        }
    }

    private record ReferenceSnapshot(Set<String> objectKeys, int mappedReferenceCount) {
    }
}
