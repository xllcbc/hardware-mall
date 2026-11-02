package com.example.mystore.event;

import java.util.List;

public record StockCacheEvent(List<Long> skuIds) {
}
