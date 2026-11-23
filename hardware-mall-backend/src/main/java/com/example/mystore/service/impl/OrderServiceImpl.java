package com.example.mystore.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.constant.DiscountConstants;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.common.exception.IdempotencyConflictException;
import com.example.mystore.common.exception.PriceChangedException;
import com.example.mystore.entity.db.Order;
import com.example.mystore.entity.db.OrderIdempotency;
import com.example.mystore.entity.db.OrderItem;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.db.Address;
import com.example.mystore.entity.db.Logistics;
import com.example.mystore.entity.db.Cart;
import com.example.mystore.entity.db.PaymentRecord;
import com.example.mystore.entity.vo.OrderVO;
import com.example.mystore.entity.vo.CheckoutItemVO;
import com.example.mystore.entity.vo.CheckoutPreviewVO;
import com.example.mystore.entity.vo.DashboardStatsVO;
import com.example.mystore.entity.dto.LevelPricing;
import com.example.mystore.entity.dto.ProductPricingFacts;
import com.example.mystore.entity.vo.RecentOrderVO;
import com.example.mystore.entity.dto.CheckoutPreviewRequest;
import com.example.mystore.entity.dto.CreateOrderRequest;
import com.example.mystore.entity.vo.SpecVO;
import com.example.mystore.event.StockCacheEvent;
import com.example.mystore.mapper.*;
import com.example.mystore.service.LevelPricingService;
import com.example.mystore.service.OrderIdempotencyService;
import com.example.mystore.service.OrderLifecycleService;
import com.example.mystore.service.OrderQueryService;
import com.example.mystore.service.OrderService;
import com.example.mystore.service.PayService;
import com.example.mystore.service.RefundResult;
import com.example.mystore.service.SkuService;
import com.example.mystore.service.UserLevelService;
import com.example.mystore.util.MoneyUtil;
import com.example.mystore.util.OrderRequestFingerprint;
import com.example.mystore.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final SpuMapper spuMapper;
    private final AddressMapper addressMapper;
    private final LogisticsMapper logisticsMapper;
    private final CartMapper cartMapper;
    private final SkuService skuService;
    @Autowired(required = false)
    private PayService payService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final PlatformTransactionManager transactionManager;
    private final LevelPricingService levelPricingService;
    private final UserLevelService userLevelService;
    private final OrderIdempotencyService orderIdempotencyService;
    private final OrderLifecycleService orderLifecycleService;
    private final OrderQueryService orderQueryService;

    @Override
    public CheckoutPreviewVO previewOrder(CheckoutPreviewRequest request) {
        SettlementSnapshot snapshot = buildSettlementSnapshot(request.getItems());
        return toCheckoutPreview(snapshot);
    }

    @Override
    public OrderVO createOrder(Long userId, CreateOrderRequest request, String idempotencyKey) {
        log.info("创建订单开始, userId={}, idemKey={}, request={}", userId, idempotencyKey, request);

        String idemKey = requireIdempotencyKey(idempotencyKey);
        String requestHash = OrderRequestFingerprint.fingerprint(request);
        OrderIdempotencyService.ClaimOutcome claim =
                orderIdempotencyService.claim(userId, idemKey, requestHash);
        switch (claim.kind()) {
            case SUCCEEDED -> {
                // 订单已提交但上次 VO 组装失败时，同 key 同指纹重试直接返回原订单
                return orderQueryService.getOrderView(claim.orderId());
            }
            case PROCESSING -> throw new BusinessException("订单处理中，请稍后重试");
            case CONFLICT -> throw new IdempotencyConflictException();
            case ACQUIRED -> {
                // 继续创建订单
            }
        }

        Long orderId;
        try {
            // ① 事务外：只读校验 + 构建订单明细快照（不占用 DB 连接事务）
            Address address = requireAddress(userId, request.getAddressId());
            Logistics logistics = requireLogistics(request.getLogisticsId());
            SettlementSnapshot settlement = buildSettlementSnapshot(request.getItems());
            requireConfirmedAmount(request.getExpectedPayAmount(), settlement);

            // ② 事务内：扣减库存 + 建单 + 明细 + 删购物车 + 幂等记录置成功 + 发布事件
            orderId = new TransactionTemplate(transactionManager)
                    .execute(status -> persistOrder(
                            userId, address.getId(), logistics.getId(),
                            request.getBuyerRemark(), settlement, claim));
        } catch (RuntimeException e) {
            // 提交前失败：独立事务置失败，允许同一幂等键立即重试
            orderIdempotencyService.markFailed(claim.recordId(), claim.ownerToken());
            orderIdempotencyService.releaseLock(
                    userId, idemKey, claim.ownerToken(), claim.lockAcquired());
            throw e;
        }

        orderIdempotencyService.releaseLock(userId, idemKey, claim.ownerToken(), claim.lockAcquired());
        // ③ 事务外：组装返回 VO；VO 失败不得把已成功的幂等记录置失败或删除
        return orderQueryService.getOrderView(orderId);
    }

    private String requireIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException("缺少幂等键，请刷新后重试");
        }
        String idemKey = idempotencyKey.trim();
        if (idemKey.length() > OrderIdempotency.IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new BusinessException("幂等键过长，请刷新后重试");
        }
        return idemKey;
    }

    private Address requireAddress(Long userId, Long addressId) {
        Address address = addressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BusinessException("收货地址不存在");
        }
        log.info("地址验证通过, addressId={}", address.getId());
        return address;
    }

    private Logistics requireLogistics(Long logisticsId) {
        Logistics logistics = logisticsMapper.selectById(logisticsId);
        log.info("物流查询结果, logistics={}", logistics);
        if (logistics == null || logistics.getStatus() != StatusConstants.LOGISTICS_STATUS_ENABLED) {
            throw new BusinessException("物流方式不存在或不可用");
        }
        return logistics;
    }

    private SettlementSnapshot buildSettlementSnapshot(List<CreateOrderRequest.CartItem> requestedItems) {
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new BusinessException("结算商品不能为空");
        }
        // ① 逐项校验 SKU 并收集（校验顺序与异常语义保持不变）
        List<Long> requestedSkuIds = requestedItems.stream()
                .map(CreateOrderRequest.CartItem::getSkuId)
                .distinct()
                .toList();
        Map<Long, Sku> skuMap = skuService.getSkuMapByIds(requestedSkuIds);
        List<Sku> skus = new ArrayList<>();
        for (CreateOrderRequest.CartItem cartItem : requestedItems) {
            Sku sku = skuMap.get(cartItem.getSkuId());
            log.info("SKU查询结果, skuId={}, sku={}", cartItem.getSkuId(), sku);
            if (sku == null || sku.getStatus() != 1) {
                throw new BusinessException("商品不存在或已下架: " + cartItem.getSkuId());
            }
            // 【库存软检查】getSkuById 已返回新鲜库存(读 sku:stock 缓存, miss 回源 DB)
            if (sku.getStock() == null || sku.getStock() < cartItem.getQuantity()) {
                throw new BusinessException("库存不足: " + sku.getId());
            }
            skus.add(sku);
        }

        // ② 一次性批量查询 SPU（N 次 selectById → 1 次 IN 查询，distinct 去重）
        List<Long> spuIds = skus.stream().map(Sku::getSpuId).distinct().toList();
        Map<Long, Spu> spuMap = spuIds.isEmpty() ? Map.of()
                : spuMapper.selectBatchIds(spuIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Spu::getId, spu -> spu));

        // ③ 一次批量报价：订单预览与创建共用同一份等级定价结果
        List<Spu> spus = new ArrayList<>(requestedItems.size());
        List<ProductPricingFacts> facts = new ArrayList<>(requestedItems.size());
        for (int i = 0; i < requestedItems.size(); i++) {
            CreateOrderRequest.CartItem cartItem = requestedItems.get(i);
            Sku sku = skus.get(i);
            Spu spu = spuMap.get(sku.getSpuId());
            if (spu == null) {
                throw new BusinessException("商品不存在或已下架: " + cartItem.getSkuId());
            }
            spus.add(spu);
            facts.add(ProductPricingFacts.of(
                    spu.getCategoryId(),
                    sku.getPrice(),
                    DiscountConstants.isExcluded(spu.getExcludeDiscount())));
        }
        List<LevelPricing> pricings = levelPricingService.priceAll(UserContext.getLevelId(), facts);
        for (int i = 0; i < pricings.size(); i++) {
            if (!pricings.get(i).sellable()) {
                throw new BusinessException(
                        "商品暂不可售: " + spus.get(i).getName() + "（SKU " + skus.get(i).getId() + "）");
            }
        }
        Map<Long, String> levelNames = userLevelService.getNames(
                pricings.stream().map(LevelPricing::levelId).toList());

        // ④ 组装订单明细快照：等级 ID、名称、倍率与单价都取自同一报价结果
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal goodsAmount = BigDecimal.ZERO;
        for (int i = 0; i < requestedItems.size(); i++) {
            CreateOrderRequest.CartItem cartItem = requestedItems.get(i);
            Sku sku = skus.get(i);
            Spu spu = spus.get(i);
            LevelPricing pricing = pricings.get(i);
            // 订单事实以单个 SKU 批发基价构建，报价的 min/max 必为同一等级结算价
            BigDecimal unitPrice = pricing.minPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            goodsAmount = goodsAmount.add(subtotal);

            OrderItem item = new OrderItem();
            item.setSkuId(sku.getId());
            item.setSpuId(sku.getSpuId());
            item.setProductName(spu.getName());

            List<SpecVO> specVOList = sku.getSpecs();

            StringBuilder specStr = new StringBuilder();
            if (specVOList != null) {
                for (SpecVO specVO : specVOList) {
                    if (specVO.getValue() != null) {
                        specStr.append(specVO.getValue()).append(" ");
                    }
                }
            }

            item.setProductSpec(specStr.length() > 0 ? specStr.toString().trim() : "");

            item.setProductImage(sku.getImage() != null ? sku.getImage() : getFirstImage(spu.getImages()));
            item.setPrice(unitPrice);
            item.setLevelId(pricing.levelId());
            item.setLevelName(levelNames.get(pricing.levelId()));
            item.setPricingRate(pricing.rate());
            item.setQuantity(cartItem.getQuantity());
            item.setSubtotal(subtotal);
            item.setCreateTime(LocalDateTime.now());
            orderItems.add(item);
        }

        BigDecimal freightAmount = BigDecimal.ZERO.setScale(2);
        BigDecimal payAmount = goodsAmount.add(freightAmount);
        requirePayableAmount(payAmount);
        return new SettlementSnapshot(
                orderItems, goodsAmount.setScale(2), freightAmount, payAmount.setScale(2));
    }

    /**
     * 结算金额下限：低于 1 分（含取整为 0 元）时直接拒绝下单，
     * 不创建订单与支付记录，避免跳到支付页后才被微信网关拒绝。
     */
    private void requirePayableAmount(BigDecimal payAmount) {
        long cents;
        try {
            cents = MoneyUtil.yuanToCents(payAmount);
        } catch (BusinessException e) {
            throw new BusinessException("订单金额非法，无法下单：" + e.getMessage());
        }
        if (cents < MoneyUtil.MIN_PAYABLE_CENTS) {
            throw new BusinessException("订单金额低于 0.01 元，无法下单");
        }
    }

    private void requireConfirmedAmount(BigDecimal expectedPayAmount, SettlementSnapshot settlement) {
        if (expectedPayAmount == null) {
            throw new BusinessException("请先确认应付金额");
        }
        if (expectedPayAmount.compareTo(settlement.payAmount()) != 0) {
            throw new PriceChangedException(expectedPayAmount, toCheckoutPreview(settlement));
        }
    }

    private CheckoutPreviewVO toCheckoutPreview(SettlementSnapshot settlement) {
        CheckoutPreviewVO preview = new CheckoutPreviewVO();
        preview.setItems(settlement.items().stream().map(this::toCheckoutItem).toList());
        preview.setGoodsAmount(settlement.goodsAmount());
        preview.setFreightAmount(settlement.freightAmount());
        preview.setPayAmount(settlement.payAmount());
        return preview;
    }

    private CheckoutItemVO toCheckoutItem(OrderItem item) {
        CheckoutItemVO vo = new CheckoutItemVO();
        vo.setSkuId(item.getSkuId());
        vo.setProductId(item.getSpuId());
        vo.setProductName(item.getProductName());
        vo.setProductImage(item.getProductImage());
        vo.setSpec(item.getProductSpec());
        vo.setPrice(item.getPrice());
        vo.setQuantity(item.getQuantity());
        vo.setSubtotal(item.getSubtotal());
        return vo;
    }

    private Long persistOrder(
            Long userId, Long addressId, Long logisticsId,
            String buyerRemark, SettlementSnapshot settlement,
            OrderIdempotencyService.ClaimOutcome idempotencyClaim) {
        // 【事务内】只扣 DB 库存 + 销量自增
        for (OrderItem item : settlement.items()) {
            boolean deducted = skuService.deductStock(item.getSkuId(), item.getQuantity());
            if (!deducted) {
                throw new BusinessException("库存扣减失败: " + item.getSkuId());
            }
            spuMapper.incrementSalesCount(item.getSpuId(), item.getQuantity());
        }

        String orderNo = generateOrderNo();
        log.info("生成订单号: {}", orderNo);

        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setAddressId(addressId);
        order.setLogisticsId(logisticsId);
        order.setStatus(StatusConstants.ORDER_PENDING_PAYMENT);
        order.setTotalAmount(settlement.goodsAmount());
        order.setFreightAmount(settlement.freightAmount());
        order.setPayAmount(settlement.payAmount());
        order.setBuyerRemark(buyerRemark);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.insert(order);
        log.info("订单插入成功, orderId={}", order.getId());

        for (OrderItem item : settlement.items()) {
            item.setOrderId(order.getId());
        }
        orderItemMapper.insertBatch(settlement.items());
        log.info("订单明细插入成功");

        List<Long> cartSkuIds = settlement.items().stream()
                .map(OrderItem::getSkuId)
                .collect(java.util.stream.Collectors.toList());
        LambdaUpdateWrapper<Cart> cartWrapper = new LambdaUpdateWrapper<>();
        cartWrapper.eq(Cart::getUserId, userId)
                   .in(Cart::getSkuId, cartSkuIds)
                   .set(Cart::getDeleteTime, System.currentTimeMillis());
        cartMapper.update(null, cartWrapper);
        log.info("删除购物车商品, userId={}, skuIds={}", userId, cartSkuIds);

        // 幂等记录置成功必须与订单创建同事务；更新失败会回滚订单
        orderIdempotencyService.markSucceeded(
                idempotencyClaim.recordId(), idempotencyClaim.ownerToken(), order.getId());

        // 发布库存同步事件，事务提交后由监听器异步执行
        List<Long> skuIds = settlement.items().stream()
                .map(OrderItem::getSkuId)
                .collect(java.util.stream.Collectors.toList());
        applicationEventPublisher.publishEvent(new StockCacheEvent(skuIds));

        return order.getId();
    }

    private record SettlementSnapshot(
            List<OrderItem> items,
            BigDecimal goodsAmount,
            BigDecimal freightAmount,
            BigDecimal payAmount) {
    }

    @Override
    public Page<OrderVO> getOrderPage(Long userId, Integer status, Integer page, Integer limit) {
        return orderQueryService.getOrderPage(userId, status, page, limit);
    }

    @Override
    public Page<OrderVO> getAdminOrderPage(Long userId, Integer status, String orderNo, String startDate, String endDate, Integer page, Integer limit) {
        return orderQueryService.getAdminOrderPage(userId, status, orderNo, startDate, endDate, page, limit);
    }

    @Override
    public OrderVO getOrderById(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }

        // 打开待付款订单详情时触发支付状态同步（去抖、修复与查单由支付模块负责）
        // 同步异常不影响详情返回
        if (order.getStatus() == StatusConstants.ORDER_PENDING_PAYMENT && payService != null) {
            try {
                payService.syncPaymentStatus(orderId);
            } catch (Exception e) {
                log.warn("⑦ lazy sync 异常, 不影响详情返回, orderId={}: {}", orderId, e.getMessage());
            }
        }

        return orderQueryService.getOrderView(orderId);
    }

    @Override
    public OrderVO getOrderByOrderNo(String orderNo) {
        return orderQueryService.getOrderByOrderNo(orderNo);
    }

    @Override
    @Transactional
    public void cancelOrder(Long userId, Long orderId, String reason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != StatusConstants.ORDER_PENDING_PAYMENT) {
            throw new BusinessException("只能取消待付款的订单");
        }

        // 先恢复库存
        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(OrderItem::getOrderId, orderId);
        List<OrderItem> items = orderItemMapper.selectList(itemWrapper);
        for (OrderItem item : items) {
            skuService.restoreStock(item.getSkuId(), item.getQuantity());
        }

        // CAS 最终确认：仅当状态仍为待付款时置为已取消，防与并发支付回调(1→2)互相覆盖
        int affected = orderMapper.update(null,
                new LambdaUpdateWrapper<Order>()
                        .eq(Order::getId, orderId)
                        .eq(Order::getStatus, StatusConstants.ORDER_PENDING_PAYMENT)
                        .set(Order::getStatus, StatusConstants.ORDER_CANCELLED)
                        .set(Order::getCancelReason, reason)
                        .set(Order::getCancelTime, LocalDateTime.now())
                        .set(Order::getUpdateTime, LocalDateTime.now()));
        if (affected == 0) {
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }

        // 发布库存同步事件，事务提交后由监听器异步执行
        List<Long> skuIds = items.stream()
                .map(OrderItem::getSkuId)
                .collect(java.util.stream.Collectors.toList());
        applicationEventPublisher.publishEvent(new StockCacheEvent(skuIds));
    }

    @Override
    public void confirmReceive(Long userId, Long orderId) {
        orderLifecycleService.confirmReceive(userId, orderId);
    }

    @Override
    public void confirmReceiveByWechat(Long userId, Long orderId) {
        orderLifecycleService.confirmReceiveByWechat(userId, orderId);
    }

    @Override
    public void deleteOrder(Long userId, Long orderId) {
        orderLifecycleService.deleteOrder(userId, orderId);
    }

    @Override
    public Map<String, Object> getOrderStats() {
        return orderQueryService.getOrderStats();
    }

    @Override
    public DashboardStatsVO getDashboardStats() {
        return orderQueryService.getDashboardStats();
    }

    @Override
    public List<RecentOrderVO> getRecentOrders(Integer limit) {
        return orderQueryService.getRecentOrders(limit);
    }

    @Override
    public void shipOrder(Long orderId, Integer deliveryType, Long logisticsId) {
        orderLifecycleService.shipOrder(orderId, deliveryType, logisticsId);
    }

    @Override
    public void refundOrder(Long orderId, String reason) {
        // null 守卫必须在 CAS claim 之前: claim 是单条 UPDATE 自动提交, 事后报错回滚不了"退款中"状态
        if (payService == null) {
            throw new BusinessException("支付服务未启用，无法退款");
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 已取消订单的迟到支付: 只校验最新支付记录已支付后直接退款, 订单保持 5
        if (order.getStatus() == StatusConstants.ORDER_CANCELLED) {
            retryCancelledOrderRefund(orderId, reason);
            return;
        }
        // 预检: 明显错状态直接报错 (CAS 才是权威并发守卫, 预检仅为友好提示)
        // 8(退款申请中) 视为管理员审核通过; 9(退款失败) 允许管理员重试: 沿用同一 CAS 占位链路 →6→微信退款→回调→7
        if (order.getStatus() != StatusConstants.ORDER_PENDING_SHIPMENT
                && order.getStatus() != StatusConstants.ORDER_SHIPPED
                && order.getStatus() != StatusConstants.ORDER_REFUND_REQUESTED
                && order.getStatus() != StatusConstants.ORDER_REFUND_FAILED) {
            throw new BusinessException("该订单状态不支持退款");
        }

        // claim-first: 先 CAS 占位 6(退款中), 命中 0 行说明状态被并发改走, 中止 (不退款不还库存)
        // 此处不加 @Transactional: claim 是本方法唯一 DB 写(库存恢复已迁至退款回调), 单条 UPDATE 自动提交;
        // 事务外的微信退款调用失败时 claim 已提交, status 保持 6 等回调自愈或人工对账
        int affected = orderMapper.update(null,
                new LambdaUpdateWrapper<Order>()
                        .eq(Order::getId, orderId)
                        .in(Order::getStatus, StatusConstants.ORDER_PENDING_SHIPMENT,
                                StatusConstants.ORDER_SHIPPED, StatusConstants.ORDER_REFUND_REQUESTED,
                                StatusConstants.ORDER_REFUND_FAILED)
                        .set(Order::getStatus, StatusConstants.ORDER_REFUNDING)
                        .set(Order::getCancelReason, reason)
                        .set(Order::getCancelTime, LocalDateTime.now())
                        .set(Order::getUpdateTime, LocalDateTime.now()));
        if (affected == 0) {
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }
        log.info("订单置退款中, orderId={}, 等待微信退款回调确认", orderId);

        handleRefundResult(orderId, payService.refund(orderId, reason));
    }

    private void retryCancelledOrderRefund(Long orderId, String reason) {
        PaymentRecord latest = payService.queryByOrderId(orderId);
        if (latest == null || latest.getStatus() != PaymentRecord.STATUS_PAID) {
            throw new BusinessException("已取消订单的最新支付记录不是已支付状态，无法重试退款");
        }
        RefundResult result = payService.refund(orderId, reason);
        log.info("已取消订单重试退款, orderId={}, result={}", orderId, result);
        handleRefundResult(orderId, result);
    }

    private void handleRefundResult(Long orderId, RefundResult result) {
        if (result == RefundResult.DEFINITE_FAILED) {
            throw new BusinessException("退款确定失败：微信明确拒绝，支付记录已回退，请稍后重试");
        }
        if (result == RefundResult.UNKNOWN) {
            log.warn("退款结果未知, 等待退款回调或对账确认, orderId={}", orderId);
        }
    }

    @Override
    public void applyRefund(Long userId, Long orderId, String reason) {
        orderLifecycleService.applyRefund(userId, orderId, reason);
    }

    @Override
    public void rejectRefund(Long orderId, String rejectReason) {
        orderLifecycleService.rejectRefund(orderId, rejectReason);
    }

    @Override
    public OrderVO getAdminOrderById(Long orderId) {
        return orderQueryService.getAdminOrderById(orderId);
    }

    @Override
    @Transactional
    public boolean autoCancelOrder(Long orderId, String cancelReason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("订单不存在, orderId={}", orderId);
            return false;
        }
        // 幂等性检查：只有待付款状态的订单才需要取消
        if (order.getStatus() != StatusConstants.ORDER_PENDING_PAYMENT) {
            log.info("订单已处理，无需取消, orderId={}, status={}", orderId, order.getStatus());
            return false;
        }

        // 先恢复库存
        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(OrderItem::getOrderId, orderId);
        List<OrderItem> items = orderItemMapper.selectList(itemWrapper);
        for (OrderItem item : items) {
            skuService.restoreStock(item.getSkuId(), item.getQuantity());
            log.info("恢复DB库存, orderId={}, skuId={}, quantity={}", orderId, item.getSkuId(), item.getQuantity());
        }

        // CAS 最终确认：仅当状态仍为待付款时置为已取消，防与并发支付回调(1→2)互相覆盖
        int affected = orderMapper.update(null,
                new LambdaUpdateWrapper<Order>()
                        .eq(Order::getId, orderId)
                        .eq(Order::getStatus, StatusConstants.ORDER_PENDING_PAYMENT)
                        .set(Order::getStatus, StatusConstants.ORDER_CANCELLED)
                        .set(Order::getCancelReason, cancelReason)
                        .set(Order::getCancelTime, LocalDateTime.now())
                        .set(Order::getUpdateTime, LocalDateTime.now()));
        if (affected == 0) {
            // 抛异常触发事务回滚, 撤销上方已执行的恢复库存; 对齐 cancelOrder 的 CAS-失败-回滚模式
            log.info("自动取消跳过（CAS 未命中，订单状态已变更）, orderId={}", orderId);
            throw new BusinessException("订单状态已变更，跳过取消");
        }

        log.info("订单自动取消成功, orderId={}", orderId);

        // 发布库存同步事件，事务提交后由监听器异步执行
        List<Long> skuIds = items.stream()
                .map(OrderItem::getSkuId)
                .collect(java.util.stream.Collectors.toList());
        applicationEventPublisher.publishEvent(new StockCacheEvent(skuIds));

        return true;
    }

    @Override
    public boolean autoConfirmReceive(Long orderId) {
        return orderLifecycleService.autoConfirmReceive(orderId);
    }

    String generateOrderNo() {
        return "SO" + IdWorker.getIdStr();
    }

    private String getFirstImage(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.get(0);
    }

    //    private List<SpecVO> toSpecVOList(Object specs) {
//        if (specs == null) return null;
//        List<SpecVO> result = new ArrayList<>();
//        for (Object item : (List<?>) specs) {
//            if (item instanceof SpecVO) {
//                result.add((SpecVO) item);
//            } else if (item instanceof Map) {
//                Map<?, ?> map = (Map<?, ?>) item;
//                SpecVO vo = new SpecVO();
//                vo.setTemplateId(toLong(map.get("templateId")));
//                vo.setItemId(toLong(map.get("itemId")));
//                vo.setName((String) map.get("name"));
//                vo.setValue((String) map.get("value"));
//                result.add(vo);
//            }
//        }
//        return result;
//    }
//
//    private Long toLong(Object value) {
//        if (value == null) return null;
//        if (value instanceof Long) return (Long) value;
//        if (value instanceof Number) return ((Number) value).longValue();
//        return Long.parseLong(value.toString());
//    }
}
