package com.example.mystore.entity.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ProductListResult implements Serializable {
    private List<ProductListVO> records;
    private long total;

    public ProductListResult() {
    }

    public ProductListResult(List<ProductListVO> records, long total) {
        this.records = records;
        this.total = total;
    }
}
