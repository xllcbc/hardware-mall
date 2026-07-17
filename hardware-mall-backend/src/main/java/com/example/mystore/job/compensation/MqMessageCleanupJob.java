package com.example.mystore.job.compensation;

import com.example.mystore.service.MqMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 消息表清理定时任务
 * 每天凌晨 3:00 清理 status=3（已消费）且超过 90 天的消息
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MqMessageCleanupJob {

    private final MqMessageService mqMessageService;

    /**
     * 每天凌晨 3:00 执行
     * 删除已消费超过 90 天的消息，防止表无限膨胀
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanup() {
        log.info("开始清理已消费的过期消息（90天前）...");
        try {
            int deleted = mqMessageService.deleteConsumedBefore(90);
            log.info("清理完成，删除 {} 条过期消息", deleted);
        } catch (Exception e) {
            log.error("消息表清理异常", e);
        }
    }
}
