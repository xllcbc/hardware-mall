package com.example.mystore.entity.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 单一等级的分类倍率缓存，使用具体类型承载 Map 泛型信息。
 * levelExists 为 null 表示旧版缓存条目，按等级存在处理。
 */
@Data
public class PricingLevelCache implements Serializable {
    private Boolean levelExists;
    private Map<Long, BigDecimal> rates = new HashMap<>();
}
