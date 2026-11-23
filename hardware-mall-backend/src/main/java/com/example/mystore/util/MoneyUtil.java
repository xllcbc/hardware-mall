package com.example.mystore.util;

import com.example.mystore.common.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 人民币「元 ↔ 整数分」换算的唯一入口。
 *
 * <p>合法金额为非负且最多两位小数；子分、负数等非法金额抛出业务异常，
 * 不做静默截断，也不依赖调用方各自实现。支付与退款都必须经过这里。
 */
public final class MoneyUtil {

    private static final int CENTS_SCALE = 2;

    private MoneyUtil() {
    }

    /**
     * 元 → 整数分。
     *
     * @throws BusinessException 金额为空、为负、含子分或超出可表示范围
     */
    public static long yuanToCents(BigDecimal yuan) {
        if (yuan == null) {
            throw new BusinessException("金额不能为空");
        }
        if (yuan.signum() < 0) {
            throw new BusinessException("金额不能为负");
        }
        if (yuan.stripTrailingZeros().scale() > CENTS_SCALE) {
            throw new BusinessException("金额最多两位小数");
        }
        try {
            return yuan.movePointRight(CENTS_SCALE)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
        } catch (ArithmeticException e) {
            throw new BusinessException("金额超出范围");
        }
    }

    /** 整数分 → 元（两位小数）。 */
    public static BigDecimal centsToYuan(long cents) {
        return BigDecimal.valueOf(cents).movePointLeft(CENTS_SCALE);
    }
}
