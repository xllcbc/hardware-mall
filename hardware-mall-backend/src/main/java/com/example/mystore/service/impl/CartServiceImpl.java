package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.Cart;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.vo.SpecVO;
import com.example.mystore.entity.vo.CartItemVO;
import com.example.mystore.mapper.CartMapper;
import com.example.mystore.mapper.SkuMapper;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.service.CartService;
import com.example.mystore.service.SkuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartMapper cartMapper;
    private final SkuService skuService;
    private final SkuMapper skuMapper;
    private final SpuMapper spuMapper;

@Override
    public List<CartItemVO> getCartList(Long userId) {
        LambdaQueryWrapper<Cart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Cart::getUserId, userId)
               .eq(Cart::getDeleteTime, 0);
        List<Cart> carts = cartMapper.selectList(wrapper);

        List<CartItemVO> result = new ArrayList<>();
        for (Cart cart : carts) {
            Sku sku = skuService.getSkuById(cart.getSkuId());
            Spu spu = spuMapper.selectById(sku.getSpuId());
            if (spu != null && spu.getStatus() == 1 && spu.getDeleteTime() == 0) {
                CartItemVO vo = new CartItemVO();
                vo.setCartId(cart.getId());
                vo.setProductId(sku.getSpuId());
                vo.setSkuId(sku.getId());
                vo.setProductName(spu.getName());
                vo.setProductImage(sku.getImage() != null && !sku.getImage().isEmpty()
                    ? sku.getImage()
                    : getFirstImage(spu.getImages()));

                vo.setSpec(sku.getSpecs().stream()
                    .map(SpecVO::getValue)
                    .collect(Collectors.joining(" ")));

                List<Sku> allSkus = skuService.getSkusBySpu(spu.getId(), 1);
                vo.setPrice(sku.getPrice());
                vo.setMinPrice(allSkus.stream().map(Sku::getPrice).min(BigDecimal::compareTo).orElse(sku.getPrice()));
                vo.setMaxPrice(allSkus.stream().map(Sku::getPrice).max(BigDecimal::compareTo).orElse(sku.getPrice()));
                vo.setQuantity(cart.getQuantity());
                vo.setSubtotal(sku.getPrice().multiply(BigDecimal.valueOf(cart.getQuantity())));
                vo.setStock(sku.getStock());
                result.add(vo);
            }
        }
        return result;
    }

    @Override
    @Transactional
    public Cart addToCart(Long userId, Long skuId, Integer quantity) {
        Sku sku = skuService.getSkuById(skuId);
        if (sku == null || sku.getStatus() != 1) {
            throw new BusinessException("商品不存在或已下架");
        }

        if (sku.getStock() < quantity) {
            throw new BusinessException("库存不足");
        }

        cartMapper.insertOrUpdateQuantity(userId, skuId, quantity);

        LambdaQueryWrapper<Cart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Cart::getUserId, userId)
               .eq(Cart::getSkuId, skuId)
               .eq(Cart::getDeleteTime, 0);
        return cartMapper.selectOne(wrapper);
    }

    @Override
    @Transactional
    public Cart updateQuantity(Long userId, Long cartId, Integer quantity) {
        Cart cart = cartMapper.selectById(cartId);
        if (cart == null || !cart.getUserId().equals(userId)) {
            throw new BusinessException("购物车记录不存在");
        }

        if (quantity <= 0) {
            cart.setDeleteTime(System.currentTimeMillis());
            cartMapper.updateById(cart);
            return null;
        }

        Sku sku = skuService.getSkuById(cart.getSkuId());
        if (sku.getStock() < quantity) {
            throw new BusinessException("库存不足");
        }

        cart.setQuantity(quantity);
        cart.setUpdateTime(LocalDateTime.now());
        cartMapper.updateById(cart);
        return cart;
    }

    @Override
    public void removeFromCart(Long userId, Long cartId) {
        Cart cart = cartMapper.selectById(cartId);
        if (cart == null || !cart.getUserId().equals(userId)) {
            throw new BusinessException("购物车记录不存在");
        }
        cart.setDeleteTime(System.currentTimeMillis());
        cartMapper.updateById(cart);
    }

    @Override
    public void clearCart(Long userId) {
        LambdaQueryWrapper<Cart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Cart::getUserId, userId);
        List<Cart> carts = cartMapper.selectList(wrapper);
        for (Cart cart : carts) {
            cart.setDeleteTime(System.currentTimeMillis());
            cartMapper.updateById(cart);
        }
    }

    private String getFirstImage(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.get(0);
    }
}
