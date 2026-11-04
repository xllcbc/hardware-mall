package com.example.mystore.entity.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class CheckoutItemVO implements Serializable {
    private Long skuId;
    private Long productId;
    private String productName;
    private String productImage;
    private String spec;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;
}
