package com.example.mystore.service.impl;

import com.example.mystore.common.constant.UserLevelStatusConstants;
import com.example.mystore.service.PriceService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PriceServiceImpl implements PriceService {

    @Override
    public BigDecimal resolveUnitPrice(BigDecimal retailPrice, BigDecimal rate) {
        if (retailPrice == null) {
            throw new IllegalArgumentException("零售价不能为空");
        }
        if (retailPrice.signum() < 0) {
            throw new IllegalArgumentException("零售价不能为负");
        }
        if (rate == null
                || rate.compareTo(UserLevelStatusConstants.MIN_EXCLUSIVE_RATE) <= 0
                || rate.compareTo(UserLevelStatusConstants.MAX_INCLUSIVE_RATE) > 0) {
            throw new IllegalArgumentException("定价倍率必须在 (0, 1] 之间");
        }
        return retailPrice.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}
