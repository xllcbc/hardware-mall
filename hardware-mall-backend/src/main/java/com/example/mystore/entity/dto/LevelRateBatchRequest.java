package com.example.mystore.entity.dto;

import lombok.Data;

import java.util.List;

@Data
public class LevelRateBatchRequest {
    private List<LevelRateItemRequest> items;
}
