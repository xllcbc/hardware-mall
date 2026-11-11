package com.example.mystore.util;

import com.example.mystore.entity.dto.CreateOrderRequest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 * 下单请求指纹：商品项（skuId + quantity，按 skuId 排序）、地址、物流、
 * 买家备注和期望支付金额的规范化摘要。同一幂等键只有同指纹才能返回原订单。
 */
public final class OrderRequestFingerprint {

    private static final String SEPARATOR = "\u001f";
    private static final String VERSION = "v1";

    private OrderRequestFingerprint() {
    }

    public static String fingerprint(CreateOrderRequest request) {
        String items = request.getItems() == null ? "" : request.getItems().stream()
                .sorted(Comparator
                        .comparing(CreateOrderRequest.CartItem::getSkuId,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(CreateOrderRequest.CartItem::getQuantity,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(item -> item.getSkuId() + ":" + item.getQuantity())
                .collect(Collectors.joining(","));

        String canonical = String.join(SEPARATOR,
                VERSION,
                "items=" + items,
                "addressId=" + request.getAddressId(),
                "logisticsId=" + request.getLogisticsId(),
                "buyerRemark=" + (request.getBuyerRemark() == null ? "" : request.getBuyerRemark()),
                "expectedPayAmount=" + normalizeAmount(request.getExpectedPayAmount()));

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String normalizeAmount(BigDecimal amount) {
        return amount == null ? "" : amount.stripTrailingZeros().toPlainString();
    }
}
