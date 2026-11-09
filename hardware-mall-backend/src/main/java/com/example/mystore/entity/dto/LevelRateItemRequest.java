package com.example.mystore.entity.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LevelRateItemRequest {
    private Long categoryId;
    private BigDecimal rate;
}
