package com.example.mystore.util;

import com.example.mystore.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyUtilTest {

    @Test
    void convertsYuanWithTwoDecimalsToCents() {
        assertThat(MoneyUtil.yuanToCents(new BigDecimal("100.00"))).isEqualTo(10_000L);
        assertThat(MoneyUtil.yuanToCents(new BigDecimal("0.01"))).isEqualTo(1L);
        assertThat(MoneyUtil.yuanToCents(new BigDecimal("12.34"))).isEqualTo(1_234L);
    }

    @Test
    void convertsZeroYuanToZeroCents() {
        assertThat(MoneyUtil.yuanToCents(BigDecimal.ZERO)).isZero();
        assertThat(MoneyUtil.centsToYuan(0L)).isEqualByComparingTo("0.00");
    }

    @Test
    void convertsLargeAmountWithoutPrecisionLoss() {
        assertThat(MoneyUtil.yuanToCents(new BigDecimal("12345678.99"))).isEqualTo(1_234_567_899L);
    }

    @Test
    void acceptsTrailingZerosBeyondTwoDecimals() {
        assertThat(MoneyUtil.yuanToCents(new BigDecimal("12.3400"))).isEqualTo(1_234L);
    }

    @Test
    void rejectsAmountBeyondLongRange() {
        assertThatThrownBy(() -> MoneyUtil.yuanToCents(new BigDecimal("999999999999999999999.99")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超出范围");
    }

    @Test
    void rejectsSubCentAmount() {
        assertThatThrownBy(() -> MoneyUtil.yuanToCents(new BigDecimal("0.001")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("两位小数");
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> MoneyUtil.yuanToCents(new BigDecimal("-0.01")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为负");
    }

    @Test
    void rejectsNullAmount() {
        assertThatThrownBy(() -> MoneyUtil.yuanToCents(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void convertsCentsBackToYuan() {
        assertThat(MoneyUtil.centsToYuan(12_345L)).isEqualByComparingTo("123.45");
        assertThat(MoneyUtil.centsToYuan(1L)).isEqualByComparingTo("0.01");
    }
}
