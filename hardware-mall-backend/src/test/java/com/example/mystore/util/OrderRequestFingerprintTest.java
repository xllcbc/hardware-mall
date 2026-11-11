package com.example.mystore.util;

import com.example.mystore.entity.dto.CreateOrderRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRequestFingerprintTest {

    @Test
    void sameRequest_producesStableSha256() {
        CreateOrderRequest first = request(List.of(item(5L, 2), item(6L, 3)));
        CreateOrderRequest second = request(List.of(item(5L, 2), item(6L, 3)));

        String hash = OrderRequestFingerprint.fingerprint(first);

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(OrderRequestFingerprint.fingerprint(second)).isEqualTo(hash);
    }

    @Test
    void itemOrder_doesNotChangeFingerprint() {
        CreateOrderRequest ordered = request(List.of(item(5L, 2), item(6L, 3)));
        CreateOrderRequest reversed = request(List.of(item(6L, 3), item(5L, 2)));

        assertThat(OrderRequestFingerprint.fingerprint(reversed))
                .isEqualTo(OrderRequestFingerprint.fingerprint(ordered));
    }

    @Test
    void quantityChange_changesFingerprint() {
        CreateOrderRequest original = request(List.of(item(5L, 2)));
        CreateOrderRequest changed = request(List.of(item(5L, 3)));

        assertThat(OrderRequestFingerprint.fingerprint(changed))
                .isNotEqualTo(OrderRequestFingerprint.fingerprint(original));
    }

    @Test
    void addressLogisticsRemarkAndAmount_changesFingerprint() {
        CreateOrderRequest original = request(List.of(item(5L, 2)));
        CreateOrderRequest otherAddress = request(List.of(item(5L, 2)));
        otherAddress.setAddressId(9L);
        CreateOrderRequest otherLogistics = request(List.of(item(5L, 2)));
        otherLogistics.setLogisticsId(8L);
        CreateOrderRequest otherRemark = request(List.of(item(5L, 2)));
        otherRemark.setBuyerRemark("放门口");
        CreateOrderRequest otherAmount = request(List.of(item(5L, 2)));
        otherAmount.setExpectedPayAmount(new BigDecimal("41.00"));

        String base = OrderRequestFingerprint.fingerprint(original);
        assertThat(OrderRequestFingerprint.fingerprint(otherAddress)).isNotEqualTo(base);
        assertThat(OrderRequestFingerprint.fingerprint(otherLogistics)).isNotEqualTo(base);
        assertThat(OrderRequestFingerprint.fingerprint(otherRemark)).isNotEqualTo(base);
        assertThat(OrderRequestFingerprint.fingerprint(otherAmount)).isNotEqualTo(base);
    }

    @Test
    void amountScale_doesNotChangeFingerprint() {
        CreateOrderRequest scaled = request(List.of(item(5L, 2)));
        scaled.setExpectedPayAmount(new BigDecimal("40.00"));
        CreateOrderRequest compact = request(List.of(item(5L, 2)));
        compact.setExpectedPayAmount(new BigDecimal("40.0"));

        assertThat(OrderRequestFingerprint.fingerprint(compact))
                .isEqualTo(OrderRequestFingerprint.fingerprint(scaled));
    }

    private CreateOrderRequest request(List<CreateOrderRequest.CartItem> items) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(items);
        request.setAddressId(1L);
        request.setLogisticsId(2L);
        request.setExpectedPayAmount(new BigDecimal("40.00"));
        return request;
    }

    private CreateOrderRequest.CartItem item(Long skuId, Integer quantity) {
        CreateOrderRequest.CartItem item = new CreateOrderRequest.CartItem();
        item.setSkuId(skuId);
        item.setQuantity(quantity);
        return item;
    }
}
