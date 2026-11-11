package com.example.mystore.service;

import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.model.Refund;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 微信支付网关：统一下单、查单、发起退款、查退款的唯一出口。
 * SDK 异常（网络、验签、微信业务错误）原样上抛，由调用方按现有语义处理。
 */
public interface WechatPayGateway {

    /** 统一下单，返回小程序调起支付所需参数 */
    Map<String, String> prepay(String outTradeNo, BigDecimal amount, String openid);

    /** 按商户单号查询支付订单 */
    Transaction queryOrder(String outTradeNo);

    /** 发起退款（按商户单号 + 商户退款单号，金额为元） */
    void createRefund(String outTradeNo, String outRefundNo, BigDecimal amount, String reason);

    /** 按商户退款单号查询退款 */
    Refund queryRefund(String outRefundNo);
}
