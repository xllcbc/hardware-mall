package com.example.mystore.config;

import com.example.mystore.service.MqMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 配置类
 * 定义交换机、队列、绑定关系，以及消息转换器和 Confirm 回调
 */
@Configuration
@Slf4j
public class RabbitMQConfig {

    // ==================== 交换机名称 ====================
    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String ORDER_DLX_EXCHANGE = "order.dlx.exchange";

    // ==================== 队列名称 ====================
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    public static final String ORDER_CANCEL_QUEUE = "order.cancel.queue";

    // ==================== 路由键 ====================
    public static final String ORDER_DELAY_ROUTING_KEY = "order.delay";
    public static final String ORDER_CANCEL_ROUTING_KEY = "order.cancel";

    // ==================== 延迟时间（30分钟 = 1800000毫秒） ====================
    public static final long ORDER_TIMEOUT_TTL = 30 * 60 * 1000;

    /**
     * 正常订单交换机（Direct类型）
     */
    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE, true, false);
    }

    /**
     * 死信交换机（Direct类型）
     * 消息过期后，会转发到这个交换机
     */
    @Bean
    public DirectExchange orderDlxExchange() {
        return new DirectExchange(ORDER_DLX_EXCHANGE, true, false);
    }

    /**
     * 延迟队列（消息在这里等待TTL过期）
     * 特点：
     * 1. 没有消费者监听
     * 2. 设置队列级TTL（30分钟）
     * 3. 设置死信交换机和死信路由键
     */
    @Bean
    public Queue orderDelayQueue() {
        Map<String, Object> args = new HashMap<>();
        // 死信交换机：消息过期后转发到这里
        args.put("x-dead-letter-exchange", ORDER_DLX_EXCHANGE);
        // 死信路由键：转发时使用这个路由键
        args.put("x-dead-letter-routing-key", ORDER_CANCEL_ROUTING_KEY);
        // 队列级TTL：30分钟（毫秒）
        args.put("x-message-ttl", ORDER_TIMEOUT_TTL);

        return QueueBuilder.durable(ORDER_DELAY_QUEUE).withArguments(args).build();
    }

    /**
     * 死信队列（消费者监听这个队列）
     * 延迟队列中的消息过期后，会变成死信进入这个队列
     */
    @Bean
    public Queue orderCancelQueue() {
        return QueueBuilder.durable(ORDER_CANCEL_QUEUE).build();
    }

    /**
     * 绑定：正常交换机 → 延迟队列
     * 路由键：order.delay
     */
    @Bean
    public Binding orderDelayBinding() {
        return BindingBuilder.bind(orderDelayQueue())
                .to(orderExchange())
                .with(ORDER_DELAY_ROUTING_KEY);
    }

    /**
     * 绑定：死信交换机 → 死信队列
     * 路由键：order.cancel
     */
    @Bean
    public Binding orderCancelBinding() {
        return BindingBuilder.bind(orderCancelQueue())
                .to(orderDlxExchange())
                .with(ORDER_CANCEL_ROUTING_KEY);
    }

    /**
     * 消息转换器：使用 Jackson JSON 序列化
     * 替代默认的 Java 序列化，更安全、跨语言
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate 配置
     * 1. 设置 JSON 消息转换器
     * 2. 开启 Publisher Confirm 回调
     * 3. 开启 Return 回调（消息无法路由时通知）
     * 4. mandatory=true：消息无法路由时触发 Return 回调
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MqMessageService mqMessageService) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());

        // Confirm 回调：消息到达 Exchange 后触发
        // 关键：MQ 确认收到后才更新数据库状态，保证发送与状态更新最终一致
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (correlationData == null) return;
            
            try {
                Long messageId = Long.parseLong(correlationData.getId());
                if (ack) {
                    mqMessageService.markAsSent(messageId);
                    log.debug("Confirm成功，消息已到达Exchange, messageId={}", messageId);
                } else {
                    mqMessageService.markAsFailed(messageId, "Confirm失败: " + cause);
                    log.error("Confirm失败, messageId={}, cause={}", messageId, cause);
                }
            } catch (Exception e) {
                log.error("Confirm回调处理异常, correlationData={}", correlationData.getId(), e);
            }
        });

        // Return 回调：消息到达 Exchange 但无法路由到 Queue 时触发
        template.setReturnsCallback(returned -> {
            log.error("消息路由失败, exchange={}, routingKey={}, replyCode={}, replyText={}",
                    returned.getExchange(),
                    returned.getRoutingKey(),
                    returned.getReplyCode(),
                    returned.getReplyText());
        });

        // mandatory=true：消息无法路由时触发 Return 回调，而不是静默丢弃
        template.setMandatory(true);

        return template;
    }
}
