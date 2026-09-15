package com.example.mystore.common.constant;

/**
 * 微信协议常量(小程序 API + 消息推送 + 交易组件发货)
 *
 * <p>按域分组: {@link Api} 接口地址 / {@link Event} 消息推送事件 / {@link OrderState} 微信侧订单状态
 * / {@link Fields} JSON 字段名 / {@link Common} 通用固定值 / {@link Alert} 告警类型
 *
 * <p><b>注意</b>: {@link OrderState} 是微信侧订单状态, 与本地 {@link StatusConstants} 的
 * 订单状态数值相同(如 3/4)但语义完全不同, 禁止混用。
 */
public final class WechatConstants {

    private WechatConstants() {
    }

    /** 接口地址(完整 URL) */
    public static final class Api {

        private Api() {
        }

        public static final String BASE = "https://api.weixin.qq.com";
        /** 小程序登录 code 换 openid */
        public static final String JSCODE2SESSION = BASE + "/sns/jscode2session";
        /** 获取接口调用凭据 */
        public static final String ACCESS_TOKEN = BASE + "/cgi-bin/token";
        /** 获取用户手机号 */
        public static final String PHONE_NUMBER = BASE + "/wxa/business/getuserphonenumber";
        /** 交易组件-发货信息录入 */
        public static final String UPLOAD_SHIPPING_INFO = BASE + "/wxa/sec/order/upload_shipping_info";
        /** 交易组件-查询订单 */
        public static final String GET_ORDER = BASE + "/wxa/sec/order/get_order";
    }

    /** 消息推送事件类型(推送报文的 Event 值) */
    public static final class Event {

        private Event() {
        }

        /** 订单结算(确认收货/交易完成推送) */
        public static final String ORDER_SETTLEMENT = "trade_manage_order_settlement";
        /** 发货超时提醒 */
        public static final String REMIND_SHIPPING = "trade_manage_remind_shipping";
    }

    /** 微信侧订单状态(勿与本地 {@link StatusConstants} 混用) */
    public static final class OrderState {

        private OrderState() {
        }

        /** 已发货(已上报) */
        public static final int SHIPPED = 2;
        /** 已确认收货 */
        public static final int CONFIRMED = 3;
        /** 交易完成 */
        public static final int COMPLETED = 4;
    }

    /** 推送/响应 JSON 字段名(wire key) */
    public static final class Fields {

        private Fields() {
        }

        public static final String ENCRYPT = "Encrypt";
        public static final String EVENT = "Event";
        public static final String MERCHANT_TRADE_NO = "merchant_trade_no";
        public static final String TRANSACTION_ID = "transaction_id";
        public static final String CONFIRM_RECEIVE_TIME = "confirm_receive_time";
        public static final String SETTLEMENT_TIME = "settlement_time";

        public static final String ERRCODE = "errcode";
        public static final String ERRMSG = "errmsg";

        public static final String OPENID = "openid";
        public static final String SESSION_KEY = "session_key";
        public static final String ACCESS_TOKEN = "access_token";
        public static final String PHONE_INFO = "phone_info";
        public static final String PURE_PHONE_NUMBER = "purePhoneNumber";
        public static final String APPID = "appid";
        public static final String SECRET = "secret";
        public static final String JS_CODE = "js_code";
        public static final String GRANT_TYPE = "grant_type";
        public static final String CODE = "code";

        public static final String ORDER_KEY = "order_key";
        public static final String MCHID = "mchid";
        public static final String OUT_TRADE_NO = "out_trade_no";
        public static final String ORDER_NUMBER_TYPE = "order_number_type";
        public static final String LOGISTICS_TYPE = "logistics_type";
        public static final String DELIVERY_MODE = "delivery_mode";
        public static final String SHIPPING_LIST = "shipping_list";
        public static final String UPLOAD_TIME = "upload_time";
        public static final String PAYER = "payer";
        public static final String ITEM_DESC = "item_desc";
        public static final String TRACKING_NO = "tracking_no";
        public static final String EXPRESS_COMPANY = "express_company";
        public static final String MERCHANT_ID = "merchant_id";
        public static final String ORDER = "order";
        public static final String ORDER_STATE = "order_state";
    }

    /** 通用固定值 */
    public static final class Common {

        private Common() {
        }

        public static final String GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code";
        public static final String GRANT_TYPE_CLIENT_CREDENTIAL = "client_credential";
        /** 微信接口成功返回的 errcode 值 */
        public static final String ERRCODE_SUCCESS = "0";
        /** 订单单号类型: 商户单号(out_trade_no) */
        public static final int ORDER_NUMBER_TYPE_OUT_TRADE_NO = 1;
        /** 物流模式: 统一发货 */
        public static final int DELIVERY_MODE_UNIFIED = 1;
    }

    /** 钉钉告警类型 */
    public static final class Alert {

        private Alert() {
        }

        public static final String WECHAT_SHIPPING_UPLOAD_FAIL = "WECHAT_SHIPPING_UPLOAD_FAIL";
        public static final String WECHAT_REMIND_SHIPPING = "WECHAT_REMIND_SHIPPING";
    }
}
