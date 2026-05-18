package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.MqMessage;
import com.example.mystore.mapper.MqMessageMapper;
import com.example.mystore.service.AlertService;
import com.example.mystore.service.MqMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地消息表服务实现
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MqMessageServiceImpl extends ServiceImpl<MqMessageMapper, MqMessage> implements MqMessageService {

    private final AlertService alertService;

    @Override
    public boolean saveMessage(MqMessage message) {
        return save(message);
    }

    @Override
    public List<MqMessage> listPendingMessages(int limit) {
        return baseMapper.selectPendingMessages(limit);
    }

    @Override
    public boolean tryLockForSend(Long messageId) {
        // 乐观锁：只有 status=0 才能更新为 status=4
        MqMessage message = new MqMessage();
        message.setId(messageId);
        message.setStatus(StatusConstants.MQ_STATUS_SENDING);
        message.setUpdateTime(LocalDateTime.now());

        return lambdaUpdate()
                .eq(MqMessage::getId, messageId)
                .eq(MqMessage::getStatus, StatusConstants.MQ_STATUS_PENDING)
                .update(message);
    }

    @Override
    public boolean markAsSent(Long messageId) {
        MqMessage message = new MqMessage();
        message.setId(messageId);
        message.setStatus(StatusConstants.MQ_STATUS_SENT);
        message.setSendTime(LocalDateTime.now());
        message.setUpdateTime(LocalDateTime.now());
        return updateById(message);
    }

    @Override
    public boolean markAsFailed(Long messageId, String errorMsg) {
        MqMessage dbMessage = getById(messageId);
        if (dbMessage == null) {
            return false;
        }
        int newRetryCount = dbMessage.getRetryCount() + 1;
        MqMessage message = new MqMessage();
        message.setId(messageId);
        message.setRetryCount(newRetryCount);
        message.setErrorMsg(errorMsg);
        message.setUpdateTime(LocalDateTime.now());

        // 重试超过3次，标记为发送失败；否则恢复为待发送，下次轮询重试
        if (newRetryCount >= 3) {
            message.setStatus(StatusConstants.MQ_STATUS_FAILED);
            log.warn("消息重试超过3次，标记为发送失败, messageId={}", messageId);
            alertService.sendAlert("MQ_SEND_FAIL",
                    String.format("消息发送失败超过3次, messageId=%s, businessType=%s, businessId=%s, error=%s",
                            messageId, dbMessage.getBusinessType(), dbMessage.getBusinessId(), errorMsg));
        } else {
            message.setStatus(StatusConstants.MQ_STATUS_PENDING);
            log.info("消息发送失败，恢复为待发送, messageId={}, retryCount={}", messageId, newRetryCount);
        }
        return updateById(message);
    }

    @Override
    public boolean markAsConsumed(Long messageId) {
        MqMessage message = new MqMessage();
        message.setId(messageId);
        message.setStatus(StatusConstants.MQ_STATUS_CONSUMED);
        message.setUpdateTime(LocalDateTime.now());
        return updateById(message);
    }

    @Override
    public List<MqMessage> listStaleMessages(int staleMinutes, int limit) {
        LocalDateTime beforeTime = LocalDateTime.now().minusMinutes(staleMinutes);
        return baseMapper.selectStaleMessages(beforeTime, limit);
    }

    @Override
    public int deleteConsumedBefore(int daysBefore) {
        LocalDateTime beforeTime = LocalDateTime.now().minusDays(daysBefore);
        return baseMapper.deleteConsumedBefore(beforeTime);
    }

    @Override
    public List<MqMessage> listStuckSendingMessages(int stuckMinutes, int limit) {
        LocalDateTime beforeTime = LocalDateTime.now().minusMinutes(stuckMinutes);
        return baseMapper.selectStuckSendingMessages(beforeTime, limit);
    }

    @Override
    public boolean recoverToPending(Long messageId) {
        // 乐观锁：只有 status=4 才能恢复为 0
        MqMessage message = new MqMessage();
        message.setId(messageId);
        message.setStatus(StatusConstants.MQ_STATUS_PENDING);
        message.setUpdateTime(LocalDateTime.now());

        return lambdaUpdate()
                .eq(MqMessage::getId, messageId)
                .eq(MqMessage::getStatus, StatusConstants.MQ_STATUS_SENDING)
                .update(message);
    }
}
