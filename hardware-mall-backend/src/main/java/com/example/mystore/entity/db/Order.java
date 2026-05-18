package com.example.mystore.entity.db;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("shop_order")
public class Order {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String orderNo;
    
    private Long userId;
    
    private Long addressId;
    
    private Long logisticsId;
    
    private Integer status;
    
    private BigDecimal totalAmount;

    private BigDecimal freightAmount;

    private BigDecimal payAmount;
    
    private String logisticsNo;
    
    private LocalDateTime payTime;
    
    private LocalDateTime shipTime;
    
    private LocalDateTime receiveTime;
    
    private LocalDateTime cancelTime;
    
    private String cancelReason;
    
    private String buyerRemark;
    
    private String adminRemark;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
    
    private Long adminDeleteTime;

    private Long userDeleteTime;
}
