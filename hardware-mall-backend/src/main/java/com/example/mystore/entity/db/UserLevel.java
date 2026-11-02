package com.example.mystore.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_level")
public class UserLevel {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Size(max = 50, message = "等级名称最多 50 字")
    private String name;

    private Integer isRetail;

    private Integer sortOrder;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
