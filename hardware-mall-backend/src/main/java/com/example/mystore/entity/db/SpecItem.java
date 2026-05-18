package com.example.mystore.entity.db;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("spec_item")
public class SpecItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long templateId;

    private String value;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long deleteTime;
}
