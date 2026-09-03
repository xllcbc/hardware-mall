package com.example.mystore.controller.admin;

import com.example.mystore.common.result.Result;
import com.example.mystore.entity.vo.DashboardStatsVO;
import com.example.mystore.entity.vo.RecentOrderVO;
import com.example.mystore.service.OrderService;
import com.example.mystore.service.SpuService;
import com.example.mystore.annotation.RequireAdmin;
import com.example.mystore.util.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@RequireAdmin
public class DashboardController {

    private final OrderService orderService;
    private final SpuService spuService;

    @GetMapping("/stats")
    public Result<DashboardStatsVO> getStats() {
        DashboardStatsVO stats = orderService.getDashboardStats();
        stats.setTotalProducts(spuService.getTotalCount());

        // M11: 上新涨幅(今日 vs 昨日新建商品数)
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayCreated = spuService.countCreatedBetween(todayStart, todayStart.plusDays(1));
        long yesterdayCreated = spuService.countCreatedBetween(todayStart.minusDays(1), todayStart);
        stats.getTrend().setProductTrend(DashboardStatsVO.percentTrend(todayCreated, yesterdayCreated));

        return Result.success(stats);
    }

    @GetMapping("/recent-orders")
    public Result<List<RecentOrderVO>> getRecentOrders(
            @RequestParam(defaultValue = "5") Integer limit) {
        return Result.success(orderService.getRecentOrders(PageUtil.clampLimit(limit, 5, 50)));
    }
}
