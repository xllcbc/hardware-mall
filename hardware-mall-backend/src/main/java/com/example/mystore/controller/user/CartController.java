package com.example.mystore.controller.user;

import com.example.mystore.common.result.Result;
import com.example.mystore.entity.db.Cart;
import com.example.mystore.entity.vo.CartItemVO;
import com.example.mystore.service.CartService;
import com.example.mystore.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping("/list")
    public Result<List<CartItemVO>> getCartList() {
        Long userId = UserContext.getUserId();
        return Result.success(cartService.getCartList(userId));
    }

    @PostMapping("/add")
    public Result<Cart> addToCart(@RequestBody Map<String, Object> params) {
        Long userId = UserContext.getUserId();
        Long skuId = Long.valueOf(params.get("skuId").toString());
        Integer quantity = Integer.valueOf(params.get("quantity").toString());
        return Result.success(cartService.addToCart(userId, skuId, quantity));
    }

    @PutMapping("/{id}/quantity")
    public Result<Cart> updateQuantity(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> params) {
        Long userId = UserContext.getUserId();
        Integer quantity = params.get("quantity");
        return Result.success(cartService.updateQuantity(userId, id, quantity));
    }

    @DeleteMapping("/{id}")
    public Result<Void> removeFromCart(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        cartService.removeFromCart(userId, id);
        return Result.success();
    }

    @DeleteMapping("/clear")
    public Result<Void> clearCart() {
        Long userId = UserContext.getUserId();
        cartService.clearCart(userId);
        return Result.success();
    }
}
