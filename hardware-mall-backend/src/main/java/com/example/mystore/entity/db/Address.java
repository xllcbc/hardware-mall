package com.example.mystore.entity.db;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("address")
public class Address {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    @Size(max = 50, message = "收货人姓名最多 50 字")
    private String consignee;
    
    @Size(max = 20, message = "手机号最多 20 位")
    private String phone;
    
    private String province;
    
    private String city;
    
    private String district;
    
    @Size(max = 255, message = "详细地址最多 255 字")
    private String detail;
    
    private String postalCode;
    
    private Integer isDefault;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
    
    private Long deleteTime;
}
