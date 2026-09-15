package com.example.mystore.controller.user;

import com.example.mystore.common.constant.WechatConstants;
import com.example.mystore.service.WechatMessageService;
import com.example.mystore.util.JsonUtil;
import com.example.mystore.util.WxMsgCrypt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 微信小程序「消息推送」接收端点(安全模式, 数据格式 JSON)
 * - GET : 服务器地址验证, 校验 signature 后原样返回 echostr
 * - POST: 校验 msg_signature, 解密 Encrypt, 分发事件
 */
@Slf4j
@RestController
@RequestMapping("/api/wechat/message")
@RequiredArgsConstructor
public class WechatMessageController {

    private final WechatMessageService wechatMessageService;

    @Value("${wechat.msg-push.token:}")
    private String token;

    @Value("${wechat.msg-push.aes-key:}")
    private String aesKey;

    @Value("${wechat.appid:}")
    private String appid;

    @GetMapping(value = "/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    public String verify(@RequestParam(required = false) String signature,
                         @RequestParam(required = false) String timestamp,
                         @RequestParam(required = false) String nonce,
                         @RequestParam(required = false) String echostr) {
        if (!StringUtils.hasText(token)) {
            log.warn("微信消息推送未配置 token, 跳过验证");
            return "";
        }
        if (WxMsgCrypt.checkSignature(token, timestamp, nonce, signature)) {
            return echostr == null ? "" : echostr;
        }
        log.warn("微信消息推送服务器验证签名失败");
        return "";
    }

    @PostMapping(value = "/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    public String receive(@RequestParam(required = false) String signature,
                          @RequestParam(required = false) String timestamp,
                          @RequestParam(required = false) String nonce,
                          @RequestParam(value = "msg_signature", required = false) String msgSignature,
                          @RequestParam(value = "encrypt_type", required = false) String encryptType,
                          @RequestBody(required = false) String body) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(aesKey)) {
            log.warn("微信消息推送未配置 token/aes-key, 跳过");
            return "";
        }
        try {
            Map<String, Object> envelope = body == null ? null : JsonUtil.parse(body);
            String encrypt = envelope == null ? null : str(envelope.get(WechatConstants.Fields.ENCRYPT));
            if (encrypt == null) {
                log.warn("微信消息推送缺少 Encrypt");
                return "";
            }
            if (!WxMsgCrypt.checkMsgSignature(token, timestamp, nonce, encrypt, msgSignature)) {
                log.warn("微信消息推送 msg_signature 校验失败");
                return "invalid";
            }
            String plain = WxMsgCrypt.decrypt(aesKey, encrypt, appid);
            wechatMessageService.handleEvent(plain);
            return "";
        } catch (Exception e) {
            log.error("处理微信消息推送失败", e);
            return "";
        }
    }

    private String str(Object value) {
        return value == null ? null : value.toString();
    }
}
