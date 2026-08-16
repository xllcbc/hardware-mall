package com.example.mystore.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageUtilTest {

    @Test
    void clampLimit_null_returnsDefault() {
        assertThat(PageUtil.clampLimit(null, 20, 50)).isEqualTo(20);
    }

    @Test
    void clampLimit_tooSmall_returnsDefault() {
        assertThat(PageUtil.clampLimit(0, 20, 50)).isEqualTo(20);
        assertThat(PageUtil.clampLimit(-5, 20, 50)).isEqualTo(20);
    }

    @Test
    void clampLimit_tooLarge_isCapped() {
        assertThat(PageUtil.clampLimit(100000, 20, 50)).isEqualTo(50);
    }

    @Test
    void clampLimit_withinRange_isKept() {
        assertThat(PageUtil.clampLimit(30, 20, 50)).isEqualTo(30);
    }
}
