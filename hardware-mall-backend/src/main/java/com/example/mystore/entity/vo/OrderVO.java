package com.example.mystore.entity.vo;

import com.example.mystore.entity.db.OrderItem;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderVO implements Serializable {
    private Long id;
    private String orderNo;
    private Integer status;
    private String statusText;
    private BigDecimal totalAmount;
    private BigDecimal freightAmount;
    private BigDecimal payAmount;
    private String logisticsName;
    private String logisticsNo;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String buyerRemark;
    private String cancelReason;
    private LocalDateTime payTime;
    private LocalDateTime shipTime;
    private LocalDateTime receiveTime;
    private LocalDateTime createTime;
    private List<OrderItem> items;
}
