package com.example.mystore.entity.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class CreateOrderRequest implements Serializable {

    @NotEmpty(message = "购物车不能为空")
    @Valid
    private List<CartItem> items;

    @NotNull(message = "收货地址不能为空")
    private Long addressId;

    @NotNull(message = "物流方式不能为空")
    private Long logisticsId;

    private String buyerRemark;

    @Data
    public static class CartItem implements Serializable {
        @NotNull(message = "skuId 不能为空")
        private Long skuId;

        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "数量必须大于 0")
        private Integer quantity;
    }
}
