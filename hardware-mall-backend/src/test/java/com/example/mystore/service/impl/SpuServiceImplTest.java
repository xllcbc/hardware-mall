package com.example.mystore.service.impl;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.vo.ProductDetailVO;
import com.example.mystore.entity.vo.ProductListVO;
import com.example.mystore.entity.vo.ProductListResult;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.service.SkuService;
import com.example.mystore.service.SpecItemService;
import com.example.mystore.service.SpecTemplateService;
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpuServiceImplTest {

    @Mock
    private SpuMapper spuMapper;

    @Mock
    private SkuService skuService;

    @Mock
    private SpecTemplateService specTemplateService;

    @Mock
    private SpecItemService specItemService;

    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private SpuServiceImpl spuService;

    private Spu spu1;
    private Sku sku1;
    private Sku sku2;

    @BeforeEach
    void setUp() {
        spu1 = new Spu();
        spu1.setId(1L);
        spu1.setCategoryId(1L);
        spu1.setName("防盗门锁 C级");
        spu1.setSubtitle("家用防盗门锁心");
        spu1.setOriginalPrice(new BigDecimal("299.00"));
        spu1.setSalesCount(52);
        spu1.setStatus(1);
        spu1.setIsRecommend(1);
        spu1.setDeleteTime(0L);

        sku1 = new Sku();
        sku1.setId(1L);
        sku1.setSpuId(1L);
        sku1.setPrice(new BigDecimal("199.00"));
        sku1.setStatus(1);

        sku2 = new Sku();
        sku2.setId(2L);
        sku2.setSpuId(1L);
        sku2.setPrice(new BigDecimal("299.00"));
        sku2.setStatus(1);
    }

    @Test
    void testGetSpuById_Success() {
        when(spuMapper.selectById(1L)).thenReturn(spu1);

        Spu result = spuService.getSpuById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("防盗门锁 C级");
    }

    @Test
    void testGetSpuById_Deleted() {
        Spu deleted = new Spu();
        deleted.setId(3L);
        deleted.setDeleteTime(System.currentTimeMillis());
        when(spuMapper.selectById(3L)).thenReturn(deleted);

        assertThatThrownBy(() -> spuService.getSpuById(3L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("商品不存在或已下架");
    }

    @Test
    void testCreateSpu_DefaultValues() {
        Spu newSpu = new Spu();
        newSpu.setName("测试商品");
        newSpu.setCategoryId(1L);

        spuService.createSpu(newSpu);

        verify(spuMapper).insert(org.mockito.Mockito.<Spu>argThat(spu ->
                spu.getSalesCount() == 0 &&
                spu.getStatus() == StatusConstants.PRODUCT_STATUS_ON_SHELF &&
                spu.getIsRecommend() == 0 &&
                spu.getDeleteTime() == 0L
        ));
    }

    @Test
    void testGetProductListVO_PriceRange() {
        when(redisUtil.queryWithCache(anyString(), eq(ProductListResult.class), anyLong(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            java.util.function.Supplier<ProductListResult> s = inv.getArgument(3);
            return s.get();
        });
        when(spuMapper.selectPage(any(), any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Spu> page = inv.getArgument(0);
            page.setRecords(Collections.singletonList(spu1));
            page.setTotal(1);
            return page;
        });
        when(skuService.getSkusBySpuIds(Collections.singletonList(1L)))
                .thenReturn(java.util.Collections.singletonMap(1L, Arrays.asList(sku1, sku2)));

        var result = spuService.getProductListVO(null, null, 1, 10, 1);

        assertThat(result.getRecords()).hasSize(1);
        ProductListVO vo = result.getRecords().get(0);
        assertThat(vo.getMinPrice()).isEqualByComparingTo(new BigDecimal("199.00"));
        assertThat(vo.getMaxPrice()).isEqualByComparingTo(new BigDecimal("299.00"));
    }

    @Test
    void getProductListVO_shouldQuerySkusInOneBatchCall() {
        when(redisUtil.queryWithCache(anyString(), eq(ProductListResult.class), anyLong(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            java.util.function.Supplier<ProductListResult> s = inv.getArgument(3);
            return s.get();
        });
        Spu s1 = new Spu();
        s1.setId(1L);
        s1.setCategoryId(1L);
        s1.setStatus(1);
        s1.setDeleteTime(0L);
        Spu s2 = new Spu();
        s2.setId(2L);
        s2.setCategoryId(1L);
        s2.setStatus(1);
        s2.setDeleteTime(0L);
        when(spuMapper.selectPage(any(), any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Spu> page = inv.getArgument(0);
            page.setRecords(Arrays.asList(s1, s2));
            page.setTotal(2);
            return page;
        });
        when(skuService.getSkusBySpuIds(Arrays.asList(1L, 2L))).thenReturn(java.util.Collections.emptyMap());

        spuService.getProductListVO(1L, null, 1, 10, 1);

        verify(skuService, times(1)).getSkusBySpuIds(anyList());
        verify(skuService, never()).getSkusBySpu(anyLong());
    }

    @Test
    void testGetProductDetailVO_FromCache() {
        ProductDetailVO cached = new ProductDetailVO();
        when(redisUtil.queryWithCache(eq(RedisConstants.PREFIX_PRODUCT_DETAIL + 1L),
                eq(ProductDetailVO.class), anyLong(), any())).thenReturn(cached);

        ProductDetailVO result = spuService.getProductDetailVO(1L);

        assertThat(result).isSameAs(cached);
        verify(spuMapper, never()).selectById(any());
    }

    @Test
    void testGetProductDetailVO_NotFound() {
        when(redisUtil.queryWithCache(eq(RedisConstants.PREFIX_PRODUCT_DETAIL + 1L),
                eq(ProductDetailVO.class), anyLong(), any())).thenReturn(null);

        assertThatThrownBy(() -> spuService.getProductDetailVO(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("商品不存在或已下架");
    }

    @Test
    void testGetProductDetailVO_AssembleFromDb() {
        sku1.setSpecs(Collections.emptyList());
        sku2.setSpecs(Collections.emptyList());
        when(redisUtil.queryWithCache(eq(RedisConstants.PREFIX_PRODUCT_DETAIL + 1L),
                eq(ProductDetailVO.class), anyLong(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            java.util.function.Supplier<ProductDetailVO> supplier = inv.getArgument(3);
            return supplier.get();
        });
        when(spuMapper.selectById(1L)).thenReturn(spu1);
        when(skuService.getSkusBySpu(1L, 1)).thenReturn(Arrays.asList(sku1, sku2));
        when(specTemplateService.getTemplatesByCategory(1L)).thenReturn(Collections.emptyList());
        when(specItemService.getItemsGroupedByTemplateIds(any())).thenReturn(Collections.emptyMap());

        ProductDetailVO result = spuService.getProductDetailVO(1L);

        assertThat(result).isNotNull();
        assertThat(result.getSpu()).isSameAs(spu1);
        assertThat(result.getSkus()).hasSize(2);
        assertThat(result.getMinPrice()).isEqualByComparingTo(new BigDecimal("199.00"));
        assertThat(result.getMaxPrice()).isEqualByComparingTo(new BigDecimal("299.00"));
    }

    @Test
    void testUpdateStatus() {
        when(spuMapper.selectById(1L)).thenReturn(spu1);

        spuService.updateStatus(1L, 0);

        verify(spuMapper).updateById(org.mockito.Mockito.<Spu>argThat(spu -> spu.getStatus() == 0));
        verify(redisUtil).delete(RedisConstants.PREFIX_PRODUCT_DETAIL + 1L);
    }

    @Test
    void testDeleteSpu() {
        when(spuMapper.selectById(1L)).thenReturn(spu1);

        spuService.deleteSpu(1L);

        verify(spuMapper).updateById(org.mockito.Mockito.<Spu>argThat(spu -> spu.getDeleteTime() > 0));
        verify(redisUtil).delete(RedisConstants.PREFIX_PRODUCT_DETAIL + 1L);
    }

    @Test
    void testGetTotalCount() {
        when(spuMapper.selectCount(any())).thenReturn(10L);

        Long count = spuService.getTotalCount();

        assertThat(count).isEqualTo(10L);
    }
}
