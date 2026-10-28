package com.example.mystore.entity.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductSkuVO implements Serializable {
    private Long id;
    private Long spuId;
    private List<SpecVO> specs;
    private BigDecimal price;
    private Integer stock;
    private String image;
    private Integer status;
}
