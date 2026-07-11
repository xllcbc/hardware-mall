package com.example.mystore.controller.user;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.result.Result;
import com.example.mystore.entity.dto.CreateOrderRequest;
import com.example.mystore.entity.vo.OrderVO;
import com.example.mystore.service.OrderService;
import com.example.mystore.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final JwtUtil jwtUtil;

    @PostMapping("/create")
    public Result<OrderVO> createOrder(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CreateOrderRequest request) {
        Long userId = extractUserId(authHeader);
        return Result.success(orderService.createOrder(userId, request));
    }

    @GetMapping("/list")
    public Result<Page<OrderVO>> getOrderList(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit) {
        Long userId = extractUserId(authHeader);
        return Result.success(orderService.getOrderPage(userId, status, page, limit));
    }

    @GetMapping("/{id}")
    public Result<OrderVO> getOrderById(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Long userId = extractUserId(authHeader);
        return Result.success(orderService.getOrderById(userId, id));
    }

    @PutMapping("/{id}/cancel")
    public Result<Void> cancelOrder(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Map<String, String> params) {
        Long userId = extractUserId(authHeader);
        orderService.cancelOrder(userId, id, params.get("reason"));
        return Result.success();
    }

    @PutMapping("/{id}/receive")
    public Result<Void> confirmReceive(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Long userId = extractUserId(authHeader);
        orderService.confirmReceive(userId, id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteOrder(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Long userId = extractUserId(authHeader);
        orderService.deleteOrder(userId, id);
        return Result.success();
    }

    private Long extractUserId(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtUtil.getUserIdFromToken(token);
        }
        throw new RuntimeException("无效的认证信息");
    }
}
