package com.example.mystore.entity.dto;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class CreateOrderRequest implements Serializable {
    private List<CartItem> items;
    private Long addressId;
    private Long logisticsId;
    private String buyerRemark;

    @Data
    public static class CartItem implements Serializable {
        private Long skuId;
        private Integer quantity;
    }
}
