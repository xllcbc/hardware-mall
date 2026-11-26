package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.example.mystore.common.constant.DiscountConstants;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.common.exception.IdempotencyConflictException;
import com.example.mystore.common.exception.PriceChangedException;
import com.example.mystore.entity.db.Address;
import com.example.mystore.entity.db.Logistics;
import com.example.mystore.entity.db.Order;
import com.example.mystore.entity.db.OrderItem;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.dto.CheckoutPreviewRequest;
import com.example.mystore.entity.dto.CreateOrderRequest;
import com.example.mystore.entity.dto.LevelPricing;
import com.example.mystore.entity.dto.LevelRateTable;
import com.example.mystore.entity.dto.ProductPricingFacts;
import com.example.mystore.entity.vo.CheckoutPreviewVO;
import com.example.mystore.entity.vo.OrderVO;
import com.example.mystore.event.StockCacheEvent;
import com.example.mystore.mapper.AddressMapper;
import com.example.mystore.mapper.CartMapper;
import com.example.mystore.mapper.LogisticsMapper;
import com.example.mystore.mapper.OrderItemMapper;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.service.LevelPricingService;
import com.example.mystore.service.OrderIdempotencyService;
import com.example.mystore.service.OrderIdempotencyService.ClaimOutcome;
import com.example.mystore.service.OrderQueryService;
import com.example.mystore.service.PricingRuleService;
import com.example.mystore.service.SkuService;
import com.example.mystore.service.UserLevelService;
import com.example.mystore.util.UserContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 订单接入模块：通过预览/创建接口断言定价、幂等、金额与事务内落库行为。
 */
