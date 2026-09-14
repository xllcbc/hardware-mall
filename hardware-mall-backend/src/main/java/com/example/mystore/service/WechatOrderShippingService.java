package com.example.mystore.service;

/**
 * 微信小程序「发货信息管理服务」对接
 * 负责发货信息上报(upload_shipping_info)与订单发货状态查询(get_order)
 */
public interface WechatOrderShippingService {

    /**
     * 上报发货信息(同城配送/用户自提)。
     * 成功后写 wechat_order_state=2(已发货); 失败抛出并由重试任务兜底。
     */
    void uploadShippingInfo(Long orderId);

    /**
     * 查询微信订单状态(order_state)。
     * 返回 1待发货/2已发货/3确认收货/4交易完成/5已退款/6资金待结算; 查询失败返回 null。
     */
    Integer queryOrderState(Long orderId);
}
