package com.example.mystore.service.impl;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.vo.SpecVO;
import com.example.mystore.mapper.SkuMapper;
import com.example.mystore.mapper.SpuMapper;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkuServiceImplTest {

    @Mock
    private SkuMapper skuMapper;

    @Mock
    private SpuMapper spuMapper;

    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private SkuServiceImpl skuService;

    private Sku sku1;
    private Sku sku2;

    @BeforeEach
    void setUp() {
        SpecVO spec1 = new SpecVO();
        spec1.setTemplateId(1L);
        spec1.setItemId(1L);
        spec1.setName("颜色");
        spec1.setValue("银色");

        SpecVO spec2 = new SpecVO();
        spec2.setTemplateId(2L);
        spec2.setItemId(4L);
        spec2.setName("尺寸");
        spec2.setValue("50mm");

        sku1 = new Sku();
        sku1.setId(1L);
        sku1.setSpuId(1L);
        sku1.setSpecs(Arrays.asList(spec1, spec2));
        sku1.setPrice(new BigDecimal("199.00"));
        sku1.setStock(50);
        sku1.setStatus(1);
        sku1.setDeleteTime(0L);

        sku2 = new Sku();
        sku2.setId(2L);
        sku2.setSpuId(1L);
        sku2.setSpecs(Arrays.asList(spec1));
        sku2.setPrice(new BigDecimal("499.00"));
        sku2.setStock(30);
        sku2.setStatus(1);
        sku2.setDeleteTime(0L);
    }

    @Test
    void testGetSkuById_CacheHit() {
        when(redisUtil.queryWithCache(eq(RedisConstants.PREFIX_SKU_INFO + 1L), eq(Sku.class), anyLong(), any()))
                .thenReturn(sku1);
        when(redisUtil.get(RedisConstants.PREFIX_SKU_STOCK + 1L)).thenReturn(80);

        Sku result = skuService.getSkuById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("199.00"));
        assertThat(result.getStock()).isEqualTo(80);
        verify(skuMapper, never()).selectById(any());
    }

    @Test
    void testGetSkuById_MetadataMiss_StockFromCache() {
        when(redisUtil.queryWithCache(eq(RedisConstants.PREFIX_SKU_INFO + 1L), eq(Sku.class), anyLong(), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<Sku> supplier = inv.getArgument(3);
                    return supplier.get();
                });
        when(redisUtil.get(RedisConstants.PREFIX_SKU_STOCK + 1L)).thenReturn(60);
        when(skuMapper.selectById(1L)).thenReturn(sku1);

        Sku result = skuService.getSkuById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("199.00"));
        assertThat(result.getStock()).isEqualTo(60);
    }

    @Test
    void testGetSkuById_Deleted() {
        Sku deleted = new Sku();
        deleted.setId(3L);
        deleted.setDeleteTime(System.currentTimeMillis());
        when(redisUtil.queryWithCache(eq(RedisConstants.PREFIX_SKU_INFO + 3L), eq(Sku.class), anyLong(), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<Sku> supplier = inv.getArgument(3);
                    return supplier.get();
                });
        when(skuMapper.selectById(3L)).thenReturn(deleted);

        assertThatThrownBy(() -> skuService.getSkuById(3L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SKU不存在");
    }

    @Test
    void testGetSkuBySpecs_ExactMatch() {
        when(skuMapper.selectList(any())).thenReturn(Arrays.asList(sku1, sku2));

        SpecVO searchSpec1 = new SpecVO();
        searchSpec1.setTemplateId(1L);
        searchSpec1.setItemId(1L);
        SpecVO searchSpec2 = new SpecVO();
        searchSpec2.setTemplateId(2L);
        searchSpec2.setItemId(4L);

        Sku result = skuService.getSkuBySpecs(1L, Arrays.asList(searchSpec1, searchSpec2));

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void testGetSkuBySpecs_PartialMatch_Fails() {
        when(skuMapper.selectList(any())).thenReturn(Arrays.asList(sku1, sku2));

        SpecVO searchSpec = new SpecVO();
        searchSpec.setTemplateId(99L);
        searchSpec.setItemId(99L);

        Sku result = skuService.getSkuBySpecs(1L, Collections.singletonList(searchSpec));

        assertThat(result).isNull();
    }

    @Test
    void testDeductStock_Success() {
        when(skuMapper.deductStock(1L, 10)).thenReturn(1);

        boolean result = skuService.deductStock(1L, 10);

        assertThat(result).isTrue();
        verify(skuMapper).deductStock(1L, 10);
    }

    @Test
    void testDeductStock_Fail() {
        when(skuMapper.deductStock(1L, 100)).thenReturn(0);

        boolean result = skuService.deductStock(1L, 100);

        assertThat(result).isFalse();
    }

    @Test
    void testRestoreStock() {
        skuService.restoreStock(1L, 20);
        verify(skuMapper).restoreStock(1L, 20);
    }

    @Test
    void testGetStockById_CacheHit() {
        when(redisUtil.get(RedisConstants.PREFIX_SKU_STOCK + 1L)).thenReturn(50);

        Integer result = skuService.getStockById(1L);

        assertThat(result).isEqualTo(50);
        verify(skuMapper, never()).selectById(any());
    }

    @Test
    void testGetStockById_CacheMiss_FallbackToDb() {
        when(redisUtil.get(RedisConstants.PREFIX_SKU_STOCK + 1L)).thenReturn(null);
        when(skuMapper.selectById(1L)).thenReturn(sku1);

        Integer result = skuService.getStockById(1L);

        assertThat(result).isEqualTo(50);
        verify(skuMapper).selectById(1L);
    }

    @Test
    void testGetStockById_NullSentinel_FallbackToDb() {
        when(redisUtil.get(RedisConstants.PREFIX_SKU_STOCK + 1L)).thenReturn(RedisConstants.CACHE_NULL);
        when(redisUtil.isNull(RedisConstants.CACHE_NULL)).thenReturn(true);
        when(skuMapper.selectById(1L)).thenReturn(sku1);

        Integer result = skuService.getStockById(1L);

        assertThat(result).isEqualTo(50);
        verify(skuMapper).selectById(1L);
    }

    @Test
    void testGetStockById_SkuNotFound() {
        when(redisUtil.get(RedisConstants.PREFIX_SKU_STOCK + 99L)).thenReturn(null);
        when(skuMapper.selectById(99L)).thenReturn(null);

        Integer result = skuService.getStockById(99L);

        assertThat(result).isNull();
    }

    @Test
    void testSyncStockToCache_Success() {
        when(skuMapper.selectById(1L)).thenReturn(sku1);

        boolean result = skuService.syncStockToCache(1L);

        assertThat(result).isTrue();
        verify(redisUtil).set(RedisConstants.PREFIX_SKU_STOCK + 1L, 50);
    }

    @Test
    void testSyncStockToCache_NullStock() {
        Sku noStock = new Sku();
        noStock.setId(3L);
        noStock.setStock(null);
        when(skuMapper.selectById(3L)).thenReturn(noStock);

        boolean result = skuService.syncStockToCache(3L);

        assertThat(result).isFalse();
    }

    @Test
    void testCountBySpuIds_GroupsBySpu_ExcludesDeleted() {
        Sku sku3 = new Sku();
        sku3.setId(3L);
        sku3.setSpuId(2L);
        sku3.setDeleteTime(0L);

        Sku deleted = new Sku();
        deleted.setId(4L);
        deleted.setSpuId(2L);
        deleted.setDeleteTime(System.currentTimeMillis());

        when(skuMapper.selectList(any())).thenReturn(Arrays.asList(sku1, sku2, sku3, deleted));

        Map<Long, Long> result = skuService.countBySpuIds(Arrays.asList(1L, 2L, 99L));

        assertThat(result).containsEntry(1L, 2L);
        assertThat(result).containsEntry(2L, 1L);
        assertThat(result).doesNotContainKey(99L);
        assertThat(result).doesNotContainKey(3L);
    }

    @Test
    void testCountBySpuIds_EmptyInput_ReturnsEmptyMap() {
        Map<Long, Long> result = skuService.countBySpuIds(Collections.emptyList());

        assertThat(result).isEmpty();
    }

    @Test
    void testDeleteSku() {
        when(skuMapper.selectById(1L)).thenReturn(sku1);

        skuService.deleteSku(1L);

        verify(skuMapper).updateById(org.mockito.Mockito.<Sku>argThat(sku -> sku.getDeleteTime() > 0));
        verify(redisUtil).delete(RedisConstants.PREFIX_PRODUCT_DETAIL + 1L);
        verify(redisUtil).delete(RedisConstants.PREFIX_SKU_INFO + 1L);
    }
}
