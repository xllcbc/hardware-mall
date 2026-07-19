package com.example.mystore.entity.db;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment_record")
public class PaymentRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String outTradeNo;

    private String transactionId;

    private BigDecimal amount;

    private Integer status;

    private LocalDateTime payTime;

    private BigDecimal refundAmount;

    private LocalDateTime refundTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_PAID = 1;
    public static final int STATUS_CLOSED = 2;
    public static final int STATUS_REFUNDED = 3;
    public static final int STATUS_REFUNDING = 4;
}