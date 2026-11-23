package com.example.mystore.service.impl;

import com.example.mystore.common.constant.DiscountConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.Cart;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.dto.LevelRateTable;
import com.example.mystore.entity.vo.CartItemVO;
import com.example.mystore.entity.vo.SpecVO;
import com.example.mystore.mapper.CartMapper;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.service.LevelPricingService;
import com.example.mystore.service.PriceService;
import com.example.mystore.service.PricingRuleService;
import com.example.mystore.service.SkuService;
import com.example.mystore.util.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
    @Mock
    private PricingRuleService pricingRuleService;

    private final PriceService priceService = new PriceServiceImpl();

    private CartServiceImpl cartService;

    @BeforeEach
    void setUp() {
        LevelPricingService levelPricingService =
                new LevelPricingServiceImpl(pricingRuleService, priceService);
        cartService = new CartServiceImpl(cartMapper, skuService, spuMapper, levelPricingService);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void customLevel_cartUsesCategoryRate() {
        UserContext.setLevelId(2L);
        stubRateTable(2L, Map.of(1L, new BigDecimal("0.8000")));
        stubSingleCart(sku(), spu());

        List<CartItemVO> result = cartService.getCartList(1L);

        CartItemVO vo = result.get(0);
        assertThat(vo.isSellable()).isTrue();
        assertThat(vo.getPrice()).isEqualByComparingTo("20.00");
        assertThat(vo.getSubtotal()).isEqualByComparingTo("40.00");
    }

    @Test
    void retailUser_cartShowsRetailPriceAndSubtotal() {
        UserContext.setLevelId(1L);
        stubRateTable(1L, Map.of(1L, BigDecimal.ONE));
        stubSingleCart(sku(), spu());

        List<CartItemVO> result = cartService.getCartList(1L);

        CartItemVO vo = result.get(0);
        assertThat(vo.isSellable()).isTrue();
        assertThat(vo.getPrice()).isEqualByComparingTo("25.00");
        assertThat(vo.getSubtotal()).isEqualByComparingTo("50.00");
    }

    @Test
    void emptyCart_returnsEmptyList() {
        when(cartMapper.selectList(any())).thenReturn(List.of());

        List<CartItemVO> result = cartService.getCartList(1L);

        assertThat(result).isEmpty();
        verify(skuService, never()).getSkuMapByIds(any());
    }

    @Test
    void offShelfSpu_lineIsSkipped() {
        UserContext.setLevelId(1L);
        when(cartMapper.selectList(any())).thenReturn(List.of(cart()));
        when(skuService.getSkuMapByIds(List.of(1L))).thenReturn(Map.of(1L, sku()));
        Spu offShelf = spu();
        offShelf.setStatus(0);
        when(spuMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(offShelf));

        List<CartItemVO> result = cartService.getCartList(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void cartList_usesBatchSkuLookup() {
        UserContext.setLevelId(1L);
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
        stubRateTable(1L, Map.of(1L, BigDecimal.ONE));

        List<CartItemVO> result = cartService.getCartList(1L);

        assertThat(result).hasSize(2);
        verify(skuService, times(1)).getSkuMapByIds(any());
        verify(skuService, never()).getSkuById(any());
        verify(pricingRuleService, times(1)).rateTable(1L);
    }

    @Test
    void gapLineIsReturnedUnsellableWithoutFailingThePage() {
        UserContext.setLevelId(2L);
        stubRateTable(2L, Map.of());
        stubSingleCart(sku(), spu());

        List<CartItemVO> result = cartService.getCartList(1L);

        assertThat(result).hasSize(1);
        CartItemVO vo = result.get(0);
        assertThat(vo.isSellable()).isFalse();
        assertThat(vo.getPrice()).isNull();
        assertThat(vo.getSubtotal()).isNull();
        assertThat(vo.getMinPrice()).isNull();
        assertThat(vo.getMaxPrice()).isNull();
        assertThat(vo.getProductName()).isEqualTo("测试");
    }

    @Test
    void gapAndSellableLinesCoexistInOneCart() {
        UserContext.setLevelId(2L);
        Cart normalCart = cart();
        Cart gapCart = cart();
        gapCart.setId(2L);
        gapCart.setSkuId(2L);

        Sku normalSku = sku();
        Sku gapSku = sku();
        gapSku.setId(2L);
        gapSku.setSpuId(2L);

        Spu normalSpu = spu();
        Spu gapSpu = spu();
        gapSpu.setId(2L);
        gapSpu.setCategoryId(2L);
        gapSpu.setName("缺口商品");

        when(cartMapper.selectList(any())).thenReturn(List.of(normalCart, gapCart));
        when(skuService.getSkuMapByIds(any()))
                .thenReturn(Map.of(1L, normalSku, 2L, gapSku));
        when(spuMapper.selectBatchIds(any())).thenReturn(List.of(normalSpu, gapSpu));
        when(skuService.getSkusBySpu(1L, 1)).thenReturn(List.of(normalSku));
        when(skuService.getSkusBySpu(2L, 1)).thenReturn(List.of(gapSku));
        stubRateTable(2L, Map.of(1L, new BigDecimal("0.8000")));

        List<CartItemVO> result = cartService.getCartList(1L);

        assertThat(result).hasSize(2);
        CartItemVO normal = result.stream()
                .filter(vo -> vo.getSkuId().equals(1L))
                .findFirst()
                .orElseThrow();
        CartItemVO gap = result.stream()
                .filter(vo -> vo.getSkuId().equals(2L))
                .findFirst()
                .orElseThrow();

        assertThat(normal.isSellable()).isTrue();
        assertThat(normal.getPrice()).isEqualByComparingTo("20.00");
        assertThat(normal.getSubtotal()).isEqualByComparingTo("40.00");

        assertThat(gap.isSellable()).isFalse();
        assertThat(gap.getPrice()).isNull();
        assertThat(gap.getSubtotal()).isNull();
        assertThat(gap.getProductName()).isEqualTo("缺口商品");
    }

    @Test
    void addToCart_gapIsRejected() {
        UserContext.setLevelId(2L);
        stubRateTable(2L, Map.of());
        when(skuService.getSkuById(1L)).thenReturn(sku());
        when(spuMapper.selectById(1L)).thenReturn(spu());

        assertThatThrownBy(() -> cartService.addToCart(1L, 1L, 2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品暂不可售");
        verify(cartMapper, never()).insertOrUpdateQuantity(anyLong(), anyLong(), anyInt());
    }

    @Test
    void addToCart_excludeDiscountResolvesRetailPricing() {
        UserContext.setLevelId(2L);
        Spu excluded = spu();
        excluded.setExcludeDiscount(DiscountConstants.DISCOUNT_PARTICIPATION_DISABLED);
        when(skuService.getSkuById(1L)).thenReturn(sku());
        when(spuMapper.selectById(1L)).thenReturn(excluded);
        when(pricingRuleService.resolveRetailLevelId()).thenReturn(1L);
        stubRateTable(1L, Map.of(1L, BigDecimal.ONE));
        when(cartMapper.selectOne(any())).thenReturn(cart());

        Cart result = cartService.addToCart(1L, 1L, 2);

        assertThat(result).isNotNull();
        verify(cartMapper).insertOrUpdateQuantity(1L, 1L, 2);
    }

    @Test
    void updateQuantity_gapIsRejected() {
        UserContext.setLevelId(2L);
        stubRateTable(2L, Map.of());
        when(cartMapper.selectById(1L)).thenReturn(cart());
        when(skuService.getSkuById(1L)).thenReturn(sku());
        when(spuMapper.selectById(1L)).thenReturn(spu());

        assertThatThrownBy(() -> cartService.updateQuantity(1L, 1L, 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品暂不可售");
        verify(cartMapper, never()).updateById(any(Cart.class));
    }

    @Test
    void retailUser_excludeDiscountCartUsesRetailRate() {
        UserContext.setLevelId(2L);
        Spu excluded = spu();
        excluded.setExcludeDiscount(DiscountConstants.DISCOUNT_PARTICIPATION_DISABLED);
        when(cartMapper.selectList(any())).thenReturn(List.of(cart()));
        when(skuService.getSkuMapByIds(List.of(1L))).thenReturn(Map.of(1L, sku()));
        when(spuMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(excluded));
        when(skuService.getSkusBySpu(1L, 1)).thenReturn(List.of(sku()));
        when(pricingRuleService.resolveRetailLevelId()).thenReturn(1L);
        stubRateTable(1L, Map.of(1L, BigDecimal.ONE));

        List<CartItemVO> result = cartService.getCartList(1L);

        assertThat(result.get(0).isSellable()).isTrue();
        assertThat(result.get(0).getPrice()).isEqualByComparingTo("25.00");
        assertThat(result.get(0).getSubtotal()).isEqualByComparingTo("50.00");
    }

    private void stubSingleCart(Sku sku, Spu spu) {
        when(cartMapper.selectList(any())).thenReturn(List.of(cart()));
        when(skuService.getSkuMapByIds(List.of(1L))).thenReturn(Map.of(1L, sku));
        when(spuMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(spu));
        when(skuService.getSkusBySpu(1L, 1)).thenReturn(List.of(sku));
    }

    private void stubRateTable(long levelId, Map<Long, BigDecimal> rates) {
        when(pricingRuleService.rateTable(levelId))
                .thenReturn(new LevelRateTable(levelId, rates));
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
