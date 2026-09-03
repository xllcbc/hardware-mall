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
    /** M11: 四个统计卡片的"较昨日"涨幅(%), 前端读 stats.trend.ordersTrend/salesTrend/shipTrend/productTrend */
    private Trend trend;

    @Data
    public static class Trend implements Serializable {
        private Long ordersTrend;
        private Long salesTrend;
        private Long shipTrend;
        private Long productTrend;
    }

    /**
     * 涨幅百分比(整数): 昨日为 0 时, 今日>0 记 +100, 否则 0 (避免除零)
     */
    public static long percentTrend(long today, long yesterday) {
        if (yesterday == 0) {
            return today > 0 ? 100 : 0;
        }
        return Math.round((today - yesterday) * 100.0 / yesterday);
    }
}
