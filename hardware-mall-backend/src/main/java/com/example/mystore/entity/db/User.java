package com.example.mystore.entity.db;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String openid;
    
    private String unionid;
    
    @Size(max = 50, message = "昵称最多 50 字")
    private String nickname;
    
    private String avatarUrl;
    
    private String phone;
    
    private String province;
    
    private String city;
    
    private Integer role;
    
    private Integer status;
    
    private LocalDateTime lastLoginTime;
    
    private String lastLoginIp;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
    
    private Long deleteTime;
}
