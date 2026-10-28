package com.example.mystore.entity.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 单一等级的全量分类倍率缓存，使用具体类型承载 Map 泛型信息。
 */
@Data
public class PricingLevelCache implements Serializable {
    private Map<Long, BigDecimal> rates = new HashMap<>();
}
