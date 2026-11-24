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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 图片回收服务：先列 OSS 对象，再读取数据库引用快照，最后用最新引用快照再确认一次，
 * 降低扫描期间新增引用的竞态。报告模式只读；回收模式先复制到回收站，复制成功后才删除原对象。
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

        if (mode == ImageGcConstants.Mode.TRASH) {
            // 回收模式在动手前再取一次引用快照，尽量缩小“确认引用”和“复制/删除”之间的竞态窗口。
            try {
                confirmed = loadReferences();
            } catch (RuntimeException e) {
                return abort(mode, "回收前再次确认图片引用失败，已中止: " + e.getMessage(),
                        objects.size(), 0, e);
            }
            if (confirmed.mappedReferenceCount() == 0) {
                return abort(mode, "回收前再次确认时映射到的图片引用数为零，已中止（请检查数据库中的图片引用）",
                        objects.size(), 0, null);
            }
        }

        Instant cutoff = Instant.now().minus(Duration.ofDays(properties.getGraceDays()));
        int referencedObjects = 0;
        int unreferencedObjects = 0;
        List<String> candidateKeys = new ArrayList<>();
        Map<String, Long> candidateBytesByKey = new HashMap<>();

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
                candidateBytesByKey.put(key, object.getSize());
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

        TrashOutcome trashOutcome = mode == ImageGcConstants.Mode.TRASH
                ? moveCandidatesToTrash(candidateKeys, candidateBytesByKey)
                : TrashOutcome.NOT_EXECUTED;

        if (trashOutcome.abortReason() != null) {
            log.error("图片回收(trash)中止: {}", trashOutcome.abortReason());
        }
        return ImageGcReport.builder()
                .executed(true)
                .mode(mode)
                .aborted(trashOutcome.abortReason() != null)
                .abortReason(trashOutcome.abortReason())
                .scannedObjectCount(objects.size())
                .mappedReferenceCount(confirmed.mappedReferenceCount())
                .referencedObjectCount(referencedObjects)
                .unreferencedObjectCount(unreferencedObjects)
                .candidateCount(candidateKeys.size())
                .reclaimableBytes(reclaimableBytes)
                .sampleObjectKeys(samples)
                .trashedObjectCount(trashOutcome.trashedCount())
                .trashFailedObjectCount(trashOutcome.failedCount())
                .trashedBytes(trashOutcome.trashedBytes())
                .build();
    }

    /**
     * 逐个候选先复制到 {@code trash/<批次日期>/<原对象键>}，复制成功后才删除原对象。
     * 复制或删除失败即中止本轮且不再处理后续候选，失败对象和后续候选的原对象全部保留（失败关闭）。
     */
    private TrashOutcome moveCandidatesToTrash(List<String> candidateKeys, Map<String, Long> candidateBytesByKey) {
        String batchDate = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String bucket = ossProperties.getBucketName();
        int trashed = 0;
        int failed = 0;
        long trashedBytes = 0;
        for (String key : candidateKeys) {
            String trashKey = ImageGcConstants.Trash.PREFIX + batchDate + "/" + key;
            try {
                ossClient.copyObject(bucket, key, bucket, trashKey);
            } catch (RuntimeException e) {
                log.error("图片回收：复制到回收站失败，中止本轮，后续候选不再删除。key={}, trashKey={}",
                        key, trashKey, e);
                return new TrashOutcome(trashed, failed + 1, trashedBytes,
                        "复制到回收站失败，已中止本轮且不再删除后续对象: " + e.getMessage());
            }
            try {
                ossClient.deleteObject(bucket, key);
                trashed++;
                trashedBytes += candidateBytesByKey.getOrDefault(key, 0L);
            } catch (RuntimeException e) {
                log.error("图片回收：回收站副本已写入，但删除原对象失败，中止本轮。key={}, trashKey={}",
                        key, trashKey, e);
                return new TrashOutcome(trashed, failed + 1, trashedBytes,
                        "删除原对象失败，已中止本轮且不再删除后续对象: " + e.getMessage());
            }
        }
        log.info("图片回收(trash)：已移入回收站 {} 个对象，共 {} 字节，失败 {} 个对象，批次={}",
                trashed, trashedBytes, failed, batchDate);
        return new TrashOutcome(trashed, failed, trashedBytes, null);
    }

    private record TrashOutcome(int trashedCount, int failedCount, long trashedBytes, String abortReason) {

        private static final TrashOutcome NOT_EXECUTED = new TrashOutcome(0, 0, 0, null);
    }

    @Override
    public boolean restoreFromTrash(String trashObjectKey) {
        Optional<String> originalKey = toOriginalKey(trashObjectKey);
        if (originalKey.isEmpty()) {
            log.warn("从回收站恢复图片失败：对象键必须是 trash/<yyyyMMdd>/<白名单前缀下的原对象键>。key={}",
                    trashObjectKey);
            return false;
        }
        String bucket = ossProperties.getBucketName();
        boolean trashCopyExists;
        try {
            trashCopyExists = ossClient.doesObjectExist(bucket, trashObjectKey);
        } catch (RuntimeException e) {
            log.error("从回收站恢复图片失败：检查回收站副本是否存在时出错。key={}", trashObjectKey, e);
            return false;
        }
        if (!trashCopyExists) {
            log.warn("从回收站恢复图片跳过：回收站副本不存在，可能已恢复过。key={}", trashObjectKey);
            return false;
        }
        try {
            ossClient.copyObject(bucket, trashObjectKey, bucket, originalKey.get());
        } catch (RuntimeException e) {
            log.error("从回收站恢复图片失败：复制回原对象键失败，回收站副本已保留。key={}", trashObjectKey, e);
            return false;
        }
        boolean accessible;
        try {
            accessible = ossClient.doesObjectExist(bucket, originalKey.get());
        } catch (RuntimeException e) {
            log.error("从回收站恢复图片失败：校验原对象可访问性时出错，回收站副本已保留。key={}",
                    trashObjectKey, e);
            return false;
        }
        if (!accessible) {
            log.error("从回收站恢复图片失败：复制后原对象仍不可访问，回收站副本已保留。key={}", trashObjectKey);
            return false;
        }
        try {
            ossClient.deleteObject(bucket, trashObjectKey);
        } catch (RuntimeException e) {
            log.warn("图片已恢复且可访问，但删除回收站副本失败，副本将在保留期清理时处理。key={}",
                    trashObjectKey, e);
        }
        log.info("已从回收站恢复图片: {} -> {}", trashObjectKey, originalKey.get());
        return true;
    }

    /**
     * 解析回收站对象键：前缀必须是 {@code trash/}，紧随其后的日期段必须是 {@code yyyyMMdd}，
     * 日期之后必须还有白名单前缀（{@code products/}、{@code avatars/}、{@code banners/}）下的原对象键。
     */
    private static Optional<String> toOriginalKey(String trashObjectKey) {
        if (trashObjectKey == null
                || !trashObjectKey.startsWith(ImageGcConstants.Trash.PREFIX)) {
            return Optional.empty();
        }
        String rest = trashObjectKey.substring(ImageGcConstants.Trash.PREFIX.length());
        int separator = rest.indexOf('/');
        if (separator <= 0 || separator == rest.length() - 1) {
            return Optional.empty();
        }
        try {
            LocalDate.parse(rest.substring(0, separator), DateTimeFormatter.BASIC_ISO_DATE);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        String originalKey = rest.substring(separator + 1);
        return isWhitelisted(originalKey) ? Optional.of(originalKey) : Optional.empty();
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
