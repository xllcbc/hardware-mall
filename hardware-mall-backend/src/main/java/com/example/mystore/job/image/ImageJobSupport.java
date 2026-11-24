package com.example.mystore.job.image;

import com.example.mystore.service.impl.DingTalkAlertService;
import com.example.mystore.util.RedisLockUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

/**
 * 图片回收相关定时任务的共用脚手架：分布式锁、异常兜底告警与报告 JSON 序列化。
 * 只服务于本包内的任务，任务自身的业务判断仍留在各自类中。
 */
@Slf4j
final class ImageJobSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ImageJobSupport() {
    }

    /**
     * 拿到锁才执行任务；异常只记录日志和告警，不向调度线程抛出；无论成功失败都释放锁。
     */
    static void runLocked(RedisLockUtil redisLockUtil, DingTalkAlertService dingTalkAlertService,
                          String lockKey, String taskName, String failureAlertType, Runnable task) {
        if (!redisLockUtil.tryLock(lockKey)) {
            log.debug("未获取到{}锁，跳过本次任务", taskName);
            return;
        }
        try {
            task.run();
        } catch (Exception e) {
            log.error("{}任务执行失败", taskName, e);
            dingTalkAlertService.alert(failureAlertType, taskName + "任务执行失败: " + e.getMessage());
        } finally {
            redisLockUtil.unlock(lockKey);
        }
    }

    static String summary(Object report, Logger logger) {
        try {
            return OBJECT_MAPPER.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            logger.warn("任务报告 JSON 序列化失败，输出默认格式", e);
            return String.valueOf(report);
        }
    }
}
