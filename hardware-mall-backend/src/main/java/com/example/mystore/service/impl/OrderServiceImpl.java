package com.example.mystore.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.entity.db.Order;
import com.example.mystore.entity.db.OrderItem;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.db.Address;
import com.example.mystore.entity.db.Logistics;
import com.example.mystore.entity.db.Cart;
import com.example.mystore.entity.db.PaymentRecord;
import com.example.mystore.entity.vo.OrderVO;
import com.example.mystore.entity.vo.DashboardStatsVO;
import com.example.mystore.entity.vo.RecentOrderVO;
import com.example.mystore.entity.dto.CreateOrderRequest;
import com.example.mystore.entity.vo.SpecVO;
import com.example.mystore.event.StockSyncEvent;
import com.example.mystore.mapper.*;
import com.example.mystore.service.CartService;
import com.example.mystore.service.OrderService;
import com.example.mystore.service.PayService;
import com.example.mystore.service.SkuService;
import com.example.mystore.util.RedisUtil;
import com.wechat.pay.java.service.payments.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final SkuMapper skuMapper;
    private final SpuMapper spuMapper;
    private final AddressMapper addressMapper;
    private final LogisticsMapper logisticsMapper;
    private final CartMapper cartMapper;
    private final CartService cartService;
    private final SkuService skuService;
    @Autowired(required = false)
    private PayService payService;
    private final RedisUtil redisUtil;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final PlatformTransactionManager transactionManager;

    @Override
    public OrderVO createOrder(Long userId, CreateOrderRequest request, String idempotencyKey) {
        log.info("创建订单开始, userId={}, idemKey={}, request={}", userId, idempotencyKey, request);

        if (idempotencyKey == null || !redisUtil.setIfAbsent(
                RedisConstants.PREFIX_ORDER_IDEMPOTENCY + idempotencyKey,
                userId, RedisConstants.IDEMPOTENCY_TTL, TimeUnit.SECONDS)) {
            throw new RuntimeException("请勿重复下单");
        }

        // ① 事务外：只读校验 + 构建订单明细快照（不占用 DB 连接事务）
        ValidatedOrder validated = validateAndBuildSnapshot(userId, request);

        // ② 事务内：扣减库存 + 建单 + 明细 + 删购物车 + 发布事件
        Long orderId = new TransactionTemplate(transactionManager)
                .execute(status -> persistOrder(userId, request, validated));

        // ③ 事务外：组装返回 VO
        return getOrderVO(orderId, userId);
    }

    private ValidatedOrder validateAndBuildSnapshot(Long userId, CreateOrderRequest request) {
        Address address = addressMapper.selectById(request.getAddressId());
        if (address == null || !address.getUserId().equals(userId)) {
            throw new RuntimeException("收货地址不存在");
        }
        log.info("地址验证通过, addressId={}", address.getId());

        Logistics logistics = logisticsMapper.selectById(request.getLogisticsId());
        log.info("物流查询结果, logistics={}", logistics);
        if (logistics == null || logistics.getStatus() != StatusConstants.LOGISTICS_STATUS_ENABLED) {
            throw new RuntimeException("物流方式不存在或不可用");
        }

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CreateOrderRequest.CartItem cartItem : request.getItems()) {
            Sku sku = skuService.getSkuById(cartItem.getSkuId());
            log.info("SKU查询结果, skuId={}, sku={}", cartItem.getSkuId(), sku);
            if (sku == null || sku.getStatus() != 1) {
                throw new RuntimeException("商品不存在或已下架: " + cartItem.getSkuId());
            }

            // 【库存软检查】getSkuById 已返回新鲜库存(读 sku:stock 缓存, miss 回源 DB)
            if (sku.getStock() == null || sku.getStock() < cartItem.getQuantity()) {
                throw new RuntimeException("库存不足: " + sku.getId());
            }

            Spu spu = spuMapper.selectById(sku.getSpuId());
            BigDecimal subtotal = sku.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            totalAmount = totalAmount.add(subtotal);

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
            item.setPrice(sku.getPrice());
            item.setQuantity(cartItem.getQuantity());
            item.setSubtotal(subtotal);
            item.setCreateTime(LocalDateTime.now());
            orderItems.add(item);
        }

        return new ValidatedOrder(address, logistics, orderItems, totalAmount);
    }

    private Long persistOrder(Long userId, CreateOrderRequest request, ValidatedOrder validated) {
        // 【事务内】只扣 DB 库存 + 销量自增
        for (OrderItem item : validated.orderItems()) {
            boolean deducted = skuService.deductStock(item.getSkuId(), item.getQuantity());
            if (!deducted) {
                throw new RuntimeException("库存扣减失败: " + item.getSkuId());
            }
            spuMapper.incrementSalesCount(item.getSpuId(), item.getQuantity());
        }

        String orderNo = generateOrderNo();
        log.info("生成订单号: {}", orderNo);

        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setAddressId(validated.address().getId());
        order.setLogisticsId(validated.logistics().getId());
        order.setStatus(StatusConstants.ORDER_PENDING_PAYMENT);
        order.setTotalAmount(validated.totalAmount());
        order.setFreightAmount(BigDecimal.ZERO);
        order.setPayAmount(validated.totalAmount());
        order.setBuyerRemark(request.getBuyerRemark());
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.insert(order);
        log.info("订单插入成功, orderId={}", order.getId());

        for (OrderItem item : validated.orderItems()) {
            item.setOrderId(order.getId());
        }
        orderItemMapper.insertBatch(validated.orderItems());
        log.info("订单明细插入成功");

        List<Long> cartSkuIds = request.getItems().stream()
                .map(CreateOrderRequest.CartItem::getSkuId)
                .collect(java.util.stream.Collectors.toList());
        LambdaUpdateWrapper<Cart> cartWrapper = new LambdaUpdateWrapper<>();
        cartWrapper.eq(Cart::getUserId, userId)
                   .in(Cart::getSkuId, cartSkuIds)
                   .set(Cart::getDeleteTime, System.currentTimeMillis());
        cartMapper.update(null, cartWrapper);
        log.info("删除购物车商品, userId={}, skuIds={}", userId, cartSkuIds);

        // 发布库存同步事件，事务提交后由监听器异步执行
        List<Long> skuIds = validated.orderItems().stream()
                .map(OrderItem::getSkuId)
                .collect(java.util.stream.Collectors.toList());
        applicationEventPublisher.publishEvent(new StockSyncEvent(skuIds));

        return order.getId();
    }

    private record ValidatedOrder(Address address, Logistics logistics, List<OrderItem> orderItems, BigDecimal totalAmount) {}

    @Override
    public Page<OrderVO> getOrderPage(Long userId, Integer status, Integer page, Integer limit) {
        Page<Order> pageParam = new Page<>(page, limit);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.isNull(Order::getUserDeleteTime).or().eq(Order::getUserDeleteTime, 0));
        if (userId != null) {
            wrapper.eq(Order::getUserId, userId);
        }

        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }
        wrapper.orderByDesc(Order::getCreateTime);
        Page<Order> orderPage = orderMapper.selectPage(pageParam, wrapper);

        Page<OrderVO> voPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        List<OrderVO> voList = new ArrayList<>();
        for (Order order : orderPage.getRecords()) {
            voList.add(getOrderVO(order.getId(), userId));
        }
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public Page<OrderVO> getAdminOrderPage(Long userId, Integer status, Integer page, Integer limit) {
        Page<Order> pageParam = new Page<>(page, limit);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.isNull(Order::getAdminDeleteTime).or().eq(Order::getAdminDeleteTime, 0));
        if (userId != null) {
            wrapper.eq(Order::getUserId, userId);
        }

        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }
        wrapper.orderByDesc(Order::getCreateTime);
        Page<Order> orderPage = orderMapper.selectPage(pageParam, wrapper);

        Page<OrderVO> voPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        List<OrderVO> voList = new ArrayList<>();
        for (Order order : orderPage.getRecords()) {
            voList.add(getOrderVO(order.getId(), userId));
        }
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public OrderVO getOrderById(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new RuntimeException("订单不存在");
        }

        // ⑦ lazy sync: 用户打开待付款订单详情时自动核查支付状态, 兜底回调丢失
        // O3 Redis 节流: 同一订单 30s 内最多查一次微信, 防刷 API 限频
        if (order.getStatus() == StatusConstants.ORDER_PENDING_PAYMENT && payService != null) {
            try {
                boolean debounced = redisUtil.setIfAbsent("lazysync:order:" + orderId, "1", 30, TimeUnit.SECONDS);
                if (debounced) {
                    PaymentRecord record = payService.queryByOrderId(orderId);
                    if (record != null && record.getStatus() == PaymentRecord.STATUS_PAID) {
                        // DB 不一致修复: payment_record 已 PAID 但 order 还是待付款 → 直接推进
                        orderMapper.update(null,
                                new LambdaUpdateWrapper<Order>()
                                        .eq(Order::getId, orderId)
                                        .eq(Order::getStatus, StatusConstants.ORDER_PENDING_PAYMENT)
                                        .set(Order::getStatus, StatusConstants.ORDER_PENDING_SHIPMENT)
                                        .set(Order::getUpdateTime, LocalDateTime.now()));
                        log.info("⑦ lazy sync DB不一致修复, orderId={}", orderId);
                    } else if (record != null && record.getStatus() == PaymentRecord.STATUS_PENDING) {
                        Transaction txn = payService.queryWechatOrder(record.getOutTradeNo());
                        if (Transaction.TradeStateEnum.SUCCESS.equals(txn.getTradeState())) {
                            payService.processPaymentSuccess(record.getOutTradeNo(), txn.getTransactionId());
                            log.info("⑦ lazy sync 微信查单补单成功, orderId={}", orderId);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("⑦ lazy sync 异常, 不影响详情返回, orderId={}: {}", orderId, e.getMessage());
            }
        }

        return getOrderVO(orderId, userId);
    }

    @Override
    public OrderVO getOrderByOrderNo(String orderNo) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderNo, orderNo);
        Order order = orderMapper.selectOne(wrapper);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        return getOrderVO(order.getId(), order.getUserId());
    }

    @Override
    @Transactional
    public void cancelOrder(Long userId, Long orderId, String reason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getStatus() != StatusConstants.ORDER_PENDING_PAYMENT) {
            throw new RuntimeException("只能取消待付款的订单");
        }

        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(OrderItem::getOrderId, orderId);
        List<OrderItem> items = orderItemMapper.selectList(itemWrapper);
        for (OrderItem item : items) {
            skuService.restoreStock(item.getSkuId(), item.getQuantity());
        }

        order.setStatus(StatusConstants.ORDER_CANCELLED);
        order.setCancelReason(reason);
        order.setCancelTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);

        // 发布库存同步事件，事务提交后由监听器异步执行
        List<Long> skuIds = items.stream()
                .map(OrderItem::getSkuId)
                .collect(java.util.stream.Collectors.toList());
        applicationEventPublisher.publishEvent(new StockSyncEvent(skuIds));
    }

    @Override
    @Transactional
    public void confirmReceive(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getStatus() != StatusConstants.ORDER_SHIPPED) {
            throw new RuntimeException("只能确认收货已发货的订单");
        }

        order.setStatus(StatusConstants.ORDER_COMPLETED);
        order.setReceiveTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    @Override
    public void deleteOrder(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getStatus() != StatusConstants.ORDER_COMPLETED
            && order.getStatus() != StatusConstants.ORDER_CANCELLED) {
            throw new RuntimeException("该订单不可删除");
        }

        order.setUserDeleteTime(System.currentTimeMillis());
        orderMapper.updateById(order);
    }

    @Override
    public Map<String, Object> getOrderStats() {
        Map<String, Object> stats = new HashMap<>();

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getStatus, StatusConstants.ORDER_PENDING_PAYMENT);
        long pendingPay = orderMapper.selectCount(wrapper);

        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getStatus, StatusConstants.ORDER_PENDING_SHIPMENT);
        long pendingShip = orderMapper.selectCount(wrapper);

        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getStatus, StatusConstants.ORDER_SHIPPED);
        long shipped = orderMapper.selectCount(wrapper);

        stats.put("pendingPay", pendingPay);
        stats.put("pendingShip", pendingShip);
        stats.put("shipped", shipped);

        return stats;
    }

    @Override
    public DashboardStatsVO getDashboardStats() {
        DashboardStatsVO stats = new DashboardStatsVO();

        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);

        stats.setTodayOrders(orderMapper.countTodayPaidOrders(todayStart));
        stats.setTodaySales(orderMapper.sumTodaySales(todayStart));

        LambdaQueryWrapper<Order> shipWrapper = new LambdaQueryWrapper<>();
        shipWrapper.eq(Order::getStatus, StatusConstants.ORDER_PENDING_SHIPMENT);
        stats.setPendingShip(orderMapper.selectCount(shipWrapper));

        return stats;
    }

    @Override
    public List<RecentOrderVO> getRecentOrders(Integer limit) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Order::getCreateTime);
        wrapper.last("LIMIT " + limit);
        List<Order> orders = orderMapper.selectList(wrapper);

        List<RecentOrderVO> result = new ArrayList<>();
        for (Order order : orders) {
            RecentOrderVO vo = new RecentOrderVO();
            vo.setId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setUserId(order.getUserId());
            vo.setTotalAmount(order.getTotalAmount());
            vo.setStatus(order.getStatus());
            vo.setStatusText(getStatusText(order.getStatus()));
            vo.setCreateTime(order.getCreateTime());
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional
    public void shipOrder(Long orderId, String logisticsNo) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getStatus() != StatusConstants.ORDER_PENDING_SHIPMENT) {
            throw new RuntimeException("只能发货待发货的订单");
        }

        order.setStatus(StatusConstants.ORDER_SHIPPED);
        order.setLogisticsNo(logisticsNo);
        order.setShipTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    @Override
    @Transactional
    public void refundOrder(Long orderId, String reason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getStatus() != StatusConstants.ORDER_PENDING_SHIPMENT && order.getStatus() != StatusConstants.ORDER_SHIPPED) {
            throw new RuntimeException("该订单状态不支持退款");
        }

        if (payService != null) {
            payService.refund(orderId, reason);
        }

        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(OrderItem::getOrderId, orderId);
        List<OrderItem> items = orderItemMapper.selectList(itemWrapper);
        for (OrderItem item : items) {
            skuService.restoreStock(item.getSkuId(), item.getQuantity());
        }

        // 退款是异步: payService.refund() 已受理, 此处置 6(退款中) 等 PayCallback 回调确认成功再置 7(已退款)
        // 用条件 update 防并发, WHERE id=? AND status IN (2,3)
        orderMapper.update(null,
                new LambdaUpdateWrapper<Order>()
                        .eq(Order::getId, orderId)
                        .in(Order::getStatus, StatusConstants.ORDER_PENDING_SHIPMENT, StatusConstants.ORDER_SHIPPED)
                        .set(Order::getStatus, StatusConstants.ORDER_REFUNDING)
                        .set(Order::getCancelReason, reason)
                        .set(Order::getCancelTime, LocalDateTime.now())
                        .set(Order::getUpdateTime, LocalDateTime.now()));

        log.info("订单置退款中, orderId={}, 等待微信退款回调确认", orderId);

        List<Long> skuIds = items.stream()
                .map(OrderItem::getSkuId)
                .collect(java.util.stream.Collectors.toList());
        applicationEventPublisher.publishEvent(new StockSyncEvent(skuIds));
    }

    @Override
    public OrderVO getAdminOrderById(Long orderId) {
        return getOrderVO(orderId, null);
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
        // 【事务内】只恢复 DB 库存，缓存由方法末尾统一同步
        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(OrderItem::getOrderId, orderId);
        List<OrderItem> items = orderItemMapper.selectList(itemWrapper);
        for (OrderItem item : items) {
            skuService.restoreStock(item.getSkuId(), item.getQuantity());
            log.info("恢复DB库存, orderId={}, skuId={}, quantity={}", orderId, item.getSkuId(), item.getQuantity());
        }
        // 更新订单状态为已取消
        order.setStatus(StatusConstants.ORDER_CANCELLED);
        order.setCancelReason(cancelReason);
        order.setCancelTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
        log.info("订单自动取消成功, orderId={}", orderId);

        // 发布库存同步事件，事务提交后由监听器异步执行
        List<Long> skuIds = items.stream()
                .map(OrderItem::getSkuId)
                .collect(java.util.stream.Collectors.toList());
        applicationEventPublisher.publishEvent(new StockSyncEvent(skuIds));

        return true;
    }

    private OrderVO getOrderVO(Long orderId, Long userId) {
        Order order = orderMapper.selectById(orderId);
        Address address = addressMapper.selectById(order.getAddressId());
        Logistics logistics = logisticsMapper.selectById(order.getLogisticsId());

        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(OrderItem::getOrderId, orderId);
        List<OrderItem> items = orderItemMapper.selectList(itemWrapper);

        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setStatusText(getStatusText(order.getStatus()));
        vo.setTotalAmount(order.getTotalAmount());
        vo.setFreightAmount(order.getFreightAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setLogisticsName(logistics != null ? logistics.getName() : null);
        vo.setLogisticsNo(order.getLogisticsNo());
        vo.setReceiverName(address != null ? address.getConsignee() : null);
        vo.setReceiverPhone(address != null ? address.getPhone() : null);
        vo.setReceiverAddress(address != null ? address.getProvince() + address.getCity() + address.getDistrict() + address.getDetail() : null);
        vo.setBuyerRemark(order.getBuyerRemark());
        vo.setPayTime(order.getPayTime());
        vo.setShipTime(order.getShipTime());
        vo.setReceiveTime(order.getReceiveTime());
        vo.setCreateTime(order.getCreateTime());
        vo.setItems(items);

        return vo;
    }

    private String getStatusText(Integer status) {
        return switch (status) {
            case StatusConstants.ORDER_PENDING_PAYMENT -> "待付款";
            case StatusConstants.ORDER_PENDING_SHIPMENT -> "待发货";
            case StatusConstants.ORDER_SHIPPED -> "已发货";
            case StatusConstants.ORDER_COMPLETED -> "已完成";
            case StatusConstants.ORDER_CANCELLED -> "已取消";
            case StatusConstants.ORDER_REFUNDING -> "退款中";
            case StatusConstants.ORDER_REFUNDED -> "已退款";
            default -> "未知";
        };
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
