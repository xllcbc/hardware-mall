package com.example.mystore.controller.user;

import com.example.mystore.common.result.Result;
import com.example.mystore.entity.db.Cart;
import com.example.mystore.entity.vo.CartItemVO;
import com.example.mystore.service.CartService;
import com.example.mystore.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final JwtUtil jwtUtil;

    @GetMapping("/list")
    public Result<List<CartItemVO>> getCartList(@RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        return Result.success(cartService.getCartList(userId));
    }

    @PostMapping("/add")
    public Result<Cart> addToCart(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> params) {
        Long userId = extractUserId(authHeader);
        Long skuId = Long.valueOf(params.get("skuId").toString());
        Integer quantity = Integer.valueOf(params.get("quantity").toString());
        return Result.success(cartService.addToCart(userId, skuId, quantity));
    }

    @PutMapping("/{id}/quantity")
    public Result<Cart> updateQuantity(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Map<String, Integer> params) {
        Long userId = extractUserId(authHeader);
        Integer quantity = params.get("quantity");
        return Result.success(cartService.updateQuantity(userId, id, quantity));
    }

    @DeleteMapping("/{id}")
    public Result<Void> removeFromCart(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Long userId = extractUserId(authHeader);
        cartService.removeFromCart(userId, id);
        return Result.success();
    }

    @DeleteMapping("/clear")
    public Result<Void> clearCart(@RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        cartService.clearCart(userId);
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
