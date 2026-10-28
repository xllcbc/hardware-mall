package com.example.mystore.service;

import java.math.BigDecimal;

/**
 * 纯定价函数: 只负责零售价与倍率的计算，不访问数据库或缓存。
 */
public interface PriceService {

    /**
     * 成交单价 = 零售价 × 倍率，四舍五入保留两位。
     */
    BigDecimal resolveUnitPrice(BigDecimal retailPrice, BigDecimal rate);
}
