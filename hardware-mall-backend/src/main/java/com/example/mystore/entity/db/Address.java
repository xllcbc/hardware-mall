package com.example.mystore.entity.db;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("address")
public class Address {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    private String consignee;
    
    private String phone;
    
    private String province;
    
    private String city;
    
    private String district;
    
    private String detail;
    
    private String postalCode;
    
    private Integer isDefault;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
    
    private Long deleteTime;
}
