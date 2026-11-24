package com.example.mystore.job.image;

import com.example.mystore.common.constant.ImageGcConstants;
import com.example.mystore.config.ImageGcProperties;
import com.example.mystore.entity.vo.ImageTrashPurgeReport;
import com.example.mystore.service.ImageTrashPurgeService;
import com.example.mystore.service.impl.DingTalkAlertService;
import com.example.mystore.util.RedisLockUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日回收站保留期清理任务。只有拿到分布式锁的实例执行；列目录失败整轮中止，
 * 单个对象删除失败保留到下一轮重试。异常只记录日志和告警，不向调度线程抛出。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageTrashPurgeJob {

    static final String LOCK_KEY = "job:image-trash-purge";

    private final ImageTrashPurgeService trashPurgeService;
    private final RedisLockUtil redisLockUtil;
    private final DingTalkAlertService dingTalkAlertService;
    private final ImageGcProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Scheduled(cron = "${image.gc.purge-cron:0 40 3 * * *}")
    public void purgeExpiredTrash() {
        if (!redisLockUtil.tryLock(LOCK_KEY)) {
            log.debug("未获取到回收站清理锁，跳过本次任务");
            return;
        }
        try {
            ImageTrashPurgeReport report = trashPurgeService.purgeExpiredTrash();
            if (report.isAborted()) {
                log.error("回收站保留期清理中止: reason={}, {}", report.getAbortReason(), summary(report));
                dingTalkAlertService.alert(ImageGcConstants.Alert.PURGE_FAILED,
                        "原因: " + report.getAbortReason() + "\n" + summary(report));
                return;
            }
            if (!report.isExecuted()) {
                log.info("回收站保留期清理未启用(mode={})，跳过本轮", report.getMode());
                return;
            }
            if (report.getFailedObjectCount() > 0) {
                log.error("回收站保留期清理部分失败，失败对象将在下一轮重试: failed={}",
                        report.getFailedObjectCount());
                dingTalkAlertService.alert(ImageGcConstants.Alert.PURGE_FAILED,
                        "本轮有 " + report.getFailedObjectCount() + " 个对象删除失败，将在下一轮重试\n"
                                + summary(report));
            }
            if (properties.exceedsDeleteThreshold(report.getDeletedBytes(), report.getDeletedObjectCount())) {
                log.warn("回收站清理单轮删除量超过阈值: objects={}, bytes={}",
                        report.getDeletedObjectCount(), report.getDeletedBytes());
                dingTalkAlertService.alert(ImageGcConstants.Alert.PURGE_THRESHOLD,
                        "本轮删除对象数=" + report.getDeletedObjectCount()
                                + ", 字节数=" + report.getDeletedBytes()
                                + "\n" + summary(report));
            }
        } catch (Exception e) {
            log.error("回收站保留期清理任务执行失败", e);
            dingTalkAlertService.alert(ImageGcConstants.Alert.PURGE_FAILED,
                    "回收站保留期清理任务执行失败: " + e.getMessage());
        } finally {
            redisLockUtil.unlock(LOCK_KEY);
        }
    }

    private String summary(ImageTrashPurgeReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            log.warn("回收站清理报告 JSON 序列化失败，输出默认格式", e);
            return String.valueOf(report);
        }
    }
}
