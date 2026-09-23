package com.example.mystore.entity.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ProductListResult implements Serializable {
    private List<ProductListCacheItem> records;
    private long total;

    public ProductListResult() {
    }

    public ProductListResult(List<ProductListCacheItem> records, long total) {
        this.records = records;
        this.total = total;
    }
}
