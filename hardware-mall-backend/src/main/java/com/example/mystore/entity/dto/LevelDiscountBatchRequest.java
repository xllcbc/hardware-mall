package com.example.mystore.entity.dto;

import lombok.Data;

import java.util.List;

@Data
public class LevelDiscountBatchRequest {
    private List<LevelDiscountItemRequest> items;
}
