package com.example.mystore.job.image;

import com.example.mystore.common.constant.ImageGcConstants;
import com.example.mystore.config.ImageGcProperties;
import com.example.mystore.entity.vo.ImageGcReport;
import com.example.mystore.service.ImageGcService;
import com.example.mystore.service.impl.DingTalkAlertService;
import com.example.mystore.util.RedisLockUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每周图片回收任务。多实例同时触发时只有拿到分布式锁的实例执行；
 * 失败或中止只记录日志和告警，不向调度线程抛出。单轮回收量超过配置阈值时额外告警。
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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
                dingTalkAlertService.alert(ImageGcConstants.Alert.ABORTED,
                        "原因: " + report.getAbortReason() + "\n" + summary(report));
                return;
            }
            if (!report.isExecuted()) {
                log.info("图片回收任务未启用(mode={})，跳过本轮", report.getMode());
                return;
            }
            log.info("图片回收报告: {}", summary(report));
            if (properties.exceedsDeleteThreshold(report.getTrashedBytes(), report.getTrashedObjectCount())) {
                log.warn("图片回收单轮回收量超过阈值: objects={}, bytes={}",
                        report.getTrashedObjectCount(), report.getTrashedBytes());
                dingTalkAlertService.alert(ImageGcConstants.Alert.THRESHOLD,
                        "本轮回收对象数=" + report.getTrashedObjectCount()
                                + ", 字节数=" + report.getTrashedBytes()
                                + "\n" + summary(report));
            }
        } catch (Exception e) {
            log.error("图片回收任务执行失败", e);
            dingTalkAlertService.alert(ImageGcConstants.Alert.FAILED,
                    "图片回收任务执行失败: " + e.getMessage());
        } finally {
            redisLockUtil.unlock(LOCK_KEY);
        }
    }

    private String summary(ImageGcReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            log.warn("图片回收报告 JSON 序列化失败，输出默认格式", e);
            return String.valueOf(report);
        }
    }
}
