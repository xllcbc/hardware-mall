package com.example.mystore.entity.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class LevelDiscountVO implements Serializable {
    private Long categoryId;
    private String categoryName;
    private BigDecimal rate;
    private boolean missing;
}
