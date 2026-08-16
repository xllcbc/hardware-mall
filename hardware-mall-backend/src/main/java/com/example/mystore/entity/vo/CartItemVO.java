package com.example.mystore.entity.vo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class CartItemVO implements Serializable {
    private Long cartId;
    private Long productId;
    private Long skuId;
    private String productName;
    private String productImage;
    private String spec;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer stock;
}
