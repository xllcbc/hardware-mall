package com.example.mystore.entity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.io.Serializable;

@Data
public class ShipOrderRequest implements Serializable {

    /** 发货方式: 2-同城配送, 4-用户自提 */
    @NotNull(message = "发货方式不能为空")
    private Integer deliveryType;

    /** 物流方式ID(同城配送必填, 用户自提可为空) */
    private Long logisticsId;
}
