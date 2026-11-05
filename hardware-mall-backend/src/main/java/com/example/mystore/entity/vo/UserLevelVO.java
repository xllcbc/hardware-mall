package com.example.mystore.entity.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserLevelVO implements Serializable {
    private Long id;
    private String name;
    private Integer isRetail;
    private Integer sortOrder;
    private Integer status;
    private Long userCount;
}
