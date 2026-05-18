package com.example.mystore.mq;

import com.example.mystore.entity.db.MqMessage;
import com.example.mystore.service.MqMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单消息生产者
 * 纯定时任务轮询驱动：每10秒扫描 mq_message 表，发送待发送消息
 * 发送状态由 RabbitMQ Confirm 回调异步更新，不再同步更新
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderMessageProducer {

    private final MqMessageService mqMessageService;
    private final RabbitTemplate rabbitTemplate;
    private final MessageConverter messageConverter;

    // 每轮最大处理条数，防止OOM
    private static final int BATCH_SIZE = 100;

    /**
     * 每10秒扫描一次，发送待发送的消息
     * fixedDelay：上次执行完毕后，再等10秒，防止任务重叠
     */
    @Scheduled(fixedDelay = 10_000)
    public void scanAndSend() {
        List<MqMessage> pendingMessages = mqMessageService.listPendingMessages(BATCH_SIZE);

        if (pendingMessages.isEmpty()) {
            return;
        }

        log.debug("扫描到 {} 条待发送消息", pendingMessages.size());

        for (MqMessage message : pendingMessages) {
            doSend(message);
        }
    }

    /**
     * 单条消息发送（供定时任务和重试任务复用）
     * 1. 乐观锁抢锁（status=0 → 4）
     * 2. 发送到 RabbitMQ，携带 CorrelationData 用于 Confirm 回调
     * 注意：不在这里同步更新状态！状态由 Confirm 回调异步更新
     */
    public void doSend(MqMessage message) {
        // 乐观锁抢锁：只有 status=0 才能更新为 4
        boolean locked = mqMessageService.tryLockForSend(message.getId());
        if (!locked) {
            log.debug("消息已被其他线程锁定, messageId={}", message.getId());
            return;
        }

        try {
            log.debug("发送消息, messageId={}, businessId={}", message.getId(), message.getBusinessId());

            // 发送消息到 RabbitMQ，携带 CorrelationData
            // Confirm 回调会根据 correlationData.id 更新数据库状态
            // RabbitTemplate 会自动把 CorrelationData.id 同步到 MessageProperties.correlationId
            rabbitTemplate.convertAndSend(
                    message.getExchange(),
                    message.getRoutingKey(),
                    message.getMessageBody(),
                    new org.springframework.amqp.rabbit.connection.CorrelationData(message.getId().toString())
            );

            log.debug("消息已投递, messageId={}", message.getId());

        } catch (Exception e) {
            // 同步发送异常（如连接断开），立即恢复为待发送
            mqMessageService.markAsFailed(message.getId(), e.getMessage());
            log.error("消息同步发送异常, messageId={}", message.getId(), e);
        }
    }
}
