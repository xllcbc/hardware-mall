package com.example.mystore.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class WechatUtil {

    @Value("${wechat.appid}")
    private String appid;

    @Value("${wechat.secret}")
    private String secret;

    public String getOpenid(String code) {
        return getSessionKey(code).get("openid");
    }

    public Map<String, String> getSessionKey(String code) {
        Map<String, String> result = new HashMap<>();
        if (!StringUtils.hasText(code)) {
            throw new RuntimeException("微信授权码不能为空");
        }

        if (code.startsWith("test_")) {
            log.info("测试模式：模拟微信登录，code={}", code);
            result.put("openid", code);
            result.put("session_key", "test_session_key_" + System.currentTimeMillis());
            return result;
        }

        String url = "https://api.weixin.qq.com/sns/jscode2session";
        Map<String, String> params = new HashMap<>();
        params.put("appid", appid);
        params.put("secret", secret);
        params.put("js_code", code);
        params.put("grant_type", "authorization_code");

        try {
            String response = HttpUtil.get(url, params);
            Map<String, Object> resultMap = JsonUtil.parse(response);

            if (resultMap.containsKey("errcode")) {
                throw new RuntimeException("微信登录失败: " + resultMap.get("errmsg"));
            }

            result.put("openid", resultMap.get("openid").toString());
            result.put("session_key", resultMap.get("session_key").toString());
            return result;
        } catch (Exception e) {
            log.error("微信登录失败", e);
            throw new RuntimeException("微信登录失败");
        }
    }
}
