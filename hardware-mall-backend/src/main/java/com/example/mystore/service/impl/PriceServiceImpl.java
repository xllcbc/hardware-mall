package com.example.mystore.service.impl;

import com.example.mystore.common.constant.UserLevelStatusConstants;
import com.example.mystore.service.PriceService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PriceServiceImpl implements PriceService {

    @Override
    public BigDecimal resolveUnitPrice(BigDecimal basePrice, BigDecimal rate) {
        if (basePrice == null) {
            throw new IllegalArgumentException("基础价格不能为空");
        }
        if (basePrice.signum() < 0) {
            throw new IllegalArgumentException("基础价格不能为负");
        }
        if (rate == null
                || rate.compareTo(UserLevelStatusConstants.MIN_EXCLUSIVE_RATE) <= 0
                || rate.compareTo(UserLevelStatusConstants.MAX_INCLUSIVE_RATE) > 0) {
            throw new IllegalArgumentException("定价倍率必须在 (0, 3] 之间");
        }
        return basePrice.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}
