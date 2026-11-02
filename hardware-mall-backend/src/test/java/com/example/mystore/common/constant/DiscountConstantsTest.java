package com.example.mystore.common.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscountConstantsTest {

    @Test
    void onlyExplicitlyExcludedValueExcludesDiscount() {
        assertThat(DiscountConstants.isExcluded(null)).isFalse();
        assertThat(DiscountConstants.isExcluded(
                DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED)).isFalse();
        assertThat(DiscountConstants.isExcluded(
                DiscountConstants.DISCOUNT_PARTICIPATION_DISABLED)).isTrue();
    }
}
