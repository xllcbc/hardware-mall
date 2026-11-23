package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mystore.common.constant.DiscountConstants;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.Cart;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.vo.SpecVO;
import com.example.mystore.entity.vo.CartItemVO;
import com.example.mystore.entity.dto.LevelPricing;
import com.example.mystore.entity.dto.ProductPricingFacts;
import com.example.mystore.mapper.CartMapper;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.service.CartService;
import com.example.mystore.service.LevelPricingService;
import com.example.mystore.service.SkuService;
import com.example.mystore.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartMapper cartMapper;
    private final SkuService skuService;
    private final SpuMapper spuMapper;
    private final LevelPricingService levelPricingService;

    @Override
    public List<CartItemVO> getCartList(Long userId) {
        LambdaQueryWrapper<Cart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Cart::getUserId, userId)
               .eq(Cart::getDeleteTime, 0);
        List<Cart> carts = cartMapper.selectList(wrapper);
        if (carts.isEmpty()) {
            return List.of();
        }
        List<Long> skuIds = carts.stream().map(Cart::getSkuId).distinct().toList();
        Map<Long, Sku> skuMap = skuService.getSkuMapByIds(skuIds);
        List<Long> spuIds = skuMap.values().stream()
                .map(Sku::getSpuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Spu> spuMap = spuIds.isEmpty() ? Map.of()
                : spuMapper.selectBatchIds(spuIds).stream()
                        .collect(Collectors.toMap(Spu::getId, spu -> spu));
        Map<Long, List<Sku>> skusBySpu = new LinkedHashMap<>();

        // 一次取齐本页所有相关 SKU 的等级结算价与可售性：每个在售 SPU 的全部 SKU
        // 用于区间，购物车里可能缺失的 SKU 再补进来，避免逐行查询倍率。
        Map<Long, Sku> priceSkus = new LinkedHashMap<>();
        for (Spu spu : spuMap.values()) {
            if (!isOnShelf(spu)) {
                continue;
            }
            getAllSkus(spu.getId(), skusBySpu)
                    .forEach(sku -> priceSkus.putIfAbsent(sku.getId(), sku));
        }
        for (Cart cart : carts) {
            Sku sku = skuMap.get(cart.getSkuId());
            if (sku == null) {
                continue;
            }
            if (isOnShelf(spuMap.get(sku.getSpuId()))) {
                priceSkus.putIfAbsent(sku.getId(), sku);
            }
        }
        Map<Long, LevelPricing> pricingBySku = loadPricings(spuMap, priceSkus);

        List<CartItemVO> result = new ArrayList<>();
        for (Cart cart : carts) {
            Sku sku = skuMap.get(cart.getSkuId());
            if (sku == null) {
                continue;
            }
            Spu spu = spuMap.get(sku.getSpuId());
            if (!isOnShelf(spu)) {
                continue;
            }

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
            vo.setQuantity(cart.getQuantity());
            vo.setStock(sku.getStock());

            LevelPricing pricing = pricingBySku.get(sku.getId());
            boolean sellable = pricing != null && pricing.sellable();
            vo.setSellable(sellable);
            if (sellable) {
                vo.setPrice(pricing.minPrice());
                vo.setSubtotal(pricing.minPrice()
                        .multiply(BigDecimal.valueOf(cart.getQuantity())));
                applyPriceRange(vo, getAllSkus(spu.getId(), skusBySpu), pricingBySku);
            }
            result.add(vo);
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
        Spu spu = requireOnShelfSpu(sku);
        requireSellable(spu, sku.getPrice());

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
        Spu spu = requireOnShelfSpu(sku);
        requireSellable(spu, sku.getPrice());
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

    private Spu requireOnShelfSpu(Sku sku) {
        if (sku == null) {
            throw new BusinessException("商品不存在或已下架");
        }
        Spu spu = spuMapper.selectById(sku.getSpuId());
        if (!isOnShelf(spu)) {
            throw new BusinessException("商品不存在或已下架");
        }
        return spu;
    }

    private boolean isOnShelf(Spu spu) {
        return spu != null
                && Integer.valueOf(StatusConstants.PRODUCT_STATUS_ON_SHELF).equals(spu.getStatus())
                && (spu.getDeleteTime() == null || spu.getDeleteTime() == 0L);
    }

    private void requireSellable(Spu spu, BigDecimal basePrice) {
        LevelPricing pricing = levelPricingService.price(
                UserContext.getLevelId(),
                ProductPricingFacts.of(
                        spu.getCategoryId(),
                        basePrice,
                        DiscountConstants.isExcluded(spu.getExcludeDiscount())));
        if (!pricing.sellable()) {
            throw new BusinessException("商品暂不可售: " + spu.getName());
        }
    }

    private Map<Long, LevelPricing> loadPricings(
            Map<Long, Spu> spuMap, Map<Long, Sku> priceSkus) {
        List<ProductPricingFacts> facts = new ArrayList<>();
        List<Long> factSkuIds = new ArrayList<>();
        for (Sku sku : priceSkus.values()) {
            if (sku.getPrice() == null) {
                continue;
            }
            Spu spu = spuMap.get(sku.getSpuId());
            if (spu == null) {
                continue;
            }
            facts.add(ProductPricingFacts.of(
                    spu.getCategoryId(),
                    sku.getPrice(),
                    DiscountConstants.isExcluded(spu.getExcludeDiscount())));
            factSkuIds.add(sku.getId());
        }

        List<LevelPricing> pricings = facts.isEmpty()
                ? List.of()
                : levelPricingService.priceAll(UserContext.getLevelId(), facts);
        Map<Long, LevelPricing> pricingBySku = new HashMap<>();
        for (int i = 0; i < factSkuIds.size(); i++) {
            pricingBySku.put(factSkuIds.get(i), pricings.get(i));
        }
        return pricingBySku;
    }

    private void applyPriceRange(
            CartItemVO vo, List<Sku> allSkus, Map<Long, LevelPricing> pricingBySku) {
        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;
        for (Sku sku : allSkus) {
            LevelPricing pricing = pricingBySku.get(sku.getId());
            if (pricing == null || !pricing.sellable()) {
                continue;
            }
            if (pricing.minPrice() != null
                    && (minPrice == null || pricing.minPrice().compareTo(minPrice) < 0)) {
                minPrice = pricing.minPrice();
            }
            if (pricing.maxPrice() != null
                    && (maxPrice == null || pricing.maxPrice().compareTo(maxPrice) > 0)) {
                maxPrice = pricing.maxPrice();
            }
        }
        vo.setMinPrice(minPrice != null ? minPrice : vo.getPrice());
        vo.setMaxPrice(maxPrice != null ? maxPrice : vo.getPrice());
    }

    private List<Sku> getAllSkus(Long spuId, Map<Long, List<Sku>> cache) {
        return cache.computeIfAbsent(spuId, id -> {
            List<Sku> skus = skuService.getSkusBySpu(id, 1);
            return skus == null ? List.of() : skus;
        });
    }

    private String getFirstImage(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.get(0);
    }
}
