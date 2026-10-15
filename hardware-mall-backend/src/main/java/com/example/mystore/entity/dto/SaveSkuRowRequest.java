package com.example.mystore.entity.dto;

import com.example.mystore.entity.vo.SpecVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class SaveSkuRowRequest implements Serializable {

    private Long id;

    @NotEmpty(message = "规格组合不能为空")
    @Valid
    private List<SpecVO> specs;

    @NotNull(message = "SKU 价格不能为空")
    @DecimalMin(value = "0", message = "SKU 价格不能小于 0")
    private BigDecimal price;

    @NotNull(message = "SKU 库存不能为空")
    @Min(value = 0, message = "SKU 库存不能小于 0")
    private Integer stock;

    private String image;

    @Min(value = 0, message = "SKU 状态不正确")
    @Max(value = 1, message = "SKU 状态不正确")
    private Integer status;
}
