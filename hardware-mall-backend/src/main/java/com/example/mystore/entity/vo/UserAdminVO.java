package com.example.mystore.entity.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserAdminVO implements Serializable {
    private Long id;
    private String nickname;
    private String avatarUrl;
    private String phone;
    private String province;
    private String city;
    private Integer role;
    private Long levelId;
    private String levelName;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private LocalDateTime createTime;
}
