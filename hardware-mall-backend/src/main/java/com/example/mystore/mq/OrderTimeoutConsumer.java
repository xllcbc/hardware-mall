package com.example.mystore.mq;

import com.example.mystore.service.AlertService;
import com.example.mystore.service.MqMessageService;
import com.example.mystore.service.OrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.example.mystore.config.RabbitMQConfig.ORDER_CANCEL_QUEUE;

/**
 * 订单超时消费者
 * 监听死信队列，处理超时未支付的订单
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderTimeoutConsumer {

    private final OrderService orderService;
    private final MqMessageService mqMessageService;
    private final AlertService alertService;

    // 最大重试次数（通过 RabbitMQ x-death header 计数）
    private static final int MAX_RETRY_COUNT = 3;

    /**
     * 监听订单取消队列（死信队列）
     * ackMode = MANUAL：手动 ACK，确保消息处理完成后再确认
     *
     * @param messageBody   消息体（订单ID）
     * @param channel       RabbitMQ Channel
     * @param deliveryTag   消息投递标签
     * @param correlationId 关联ID（对应 mq_message.id）
     * @param xDeath        死信计数（RabbitMQ 自动维护）
     */
    @RabbitListener(queues = ORDER_CANCEL_QUEUE, ackMode = "MANUAL")
    public void handleOrderTimeout(String messageBody, Channel channel,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                    @Header(name = AmqpHeaders.CORRELATION_ID, required = false) String correlationId,
                                    @Header(name = "x-death", required = false) List<Map<String, ?>> xDeath) throws IOException {
        log.info("收到订单超时消息, messageBody={}, correlationId={}", messageBody, correlationId);

        // 检查重试次数，防止无限循环
        int retryCount = 0;
        if (xDeath != null && !xDeath.isEmpty()) {
            Map<String, ?> lastDeath = xDeath.get(xDeath.size() - 1);
            Object count = lastDeath.get("count");
            if (count instanceof Number) {
                retryCount = ((Number) count).intValue();
            }
        }

        if (retryCount >= MAX_RETRY_COUNT) {
            log.error("消息消费重试超过{}次，放弃处理, messageBody={}, correlationId={}",
                    MAX_RETRY_COUNT, messageBody, correlationId);
            alertService.sendAlert("MQ_CONSUME_DISCARD",
                    String.format("消息消费丢弃, messageBody=%s, correlationId=%s, retryCount=%d",
                            messageBody, correlationId, retryCount));
            // 不再重新入队，直接丢弃
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        try {
            // 消息体是订单ID
            Long orderId = Long.parseLong(messageBody);

            // 调用公共方法自动取消订单（内部已做幂等性检查）
            boolean cancelled = orderService.autoCancelOrder(orderId, "超时未支付，系统自动取消");

            if (cancelled && correlationId != null) {
                // 消费成功，更新消息表状态为已消费
                try {
                    mqMessageService.markAsConsumed(Long.parseLong(correlationId));
                } catch (Exception e) {
                    log.warn("更新消息消费状态失败, correlationId={}", correlationId, e);
                    // 不影响主流程，只打日志
                }
            }

            // 手动确认消息
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("订单超时处理异常, messageBody={}, correlationId={}, retryCount={}",
                    messageBody, correlationId, retryCount, e);
            // 处理失败，重新入队（RabbitMQ 会记录 x-death）
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
