package com.example.mystore.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.example.mystore.common.constant.ImageGcConstants;
import com.example.mystore.config.ImageGcProperties;
import com.example.mystore.config.OssProperties;
import com.example.mystore.entity.vo.ImageTrashPurgeReport;
import com.example.mystore.service.ImageTrashPurgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 回收站保留期清理。是否过期只由对象键里的回收批次日期决定：
 * {@code trash/<yyyyMMdd>/...} 中日期距今天超过保留天数才删除，未过期副本和无法识别布局的对象一律保留。
 * 单个对象删除失败只记录并留给下一轮，重新列目录后按同一日期规则重试，因此任务可幂等重跑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageTrashPurgeServiceImpl implements ImageTrashPurgeService {

    /** 每页列目录大小。 */
    private static final int LIST_PAGE_SIZE = 1000;

    private final OSS ossClient;
    private final OssProperties ossProperties;
    private final ImageGcProperties properties;

    @Override
    public ImageTrashPurgeReport purgeExpiredTrash() {
        ImageGcConstants.Mode mode = properties.getMode();
        // 报告模式只观察、关闭模式立即止损：两者都不物理删除回收站副本。
        if (mode != ImageGcConstants.Mode.TRASH) {
            log.info("回收站保留期清理跳过(mode={})，仅 trash 模式执行", mode);
            return ImageTrashPurgeReport.notExecuted(mode);
        }

        List<OSSObjectSummary> objects;
        try {
            objects = listTrashObjects();
        } catch (RuntimeException e) {
            log.error("回收站保留期清理中止：列目录失败", e);
            return ImageTrashPurgeReport.builder()
                    .executed(true)
                    .mode(mode)
                    .aborted(true)
                    .abortReason("列目录失败，已中止: " + e.getMessage())
                    .build();
        }

        LocalDate today = LocalDate.now();
        int retentionDays = properties.getTrashRetentionDays();
        int expired = 0;
        int deleted = 0;
        long deletedBytes = 0;
        int failed = 0;
        int skipped = 0;

        for (OSSObjectSummary object : objects) {
            Optional<LocalDate> batchDate = batchDate(object.getKey());
            if (batchDate.isEmpty()) {
                skipped++;
                log.warn("回收站保留期清理跳过无法识别批次日期的对象: key={}", object.getKey());
                continue;
            }
            if (ChronoUnit.DAYS.between(batchDate.get(), today) <= retentionDays) {
                continue;
            }
            expired++;
            try {
                ossClient.deleteObject(ossProperties.getBucketName(), object.getKey());
                deleted++;
                deletedBytes += object.getSize();
            } catch (RuntimeException e) {
                failed++;
                log.error("回收站保留期清理：删除副本失败，保留到下一轮重试。key={}", object.getKey(), e);
            }
        }

        log.info("回收站保留期清理完成: listed={}, expired={}, deleted={}, deletedBytes={}, failed={}, skipped={}, "
                        + "retentionDays={}", objects.size(), expired, deleted, deletedBytes, failed, skipped,
                retentionDays);
        return ImageTrashPurgeReport.builder()
                .executed(true)
                .mode(mode)
                .aborted(false)
                .listedObjectCount(objects.size())
                .expiredObjectCount(expired)
                .deletedObjectCount(deleted)
                .deletedBytes(deletedBytes)
                .failedObjectCount(failed)
                .skippedObjectCount(skipped)
                .build();
    }

    private List<OSSObjectSummary> listTrashObjects() {
        List<OSSObjectSummary> objects = new ArrayList<>();
        String marker = null;
        do {
            ListObjectsRequest request = new ListObjectsRequest(ossProperties.getBucketName());
            request.setPrefix(ImageGcConstants.Trash.PREFIX);
            request.setMaxKeys(LIST_PAGE_SIZE);
            if (marker != null) {
                request.setMarker(marker);
            }
            ObjectListing listing = ossClient.listObjects(request);
            for (OSSObjectSummary summary : listing.getObjectSummaries()) {
                if (summary.getKey() != null
                        && summary.getKey().startsWith(ImageGcConstants.Trash.PREFIX)) {
                    objects.add(summary);
                }
            }
            marker = listing.isTruncated() ? listing.getNextMarker() : null;
        } while (marker != null);
        return objects;
    }

    /**
     * 取对象键中的回收批次日期：必须是 {@code trash/<yyyyMMdd>/<至少一个字符>}。
     * 布局不完整或日期非法时返回 empty，调用方据此保留该对象，避免误删。
     */
    private static Optional<LocalDate> batchDate(String key) {
        if (key == null || !key.startsWith(ImageGcConstants.Trash.PREFIX)) {
            return Optional.empty();
        }
        String rest = key.substring(ImageGcConstants.Trash.PREFIX.length());
        int separator = rest.indexOf('/');
        if (separator <= 0 || separator == rest.length() - 1) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(
                    rest.substring(0, separator), DateTimeFormatter.BASIC_ISO_DATE));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
