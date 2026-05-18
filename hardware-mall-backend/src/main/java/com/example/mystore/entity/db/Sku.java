package com.example.mystore.entity.db;

import com.baomidou.mybatisplus.annotation.*;
import com.example.mystore.entity.vo.SpecVO;
import com.example.mystore.handler.SpecVOTypeHandler;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "sku", autoResultMap = true)
public class Sku {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long spuId;

    @TableField(typeHandler = SpecVOTypeHandler.class)
    private List<SpecVO> specs;

    private BigDecimal price;

    private Integer stock;

    private String image;

    private Integer status;

    private String specHash;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long deleteTime;
}
