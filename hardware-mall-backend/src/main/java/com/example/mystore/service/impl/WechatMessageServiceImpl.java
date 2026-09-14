package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.mystore.entity.db.Order;
import com.example.mystore.entity.db.PaymentRecord;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.mapper.PaymentRecordMapper;
import com.example.mystore.service.OrderService;
import com.example.mystore.service.WechatMessageService;
import com.example.mystore.service.WechatOrderShippingService;
import com.example.mystore.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 微信消息推送事件处理实现
 * 重点: trade_manage_order_settlement(订单发货/结算推送) 携带确认收货/结算信息,
 * 据此权威地把本地订单收敛为已完成, 不依赖前端确认组件回调
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WechatMessageServiceImpl implements WechatMessageService {

    private final OrderMapper orderMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final WechatOrderShippingService wechatOrderShippingService;
    private final OrderService orderService;
    private final DingTalkAlertService dingTalkAlertService;

    @Override
    public void handleEvent(String plainJson) {
        Map<String, Object> event = JsonUtil.parse(plainJson);
        if (event == null) {
            return;
        }
        String eventType = str(event.get("Event"));
        log.info("收到微信消息推送, event={}", eventType);
        if ("trade_manage_order_settlement".equals(eventType)) {
            handleSettlement(event);
        } else if ("trade_manage_remind_shipping".equals(eventType)) {
            dingTalkAlertService.alert("WECHAT_REMIND_SHIPPING",
                    "订单超48h未发货, merchant_trade_no=" + str(event.get("merchant_trade_no")));
        } else {
            log.info("暂不处理的微信事件, event={}", eventType);
        }
    }

    private void handleSettlement(Map<String, Object> event) {
        String merchantTradeNo = str(event.get("merchant_trade_no"));
        String transactionId = str(event.get("transaction_id"));
        Object confirmReceiveTime = event.get("confirm_receive_time");
        Object settlementTime = event.get("settlement_time");

        Order order = findOrder(merchantTradeNo, transactionId);
        if (order == null) {
            log.warn("微信结算事件未匹配到订单, merchantTradeNo={}, transactionId={}",
                    merchantTradeNo, transactionId);
            return;
        }

        // 以 get_order 为准刷新微信状态(失败则退化为按 confirm_receive_time 判定)
        Integer state = wechatOrderShippingService.queryOrderState(order.getId());
        if (state != null) {
            orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, order.getId())
                    .set(Order::getWechatOrderState, state)
                    .set(Order::getUpdateTime, LocalDateTime.now()));
        }
        boolean confirmed = state != null ? (state == 3 || state == 4) : (confirmReceiveTime != null);
        if (confirmed) {
            boolean ok = orderService.autoConfirmReceive(order.getId());
            log.info("微信结算事件推进本地完成, orderId={}, state={}, confirmTime={}, settlementTime={}, ok={}",
                    order.getId(), state, confirmReceiveTime, settlementTime, ok);
        }
    }

    private Order findOrder(String merchantTradeNo, String transactionId) {
        if (merchantTradeNo != null && !merchantTradeNo.isEmpty()) {
            Order byNo = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                    .eq(Order::getOrderNo, merchantTradeNo).last("LIMIT 1"));
            if (byNo != null) {
                return byNo;
            }
            PaymentRecord pr = paymentRecordMapper.selectOne(new LambdaQueryWrapper<PaymentRecord>()
                    .eq(PaymentRecord::getOutTradeNo, merchantTradeNo).last("LIMIT 1"));
            if (pr != null) {
                return orderMapper.selectById(pr.getOrderId());
            }
        }
        if (transactionId != null && !transactionId.isEmpty()) {
            PaymentRecord pr = paymentRecordMapper.selectOne(new LambdaQueryWrapper<PaymentRecord>()
                    .eq(PaymentRecord::getTransactionId, transactionId).last("LIMIT 1"));
            if (pr != null) {
                return orderMapper.selectById(pr.getOrderId());
            }
        }
        return null;
    }

    private String str(Object value) {
        return value == null ? null : value.toString();
    }
}
