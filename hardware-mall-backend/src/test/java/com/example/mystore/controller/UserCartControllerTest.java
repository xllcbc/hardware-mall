package com.example.mystore.controller;

import com.example.mystore.entity.vo.CartItemVO;
import com.example.mystore.controller.user.CartController;
import com.example.mystore.service.CartService;
import com.example.mystore.util.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 购物车 HTTP 契约：响应逐行暴露可售标记，缺口行不再让整页报错。
 */
@ExtendWith(MockitoExtension.class)
class UserCartControllerTest {

    @Mock
    private CartService cartService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CartController(cartService)).build();
        UserContext.setUserId(1L);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void cartListExposesPerRowSellableFlagAndGapRowWithoutPrice() throws Exception {
        when(cartService.getCartList(1L)).thenReturn(List.of(normalItem(), gapItem()));

        mvc.perform(get("/api/user/cart/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sellable").value(true))
                .andExpect(jsonPath("$.data[0].price").value(20.00))
                .andExpect(jsonPath("$.data[0].subtotal").value(40.00))
                .andExpect(jsonPath("$.data[1].sellable").value(false))
                .andExpect(jsonPath("$.data[1].price").value(nullValue()))
                .andExpect(jsonPath("$.data[1].subtotal").value(nullValue()));
    }

    private CartItemVO normalItem() {
        CartItemVO vo = new CartItemVO();
        vo.setCartId(1L);
        vo.setSkuId(9301L);
        vo.setProductName("正常商品");
        vo.setPrice(new BigDecimal("20.00"));
        vo.setQuantity(2);
        vo.setSubtotal(new BigDecimal("40.00"));
        vo.setSellable(true);
        return vo;
    }

    private CartItemVO gapItem() {
        CartItemVO vo = new CartItemVO();
        vo.setCartId(2L);
        vo.setSkuId(9302L);
        vo.setProductName("缺口商品");
        vo.setPrice(null);
        vo.setQuantity(1);
        vo.setSubtotal(null);
        vo.setSellable(false);
        return vo;
    }
}
