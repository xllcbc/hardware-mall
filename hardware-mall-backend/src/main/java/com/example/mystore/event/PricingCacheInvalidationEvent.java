package com.example.mystore.event;

public record PricingCacheInvalidationEvent(
        Type type,
        Long levelId) {

    public enum Type {
        MATRIX_CHANGED,
        LEVEL_AVAILABILITY_CHANGED
    }
}