@ExtendWith(MockitoExtension.class)
class OrderIntakeServiceImplTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private SpuMapper spuMapper;
    @Mock
    private AddressMapper addressMapper;
    @Mock
    private LogisticsMapper logisticsMapper;
    @Mock
    private CartMapper cartMapper;
    @Mock
    private SkuService skuService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Mock
    private PricingRuleService pricingRuleService;
    @Mock
    private LevelPricingService levelPricingService;
    @Mock
    private UserLevelService userLevelService;
    @Mock
    private OrderIdempotencyService orderIdempotencyService;
    @Mock
    private OrderQueryService orderQueryService;

    @InjectMocks
    private OrderIntakeServiceImpl intakeService;

    private Address address;
    private Logistics logistics;
    private Sku sku;
    private Spu spu;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), com.example.mystore.entity.db.Cart.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderItem.class);
        // LambdaUpdateWrapper.set() 立即解析列名, 单测无 Spring 上下文需手动注册 Order 元数据
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Order.class);
    }

    @BeforeEach
    void setUp() {
        address = new Address();
        address.setId(1L);
        address.setUserId(2L);
        address.setConsignee("张三");
        address.setPhone("13800001001");
        address.setProvince("广东省");
        address.setCity("深圳市");
        address.setDistrict("南山区");
        address.setDetail("科技园南路88号");

        logistics = new Logistics();
        logistics.setId(1L);
        logistics.setName("德邦物流");
        logistics.setStatus(1);

        sku = new Sku();
        sku.setId(5L);
        sku.setSpuId(1L);
        sku.setPrice(new BigDecimal("219.00"));
        sku.setStock(40);
        sku.setStatus(1);

        spu = new Spu();
        spu.setId(1L);
        spu.setCategoryId(1L);
        spu.setName("防盗门锁 C级");
        spu.setSalesCount(52);
        spu.setExcludeDiscount(DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        lenient().when(pricingRuleService.resolveRetailLevelId()).thenReturn(1L);
        lenient().when(levelPricingService.priceAll(any(), anyList())).thenAnswer(inv -> {
            Long levelId = inv.getArgument(0);
            List<ProductPricingFacts> facts = inv.getArgument(1);
            Long effectiveLevelId = levelId == null ? 1L : levelId;
            return facts.stream()
                    .map(fact -> LevelPricing.priced(
                            effectiveLevelId,
                            BigDecimal.ONE,
                            fact.basePriceMin(),
                            fact.basePriceMax()))
                    .toList();
        });
        lenient().when(userLevelService.getNames(anyCollection())).thenAnswer(inv -> {
            Collection<Long> levelIds = inv.getArgument(0);
            Map<Long, String> names = new HashMap<>();
            for (Long levelId : levelIds) {
                names.put(levelId, levelId == 2L ? "批发A" : "零售");
            }
            return names;
        });
        lenient().when(orderIdempotencyService.claim(anyLong(), anyString(), anyString()))
                .thenReturn(new ClaimOutcome(
                        ClaimOutcome.Kind.ACQUIRED, 10L, "owner-token", null));
        lenient().when(orderQueryService.getOrderView(anyLong(), anyLong()))
                .thenReturn(new OrderVO());
    }

    // ==================== 创建: 地址与物流校验 ====================

    @Test
    void testCreateOrder_AddressNotFound() {
        when(addressMapper.selectById(1L)).thenReturn(null);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId(1L);
        request.setLogisticsId(1L);
        request.setItems(Collections.emptyList());

        assertThatThrownBy(() -> intakeService.createOrder(2L, request, "test-key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("收货地址不存在");
    }

    @Test
    void testCreateOrder_AddressNotBelongToUser() {
        Address others = new Address();
        others.setId(1L);
        others.setUserId(99L); // 不属于当前用户
        when(addressMapper.selectById(1L)).thenReturn(others);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId(1L);
        request.setLogisticsId(1L);
        request.setItems(Collections.emptyList());

        assertThatThrownBy(() -> intakeService.createOrder(2L, request, "test-key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("收货地址不存在");
    }

    @Test
    void testCreateOrder_LogisticsDisabled() {
        when(addressMapper.selectById(1L)).thenReturn(address);
        Logistics disabled = new Logistics();
        disabled.setId(1L);
        disabled.setStatus(0);
        when(logisticsMapper.selectById(1L)).thenReturn(disabled);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId(1L);
        request.setLogisticsId(1L);
        request.setItems(Collections.emptyList());

        assertThatThrownBy(() -> intakeService.createOrder(2L, request, "test-key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("物流方式不存在或不可用");
    }

    @Test
    void testCreateOrder_StockInsufficient() {
        when(addressMapper.selectById(1L)).thenReturn(address);
        when(logisticsMapper.selectById(1L)).thenReturn(logistics);
        when(skuService.getSkuMapByIds(any())).thenReturn(Map.of(5L, sku));

        CreateOrderRequest.CartItem item = new CreateOrderRequest.CartItem();
        item.setSkuId(5L);
        item.setQuantity(100); // 超过库存

        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId(1L);
        request.setLogisticsId(1L);
        request.setItems(Collections.singletonList(item));

        assertThatThrownBy(() -> intakeService.createOrder(2L, request, "test-key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("库存不足");
    }

    // ==================== 创建: 幂等四种结果 ====================

    @Test
    void createOrder_succeededSameFingerprint_returnsOriginalOrder() {
        when(orderIdempotencyService.claim(eq(2L), eq("k1"), anyString()))
                .thenReturn(new ClaimOutcome(
                        ClaimOutcome.Kind.SUCCEEDED, 10L, "owner-token", 77L));
        OrderVO original = new OrderVO();
        original.setId(77L);
        original.setOrderNo("SO-ORIGINAL");
        when(orderQueryService.getOrderView(2L, 77L)).thenReturn(original);

        OrderVO vo = intakeService.createOrder(2L, stubValidRequest(), "k1");

        assertThat(vo.getId()).isEqualTo(77L);
        assertThat(vo.getOrderNo()).isEqualTo("SO-ORIGINAL");
        verify(orderMapper, never()).insert(any(Order.class));
        verify(skuService, never()).deductStock(anyLong(), anyInt());
        verify(orderIdempotencyService, never()).markSucceeded(any(), any(), any());
        verify(orderIdempotencyService, never()).markFailed(any(), any());
    }

    @Test
    void createOrder_processing_throwsProcessingWithoutInsert() {
        when(orderIdempotencyService.claim(eq(2L), eq("k1"), anyString()))
                .thenReturn(new ClaimOutcome(
                        ClaimOutcome.Kind.PROCESSING, 10L, "other-owner", null));

        assertThatThrownBy(() -> intakeService.createOrder(2L, stubValidRequest(), "k1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单处理中，请稍后重试");

        verify(orderMapper, never()).insert(any(Order.class));
        verify(orderIdempotencyService, never()).markFailed(any(), any());
    }

    @Test
    void createOrder_conflictingFingerprint_throwsConflict() {
        when(orderIdempotencyService.claim(eq(2L), eq("k1"), anyString()))
                .thenReturn(new ClaimOutcome(
                        ClaimOutcome.Kind.CONFLICT, 10L, "owner-token", null));

        assertThatThrownBy(() -> intakeService.createOrder(2L, stubValidRequest(), "k1"))
                .isInstanceOfSatisfying(IdempotencyConflictException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(IdempotencyConflictException.CODE);
                    assertThat(e).hasMessage("幂等键已用于其他请求，请刷新后重试");
                });

        verify(orderMapper, never()).insert(any(Order.class));
        verify(orderIdempotencyService, never()).markFailed(any(), any());
    }

    @Test
    void createOrder_missingIdempotencyKey_rejectedBeforeClaim() {
        assertThatThrownBy(() -> intakeService.createOrder(2L, stubValidRequest(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("缺少幂等键，请刷新后重试");

        verifyNoInteractions(orderIdempotencyService);
        verify(orderMapper, never()).insert(any(Order.class));
    }

    // ==================== 创建: 事务内落库与失败标记 ====================

    @Test
    @SuppressWarnings("unchecked")
    void createOrder_success_marksRecordSucceededInOrderTransaction() {
        stubSuccessfulOrderCreation();
        stubOrderVo(100L);

        intakeService.createOrder(2L, singleLineRequest(1, "219.00"), "success-key");

        verify(orderMapper).insert(any(Order.class));
        verify(orderIdempotencyService).markSucceeded(10L, "owner-token", 100L);
        verify(orderIdempotencyService, never()).markFailed(any(), any());
    }

    @Test
    void createOrder_success_deductsStockWritesOrderRemovesCartAndPublishesStockEvent() {
        stubSuccessfulOrderCreation();
        stubOrderVo(100L);

        intakeService.createOrder(2L, singleLineRequest(1, "219.00"), "persist-key");

        verify(skuService).deductStock(5L, 1);
        verify(spuMapper).incrementSalesCount(1L, 1);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(StatusConstants.ORDER_PENDING_PAYMENT);
        assertThat(orderCaptor.getValue().getPayAmount()).isEqualByComparingTo("219.00");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrderItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderItemMapper).insertBatch(itemsCaptor.capture());
        assertThat(itemsCaptor.getValue()).singleElement()
                .satisfies(item -> assertThat(item.getOrderId()).isEqualTo(100L));

        verify(cartMapper).update(isNull(), any());
        ArgumentCaptor<StockCacheEvent> eventCaptor = ArgumentCaptor.forClass(StockCacheEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().skuIds()).containsExactly(5L);
    }

    @Test
    void createOrder_markSucceededFails_marksFailedAndRethrows() {
        stubSuccessfulOrderCreation();
        doThrow(new BusinessException("幂等记录状态已变更，请重试"))
                .when(orderIdempotencyService).markSucceeded(10L, "owner-token", 100L);

        assertThatThrownBy(() -> intakeService.createOrder(
                2L, singleLineRequest(1, "219.00"), "fail-key"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("幂等记录状态已变更，请重试");

        verify(orderIdempotencyService).markFailed(10L, "owner-token");
    }

    @Test
    void createOrder_voAssemblyFails_doesNotFailSucceededRecord() {
        stubSuccessfulOrderCreation();
        when(orderQueryService.getOrderView(2L, 100L)).thenThrow(new RuntimeException("vo assembly failed"));

        assertThatThrownBy(() -> intakeService.createOrder(
                2L, singleLineRequest(1, "219.00"), "vo-key"))
                .isInstanceOf(RuntimeException.class);

        verify(orderIdempotencyService).markSucceeded(10L, "owner-token", 100L);
        verify(orderIdempotencyService, never()).markFailed(any(), any());
    }

    @Test
    void createOrder_validationFails_marksRecordFailedForRetry() {
        // 业务失败（库存不足）时置为失败，允许用户立即重试
        when(addressMapper.selectById(1L)).thenReturn(address);
        when(logisticsMapper.selectById(1L)).thenReturn(logistics);
        when(skuService.getSkuMapByIds(any())).thenReturn(Map.of(5L, sku));

        CreateOrderRequest.CartItem item = new CreateOrderRequest.CartItem();
        item.setSkuId(5L);
        item.setQuantity(100); // 超过库存

        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId(1L);
        request.setLogisticsId(1L);
        request.setItems(Collections.singletonList(item));

        assertThatThrownBy(() -> intakeService.createOrder(2L, request, "stock-key"))
                .hasMessageContaining("库存不足");

        verify(orderIdempotencyService).markFailed(10L, "owner-token");
        verify(orderIdempotencyService, never()).markSucceeded(any(), any(), any());
    }

    @Test
    void createOrder_stockDeductionFailure_abortsBeforeOrderAndMarksRecordFailed() {
        when(addressMapper.selectById(1L)).thenReturn(address);
        when(logisticsMapper.selectById(1L)).thenReturn(logistics);

        Sku secondSku = new Sku();
        secondSku.setId(6L);
        secondSku.setSpuId(2L);
        secondSku.setPrice(new BigDecimal("99.00"));
        secondSku.setStock(10);
        secondSku.setStatus(1);

        Spu secondSpu = new Spu();
        secondSpu.setId(2L);
        secondSpu.setCategoryId(1L);
        secondSpu.setName("不锈钢合页");
        secondSpu.setExcludeDiscount(DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);

        when(skuService.getSkuMapByIds(any())).thenReturn(Map.of(5L, sku, 6L, secondSku));
        when(spuMapper.selectBatchIds(any())).thenReturn(List.of(spu, secondSpu));
        when(skuService.deductStock(5L, 2)).thenReturn(true);
        when(skuService.deductStock(6L, 3)).thenReturn(false);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(line(5L, 2), line(6L, 3)));
        request.setAddressId(1L);
        request.setLogisticsId(1L);
        request.setExpectedPayAmount(new BigDecimal("735.00"));

        assertThatThrownBy(() -> intakeService.createOrder(2L, request, "rollback-key"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库存扣减失败");

        verify(orderIdempotencyService).markFailed(10L, "owner-token");
        verify(orderMapper, never()).insert(any(Order.class));
        verify(cartMapper, never()).update(any(), any());
    }

    @Test
    void createOrder_spuMissingFromBatch_shouldThrow() {
        when(addressMapper.selectById(1L)).thenReturn(address);
        when(logisticsMapper.selectById(1L)).thenReturn(logistics);
        when(skuService.getSkuMapByIds(any())).thenReturn(Map.of(5L, sku));
        when(spuMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());

        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId(1L);
        request.setLogisticsId(1L);
        request.setItems(List.of(line(5L, 1), line(5L, 2)));

        assertThatThrownBy(() -> intakeService.createOrder(2L, request, "k"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("商品不存在或已下架");

        verify(orderMapper, never()).insert(any(Order.class));
        verify(spuMapper, times(1)).selectBatchIds(anyCollection());
        verify(spuMapper, never()).selectById(any());
    }

    // ==================== 创建: 金额校验 ====================

    @Test
    void createOrder_zeroAmountIsRejectedBeforeOrderIsWritten() {
        when(addressMapper.selectById(1L)).thenReturn(address);
        when(logisticsMapper.selectById(1L)).thenReturn(logistics);
        when(skuService.getSkuMapByIds(any())).thenReturn(Map.of(5L, sku));
        when(spuMapper.selectBatchIds(any())).thenReturn(List.of(spu));
        when(levelPricingService.priceAll(any(), anyList()))
                .thenReturn(List.of(LevelPricing.priced(
                        1L, BigDecimal.ONE,
                        new BigDecimal("0.00"), new BigDecimal("0.00"))));

        CreateOrderRequest request = singleLineRequest(2, "0.00");

        assertThatThrownBy(() -> intakeService.createOrder(2L, request, "zero-key"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("订单金额低于 0.01 元")
                .hasMessageNotContaining("创建支付订单失败");

        // 没有订单就没有可支付对象：不建单、不扣库存
        verify(orderMapper, never()).insert(any(Order.class));
        verify(skuService, never()).deductStock(anyLong(), anyInt());
        verify(orderIdempotencyService).markFailed(10L, "owner-token");
    }

    @Test
    void createOrder_gapIsRejectedBeforeWriting() {
        UserContext.setLevelId(2L);
        try {
            when(levelPricingService.priceAll(eq(2L), anyList()))
                    .thenReturn(List.of(LevelPricing.missing(2L)));
            when(addressMapper.selectById(1L)).thenReturn(address);
            when(logisticsMapper.selectById(1L)).thenReturn(logistics);
            when(skuService.getSkuMapByIds(any())).thenReturn(Map.of(5L, sku));
            when(spuMapper.selectBatchIds(any())).thenReturn(List.of(spu));

            CreateOrderRequest request = new CreateOrderRequest();
            request.setItems(List.of(line(5L, 2)));
            request.setAddressId(1L);
            request.setLogisticsId(1L);

            assertThatThrownBy(() -> intakeService.createOrder(2L, request, "gap-key"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("商品暂不可售")
                    .hasMessageContaining("防盗门锁 C级");

            verify(orderMapper, never()).insert(any(Order.class));
            verify(skuService, never()).deductStock(anyLong(), anyInt());
            verify(orderIdempotencyService).markFailed(10L, "owner-token");
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void createOrder_priceChanged_returnsLatestPreviewAndMarksRecordFailed() {
        UserContext.setLevelId(2L);
        try {
            when(levelPricingService.priceAll(eq(2L), anyList()))
                    .thenReturn(List.of(LevelPricing.priced(
                            2L, new BigDecimal("0.8000"),
                            new BigDecimal("175.20"), new BigDecimal("175.20"))));
            when(addressMapper.selectById(1L)).thenReturn(address);
            when(logisticsMapper.selectById(1L)).thenReturn(logistics);
            when(skuService.getSkuMapByIds(any())).thenReturn(Map.of(5L, sku));
            when(spuMapper.selectBatchIds(any())).thenReturn(List.of(spu));

            CreateOrderRequest request = new CreateOrderRequest();
            request.setItems(List.of(line(5L, 2)));
            request.setAddressId(1L);
            request.setLogisticsId(1L);
            request.setExpectedPayAmount(new BigDecimal("300.00"));

            assertThatThrownBy(() -> intakeService.createOrder(2L, request, "changed-key"))
                    .isInstanceOfSatisfying(PriceChangedException.class, error -> {
                        assertThat(error.getPriceChange().getExpectedPayAmount())
                                .isEqualByComparingTo("300.00");
                        assertThat(error.getPriceChange().getPreview().getItems().get(0).getPrice())
                                .isEqualByComparingTo("175.20");
                        assertThat(error.getPriceChange().getPreview().getPayAmount())
                                .isEqualByComparingTo("350.40");
                    });

            verify(orderIdempotencyService).markFailed(10L, "owner-token");
            verify(skuService, never()).deductStock(anyLong(), anyInt());
            verify(orderMapper, never()).insert(any(Order.class));
            verify(cartMapper, never()).update(any(), any());
            verify(applicationEventPublisher, never()).publishEvent(any());
        } finally {
            UserContext.clear();
        }
    }

    // ==================== 预览 ====================

    @Test
    void previewOrder_wiresModuleSettlementWithoutWriting() {
        UserContext.setLevelId(2L);
        try {
            when(levelPricingService.priceAll(eq(2L), anyList()))
                    .thenReturn(List.of(LevelPricing.priced(
                            2L, new BigDecimal("0.8000"),
                            new BigDecimal("175.20"), new BigDecimal("175.20"))));
            when(skuService.getSkuMapByIds(any())).thenReturn(Map.of(5L, sku));
            when(spuMapper.selectBatchIds(any())).thenReturn(List.of(spu));

            CheckoutPreviewVO preview = intakeService.previewOrder(previewRequest(5L, 2));

            assertThat(preview.getItems()).hasSize(1);
            assertThat(preview.getItems().get(0).getPrice()).isEqualByComparingTo("175.20");
            assertThat(preview.getItems().get(0).getSubtotal()).isEqualByComparingTo("350.40");
            assertThat(preview.getGoodsAmount()).isEqualByComparingTo("350.40");
            assertThat(preview.getFreightAmount()).isEqualByComparingTo("0.00");
            assertThat(preview.getPayAmount()).isEqualByComparingTo("350.40");
            verify(orderMapper, never()).insert(any(Order.class));
            verify(cartMapper, never()).update(any(), any());
            verify(skuService, never()).deductStock(anyLong(), anyInt());
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void previewOrder_gapIsRejected() {
        UserContext.setLevelId(2L);
        try {
            when(levelPricingService.priceAll(eq(2L), anyList()))
                    .thenReturn(List.of(LevelPricing.missing(2L)));
            when(skuService.getSkuMapByIds(any())).thenReturn(Map.of(5L, sku));
            when(spuMapper.selectBatchIds(any())).thenReturn(List.of(spu));

            assertThatThrownBy(() -> intakeService.previewOrder(previewRequest(5L, 2)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("商品暂不可售")
                    .hasMessageContaining("防盗门锁 C级");
            verify(orderMapper, never()).insert(any(Order.class));
            verify(skuService, never()).deductStock(anyLong(), anyInt());
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void previewOrder_zeroAmountIsRejected() {
        when(levelPricingService.priceAll(any(), anyList()))
                .thenReturn(List.of(LevelPricing.priced(
                        1L, BigDecimal.ONE,
                        new BigDecimal("0.00"), new BigDecimal("0.00"))));
        when(skuService.getSkuMapByIds(any())).thenReturn(Map.of(5L, sku));
        when(spuMapper.selectBatchIds(any())).thenReturn(List.of(spu));

        assertThatThrownBy(() -> intakeService.previewOrder(previewRequest(5L, 3)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("订单金额低于 0.01 元")
                .hasMessageNotContaining("创建支付订单失败");
    }

    @Test
    void previewOrder_subCentSettlementAmountIsRejected() {
        // 等级结算价按契约已取整到分；这里模拟任何非法子分来源穿透到结算，
        // 断言结算边界仍以业务错误失败关闭，而不是 ArithmeticException 或网关失败
        when(levelPricingService.priceAll(any(), anyList()))
                .thenReturn(List.of(LevelPricing.priced(
                        1L, BigDecimal.ONE,
                        new BigDecimal("0.004"), new BigDecimal("0.004"))));
        when(skuService.getSkuMapByIds(any())).thenReturn(Map.of(5L, sku));
        when(spuMapper.selectBatchIds(any())).thenReturn(List.of(spu));

        assertThatThrownBy(() -> intakeService.previewOrder(previewRequest(5L, 2)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("订单金额非法")
                .hasMessageNotContaining("创建支付订单失败");
    }

    @Test
    void previewOrder_oneCentAmountIsAccepted() {
        when(levelPricingService.priceAll(any(), anyList()))
                .thenReturn(List.of(LevelPricing.priced(
                        1L, BigDecimal.ONE,
                        new BigDecimal("0.01"), new BigDecimal("0.01"))));
        when(skuService.getSkuMapByIds(any())).thenReturn(Map.of(5L, sku));
        when(spuMapper.selectBatchIds(any())).thenReturn(List.of(spu));

        CheckoutPreviewVO preview = intakeService.previewOrder(previewRequest(5L, 1));

        assertThat(preview.getPayAmount()).isEqualByComparingTo("0.01");
    }

    @Test
    void previewOrder_mapsExcludeDiscountFactForPricing() {
        UserContext.setLevelId(2L);
        try {
            spu.setExcludeDiscount(DiscountConstants.DISCOUNT_PARTICIPATION_DISABLED);
            when(levelPricingService.priceAll(eq(2L), anyList()))
                    .thenReturn(List.of(LevelPricing.priced(
                            1L, BigDecimal.ONE,
                            new BigDecimal("219.00"), new BigDecimal("219.00"))));
            when(skuService.getSkuMapByIds(any())).thenReturn(Map.of(5L, sku));
            when(spuMapper.selectBatchIds(any())).thenReturn(List.of(spu));

            intakeService.previewOrder(previewRequest(5L, 2));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<ProductPricingFacts>> factsCaptor =
                    ArgumentCaptor.forClass((Class) List.class);
            verify(levelPricingService).priceAll(eq(2L), factsCaptor.capture());
            assertThat(factsCaptor.getValue())
                    .singleElement()
                    .satisfies(facts -> {
                        assertThat(facts.categoryId()).isEqualTo(1L);
                        assertThat(facts.basePriceMin()).isEqualByComparingTo("219.00");
                        assertThat(facts.excludeDiscount()).isTrue();
                    });
        } finally {
            UserContext.clear();
        }
    }

    // ==================== 批量定价与订单等级快照 ====================

    @Test
    void createOrder_buildsItemsFromBatchSpuMap() {
        when(addressMapper.selectById(1L)).thenReturn(address);
        when(logisticsMapper.selectById(1L)).thenReturn(logistics);

        Sku skuA = new Sku();
        skuA.setId(5L);
        skuA.setSpuId(1L);
        skuA.setPrice(new BigDecimal("219.00"));
        skuA.setStock(40);
        skuA.setStatus(1);

        Spu spuA = new Spu();
        spuA.setId(1L);
        spuA.setName("防盗门锁 C级");
        spuA.setImages(List.of("img-a.jpg"));

        Sku skuB = new Sku();
        skuB.setId(6L);
        skuB.setSpuId(2L);
        skuB.setPrice(new BigDecimal("99.00"));
        skuB.setStock(10);
        skuB.setStatus(1);

        Spu spuB = new Spu();
        spuB.setId(2L);
        spuB.setName("不锈钢合页");
        spuB.setImages(List.of("img-b.jpg"));

        when(skuService.getSkuMapByIds(any())).thenReturn(Map.of(5L, skuA, 6L, skuB));
        when(spuMapper.selectBatchIds(any())).thenReturn(List.of(spuA, spuB));
        when(skuService.deductStock(anyLong(), anyInt())).thenReturn(true);
        when(orderMapper.insert(any(Order.class))).thenAnswer(inv -> {
            inv.getArgument(0, Order.class).setId(100L);
            return 1;
        });
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId(1L);
        request.setLogisticsId(1L);
        request.setItems(List.of(line(5L, 2), line(6L, 3)));
        request.setExpectedPayAmount(new BigDecimal("735.00"));

        OrderVO vo = intakeService.createOrder(2L, request, "k");

        assertThat(vo).isNotNull();
        verify(spuMapper, times(1)).selectBatchIds(anyCollection());
        verify(spuMapper, never()).selectById(any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrderItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderItemMapper).insertBatch(captor.capture());
        List<OrderItem> items = captor.getValue();

        assertThat(items).hasSize(2);
        OrderItem first = items.get(0);
        assertThat(first.getSkuId()).isEqualTo(5L);
        assertThat(first.getSpuId()).isEqualTo(1L);
        assertThat(first.getProductName()).isEqualTo("防盗门锁 C级");
        assertThat(first.getProductImage()).isEqualTo("img-a.jpg");
        assertThat(first.getQuantity()).isEqualTo(2);
        assertThat(first.getSubtotal()).isEqualByComparingTo("438.00");

        OrderItem second = items.get(1);
        assertThat(second.getSkuId()).isEqualTo(6L);
        assertThat(second.getSpuId()).isEqualTo(2L);
        assertThat(second.getProductName()).isEqualTo("不锈钢合页");
        assertThat(second.getProductImage()).isEqualTo("img-b.jpg");
        assertThat(second.getQuantity()).isEqualTo(3);
        assertThat(second.getSubtotal()).isEqualByComparingTo("297.00");
    }

    @Test
    void orderSnapshotFieldsAllComeFromOneLevelPricingResult() {
        UserContext.setLevelId(2L);
        try {
            Sku skuB = new Sku();
            skuB.setId(6L);
            skuB.setSpuId(2L);
            skuB.setPrice(new BigDecimal("219.00"));
            skuB.setStock(10);
            skuB.setStatus(1);

            Spu spuB = new Spu();
            spuB.setId(2L);
            spuB.setCategoryId(1L);
            spuB.setName("不锈钢合页");
            spuB.setExcludeDiscount(DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);

            when(levelPricingService.priceAll(eq(2L), anyList()))
                    .thenReturn(List.of(
                            LevelPricing.priced(7L, new BigDecimal("0.6500"),
                                    new BigDecimal("142.35"), new BigDecimal("142.35")),
                            LevelPricing.priced(7L, new BigDecimal("0.6500"),
                                    new BigDecimal("142.35"), new BigDecimal("142.35"))));
            when(userLevelService.getNames(anyCollection())).thenReturn(Map.of(7L, "工程客户"));
            when(addressMapper.selectById(1L)).thenReturn(address);
            when(logisticsMapper.selectById(1L)).thenReturn(logistics);
            when(skuService.getSkuMapByIds(any())).thenReturn(Map.of(5L, sku, 6L, skuB));
            when(spuMapper.selectBatchIds(any())).thenReturn(List.of(spu, spuB));
            when(skuService.deductStock(anyLong(), anyInt())).thenReturn(true);
            when(orderMapper.insert(any(Order.class))).thenAnswer(inv -> {
                inv.getArgument(0, Order.class).setId(100L);
                return 1;
            });
            CreateOrderRequest request = new CreateOrderRequest();
            request.setAddressId(1L);
            request.setLogisticsId(1L);
            request.setItems(List.of(line(5L, 1), line(6L, 1)));
            request.setExpectedPayAmount(new BigDecimal("284.70"));

            intakeService.createOrder(2L, request, "snapshot-key");

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<OrderItem>> captor = ArgumentCaptor.forClass(List.class);
            verify(orderItemMapper).insertBatch(captor.capture());
            assertThat(captor.getValue()).allSatisfy(item -> {
                assertThat(item.getLevelId()).isEqualTo(7L);
                assertThat(item.getLevelName()).isEqualTo("工程客户");
                assertThat(item.getPricingRate()).isEqualByComparingTo("0.6500");
                assertThat(item.getPrice()).isEqualByComparingTo("142.35");
                assertThat(item.getSubtotal()).isEqualByComparingTo("142.35");
            });

            verify(levelPricingService, times(1)).priceAll(eq(2L), anyList());
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<Long>> levelIdsCaptor = ArgumentCaptor.forClass((Class) Collection.class);
            verify(userLevelService, times(1)).getNames(levelIdsCaptor.capture());
            assertThat(levelIdsCaptor.getValue()).containsOnly(7L);
            verify(userLevelService, never()).getName(any());
        } finally {
            UserContext.clear();
        }
    }

    /**
     * 切换前（逐项 rateFor → 单价取整 → 乘数量）记录的订单金额黄金值，切换后必须逐分一致。
     */
    @Test
    void settlementAmountsMatchPreSwitchGoldenValues() {
        UserContext.setLevelId(2L);
        try {
            LevelPricingService realLevelPricing =
                    new LevelPricingServiceImpl(pricingRuleService);
            ReflectionTestUtils.setField(intakeService, "levelPricingService", realLevelPricing);
            when(pricingRuleService.rateTable(2L)).thenReturn(new LevelRateTable(
                    2L, Map.of(1L, new BigDecimal("0.8000"), 10L, new BigDecimal("1.0000"))));
            when(pricingRuleService.rateTable(1L)).thenReturn(new LevelRateTable(
                    1L, Map.of(1L, BigDecimal.ONE, 10L, BigDecimal.ONE)));

            Sku skuB = sku(6L, 2L, "19.99");
            Sku skuC = sku(7L, 3L, "10.00");
            Sku skuD = sku(8L, 4L, "25.00");

            Spu spuB = spu(2L, 1L, "不锈钢合页", DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
            Spu spuC = spu(3L, 10L, "膨胀螺丝", DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
            Spu spuD = spu(4L, 1L, "不参与折扣商品", DiscountConstants.DISCOUNT_PARTICIPATION_DISABLED);

            when(addressMapper.selectById(1L)).thenReturn(address);
            when(logisticsMapper.selectById(1L)).thenReturn(logistics);
            when(skuService.getSkuMapByIds(any()))
                    .thenReturn(Map.of(5L, sku, 6L, skuB, 7L, skuC, 8L, skuD));
            when(spuMapper.selectBatchIds(any())).thenReturn(List.of(spu, spuB, spuC, spuD));
            when(skuService.deductStock(anyLong(), anyInt())).thenReturn(true);
            when(orderMapper.insert(any(Order.class))).thenAnswer(inv -> {
                inv.getArgument(0, Order.class).setId(100L);
                return 1;
            });
            CreateOrderRequest request = new CreateOrderRequest();
            request.setAddressId(1L);
            request.setLogisticsId(1L);
            request.setItems(List.of(
                    line(5L, 2),
                    line(6L, 3),
                    line(7L, 1),
                    line(8L, 2)));
            request.setExpectedPayAmount(new BigDecimal("458.37"));

            intakeService.createOrder(2L, request, "golden-key");

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<OrderItem>> itemCaptor = ArgumentCaptor.forClass(List.class);
            verify(orderItemMapper).insertBatch(itemCaptor.capture());
            assertThat(itemCaptor.getValue()).extracting(OrderItem::getPrice)
                    .containsExactly(
                            new BigDecimal("175.20"),
                            new BigDecimal("15.99"),
                            new BigDecimal("10.00"),
                            new BigDecimal("25.00"));
            assertThat(itemCaptor.getValue()).extracting(OrderItem::getSubtotal)
                    .containsExactly(
                            new BigDecimal("350.40"),
                            new BigDecimal("47.97"),
                            new BigDecimal("10.00"),
                            new BigDecimal("50.00"));
            assertThat(itemCaptor.getValue()).extracting(OrderItem::getLevelId)
                    .containsExactly(2L, 2L, 2L, 1L);
            assertThat(itemCaptor.getValue()).extracting(OrderItem::getPricingRate)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(
                            new BigDecimal("0.8000"),
                            new BigDecimal("0.8000"),
                            new BigDecimal("1.0000"),
                            BigDecimal.ONE);

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderMapper).insert(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getTotalAmount()).isEqualByComparingTo("458.37");
            assertThat(orderCaptor.getValue().getPayAmount()).isEqualByComparingTo("458.37");
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void order_usesBatchSkuLookupForDuplicateLines() {
        UserContext.setLevelId(1L);
        try {
            when(addressMapper.selectById(1L)).thenReturn(address);
            when(logisticsMapper.selectById(1L)).thenReturn(logistics);
            when(skuService.getSkuMapByIds(any())).thenReturn(Map.of(5L, sku));
            when(spuMapper.selectBatchIds(any())).thenReturn(List.of(spu));
            when(skuService.deductStock(anyLong(), anyInt())).thenReturn(true);
            when(orderMapper.insert(any(Order.class))).thenAnswer(inv -> {
                inv.getArgument(0, Order.class).setId(100L);
                return 1;
            });
            CreateOrderRequest request = new CreateOrderRequest();
            request.setAddressId(1L);
            request.setLogisticsId(1L);
            request.setItems(List.of(line(5L, 1), line(5L, 2)));
            request.setExpectedPayAmount(new BigDecimal("657.00"));

            intakeService.createOrder(2L, request, "batch-key");

            verify(skuService, times(1)).getSkuMapByIds(any());
            verify(skuService, never()).getSkuById(any());
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<OrderItem>> captor = ArgumentCaptor.forClass(List.class);
            verify(orderItemMapper).insertBatch(captor.capture());
            assertThat(captor.getValue()).hasSize(2);
            assertThat(captor.getValue().get(0).getSkuId()).isEqualTo(5L);
            assertThat(captor.getValue().get(1).getSkuId()).isEqualTo(5L);
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void generateOrderNo_concurrent10000_allUnique() throws Exception {
        int n = 10_000;
        java.util.Set<String> nos = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(20);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            pool.submit(() -> {
                try {
                    nos.add(intakeService.generateOrderNo());
                } finally {
                    done.countDown();
                }
            });
        }
        done.await();
        pool.shutdown();

        assertThat(nos).hasSize(n);
        assertThat(intakeService.generateOrderNo()).startsWith("SO").hasSizeLessThan(32);
    }

    // ==================== helpers ====================

    private void stubSuccessfulOrderCreation() {
        when(addressMapper.selectById(1L)).thenReturn(address);
        when(logisticsMapper.selectById(1L)).thenReturn(logistics);
        when(skuService.getSkuMapByIds(any())).thenReturn(Map.of(5L, sku));
        when(spuMapper.selectBatchIds(any())).thenReturn(List.of(spu));
        when(skuService.deductStock(anyLong(), anyInt())).thenReturn(true);
        when(orderMapper.insert(any(Order.class))).thenAnswer(inv -> {
            inv.getArgument(0, Order.class).setId(100L);
            return 1;
        });
    }

    private void stubOrderVo(Long orderId) {
        OrderVO vo = new OrderVO();
        vo.setId(orderId);
        vo.setOrderNo("SO" + orderId);
        when(orderQueryService.getOrderView(2L, orderId)).thenReturn(vo);
    }

    private CreateOrderRequest singleLineRequest(int quantity, String expectedPayAmount) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId(1L);
        request.setLogisticsId(1L);
        request.setItems(List.of(line(5L, quantity)));
        request.setExpectedPayAmount(new BigDecimal(expectedPayAmount));
        return request;
    }

    private CheckoutPreviewRequest previewRequest(Long skuId, int quantity) {
        CheckoutPreviewRequest request = new CheckoutPreviewRequest();
        request.setItems(List.of(line(skuId, quantity)));
        return request;
    }

    private CreateOrderRequest stubValidRequest() {
        CreateOrderRequest r = new CreateOrderRequest();
        CreateOrderRequest.CartItem ci = new CreateOrderRequest.CartItem();
        ci.setSkuId(1L);
        ci.setQuantity(1);
        r.setItems(List.of(ci));
        r.setAddressId(1L);
        r.setLogisticsId(1L);
        return r;
    }

    private Sku sku(Long id, Long spuId, String price) {
        Sku result = new Sku();
        result.setId(id);
        result.setSpuId(spuId);
        result.setPrice(new BigDecimal(price));
        result.setStock(10);
        result.setStatus(1);
        return result;
    }

    private Spu spu(Long id, Long categoryId, String name, int excludeDiscount) {
        Spu result = new Spu();
        result.setId(id);
        result.setCategoryId(categoryId);
        result.setName(name);
        result.setExcludeDiscount(excludeDiscount);
        return result;
    }

    private CreateOrderRequest.CartItem line(Long skuId, int quantity) {
        CreateOrderRequest.CartItem line = new CreateOrderRequest.CartItem();
        line.setSkuId(skuId);
        line.setQuantity(quantity);
        return line;
    }
}
