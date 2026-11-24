package com.example.mystore.job.image;

import com.example.mystore.common.constant.ImageGcConstants;
import com.example.mystore.config.ImageGcProperties;
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
 * 失败或中止只记录日志和告警，不向调度线程抛出。单轮回收量（report 模式为候选量）超过配置阈值时额外告警。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageGcJob {

    static final String LOCK_KEY = "job:image-gc";

    private final ImageGcService imageGcService;
    private final RedisLockUtil redisLockUtil;
    private final DingTalkAlertService dingTalkAlertService;
    private final ImageGcProperties properties;

    @Scheduled(cron = "${image.gc.report-cron:0 0 3 * * MON}")
    public void runWeeklyReport() {
        ImageJobSupport.runLocked(redisLockUtil, dingTalkAlertService, LOCK_KEY, "图片回收",
                ImageGcConstants.Alert.FAILED, this::executeReport);
    }

    private void executeReport() {
        ImageGcReport report = imageGcService.runOnce();
        if (report.isAborted()) {
            log.error("图片回收报告中止: reason={}, {}", report.getAbortReason(), summary(report));
            dingTalkAlertService.alert(ImageGcConstants.Alert.ABORTED,
                    "原因: " + report.getAbortReason() + "\n" + summary(report));
            return;
        }
        if (!report.isExecuted()) {
            log.info("图片回收任务未启用(mode={})，跳过本轮", report.getMode());
            return;
        }
        log.info("图片回收报告: {}", summary(report));
        if (exceedsThreshold(report)) {
            log.warn("图片回收单轮回收量超过阈值: mode={}, objects={}, bytes={}",
                    report.getMode(), thresholdCount(report), thresholdBytes(report));
            dingTalkAlertService.alert(ImageGcConstants.Alert.THRESHOLD, thresholdMessage(report)
                    + "\n" + summary(report));
        }
    }

    /**
     * report 模式比较的是“如果切到 trash 会回收多少”的候选量，本轮并未删除任何对象；
     * trash 模式比较的是实际移入回收站的数量与字节数。
     */
    private boolean exceedsThreshold(ImageGcReport report) {
        return properties.exceedsDeleteThreshold(thresholdBytes(report), thresholdCount(report));
    }

    private static int thresholdCount(ImageGcReport report) {
        return report.getMode() == ImageGcConstants.Mode.REPORT
                ? report.getCandidateCount()
                : report.getTrashedObjectCount();
    }

    private static long thresholdBytes(ImageGcReport report) {
        return report.getMode() == ImageGcConstants.Mode.REPORT
                ? report.getReclaimableBytes()
                : report.getTrashedBytes();
    }

    private static String thresholdMessage(ImageGcReport report) {
        String prefix = report.getMode() == ImageGcConstants.Mode.REPORT
                ? "report 模式本轮候选回收量超过阈值（未删除任何对象）: 候选对象数="
                : "本轮回收对象数=";
        return prefix + thresholdCount(report) + ", 字节数=" + thresholdBytes(report);
    }

    private String summary(ImageGcReport report) {
        return ImageJobSupport.summary(report, log);
    }
}
