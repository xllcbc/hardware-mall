package com.example.mystore.entity.vo;

import com.example.mystore.entity.db.Sku;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class SkuConfigVO implements Serializable {
    private Long spuId;
    private Long categoryId;
    private String spuName;
    private List<SkuConfigTemplateVO> templates;
    private List<Sku> skus;
}
