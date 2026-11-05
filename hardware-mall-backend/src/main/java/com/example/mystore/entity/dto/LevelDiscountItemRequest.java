package com.example.mystore.entity.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LevelDiscountItemRequest {
    private Long categoryId;
    private BigDecimal rate;
}
