package com.example.mystore.entity.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class UserLevelProductPreviewVO implements Serializable {
    private Long id;
    private Long categoryId;
    private String name;
    private List<String> images;
    private BigDecimal originalPrice;
    private BigDecimal retailMinPrice;
    private BigDecimal retailMaxPrice;
    private BigDecimal discountRate;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer excludeDiscount;
}
