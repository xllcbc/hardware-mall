package com.example.mystore.service;

/**
 * 微信小程序消息推送事件处理
 */
public interface WechatMessageService {

    /**
     * 处理解密后的明文事件(JSON)
     */
    void handleEvent(String plainJson);
}
