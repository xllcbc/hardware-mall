package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.constant.WechatConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.Logistics;
import com.example.mystore.entity.db.Order;
import com.example.mystore.entity.db.OrderItem;
import com.example.mystore.entity.db.PaymentRecord;
import com.example.mystore.entity.db.User;
import com.example.mystore.mapper.LogisticsMapper;
import com.example.mystore.mapper.OrderItemMapper;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.mapper.PaymentRecordMapper;
import com.example.mystore.mapper.UserMapper;
import com.example.mystore.service.WechatOrderShippingService;
import com.example.mystore.util.HttpUtil;
import com.example.mystore.util.JsonUtil;
import com.example.mystore.util.WechatUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 微信发货信息管理服务实现
 * 单号口径: 商户单号(out_trade_no) == 订单号(order_no); 交易单号字段仅用于查询/对账
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WechatOrderShippingServiceImpl implements WechatOrderShippingService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final UserMapper userMapper;
    private final LogisticsMapper logisticsMapper;
    private final WechatUtil wechatUtil;
    private final DingTalkAlertService dingTalkAlertService;

    @Value("${wechat.pay.mch-id:}")
    private String mchId;

    private static final DateTimeFormatter RFC3339_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    @Override
    public void uploadShippingInfo(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return;
        }
        Integer deliveryType = order.getDeliveryType();
        if (deliveryType == null
                || (deliveryType != StatusConstants.DELIVERY_TYPE_LOCAL
                    && deliveryType != StatusConstants.DELIVERY_TYPE_PICKUP)) {
            log.info("非同城/自提订单, 跳过微信发货上报, orderId={}, deliveryType={}", orderId, deliveryType);
            return;
        }

        PaymentRecord record = paymentRecordMapper.selectOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderId, orderId)
                .eq(PaymentRecord::getStatus, PaymentRecord.STATUS_PAID));
        if (record == null || !StringUtils.hasText(record.getOutTradeNo())) {
            log.warn("微信发货上报: 未找到已支付记录, orderId={}", orderId);
            return;
        }
        User user = userMapper.selectById(order.getUserId());
        if (user == null || !StringUtils.hasText(user.getOpenid())) {
            log.warn("微信发货上报: 用户 openid 缺失, orderId={}", orderId);
            return;
        }

        Map<String, Object> body = new HashMap<>();
        Map<String, Object> orderKey = new HashMap<>();
        orderKey.put(WechatConstants.Fields.ORDER_NUMBER_TYPE, WechatConstants.Common.ORDER_NUMBER_TYPE_OUT_TRADE_NO);
        orderKey.put(WechatConstants.Fields.MCHID, mchId);
        orderKey.put(WechatConstants.Fields.OUT_TRADE_NO, record.getOutTradeNo());
        body.put(WechatConstants.Fields.ORDER_KEY, orderKey);
        body.put(WechatConstants.Fields.LOGISTICS_TYPE, deliveryType);
        body.put(WechatConstants.Fields.DELIVERY_MODE, WechatConstants.Common.DELIVERY_MODE_UNIFIED);
        body.put(WechatConstants.Fields.SHIPPING_LIST, buildShippingList(order, deliveryType));
        body.put(WechatConstants.Fields.UPLOAD_TIME, OffsetDateTime.now().format(RFC3339_MILLIS));
        Map<String, Object> payer = new HashMap<>();
        payer.put(WechatConstants.Fields.OPENID, user.getOpenid());
        body.put(WechatConstants.Fields.PAYER, payer);

        String url = WechatConstants.Api.UPLOAD_SHIPPING_INFO + "?access_token="
                + wechatUtil.getAccessToken();
        try {
            String resp = HttpUtil.post(url, JsonUtil.toJson(body));
            Map<String, Object> result = JsonUtil.parse(resp);
            Object errcode = result.get(WechatConstants.Fields.ERRCODE);
            if (errcode != null && !WechatConstants.Common.ERRCODE_SUCCESS.equals(errcode.toString())) {
                throw new BusinessException("微信发货上报失败: " + result.get(WechatConstants.Fields.ERRMSG));
            }
            // 上报成功: 记录微信侧状态=已发货
            orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, orderId)
                    .set(Order::getWechatOrderState, WechatConstants.OrderState.SHIPPED)
                    .set(Order::getUpdateTime, LocalDateTime.now()));
            log.info("微信发货上报成功, orderId={}, outTradeNo={}, logisticsType={}",
                    orderId, record.getOutTradeNo(), deliveryType);
        } catch (Exception e) {
            log.error("微信发货上报异常, orderId={}", orderId, e);
            dingTalkAlertService.alert(WechatConstants.Alert.WECHAT_SHIPPING_UPLOAD_FAIL,
                    "订单=" + orderId + " 微信发货上报失败: " + e.getMessage());
            throw new BusinessException("微信发货上报失败: " + e.getMessage());
        }
    }

    @Override
    public Integer queryOrderState(Long orderId) {
        PaymentRecord record = paymentRecordMapper.selectOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderId, orderId)
                .isNotNull(PaymentRecord::getOutTradeNo)
                .orderByDesc(PaymentRecord::getCreateTime)
                .last("LIMIT 1"));
        if (record == null || !StringUtils.hasText(record.getOutTradeNo())) {
            log.warn("微信查单: 支付记录缺失, orderId={}", orderId);
            return null;
        }
        Map<String, Object> body = new HashMap<>();
        body.put(WechatConstants.Fields.MERCHANT_ID, mchId);
        body.put(WechatConstants.Fields.MERCHANT_TRADE_NO, record.getOutTradeNo());

        String url = WechatConstants.Api.GET_ORDER + "?access_token="
                + wechatUtil.getAccessToken();
        try {
            String resp = HttpUtil.post(url, JsonUtil.toJson(body));
            Map<String, Object> result = JsonUtil.parse(resp);
            Object errcode = result.get(WechatConstants.Fields.ERRCODE);
            if (errcode != null && !WechatConstants.Common.ERRCODE_SUCCESS.equals(errcode.toString())) {
                log.warn("微信查单失败, orderId={}, errcode={}, errmsg={}",
                        orderId, errcode, result.get(WechatConstants.Fields.ERRMSG));
                return null;
            }
            Object orderObj = result.get(WechatConstants.Fields.ORDER);
            if (!(orderObj instanceof Map<?, ?> orderMap)) {
                return null;
            }
            Object state = orderMap.get(WechatConstants.Fields.ORDER_STATE);
            return state == null ? null : Integer.valueOf(state.toString());
        } catch (Exception e) {
            log.error("微信查单异常, orderId={}", orderId, e);
            return null;
        }
    }

    /** 组装物流信息列表: 同城配送带物流公司名+配送单号; 自提仅商品描述 */
    private List<Map<String, Object>> buildShippingList(Order order, Integer deliveryType) {
        Map<String, Object> ship = new HashMap<>();
        ship.put(WechatConstants.Fields.ITEM_DESC, buildItemDesc(order.getId()));
        if (deliveryType == StatusConstants.DELIVERY_TYPE_LOCAL) {
            ship.put(WechatConstants.Fields.TRACKING_NO, order.getLogisticsNo());
            Logistics logistics = order.getLogisticsId() == null
                    ? null : logisticsMapper.selectById(order.getLogisticsId());
            ship.put(WechatConstants.Fields.EXPRESS_COMPANY, logistics != null ? logistics.getName() : "同城配送");
        }
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(ship);
        return list;
    }

    /** 商品描述: "名称×数量; ..." 截断至 120 字 */
    private String buildItemDesc(Long orderId) {
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        StringBuilder sb = new StringBuilder();
        for (OrderItem item : items) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(item.getProductName()).append("×").append(item.getQuantity());
        }
        String desc = sb.toString();
        return desc.length() > 120 ? desc.substring(0, 120) : desc;
    }
}
