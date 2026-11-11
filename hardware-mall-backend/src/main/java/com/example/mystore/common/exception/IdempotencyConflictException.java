package com.example.mystore.common.exception;

/**
 * 同一幂等键被用于不同请求指纹。客户端据此刷新幂等键后重试。
 */
public class IdempotencyConflictException extends BusinessException {

    public static final int CODE = 40902;

    public IdempotencyConflictException() {
        super(CODE, "幂等键已用于其他请求，请刷新后重试");
    }
}
