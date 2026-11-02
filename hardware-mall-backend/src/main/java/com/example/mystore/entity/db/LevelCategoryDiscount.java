package com.example.mystore.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("level_category_discount")
public class LevelCategoryDiscount {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long levelId;

    private Long categoryId;

    private BigDecimal rate;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
