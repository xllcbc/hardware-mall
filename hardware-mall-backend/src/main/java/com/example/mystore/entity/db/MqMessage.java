package com.example.mystore.entity.db;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地消息表实体
 * 用于保证分布式事务的最终一致性
 */
@Data
@TableName("mq_message")
public class MqMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 业务类型：ORDER_TIMEOUT
     */
    private String businessType;

    /**
     * 业务ID：订单ID
     */
    private String businessId;

    /**
     * MQ交换机
     */
    private String exchange;

    /**
     * MQ路由键
     */
    private String routingKey;

    /**
     * 消息内容（JSON格式）
     */
    private String messageBody;

    /**
     * 状态：0-待发送，1-已发送，2-发送失败，3-已消费
     */
    private Integer status;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 失败原因
     */
    private String errorMsg;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 发送时间
     */
    private LocalDateTime sendTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
