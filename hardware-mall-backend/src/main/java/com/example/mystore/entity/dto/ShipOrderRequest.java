package com.example.mystore.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;

@Data
public class ShipOrderRequest implements Serializable {

    @NotNull(message = "物流公司不能为空")
    private Long logisticsId;

    @NotBlank(message = "物流单号不能为空")
    @Size(max = 50, message = "物流单号最多 50 字")
    private String logisticsNo;
}
