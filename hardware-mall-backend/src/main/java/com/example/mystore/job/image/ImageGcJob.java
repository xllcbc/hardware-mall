package com.example.mystore.job.image;

import com.example.mystore.entity.vo.ImageGcReport;
import com.example.mystore.service.ImageGcService;
import com.example.mystore.service.impl.DingTalkAlertService;
import com.example.mystore.util.RedisLockUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每周图片回收任务。多实例同时触发时只有拿到分布式锁的实例执行；
 * 失败或中止只记录日志和告警，不向调度线程抛出。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageGcJob {

    static final String LOCK_KEY = "job:image-gc";
    static final String ALERT_ABORTED = "IMAGE_GC_ABORTED";
    static final String ALERT_FAILED = "IMAGE_GC_FAILED";

    private final ImageGcService imageGcService;
    private final RedisLockUtil redisLockUtil;
    private final DingTalkAlertService dingTalkAlertService;

    @Scheduled(cron = "${image.gc.report-cron:0 0 3 * * MON}")
    public void runWeeklyReport() {
        if (!redisLockUtil.tryLock(LOCK_KEY)) {
            log.debug("未获取到图片回收锁，跳过本次任务");
            return;
        }
        try {
            ImageGcReport report = imageGcService.runOnce();
            if (report.isAborted()) {
                log.error("图片回收报告中止: reason={}, {}", report.getAbortReason(), summary(report));
                dingTalkAlertService.alert(ALERT_ABORTED,
                        "原因: " + report.getAbortReason() + "\n" + summary(report));
                return;
            }
            if (!report.isExecuted()) {
                log.info("图片回收任务未启用(mode={})，跳过本轮", report.getMode());
                return;
            }
            log.info("图片回收报告: {}", summary(report));
        } catch (Exception e) {
            log.error("图片回收任务执行失败", e);
            dingTalkAlertService.alert(ALERT_FAILED, "图片回收任务执行失败: " + e.getMessage());
        } finally {
            redisLockUtil.unlock(LOCK_KEY);
        }
    }

    private static String summary(ImageGcReport report) {
        return "mode=" + report.getMode()
                + ", scanned=" + report.getScannedObjectCount()
                + ", mappedReferences=" + report.getMappedReferenceCount()
                + ", referenced=" + report.getReferencedObjectCount()
                + ", unreferenced=" + report.getUnreferencedObjectCount()
                + ", candidates=" + report.getCandidateCount()
                + ", reclaimableBytes=" + report.getReclaimableBytes()
                + ", samples=" + report.getSampleObjectKeys();
    }
}
