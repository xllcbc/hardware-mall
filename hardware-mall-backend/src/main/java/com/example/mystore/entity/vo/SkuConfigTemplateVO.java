package com.example.mystore.entity.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class SkuConfigTemplateVO implements Serializable {
    private Long templateId;
    private String name;
    private Integer specType;
    private Integer isRequired;
    private Integer sortOrder;
    private Boolean archived;
    private List<SkuConfigItemVO> items;
}
