package com.example.mystore.entity.vo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class DashboardStatsVO implements Serializable {
    private Long todayOrders;
    private BigDecimal todaySales;
    private Long pendingShip;
    private Long totalProducts;
}
