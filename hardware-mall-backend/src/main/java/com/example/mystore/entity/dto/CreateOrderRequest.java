package com.example.mystore.entity.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
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

    @NotNull(message = "请先确认应付金额")
    @DecimalMin(value = "0.00", message = "应付金额不能为负")
    @Digits(integer = 10, fraction = 2, message = "应付金额最多保留两位小数")
    private BigDecimal expectedPayAmount;

    @Size(max = 500, message = "订单备注最多 500 字")
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
