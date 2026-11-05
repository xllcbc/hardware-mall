package com.example.mystore.common.exception;

import java.util.List;

public class ProductCacheInvalidationException extends RuntimeException {

    private final List<Failure> failures;

    public ProductCacheInvalidationException(List<Failure> failures) {
        super(buildMessage(failures));
        this.failures = List.copyOf(failures);
    }

    public List<Failure> failures() {
        return failures;
    }

    private static String buildMessage(List<Failure> failures) {
        return "商品缓存失效失败: " + String.join(", ", failures.stream()
                .map(failure -> failure.key() + "(" + failure.cause() + ")")
                .toList());
    }

    public record Failure(String key, String cause) {
    }
}
