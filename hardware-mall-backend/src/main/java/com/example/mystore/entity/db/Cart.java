package com.example.mystore.entity.db;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("cart")
public class Cart {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long skuId;

    private Integer quantity;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long deleteTime;
}
