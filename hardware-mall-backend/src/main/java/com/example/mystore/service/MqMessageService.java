package com.example.mystore.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.mystore.entity.db.MqMessage;

import java.util.List;

/**
 * 本地消息表服务接口
 */
public interface MqMessageService extends IService<MqMessage> {

    /**
     * 保存消息记录（用于事务内写入）
     */
    boolean saveMessage(MqMessage message);

    /**
     * 查询待发送的消息（带LIMIT）
     */
    List<MqMessage> listPendingMessages(int limit);

    /**
     * 乐观锁抢锁：将 status=0 更新为 status=4（发送中）
     * @return true-抢锁成功；false-已被其他线程抢走
     */
    boolean tryLockForSend(Long messageId);

    /**
     * 标记消息为已发送
     */
    boolean markAsSent(Long messageId);

    /**
     * 标记消息发送失败（增加重试次数）
     */
    boolean markAsFailed(Long messageId, String errorMsg);

    /**
     * 标记消息为已消费
     */
    boolean markAsConsumed(Long messageId);

    /**
     * 查询滞留消息（创建时间超过指定分钟数，带LIMIT）
     */
    List<MqMessage> listStaleMessages(int staleMinutes, int limit);

    /**
     * 删除已消费超过指定天数的消息
     * @param daysBefore 保留天数，如 90 表示删除 90 天前的已消费消息
     * @return 删除条数
     */
    int deleteConsumedBefore(int daysBefore);

    /**
     * 查询卡住的发送中消息（status=4 且 update_time 超过指定分钟数）
     * 用于处理 Confirm 回调丢失导致的僵尸消息
     */
    List<MqMessage> listStuckSendingMessages(int stuckMinutes, int limit);

    /**
     * 强制恢复消息为待发送状态（用于定时任务兜底）
     */
    boolean recoverToPending(Long messageId);
}
