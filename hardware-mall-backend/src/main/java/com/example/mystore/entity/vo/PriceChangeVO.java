package com.example.mystore.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceChangeVO implements Serializable {
    private BigDecimal expectedPayAmount;
    private CheckoutPreviewVO preview;
}
