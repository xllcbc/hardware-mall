package com.example.mystore.entity.db;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "logistics", autoResultMap = true)
public class Logistics {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String name;
    
    private String code;
    
    private String description;
    
    private String contact;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> phones;

    private String city;
    
    private String address;

    private Integer sortOrder;
    
    private Integer status;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
    
    private Long deleteTime;
}
