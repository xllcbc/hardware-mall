package com.example.mystore.service.impl;

import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.service.PriceService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PriceServiceImplTest {

    private final PriceService priceService = new PriceServiceImpl();

    private Sku sku(BigDecimal price, BigDecimal wholesalePrice) {
        Sku sku = new Sku();
        sku.setPrice(price);
        sku.setWholesalePrice(wholesalePrice);
        return sku;
    }

    @Test
    void wholesaleUserWithWholesalePrice_paysWholesale() {
        Sku sku = sku(new BigDecimal("25.00"), new BigDecimal("18.00"));
        PriceService.PriceResult r = priceService.resolve(sku, StatusConstants.USER_GROUP_WHOLESALE);
        assertThat(r.unitPrice()).isEqualByComparingTo("18.00");
        assertThat(r.priceType()).isEqualTo(StatusConstants.PRICE_TYPE_WHOLESALE);
    }

    @Test
    void wholesaleUserWithoutWholesalePrice_fallsBackToRetail() {
        Sku sku = sku(new BigDecimal("25.00"), null);
        PriceService.PriceResult r = priceService.resolve(sku, StatusConstants.USER_GROUP_WHOLESALE);
        assertThat(r.unitPrice()).isEqualByComparingTo("25.00");
        assertThat(r.priceType()).isEqualTo(StatusConstants.PRICE_TYPE_RETAIL);
    }

    @Test
    void retailUserAlwaysPaysRetail_evenIfWholesalePriceExists() {
        Sku sku = sku(new BigDecimal("25.00"), new BigDecimal("18.00"));
        PriceService.PriceResult r = priceService.resolve(sku, StatusConstants.USER_GROUP_RETAIL);
        assertThat(r.unitPrice()).isEqualByComparingTo("25.00");
        assertThat(r.priceType()).isEqualTo(StatusConstants.PRICE_TYPE_RETAIL);
    }

    @Test
    void nullUserGroup_treatedAsRetail() {
        Sku sku = sku(new BigDecimal("25.00"), new BigDecimal("18.00"));
        PriceService.PriceResult r = priceService.resolve(sku, null);
        assertThat(r.unitPrice()).isEqualByComparingTo("25.00");
        assertThat(r.priceType()).isEqualTo(StatusConstants.PRICE_TYPE_RETAIL);
    }
}
