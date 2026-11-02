package com.example.mystore.entity.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderItemVO implements Serializable {
    private Long id;
    private Long orderId;
    private Long skuId;
    private Long spuId;
    private String productName;
    private String productSpec;
    private String productImage;
    private BigDecimal price;
    private Long levelId;
    private String levelName;
    private BigDecimal discountRate;
    private Integer quantity;
    private BigDecimal subtotal;
    private LocalDateTime createTime;
}
