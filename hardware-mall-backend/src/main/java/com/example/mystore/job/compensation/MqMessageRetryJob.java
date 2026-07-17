package com.example.mystore.job.compensation;

import com.example.mystore.entity.db.MqMessage;
import com.example.mystore.mq.OrderMessageProducer;
import com.example.mystore.service.AlertService;
import com.example.mystore.service.MqMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MQ 消息重试定时任务
 * 扫描滞留的待发送消息，重新发送到 RabbitMQ
 * 作为 MqMessageProducer 的兜底机制
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MqMessageRetryJob {

    private final MqMessageService mqMessageService;
    private final OrderMessageProducer orderMessageProducer;
    private final AlertService alertService;

    // 滞留时间阈值：5分钟
    private static final int STALE_MINUTES = 5;
    // 发送中卡住时间阈值：10分钟
    private static final int STUCK_SENDING_MINUTES = 10;
    // 每轮最大处理条数
    private static final int BATCH_SIZE = 100;

    /**
     * 每5分钟执行一次，扫描滞留消息并重发
     * fixedDelay：上次执行完毕后，再等5分钟，防止任务重叠
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void retryStaleMessages() {
        log.info("开始扫描滞留消息...");

        // 1. 扫描滞留的待发送消息（status=0）
        List<MqMessage> staleMessages = mqMessageService.listStaleMessages(STALE_MINUTES, BATCH_SIZE);

        if (!staleMessages.isEmpty()) {
            log.warn("发现 {} 条滞留消息", staleMessages.size());

            for (MqMessage message : staleMessages) {
                log.warn("处理滞留消息, messageId={}, businessId={}, retryCount={}, createTime={}",
                        message.getId(), message.getBusinessId(), message.getRetryCount(), message.getCreateTime());

                // 复用生产者的发送逻辑（包含乐观锁、异常处理等）
                orderMessageProducer.doSend(message);
            }
        }

        // 2. 扫描卡住的发送中消息（status=4 超过 10 分钟，Confirm 回调丢失兜底）
        List<MqMessage> stuckMessages = mqMessageService.listStuckSendingMessages(STUCK_SENDING_MINUTES, BATCH_SIZE);
        if (!stuckMessages.isEmpty()) {
            log.warn("发现 {} 条卡住的发送中消息（Confirm回调可能丢失）", stuckMessages.size());
            alertService.sendAlert("MQ_STUCK_SENDING",
                    String.format("发现%s条status=4卡住消息, 将恢复为待发送重试", stuckMessages.size()));

            for (MqMessage message : stuckMessages) {
                log.warn("恢复卡住消息为待发送, messageId={}, businessId={}",
                        message.getId(), message.getBusinessId());
                boolean recovered = mqMessageService.recoverToPending(message.getId());
                if (recovered) {
                    orderMessageProducer.doSend(message);
                }
            }
        }

        if (staleMessages.isEmpty() && stuckMessages.isEmpty()) {
            log.info("没有滞留消息");
        }
    }
}
