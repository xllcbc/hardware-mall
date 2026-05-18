package com.example.mystore.service.impl;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.vo.ProductDetailVO;
import com.example.mystore.entity.vo.ProductListVO;
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

        verify(spuMapper).insert(argThat(spu ->
                spu.getSalesCount() == 0 &&
                spu.getStatus() == StatusConstants.PRODUCT_STATUS_ON_SHELF &&
                spu.getIsRecommend() == 0 &&
                spu.getDeleteTime() == 0L
        ));
    }

    @Test
    void testGetProductListVO_PriceRange() {
        when(spuMapper.selectPage(any(), any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Spu> page = inv.getArgument(0);
            page.setRecords(Collections.singletonList(spu1));
            page.setTotal(1);
            return page;
        });
        when(skuService.getSkusBySpu(1L, 1)).thenReturn(Arrays.asList(sku1, sku2));

        var result = spuService.getProductListVO(null, null, 1, 10, 1);

        assertThat(result.getRecords()).hasSize(1);
        ProductListVO vo = result.getRecords().get(0);
        assertThat(vo.getMinPrice()).isEqualByComparingTo(new BigDecimal("199.00"));
        assertThat(vo.getMaxPrice()).isEqualByComparingTo(new BigDecimal("299.00"));
    }

    @Test
    void testGetProductDetailVO_CacheHit() {
        ProductDetailVO cached = new ProductDetailVO();
        when(redisUtil.get(RedisConstants.PREFIX_PRODUCT_DETAIL + 1L)).thenReturn(cached);

        ProductDetailVO result = spuService.getProductDetailVO(1L);

        assertThat(result).isSameAs(cached);
        verify(spuMapper, never()).selectById(any());
    }

    @Test
    void testGetProductDetailVO_CacheHit_Null() {
        when(redisUtil.get(RedisConstants.PREFIX_PRODUCT_DETAIL + 1L)).thenReturn(RedisConstants.CACHE_NULL);
        when(redisUtil.isNull(RedisConstants.CACHE_NULL)).thenReturn(true);

        ProductDetailVO result = spuService.getProductDetailVO(1L);

        assertThat(result).isNull();
    }

    @Test
    void testUpdateStatus() {
        when(spuMapper.selectById(1L)).thenReturn(spu1);

        spuService.updateStatus(1L, 0);

        verify(spuMapper).updateById(argThat(spu -> spu.getStatus() == 0));
        verify(redisUtil).delete(RedisConstants.PREFIX_PRODUCT_DETAIL + 1L);
    }

    @Test
    void testDeleteSpu() {
        when(spuMapper.selectById(1L)).thenReturn(spu1);

        spuService.deleteSpu(1L);

        verify(spuMapper).updateById(argThat(spu -> spu.getDeleteTime() > 0));
        verify(redisUtil).delete(RedisConstants.PREFIX_PRODUCT_DETAIL + 1L);
    }

    @Test
    void testGetTotalCount() {
        when(spuMapper.selectCount(any())).thenReturn(10L);

        Long count = spuService.getTotalCount();

        assertThat(count).isEqualTo(10L);
    }
}
