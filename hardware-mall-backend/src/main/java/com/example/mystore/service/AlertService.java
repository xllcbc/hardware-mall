package com.example.mystore.service;

/**
 * 告警服务接口
 * 用于在异常场景（消息发送失败、消费丢弃等）时触发告警
 * 当前实现：ERROR 日志（可扩展为钉钉/企微/邮件）
 */
public interface AlertService {

    /**
     * 发送告警
     * @param alertType 告警类型（如 MQ_SEND_FAIL, MQ_CONSUME_DISCARD）
     * @param content 告警内容
     */
    void sendAlert(String alertType, String content);
}
