package com.example.mystore.service.impl;

import com.example.mystore.service.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 基于日志的告警实现
 * 通过 ERROR 级别日志触发告警，适合学习项目
 * 生产环境可替换为钉钉/企业微信机器人
 */
@Service
@Slf4j
public class LogAlertServiceImpl implements AlertService {

    @Override
    public void sendAlert(String alertType, String content) {
        // 使用 ERROR 级别，方便接入 ELK/Logstash 做告警
        log.error("[ALERT] type={}, content={}", alertType, content);
    }
}
