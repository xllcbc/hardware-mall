package com.example.mystore.common.constant;

/**
 * 折扣参与规则常量。
 */
public final class DiscountConstants {

    /** 参与折扣。 */
    public static final int DISCOUNT_PARTICIPATION_ENABLED = 0;
    /** 不参与折扣。 */
    public static final int DISCOUNT_PARTICIPATION_DISABLED = 1;

    private DiscountConstants() {
    }

    public static boolean isExcluded(Integer excludeDiscount) {
        return Integer.valueOf(DISCOUNT_PARTICIPATION_DISABLED).equals(excludeDiscount);
    }
}
