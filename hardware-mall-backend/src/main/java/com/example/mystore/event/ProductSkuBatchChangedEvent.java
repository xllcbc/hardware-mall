package com.example.mystore.event;

import java.util.List;

public record ProductSkuBatchChangedEvent(Long spuId, List<Long> skuIds) {

    public ProductSkuBatchChangedEvent {
        skuIds = skuIds == null ? List.of() : List.copyOf(skuIds);
    }
}
