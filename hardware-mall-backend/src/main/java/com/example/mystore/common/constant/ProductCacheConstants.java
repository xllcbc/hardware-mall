package com.example.mystore.common.constant;

public final class ProductCacheConstants {

    private ProductCacheConstants() {
    }

    public static final long TTL_SECONDS = 240L;
    public static final long JITTER_SECONDS = 60L;
    public static final long MAX_STALE_SECONDS = TTL_SECONDS + JITTER_SECONDS;
    public static final long DEFAULT_LOCK_WAIT_MILLIS = 500L;

    public static final class Alert {

        private Alert() {
        }

        public static final String INVALIDATION_FAILED = "PRODUCT_CACHE_INVALIDATION_FAILED";
    }
}
