package com.example.mystore.mq;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class RabbitMQIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void testConnectionAndSendReceive() {
        String queueName = "test.queue";
        String message = "hello rabbitmq";

        // 创建队列（如果不存在）
        rabbitTemplate.execute(channel -> {
            channel.queueDeclare(queueName, false, false, false, null);
            return null;
        });

        // 发送消息
        rabbitTemplate.convertAndSend(queueName, message);

        // 接收消息
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Object received = rabbitTemplate.receiveAndConvert(queueName, 1000);
            assertThat(received).isEqualTo(message);
        });
    }

}
