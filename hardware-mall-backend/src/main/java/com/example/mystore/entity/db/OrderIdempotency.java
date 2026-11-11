package com.example.mystore.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_idempotency")
public class OrderIdempotency {

    public static final String STATE_PROCESSING = "PROCESSING";
    public static final String STATE_SUCCEEDED = "SUCCEEDED";
    public static final String STATE_FAILED = "FAILED";
    public static final int IDEMPOTENCY_KEY_MAX_LENGTH = 128;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String idemKey;

    private String requestHash;

    private String state;

    private String ownerToken;

    private Long orderId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
