package com.example.mystore.controller.user;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.result.Result;
import com.example.mystore.entity.dto.CreateOrderRequest;
import com.example.mystore.entity.vo.OrderVO;
import com.example.mystore.service.OrderService;
import com.example.mystore.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/create")
    public Result<OrderVO> createOrder(@RequestBody CreateOrderRequest request) {
        Long userId = UserContext.getUserId();
        return Result.success(orderService.createOrder(userId, request));
    }

    @GetMapping("/list")
    public Result<Page<OrderVO>> getOrderList(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit) {
        Long userId = UserContext.getUserId();
        return Result.success(orderService.getOrderPage(userId, status, page, limit));
    }

    @GetMapping("/{id}")
    public Result<OrderVO> getOrderById(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        return Result.success(orderService.getOrderById(userId, id));
    }

    @PutMapping("/{id}/cancel")
    public Result<Void> cancelOrder(
            @PathVariable Long id,
            @RequestBody Map<String, String> params) {
        Long userId = UserContext.getUserId();
        orderService.cancelOrder(userId, id, params.get("reason"));
        return Result.success();
    }

    @PutMapping("/{id}/receive")
    public Result<Void> confirmReceive(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        orderService.confirmReceive(userId, id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteOrder(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        orderService.deleteOrder(userId, id);
        return Result.success();
    }
}
