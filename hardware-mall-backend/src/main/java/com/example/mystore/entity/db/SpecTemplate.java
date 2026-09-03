package com.example.mystore.entity.db;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("spec_template")
public class SpecTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long categoryId;

    @Size(max = 50, message = "规格模板名称最多 50 字")
    private String name;

    private Integer specType;

    private Integer isRequired;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long deleteTime;
}
