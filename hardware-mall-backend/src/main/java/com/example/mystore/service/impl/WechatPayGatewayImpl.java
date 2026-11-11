package com.example.mystore.service.impl;

import com.example.mystore.common.constant.WechatConstants;
import com.example.mystore.service.WechatPayGateway;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.Amount;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.jsapi.model.QueryOrderByOutTradeNoRequest;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * wechatpay-java SDK 适配层。请求参数、签名与错误传播与原先保持完全一致。
 */
@Service
@RequiredArgsConstructor
public class WechatPayGatewayImpl implements WechatPayGateway {

    private final Config wechatPayConfig;

    @Value("${wechat.appid}")
    private String appId;

    @Value("${wechat.pay.mch-id}")
    private String mchId;

    @Value("${wechat.pay.notify-url}")
    private String notifyUrl;

    @Override
    public Map<String, String> prepay(String outTradeNo, BigDecimal amount, String openid) {
        PrepayRequest prepayRequest = new PrepayRequest();
        prepayRequest.setAppid(appId);
        prepayRequest.setMchid(mchId);
        prepayRequest.setDescription("五金商城-订单支付");
        prepayRequest.setOutTradeNo(outTradeNo);
        prepayRequest.setNotifyUrl(notifyUrl);

        Amount reqAmount = new Amount();
        reqAmount.setTotal(amount.multiply(BigDecimal.valueOf(WechatConstants.Pay.CENTS_PER_YUAN)).intValueExact());
        reqAmount.setCurrency(WechatConstants.Pay.CURRENCY_CNY);
        prepayRequest.setAmount(reqAmount);

        Payer payer = new Payer();
        payer.setOpenid(openid);
        prepayRequest.setPayer(payer);

        PrepayWithRequestPaymentResponse response = jsapiService().prepayWithRequestPayment(prepayRequest);

        Map<String, String> params = new HashMap<>();
        params.put(WechatConstants.Pay.PARAM_APP_ID, response.getAppId());
        params.put(WechatConstants.Pay.PARAM_TIME_STAMP, response.getTimeStamp());
        params.put(WechatConstants.Pay.PARAM_NONCE_STR, response.getNonceStr());
        params.put(WechatConstants.Pay.PARAM_PACKAGE, response.getPackageVal());
        params.put(WechatConstants.Pay.PARAM_SIGN_TYPE, response.getSignType());
        params.put(WechatConstants.Pay.PARAM_PAY_SIGN, response.getPaySign());
        return params;
    }

    @Override
    public Transaction queryOrder(String outTradeNo) {
        QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
        request.setOutTradeNo(outTradeNo);
        request.setMchid(mchId);
        return jsapiService().queryOrderByOutTradeNo(request);
    }

    @Override
    public void createRefund(String outTradeNo, String outRefundNo, BigDecimal amount, String reason) {
        CreateRequest refundRequest = new CreateRequest();
        refundRequest.setOutTradeNo(outTradeNo);
        refundRequest.setOutRefundNo(outRefundNo);
        refundRequest.setReason(reason);
        refundRequest.setNotifyUrl(notifyUrl + WechatConstants.Pay.REFUND_NOTIFY_PATH);

        long amountInCents = amount.multiply(BigDecimal.valueOf(WechatConstants.Pay.CENTS_PER_YUAN)).longValue();
        AmountReq amountReq = new AmountReq();
        amountReq.setRefund(amountInCents);
        amountReq.setTotal(amountInCents);
        amountReq.setCurrency(WechatConstants.Pay.CURRENCY_CNY);
        refundRequest.setAmount(amountReq);

        refundService().create(refundRequest);
    }

    @Override
    public Refund queryRefund(String outRefundNo) {
        QueryByOutRefundNoRequest request = new QueryByOutRefundNoRequest();
        request.setOutRefundNo(outRefundNo);
        return refundService().queryByOutRefundNo(request);
    }

    private JsapiServiceExtension jsapiService() {
        return new JsapiServiceExtension.Builder().config(wechatPayConfig).build();
    }

    private RefundService refundService() {
        return new RefundService.Builder().config(wechatPayConfig).build();
    }
}
