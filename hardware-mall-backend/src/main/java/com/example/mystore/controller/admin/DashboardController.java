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
        return Result.success(stats);
    }

    @GetMapping("/recent-orders")
    public Result<List<RecentOrderVO>> getRecentOrders(
            @RequestParam(defaultValue = "5") Integer limit) {
        return Result.success(orderService.getRecentOrders(PageUtil.clampLimit(limit, 5, 50)));
    }
}
