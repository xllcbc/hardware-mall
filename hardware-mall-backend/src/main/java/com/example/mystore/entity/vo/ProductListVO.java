package com.example.mystore.entity.vo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductListVO implements Serializable {
    private Long id;
    private Long categoryId;
    private String name;
    private String subtitle;
    private List<String> images;
    private BigDecimal originalPrice;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer salesCount;
    private Integer status;
}