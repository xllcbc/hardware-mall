package com.example.mystore.event;

public record ProductCacheInvalidationEvent(
        Type type,
        Long spuId,
        Long skuId,
        boolean recommendChanged) {

    public enum Type {
        SKU_CHANGED,
        SPU_CHANGED
    }
}
