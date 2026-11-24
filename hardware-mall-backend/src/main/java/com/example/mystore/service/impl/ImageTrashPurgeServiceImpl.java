package com.example.mystore.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObjectSummary;
import com.example.mystore.common.constant.ImageGcConstants;
import com.example.mystore.config.ImageGcProperties;
import com.example.mystore.config.OssProperties;
import com.example.mystore.entity.vo.ImageTrashPurgeReport;
import com.example.mystore.service.ImageTrashPurgeService;
import com.example.mystore.util.ImageObjectKeyUtil;
import com.example.mystore.util.OssListUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
            objects = OssListUtil.listAll(
                    ossClient, ossProperties.getBucketName(), ImageGcConstants.Trash.PREFIX);
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
            Optional<LocalDate> batchDate = ImageObjectKeyUtil.trashBatchDate(object.getKey());
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
}
