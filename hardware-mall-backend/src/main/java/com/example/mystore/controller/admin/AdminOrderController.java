package com.example.mystore.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.result.Result;
import com.example.mystore.entity.dto.ShipOrderRequest;
import com.example.mystore.entity.vo.OrderVO;
import com.example.mystore.service.OrderService;
import com.example.mystore.annotation.RequireAdmin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/order")
@RequiredArgsConstructor
@RequireAdmin
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping("/list")
    public Result<Page<OrderVO>> getOrderList(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(orderService.getAdminOrderPage(userId, status, orderNo, startDate, endDate, page, limit));
    }

    @GetMapping("/{id}")
    public Result<OrderVO> getOrderById(@PathVariable Long id) {
        return Result.success(orderService.getAdminOrderById(id));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getOrderStats() {
        return Result.success(orderService.getOrderStats());
    }

    @PutMapping("/{id}/ship")
    public Result<Void> shipOrder(
            @PathVariable Long id,
            @RequestBody @Valid ShipOrderRequest request) {
        orderService.shipOrder(id, request.getLogisticsId(), request.getLogisticsNo());
        return Result.success();
    }

    @PutMapping("/{id}/refund")
    public Result<Void> refundOrder(
            @PathVariable Long id,
            @RequestBody Map<String, String> params) {
        orderService.refundOrder(id, params.get("reason"));
        return Result.success();
    }

    @PutMapping("/{id}/reject-refund")
    public Result<Void> rejectRefund(
            @PathVariable Long id,
            @RequestBody Map<String, String> params) {
        orderService.rejectRefund(id, params.get("reason"));
        return Result.success();
    }
}
