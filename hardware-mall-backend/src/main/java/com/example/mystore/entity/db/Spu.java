package com.example.mystore.entity.db;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "spu", autoResultMap = true)
public class Spu {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long categoryId;

    @Size(max = 100, message = "商品名称最多 100 字")
    private String name;

    private String subtitle;

    private String description;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    private BigDecimal originalPrice;

    private BigDecimal weight;

    private Integer salesCount;

    private Integer status;

    private Integer isRecommend;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long deleteTime;
}
