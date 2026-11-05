package com.example.mystore.entity.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class CheckoutPreviewRequest implements Serializable {

    @NotEmpty(message = "结算商品不能为空")
    @Valid
    private List<CreateOrderRequest.CartItem> items;
}
