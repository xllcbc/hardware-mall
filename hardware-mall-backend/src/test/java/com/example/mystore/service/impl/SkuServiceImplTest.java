package com.example.mystore.service.impl;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.SpecItem;
import com.example.mystore.entity.db.SpecTemplate;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.dto.SaveSkuConfigRequest;
import com.example.mystore.entity.dto.SaveSkuRowRequest;
import com.example.mystore.entity.vo.SkuConfigVO;
import com.example.mystore.entity.vo.SpecVO;
import com.example.mystore.event.ProductCacheInvalidationEvent;
import com.example.mystore.event.ProductSkuBatchChangedEvent;
import com.example.mystore.event.StockCacheEvent;
import com.example.mystore.mapper.SkuMapper;
import com.example.mystore.mapper.SpecItemMapper;
import com.example.mystore.mapper.SpecTemplateMapper;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.service.ProductCacheService;
import com.example.mystore.service.OssService;
import com.example.mystore.service.SpecItemService;
import com.example.mystore.service.SpecTemplateService;
import com.example.mystore.util.RedisLockUtil;
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
    private SpecTemplateMapper specTemplateMapper;

    @Mock
    private SpecItemMapper specItemMapper;

    @Mock
    private SpecTemplateService specTemplateService;

    @Mock
    private SpecItemService specItemService;

    @Mock
    private OssService ossService;

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private RedisLockUtil redisLockUtil;

    @Mock
    private ProductCacheService productCacheService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SkuServiceImpl skuService;

    private Sku sku1;
    private Sku sku2;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(skuService, "stockLockWaitMillis", 100L);
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
        when(productCacheService.getSkusByIds(eq(List.of(1L)), any()))
                .thenReturn(List.of(sku1));
        stubStockQuery(1L, 80);

        Sku result = skuService.getSkuById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("199.00"));
        assertThat(result.getStock()).isEqualTo(80);
        verify(skuMapper, never()).selectById(any());
    }

    @Test
    void getSkuMapByIds_loadsStockInBatchAndFiltersDeletedSku() {
        Sku cached = new Sku();
        cached.setId(1L);
        cached.setSpuId(1L);
        cached.setPrice(new BigDecimal("199.00"));
        cached.setStatus(1);
        cached.setDeleteTime(0L);

        Sku deleted = new Sku();
        deleted.setId(2L);
        deleted.setDeleteTime(System.currentTimeMillis());

        when(productCacheService.getSkusByIds(eq(List.of(1L, 2L)), any()))
                .thenReturn(Arrays.asList(cached, deleted));
        when(redisUtil.multiGet(List.of(
                RedisConstants.PREFIX_SKU_STOCK + 1L)))
                .thenReturn(Collections.singletonList(null), Collections.singletonList(null));
        when(redisLockUtil.tryLock(
                eq(RedisConstants.PREFIX_SKU_STOCK + 1L), anyLong())).thenReturn(true);

        Sku loadedFromDb = new Sku();
        loadedFromDb.setId(1L);
        loadedFromDb.setStock(55);
        when(skuMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(loadedFromDb));

        Map<Long, Sku> result = skuService.getSkuMapByIds(List.of(1L, 2L));

        assertThat(result).containsOnlyKeys(1L);
        assertThat(result.get(1L).getStock()).isEqualTo(55);
        verify(redisUtil).set(
                RedisConstants.PREFIX_SKU_STOCK + 1L,
                55,
                RedisConstants.SKU_STOCK_TTL_SECONDS,
                TimeUnit.SECONDS);
        verify(redisLockUtil).unlock(RedisConstants.PREFIX_SKU_STOCK + 1L);
    }

    @Test
    void getSkuMapByIds_stockNullSentinel_returnsNullStockWithoutDbQuery() {
        when(productCacheService.getSkusByIds(eq(List.of(1L, 2L)), any()))
                .thenReturn(Arrays.asList(sku1, sku2));
        when(redisUtil.multiGet(List.of(
                RedisConstants.PREFIX_SKU_STOCK + 1L,
                RedisConstants.PREFIX_SKU_STOCK + 2L)))
                .thenReturn(Arrays.asList(RedisConstants.CACHE_NULL, 30));
        when(redisUtil.isNull(RedisConstants.CACHE_NULL)).thenReturn(true);

        Map<Long, Sku> result = skuService.getSkuMapByIds(List.of(1L, 2L));

        assertThat(result.get(1L).getStock()).isNull();
        assertThat(result.get(2L).getStock()).isEqualTo(30);
        verify(skuMapper, never()).selectBatchIds(any());
    }

    @Test
    void getSkuMapByIds_allStockHits_returnsCachedStocksWithoutDbQuery() {
        when(productCacheService.getSkusByIds(eq(List.of(1L, 2L)), any()))
                .thenReturn(Arrays.asList(sku1, sku2));
        when(redisUtil.multiGet(List.of(
                RedisConstants.PREFIX_SKU_STOCK + 1L,
                RedisConstants.PREFIX_SKU_STOCK + 2L)))
                .thenReturn(Arrays.asList(50, 30));

        Map<Long, Sku> result = skuService.getSkuMapByIds(List.of(1L, 2L));

        assertThat(result.get(1L).getStock()).isEqualTo(50);
        assertThat(result.get(2L).getStock()).isEqualTo(30);
        verify(skuMapper, never()).selectBatchIds(any());
        verify(redisUtil, never()).set(any(), any(), anyLong(), any());
    }

    @Test
    void createSku_publishesSkuChangedEvent() {
        Spu spu = new Spu();
        spu.setId(1L);
        when(spuMapper.selectById(1L)).thenReturn(spu);
        when(skuMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(skuMapper.insert(any(Sku.class))).thenAnswer(inv -> {
            inv.getArgument(0, Sku.class).setId(5L);
            return 1;
        });

        Sku incoming = new Sku();
        incoming.setSpuId(1L);
        incoming.setSpecs(Collections.emptyList());
        incoming.setPrice(new BigDecimal("10.00"));
        incoming.setStock(10);

        skuService.createSku(incoming);

        verify(eventPublisher).publishEvent(new ProductCacheInvalidationEvent(
                ProductCacheInvalidationEvent.Type.SKU_CHANGED,
                1L,
                5L,
                false));
    }

    @Test
    void testGetSkuById_MetadataMiss_StockFromCache() {
        when(productCacheService.getSkusByIds(eq(List.of(1L)), any()))
                .thenReturn(List.of(sku1));
        stubStockQuery(1L, 60);

        Sku result = skuService.getSkuById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("199.00"));
        assertThat(result.getStock()).isEqualTo(60);
    }

    @Test
    void testGetSkuById_Deleted() {
        when(productCacheService.getSkusByIds(eq(List.of(3L)), any()))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> skuService.getSkuById(3L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SKU不存在");
    }

    @Test
    void testCreateSku_DuplicateSpecCombo_SeedHashFormat_Throws() {
        sku2.setSpecHash("auto_c1_i1");
        when(spuMapper.selectById(1L)).thenReturn(new Spu());
        when(skuMapper.selectList(any())).thenReturn(Collections.singletonList(sku2));

        Sku incoming = new Sku();
        incoming.setSpuId(1L);
        SpecVO dupSpec = new SpecVO();
        dupSpec.setTemplateId(1L);
        dupSpec.setItemId(1L);
        incoming.setSpecs(Arrays.asList(dupSpec));

        assertThatThrownBy(() -> skuService.createSku(incoming))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");
        verify(skuMapper, never()).insert(any(Sku.class));
    }

    @Test
    void testCreateSku_NewCombo_Inserts() {
        when(spuMapper.selectById(1L)).thenReturn(new Spu());
        when(skuMapper.selectList(any())).thenReturn(Collections.singletonList(sku2));
        when(skuMapper.insert(any(Sku.class))).thenAnswer(inv -> {
            inv.getArgument(0, Sku.class).setId(5L);
            return 1;
        });

        Sku incoming = new Sku();
        incoming.setSpuId(1L);
        SpecVO freshSpec = new SpecVO();
        freshSpec.setTemplateId(1L);
        freshSpec.setItemId(2L);
        incoming.setSpecs(Arrays.asList(freshSpec));

        Sku result = skuService.createSku(incoming);

        verify(skuMapper).insert(any(Sku.class));
        assertThat(result.getSpecHash()).hasSize(32).matches("[0-9a-f]{32}");
        verify(eventPublisher).publishEvent(new ProductCacheInvalidationEvent(
                ProductCacheInvalidationEvent.Type.SKU_CHANGED, 1L, 5L, false));
        verify(eventPublisher).publishEvent(new StockCacheEvent(List.of(5L)));
    }

    @Test
    void testUpdateSku_PriceChange_InvalidatesListCache() {
        when(skuMapper.selectById(1L)).thenReturn(sku1);

        Sku incoming = new Sku();
        incoming.setId(1L);
        incoming.setPrice(new BigDecimal("299.00"));

        skuService.updateSku(incoming);

        verify(eventPublisher).publishEvent(new ProductCacheInvalidationEvent(
                ProductCacheInvalidationEvent.Type.SKU_CHANGED, 1L, 1L, false));
    }

    @Test
    void testUpdateSku_DuplicateSpecCombo_Throws() {
        when(skuMapper.selectById(1L)).thenReturn(sku1);
        when(skuMapper.selectList(any())).thenReturn(Arrays.asList(sku1, sku2));

        Sku incoming = new Sku();
        incoming.setId(1L);
        SpecVO collidingSpec = new SpecVO();
        collidingSpec.setTemplateId(1L);
        collidingSpec.setItemId(1L);
        incoming.setSpecs(Arrays.asList(collidingSpec));

        assertThatThrownBy(() -> skuService.updateSku(incoming))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");
        verify(skuMapper, never()).updateById(any(Sku.class));
    }

    private Sku tombstone(Long id, Long deleteTime, String specHash) {
        Sku sku = new Sku();
        sku.setId(id);
        sku.setSpuId(1L);
        SpecVO spec = new SpecVO();
        spec.setTemplateId(1L);
        spec.setItemId(1L);
        sku.setSpecs(Arrays.asList(spec));
        sku.setSpecHash(specHash);
        sku.setPrice(new BigDecimal("55.00"));
        sku.setStock(10);
        sku.setDeleteTime(deleteTime);
        return sku;
    }

    @Test
    void testCreateSku_MatchesTombstone_Resurrects() {
        Sku deleted = tombstone(9L, 1725000000000L, "auto_c1_i1");
        when(spuMapper.selectById(1L)).thenReturn(new Spu());
        when(skuMapper.selectList(any())).thenReturn(Collections.singletonList(deleted));

        Sku incoming = new Sku();
        incoming.setSpuId(1L);
        SpecVO spec = new SpecVO();
        spec.setTemplateId(1L);
        spec.setItemId(1L);
        incoming.setSpecs(Arrays.asList(spec));
        incoming.setPrice(new BigDecimal("66.00"));

        Sku result = skuService.createSku(incoming);

        verify(skuMapper, never()).insert(any(Sku.class));
        verify(skuMapper).updateById(org.mockito.Mockito.<Sku>argThat(
                s -> Long.valueOf(9L).equals(s.getId()) && Long.valueOf(0L).equals(s.getDeleteTime())));
        assertThat(result.getId()).isEqualTo(9L);
        assertThat(result.getDeleteTime()).isZero();
        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(result.getSpecHash()).hasSize(32).matches("[0-9a-f]{32}");
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("66.00"));
        verify(eventPublisher).publishEvent(new ProductCacheInvalidationEvent(
                ProductCacheInvalidationEvent.Type.SKU_CHANGED, 1L, 9L, false));
        verify(eventPublisher).publishEvent(new StockCacheEvent(List.of(9L)));
    }

    @Test
    void testCreateSku_MultipleTombstones_ResurrectsLatest() {
        Sku older = tombstone(7L, 1000L, "auto_c1_i1");
        Sku latest = tombstone(8L, 2000L, "11111111111111111111111111111111");
        when(spuMapper.selectById(1L)).thenReturn(new Spu());
        when(skuMapper.selectList(any())).thenReturn(Arrays.asList(older, latest));

        Sku incoming = new Sku();
        incoming.setSpuId(1L);
        SpecVO spec = new SpecVO();
        spec.setTemplateId(1L);
        spec.setItemId(1L);
        incoming.setSpecs(Arrays.asList(spec));

        Sku result = skuService.createSku(incoming);

        verify(skuMapper, never()).insert(any(Sku.class));
        verify(skuMapper, times(1)).updateById(any(Sku.class));
        assertThat(result.getId()).isEqualTo(8L);
    }

    @Test
    void testCreateSku_LiveAndTombstoneSameCombo_Throws() {
        Sku deleted = tombstone(9L, 2000L, "auto_c1_i1");
        when(spuMapper.selectById(1L)).thenReturn(new Spu());
        when(skuMapper.selectList(any())).thenReturn(Arrays.asList(sku2, deleted));

        Sku incoming = new Sku();
        incoming.setSpuId(1L);
        SpecVO spec = new SpecVO();
        spec.setTemplateId(1L);
        spec.setItemId(1L);
        incoming.setSpecs(Arrays.asList(spec));

        assertThatThrownBy(() -> skuService.createSku(incoming))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");
        verify(skuMapper, never()).insert(any(Sku.class));
        verify(skuMapper, never()).updateById(any(Sku.class));
    }

    @Test
    void testUpdateSku_TombstoneOnlyConflict_FreesSlotAndProceeds() {
        Sku deleted = tombstone(9L, 5000L, "22222222222222222222222222222222");
        when(skuMapper.selectById(1L)).thenReturn(sku1);
        when(skuMapper.selectList(any())).thenReturn(Arrays.asList(sku1, deleted));

        Sku incoming = new Sku();
        incoming.setId(1L);
        SpecVO spec = new SpecVO();
        spec.setTemplateId(1L);
        spec.setItemId(1L);
        incoming.setSpecs(Arrays.asList(spec));

        Sku result = skuService.updateSku(incoming);

        verify(skuMapper, never()).insert(any(Sku.class));
        verify(skuMapper).updateById(org.mockito.Mockito.<Sku>argThat(
                s -> Long.valueOf(9L).equals(s.getId()) && s.getSpecHash().endsWith("#del5000")));
        verify(skuMapper).updateById(org.mockito.Mockito.<Sku>argThat(
                s -> Long.valueOf(1L).equals(s.getId())));
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getSpecHash()).hasSize(32).matches("[0-9a-f]{32}");
    }

    @Test
    void testUpdateSku_AlreadyFreedTombstone_SkipsMutation() {
        Sku deleted = tombstone(9L, 5000L, "22222222222222222222222222222222#del5000");
        when(skuMapper.selectById(1L)).thenReturn(sku1);
        when(skuMapper.selectList(any())).thenReturn(Arrays.asList(sku1, deleted));

        Sku incoming = new Sku();
        incoming.setId(1L);
        SpecVO spec = new SpecVO();
        spec.setTemplateId(1L);
        spec.setItemId(1L);
        incoming.setSpecs(Arrays.asList(spec));

        Sku result = skuService.updateSku(incoming);

        verify(skuMapper, times(1)).updateById(any(Sku.class));
        verify(skuMapper).updateById(org.mockito.Mockito.<Sku>argThat(
                s -> Long.valueOf(1L).equals(s.getId())));
        assertThat(result.getId()).isEqualTo(1L);
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
        stubStockQuery(1L, 50);

        Integer result = skuService.getStockById(1L);

        assertThat(result).isEqualTo(50);
        verify(skuMapper, never()).selectById(any());
    }

    @Test
    void testGetStockById_CacheMiss_FallbackToDb() {
        when(redisUtil.queryWithCache(
                eq(RedisConstants.PREFIX_SKU_STOCK + 1L),
                eq(Integer.class),
                eq(RedisConstants.SKU_STOCK_TTL_SECONDS),
                eq(RedisConstants.CACHE_NULL_TTL),
                eq(0L),
                eq(100L),
                any())).thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<Integer> loader = invocation.getArgument(6);
                    return loader.get();
                });
        when(skuMapper.selectById(1L)).thenReturn(sku1);

        Integer result = skuService.getStockById(1L);

        assertThat(result).isEqualTo(50);
        verify(skuMapper).selectById(1L);
    }

    @Test
    void testGetStockById_NullSentinel_ReturnsNullWithoutDb() {
        stubStockQuery(1L, null);

        Integer result = skuService.getStockById(1L);

        assertThat(result).isNull();
        verify(skuMapper, never()).selectById(any());
    }

    @Test
    void testGetStockById_SkuNotFound() {
        when(redisUtil.queryWithCache(
                eq(RedisConstants.PREFIX_SKU_STOCK + 99L),
                eq(Integer.class),
                eq(RedisConstants.SKU_STOCK_TTL_SECONDS),
                eq(RedisConstants.CACHE_NULL_TTL),
                eq(0L),
                eq(100L),
                any())).thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<Integer> loader = invocation.getArgument(6);
                    return loader.get();
                });
        when(skuMapper.selectById(99L)).thenReturn(null);

        Integer result = skuService.getStockById(99L);

        assertThat(result).isNull();
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
        verify(eventPublisher).publishEvent(new ProductCacheInvalidationEvent(
                ProductCacheInvalidationEvent.Type.SKU_CHANGED, 1L, 1L, false));
        verify(eventPublisher).publishEvent(new StockCacheEvent(List.of(1L)));
    }

    @Test
    void deleteSku_fileCleanupFailureDoesNotSkipCacheInvalidation() {
        sku1.setImage("https://example.test/sku.png");
        when(skuMapper.selectById(1L)).thenReturn(sku1);
        doThrow(new RuntimeException("oss unavailable")).when(ossService).deleteFile(sku1.getImage());

        assertThatCode(() -> skuService.deleteSku(1L)).doesNotThrowAnyException();

        verify(skuMapper).updateById(org.mockito.Mockito.<Sku>argThat(sku -> sku.getDeleteTime() > 0));
        verify(eventPublisher).publishEvent(new ProductCacheInvalidationEvent(
                ProductCacheInvalidationEvent.Type.SKU_CHANGED, 1L, 1L, false));
        verify(eventPublisher).publishEvent(new StockCacheEvent(List.of(1L)));
    }

    @Test
    void testGetSkuConfig_MergesArchivedOptionsReferencedByLiveSkus() {
        Spu spu = configSpu();
        SpecTemplate liveTemplate = specTemplate(1L, 1L, "颜色");
        SpecItem liveItem = specItem(1L, 1L, "黑色");
        when(spuMapper.selectById(1L)).thenReturn(spu);
        when(specTemplateService.getTemplatesByCategory(1L)).thenReturn(Collections.singletonList(liveTemplate));
        when(specItemService.getItemsByTemplate(1L)).thenReturn(Collections.singletonList(liveItem));
        when(skuMapper.selectList(any())).thenReturn(Collections.singletonList(sku1));

        SkuConfigVO result = skuService.getSkuConfig(1L);

        assertThat(result.getTemplates()).hasSize(2);
        assertThat(result.getTemplates().get(0).getTemplateId()).isEqualTo(1L);
        assertThat(result.getTemplates().get(0).getItems()).hasSize(1);
        assertThat(result.getTemplates().get(0).getItems().get(0).getArchived()).isFalse();
        assertThat(result.getTemplates().get(1).getTemplateId()).isEqualTo(2L);
        assertThat(result.getTemplates().get(1).getArchived()).isTrue();
        assertThat(result.getTemplates().get(1).getItems().get(0).getItemId()).isEqualTo(4L);
        assertThat(result.getTemplates().get(1).getItems().get(0).getArchived()).isTrue();
    }

    @Test
    void testSaveSkuConfig_UpdatesCreatesDeletesAndInvalidatesCaches() {
        Spu spu = configSpu();
        SpecTemplate template = specTemplate(1L, 1L, "颜色");
        SpecItem existingItem = specItem(1L, 1L, "黑色");
        SpecItem newItem = specItem(1L, 3L, "金色");
        Sku existing = configSku(1L, spec(1L, 1L), "10.00", 2);
        Sku removed = configSku(2L, spec(1L, 2L), "20.00", 3);
        Sku[] inserted = new Sku[1];
        AtomicInteger selectCount = new AtomicInteger();

        when(spuMapper.selectById(1L)).thenReturn(spu);
        when(specTemplateMapper.selectById(1L)).thenReturn(template);
        when(specItemMapper.selectById(1L)).thenReturn(existingItem);
        when(specItemMapper.selectById(3L)).thenReturn(newItem);
        when(skuMapper.selectList(any())).thenAnswer(inv ->
                selectCount.getAndIncrement() == 0
                        ? Arrays.asList(existing, removed)
                        : Arrays.asList(existing, inserted[0]));
        when(skuMapper.insert(any(Sku.class))).thenAnswer(inv -> {
            Sku insertedSku = inv.getArgument(0);
            insertedSku.setId(3L);
            inserted[0] = insertedSku;
            return 1;
        });
        when(specTemplateService.getTemplatesByCategory(1L)).thenReturn(Collections.singletonList(template));
        when(specItemService.getItemsByTemplate(1L)).thenReturn(Collections.emptyList());

        SaveSkuConfigRequest request = new SaveSkuConfigRequest();
        request.setSkus(Arrays.asList(
                saveRow(1L, spec(1L, 1L), "25.00", 6),
                saveRow(null, spec(1L, 3L), "30.00", 7)
        ));

        SkuConfigVO result = skuService.saveSkuConfig(1L, request);

        assertThat(result.getSkus()).hasSize(2);
        assertThat(existing.getPrice()).isEqualByComparingTo("25.00");
        assertThat(removed.getDeleteTime()).isGreaterThan(0L);
        assertThat(inserted[0]).isNotNull();
        assertThat(inserted[0].getSpecHash()).hasSize(32);
        verify(skuMapper).insert(org.mockito.Mockito.<Sku>argThat(sku ->
                Long.valueOf(3L).equals(sku.getId()) && sku.getStock() == 7));
        verify(skuMapper).updateById(org.mockito.Mockito.<Sku>argThat(sku ->
                Long.valueOf(2L).equals(sku.getId()) && sku.getDeleteTime() > 0));
        verify(eventPublisher).publishEvent((Object) argThat(event ->
                event instanceof ProductSkuBatchChangedEvent batchEvent
                        && batchEvent.skuIds().containsAll(List.of(1L, 2L, 3L))));
        verify(eventPublisher).publishEvent((Object) argThat(event ->
                event instanceof StockCacheEvent stockEvent
                        && stockEvent.skuIds().containsAll(List.of(1L, 2L, 3L))));
        verify(redisUtil, never()).delete(any(String.class));
        verify(redisUtil, never()).deleteByPattern(any());
    }

    @Test
    void testSaveSkuConfig_EmptyRequest_SoftDeletesAllLiveSkus() {
        Spu spu = configSpu();
        Sku existing = configSku(1L, spec(1L, 1L), "10.00", 2);
        when(spuMapper.selectById(1L)).thenReturn(spu);
        when(skuMapper.selectList(any())).thenReturn(
                Collections.singletonList(existing),
                Collections.emptyList());
        when(specTemplateService.getTemplatesByCategory(1L)).thenReturn(Collections.emptyList());

        SaveSkuConfigRequest request = new SaveSkuConfigRequest();
        request.setSkus(Collections.emptyList());
        SkuConfigVO result = skuService.saveSkuConfig(1L, request);

        assertThat(result.getSkus()).isEmpty();
        assertThat(existing.getDeleteTime()).isGreaterThan(0L);
        verify(skuMapper).updateById(org.mockito.Mockito.<Sku>argThat(sku ->
                Long.valueOf(1L).equals(sku.getId()) && sku.getDeleteTime() > 0));
    }

    @Test
    void testSaveSkuConfig_DuplicateCombination_Throws() {
        when(spuMapper.selectById(1L)).thenReturn(configSpu());
        when(skuMapper.selectList(any())).thenReturn(Collections.emptyList());

        SaveSkuConfigRequest request = new SaveSkuConfigRequest();
        request.setSkus(Arrays.asList(
                saveRow(null, legacySpec(), "10.00", 1),
                saveRow(null, legacySpec(), "11.00", 1)
        ));

        assertThatThrownBy(() -> skuService.saveSkuConfig(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重复");
    }

    @Test
    void testSaveSkuConfig_TemplateOutsideCategory_Throws() {
        Spu spu = configSpu();
        SpecTemplate otherCategoryTemplate = specTemplate(1L, 2L, "颜色");
        when(spuMapper.selectById(1L)).thenReturn(spu);
        when(skuMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(specTemplateMapper.selectById(1L)).thenReturn(otherCategoryTemplate);

        SaveSkuConfigRequest request = new SaveSkuConfigRequest();
        request.setSkus(Collections.singletonList(saveRow(null, spec(1L, 1L), "10.00", 1)));

        assertThatThrownBy(() -> skuService.saveSkuConfig(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分类");
    }

    @Test
    void testSaveSkuConfig_MatchesTombstone_ReusesSkuId() {
        Spu spu = configSpu();
        SpecTemplate template = specTemplate(1L, 1L, "颜色");
        SpecItem item = specItem(1L, 1L, "黑色");
        Sku deleted = tombstone(9L, 1725000000000L, "auto_c1_i1");
        when(spuMapper.selectById(1L)).thenReturn(spu);
        when(skuMapper.selectList(any())).thenReturn(
                Collections.singletonList(deleted),
                Collections.singletonList(deleted));
        when(specTemplateMapper.selectById(1L)).thenReturn(template);
        when(specItemMapper.selectById(1L)).thenReturn(item);
        when(specTemplateService.getTemplatesByCategory(1L)).thenReturn(Collections.emptyList());

        SaveSkuConfigRequest request = new SaveSkuConfigRequest();
        request.setSkus(Collections.singletonList(saveRow(null, spec(1L, 1L), "66.00", 9)));

        SkuConfigVO result = skuService.saveSkuConfig(1L, request);

        assertThat(result.getSkus()).hasSize(1);
        verify(skuMapper, never()).insert(any(Sku.class));
        verify(skuMapper).updateById(org.mockito.Mockito.<Sku>argThat(sku ->
                Long.valueOf(9L).equals(sku.getId())
                        && Long.valueOf(0L).equals(sku.getDeleteTime())
                        && sku.getStock() == 9));
    }

    private Spu configSpu() {
        Spu spu = new Spu();
        spu.setId(1L);
        spu.setCategoryId(1L);
        spu.setName("测试商品");
        spu.setDeleteTime(0L);
        return spu;
    }

    private void stubStockQuery(Long skuId, Integer stock) {
        when(redisUtil.queryWithCache(
                eq(RedisConstants.PREFIX_SKU_STOCK + skuId),
                eq(Integer.class),
                eq(RedisConstants.SKU_STOCK_TTL_SECONDS),
                eq(RedisConstants.CACHE_NULL_TTL),
                eq(0L),
                eq(100L),
                any())).thenReturn(stock);
    }

    private SpecVO spec(Long templateId, Long itemId) {
        SpecVO spec = new SpecVO();
        spec.setTemplateId(templateId);
        spec.setItemId(itemId);
        spec.setName("规格" + templateId);
        spec.setValue("值" + itemId);
        return spec;
    }

    private SpecVO legacySpec() {
        SpecVO spec = new SpecVO();
        spec.setName("颜色");
        spec.setValue("默认");
        return spec;
    }

    private Sku configSku(Long id, SpecVO spec, String price, int stock) {
        Sku sku = new Sku();
        sku.setId(id);
        sku.setSpuId(1L);
        sku.setSpecs(Collections.singletonList(spec));
        sku.setPrice(new BigDecimal(price));
        sku.setStock(stock);
        sku.setStatus(1);
        sku.setDeleteTime(0L);
        return sku;
    }

    private SaveSkuRowRequest saveRow(Long id, SpecVO spec, String price, int stock) {
        SaveSkuRowRequest row = new SaveSkuRowRequest();
        row.setId(id);
        row.setSpecs(Collections.singletonList(spec));
        row.setPrice(new BigDecimal(price));
        row.setStock(stock);
        row.setStatus(1);
        return row;
    }

    private SpecTemplate specTemplate(Long id, Long categoryId, String name) {
        SpecTemplate template = new SpecTemplate();
        template.setId(id);
        template.setCategoryId(categoryId);
        template.setName(name);
        template.setSpecType(1);
        template.setIsRequired(1);
        template.setSortOrder(0);
        template.setDeleteTime(0L);
        return template;
    }

    private SpecItem specItem(Long templateId, Long id, String value) {
        SpecItem item = new SpecItem();
        item.setId(id);
        item.setTemplateId(templateId);
        item.setValue(value);
        item.setSortOrder(0);
        item.setDeleteTime(0L);
        return item;
    }
}
