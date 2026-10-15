package com.example.mystore.entity.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class SaveSkuConfigRequest implements Serializable {

    @NotNull(message = "SKU 配置不能为空")
    @Valid
    private List<SaveSkuRowRequest> skus;
}
