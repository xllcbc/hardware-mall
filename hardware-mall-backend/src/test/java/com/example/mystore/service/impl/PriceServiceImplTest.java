package com.example.mystore.service.impl;

import com.example.mystore.service.PriceService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceServiceImplTest {

    private final PriceService priceService = new PriceServiceImpl();

    @Test
    void rateOne_keepsRetailScale() {
        assertThat(priceService.resolveUnitPrice(new BigDecimal("25.00"), new BigDecimal("1.0000")))
                .isEqualByComparingTo("25.00");
    }

    @Test
    void discountsUsingHalfUpToTwoDecimals() {
        assertThat(priceService.resolveUnitPrice(new BigDecimal("8.115"), new BigDecimal("1.0000")))
                .isEqualByComparingTo("8.12");
        assertThat(priceService.resolveUnitPrice(new BigDecimal("10.00"), new BigDecimal("0.8000")))
                .isEqualByComparingTo("8.00");
    }

    @Test
    void rejectsInvalidRate() {
        assertThatThrownBy(() -> priceService.resolveUnitPrice(
                new BigDecimal("10.00"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> priceService.resolveUnitPrice(
                new BigDecimal("10.00"), new BigDecimal("1.0001")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingOrNegativeRetailPrice() {
        assertThatThrownBy(() -> priceService.resolveUnitPrice(null, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> priceService.resolveUnitPrice(new BigDecimal("-1.00"), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
