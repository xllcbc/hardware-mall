package com.example.mystore.service.impl;

import com.example.mystore.common.constant.DiscountConstants;
import com.example.mystore.entity.db.Cart;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.vo.CartItemVO;
import com.example.mystore.entity.vo.SpecVO;
import com.example.mystore.mapper.CartMapper;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.service.PriceService;
import com.example.mystore.service.PricingRuleService;
import com.example.mystore.service.SkuService;
import com.example.mystore.util.UserContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartMapper cartMapper;
    @Mock
    private SkuService skuService;
    @Mock
    private SpuMapper spuMapper;

    @Spy
    private PriceService priceService = new PriceServiceImpl();

    @Mock
    private PricingRuleService pricingRuleService;

    @InjectMocks
    private CartServiceImpl cartService;

    @Test
    void customLevel_cartUsesCategoryRate() {
        UserContext.setLevelId(2L);
        try {
            when(cartMapper.selectList(any())).thenReturn(List.of(cart()));
            when(skuService.getSkuMapByIds(List.of(1L))).thenReturn(Map.of(1L, sku()));
            when(spuMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(spu()));
            when(skuService.getSkusBySpu(1L, 1)).thenReturn(List.of(sku()));
            when(pricingRuleService.rateFor(2L, 1L, false)).thenReturn(new BigDecimal("0.8000"));

            List<CartItemVO> result = cartService.getCartList(1L);

            CartItemVO vo = result.get(0);
            assertThat(vo.getPrice()).isEqualByComparingTo("20.00");
            assertThat(vo.getSubtotal()).isEqualByComparingTo("40.00");
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void retailUser_cartShowsRetailPriceAndSubtotal() {
        UserContext.setLevelId(1L);
        try {
            when(cartMapper.selectList(any())).thenReturn(List.of(cart()));
            when(skuService.getSkuMapByIds(List.of(1L))).thenReturn(Map.of(1L, sku()));
            when(spuMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(spu()));
            when(skuService.getSkusBySpu(1L, 1)).thenReturn(List.of(sku()));
            when(pricingRuleService.rateFor(1L, 1L, false)).thenReturn(BigDecimal.ONE);

            List<CartItemVO> result = cartService.getCartList(1L);

            CartItemVO vo = result.get(0);
            assertThat(vo.getPrice()).isEqualByComparingTo("25.00");
            assertThat(vo.getSubtotal()).isEqualByComparingTo("50.00");
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void emptyCart_returnsEmptyList() {
        when(cartMapper.selectList(any())).thenReturn(List.of());

        List<CartItemVO> result = cartService.getCartList(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void offShelfSpu_lineIsSkipped() {
        UserContext.setLevelId(1L);
        try {
            when(cartMapper.selectList(any())).thenReturn(List.of(cart()));
            when(skuService.getSkuMapByIds(List.of(1L))).thenReturn(Map.of(1L, sku()));
            Spu offShelf = spu();
            offShelf.setStatus(0);
            when(spuMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(offShelf));

            List<CartItemVO> result = cartService.getCartList(1L);

            assertThat(result).isEmpty();
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void cartList_usesBatchSkuLookup() {
        Cart first = cart();
        Cart second = cart();
        second.setId(2L);
        second.setSkuId(2L);
        when(cartMapper.selectList(any())).thenReturn(List.of(first, second));

        Sku firstSku = sku();
        Sku secondSku = sku();
        secondSku.setId(2L);
        when(skuService.getSkuMapByIds(any()))
                .thenReturn(Map.of(1L, firstSku, 2L, secondSku));
        when(spuMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(spu()));
        when(skuService.getSkusBySpu(1L, 1)).thenReturn(List.of(firstSku));
        when(pricingRuleService.rateFor(any(), eq(1L), eq(false))).thenReturn(BigDecimal.ONE);

        List<CartItemVO> result = cartService.getCartList(1L);

        assertThat(result).hasSize(2);
        verify(skuService, times(1)).getSkuMapByIds(any());
        verify(skuService, never()).getSkuById(any());
    }

    private Cart cart() {
        Cart cart = new Cart();
        cart.setId(1L);
        cart.setUserId(1L);
        cart.setSkuId(1L);
        cart.setQuantity(2);
        cart.setDeleteTime(0L);
        return cart;
    }

    private Sku sku() {
        Sku sku = new Sku();
        sku.setId(1L);
        sku.setSpuId(1L);
        sku.setPrice(new BigDecimal("25.00"));
        sku.setStock(10);
        sku.setStatus(1);
        SpecVO spec = new SpecVO();
        spec.setValue("红");
        sku.setSpecs(List.of(spec));
        return sku;
    }

    private Spu spu() {
        Spu spu = new Spu();
        spu.setId(1L);
        spu.setCategoryId(1L);
        spu.setStatus(1);
        spu.setDeleteTime(0L);
        spu.setExcludeDiscount(DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        spu.setName("测试");
        spu.setImages(List.of("img"));
        return spu;
    }
}
