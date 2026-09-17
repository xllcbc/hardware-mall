package com.example.mystore.service.impl;

import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Cart;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.vo.CartItemVO;
import com.example.mystore.entity.vo.SpecVO;
import com.example.mystore.mapper.CartMapper;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.service.PriceService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private CartServiceImpl cartService;

    @Test
    void wholesaleUser_cartShowsWholesalePriceAndSubtotal() {
        UserContext.setUserGroup(StatusConstants.USER_GROUP_WHOLESALE);
        try {
            when(cartMapper.selectList(any())).thenReturn(List.of(cart()));
            when(skuService.getSkuById(1L)).thenReturn(sku());
            when(spuMapper.selectById(1L)).thenReturn(spu());
            when(skuService.getSkusBySpu(1L, 1)).thenReturn(List.of(sku()));

            List<CartItemVO> result = cartService.getCartList(1L);

            CartItemVO vo = result.get(0);
            assertThat(vo.getPrice()).isEqualByComparingTo("18.00");
            assertThat(vo.getSubtotal()).isEqualByComparingTo("36.00");
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void retailUser_cartShowsRetailPriceAndSubtotal() {
        UserContext.setUserGroup(StatusConstants.USER_GROUP_RETAIL);
        try {
            when(cartMapper.selectList(any())).thenReturn(List.of(cart()));
            when(skuService.getSkuById(1L)).thenReturn(sku());
            when(spuMapper.selectById(1L)).thenReturn(spu());
            when(skuService.getSkusBySpu(1L, 1)).thenReturn(List.of(sku()));

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
        UserContext.setUserGroup(StatusConstants.USER_GROUP_RETAIL);
        try {
            when(cartMapper.selectList(any())).thenReturn(List.of());

            List<CartItemVO> result = cartService.getCartList(1L);

            assertThat(result).isEmpty();
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void offShelfSpu_lineIsSkipped() {
        UserContext.setUserGroup(StatusConstants.USER_GROUP_RETAIL);
        try {
            when(cartMapper.selectList(any())).thenReturn(List.of(cart()));
            when(skuService.getSkuById(1L)).thenReturn(sku());
            Spu offShelf = spu();
            offShelf.setStatus(0);
            when(spuMapper.selectById(1L)).thenReturn(offShelf);

            List<CartItemVO> result = cartService.getCartList(1L);

            assertThat(result).isEmpty();
        } finally {
            UserContext.clear();
        }
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
        sku.setWholesalePrice(new BigDecimal("18.00"));
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
        spu.setStatus(1);
        spu.setDeleteTime(0L);
        spu.setName("测试");
        spu.setImages(List.of("img"));
        return spu;
    }
}
