package com.example.mystore.service;

import com.example.mystore.entity.db.Sku;

import java.math.BigDecimal;

/**
 * 定价入口: 按用户分组解析 SKU 成交价。
 * 所有成交价必须经此解析, 不得在别处直接读 sku.price。
 */
public interface PriceService {

    /**
     * @param unitPrice 成交单价
     * @param priceType 价格类型(StatusConstants.PRICE_TYPE_*)
     */
    record PriceResult(BigDecimal unitPrice, int priceType) {
    }

    /**
     * @param userGroup StatusConstants.USER_GROUP_*, 为 null 按零售处理
     */
    PriceResult resolve(Sku sku, Integer userGroup);
}
