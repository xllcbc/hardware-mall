package com.example.mystore.service;

import com.example.mystore.entity.db.Cart;
import com.example.mystore.entity.vo.CartItemVO;

import java.util.List;

public interface CartService {
    List<CartItemVO> getCartList(Long userId);
    Cart addToCart(Long userId, Long skuId, Integer quantity);
    Cart updateQuantity(Long userId, Long cartId, Integer quantity);
    void removeFromCart(Long userId, Long cartId);
    void clearCart(Long userId);
}
