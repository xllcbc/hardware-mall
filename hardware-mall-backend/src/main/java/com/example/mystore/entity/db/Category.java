package com.example.mystore.entity.db;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("category")
public class Category {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long parentId;
    
    @Size(max = 50, message = "分类名称最多 50 字")
    private String name;
    
    private String icon;
    
    private Integer sortOrder;
    
    private Integer status;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
    
    private Long deleteTime;
}
