package com.example.mystore.service.impl;

import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.service.PriceService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PriceServiceImpl implements PriceService {

    @Override
    public PriceResult resolve(Sku sku, Integer userGroup) {
        if (sku == null || sku.getPrice() == null) {
            throw new IllegalArgumentException("SKU 价格缺失");
        }
        if (userGroup != null
                && userGroup == StatusConstants.USER_GROUP_WHOLESALE
                && sku.getWholesalePrice() != null) {
            return new PriceResult(sku.getWholesalePrice(), StatusConstants.PRICE_TYPE_WHOLESALE);
        }
        return new PriceResult(sku.getPrice(), StatusConstants.PRICE_TYPE_RETAIL);
    }
}
