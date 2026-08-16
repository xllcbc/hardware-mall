package com.example.mystore.common.constant;

public class RedisConstants {

    private RedisConstants() {
    }

    public static final String PREFIX_TOKEN_BLACKLIST = "token:blacklist:";
    public static final String PREFIX_USER_TOKENS = "user:tokens:";
    public static final String PREFIX_WECHAT_SESSION = "wechat:session:";
    public static final String PREFIX_WECHAT_ACCESS_TOKEN = "wechat:access_token";
    public static final long WECHAT_ACCESS_TOKEN_TTL = 7000L;
    public static final String PREFIX_CATEGORY_LIST = "category:list";
    public static final String PREFIX_LOGISTICS_ENABLED = "logistics:enabled";
    public static final String PREFIX_PRODUCT_DETAIL = "product:detail:";
    public static final String PREFIX_PRODUCT_RECOMMEND = "product:recommend";
    public static final String PREFIX_SKU_STOCK = "sku:stock:";
    public static final String PREFIX_SKU_INFO = "sku:info:";
    public static final String PREFIX_RATE_LIMIT = "rate:limit:";
    public static final String PREFIX_ORDER_IDEMPOTENCY = "order:idem:";

    public static final long CACHE_TTL_HOUR = 3600L;
    public static final long CACHE_TTL_DAY = 86400L;
    public static final long WECHAT_SESSION_TTL = 7200L;
    public static final long RATE_LIMIT_TTL = 60L;
    public static final long IDEMPOTENCY_TTL = 300L;

    public static final String CACHE_NULL = "NULL";
    public static final long CACHE_NULL_TTL = 300L;
    public static final long CACHE_JITTER_MAX = 300L;
}