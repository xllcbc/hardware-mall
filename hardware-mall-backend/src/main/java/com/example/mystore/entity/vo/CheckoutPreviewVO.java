package com.example.mystore.entity.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class CheckoutPreviewVO implements Serializable {
    private List<CheckoutItemVO> items;
    private BigDecimal goodsAmount;
    private BigDecimal freightAmount;
    private BigDecimal payAmount;
}
