package com.example.mystore.entity.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class SkuConfigItemVO implements Serializable {
    private Long itemId;
    private String value;
    private Integer sortOrder;
    private Boolean archived;
}
