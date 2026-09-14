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
    private Integer deliveryType;
    private String deliveryTypeText;
    /** 微信确认收货组件参数 */
    private String merchantId;
    private String merchantTradeNo;
    private String transactionId;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String buyerRemark;
    private String cancelReason;
    /** 管理员拒绝退款的原因, 仅在拒绝退款时写入 */
    private String adminRemark;
    private LocalDateTime payTime;
    private LocalDateTime shipTime;
    private LocalDateTime receiveTime;
    private LocalDateTime createTime;
    private List<OrderItem> items;
}
