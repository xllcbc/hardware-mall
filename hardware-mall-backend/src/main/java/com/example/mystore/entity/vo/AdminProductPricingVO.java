package com.example.mystore.entity.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 管理端商品页的只读价格视角。
 */
@Data
public class AdminProductPricingVO implements Serializable {
    private Long id;
    private Long categoryId;
    private String name;
    private String subtitle;
    private String description;
    private List<String> images;
    private BigDecimal originalPrice;
    private BigDecimal weight;
    private BigDecimal retailMinPrice;
    private BigDecimal retailMaxPrice;
    private BigDecimal discountRate;
    private BigDecimal effectiveRate;
    private BigDecimal settlementMinPrice;
    private BigDecimal settlementMaxPrice;
    private boolean rateMissing;
    private Integer excludeDiscount;
    private Integer status;
    private Integer isRecommend;
    private Integer salesCount;
}
