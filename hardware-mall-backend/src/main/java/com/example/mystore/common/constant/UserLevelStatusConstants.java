package com.example.mystore.common.constant;

import java.math.BigDecimal;

/**
 * 用户等级与定价倍率常量。
 */
public class UserLevelStatusConstants {

    private UserLevelStatusConstants() {
    }

    public static final long RETAIL_LEVEL_ID = 1L;

    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_ENABLED = 1;

    public static final int NOT_RETAIL = 0;
    public static final int IS_RETAIL = 1;

    public static final BigDecimal DEFAULT_RATE = new BigDecimal("1.0000");
    public static final BigDecimal MIN_EXCLUSIVE_RATE = BigDecimal.ZERO;
    public static final BigDecimal MAX_INCLUSIVE_RATE = BigDecimal.ONE;
}
