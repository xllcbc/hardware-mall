package com.example.mystore.util;

import com.example.mystore.common.constant.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class WechatUtil {

    private final RedisUtil redisUtil;

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

            if (resultMap.containsKey("errcode") && !"0".equals(resultMap.get("errcode").toString())) {
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

    public String getAccessToken() {
        Object cached = redisUtil.get(RedisConstants.PREFIX_WECHAT_ACCESS_TOKEN);
        if (cached != null) {
            return cached.toString();
        }

        String url = "https://api.weixin.qq.com/cgi-bin/token";
        Map<String, String> params = new HashMap<>();
        params.put("grant_type", "client_credential");
        params.put("appid", appid);
        params.put("secret", secret);

        try {
            String response = HttpUtil.get(url, params);
            Map<String, Object> resultMap = JsonUtil.parse(response);

            if (resultMap.containsKey("errcode") && !"0".equals(resultMap.get("errcode").toString())) {
                throw new RuntimeException("获取access_token失败: " + resultMap.get("errmsg"));
            }

            String accessToken = resultMap.get("access_token").toString();
            redisUtil.set(RedisConstants.PREFIX_WECHAT_ACCESS_TOKEN, accessToken,
                    RedisConstants.WECHAT_ACCESS_TOKEN_TTL, TimeUnit.SECONDS);
            return accessToken;
        } catch (Exception e) {
            log.error("获取access_token失败", e);
            throw new RuntimeException("获取access_token失败");
        }
    }

    public String getPhoneNumber(String phoneCode) {
        if (!StringUtils.hasText(phoneCode)) {
            throw new RuntimeException("手机号授权码不能为空");
        }

        if (phoneCode.startsWith("test_")) {
            log.info("测试模式：模拟获取手机号，phoneCode={}", phoneCode);
            return "13800138000";
        }

        String accessToken = getAccessToken();
        String url = "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=" + accessToken;

        try {
            String body = "{\"code\":\"" + phoneCode + "\"}";
            String response = HttpUtil.post(url, body);
            Map<String, Object> resultMap = JsonUtil.parse(response);

            if (resultMap.containsKey("errcode") && !"0".equals(resultMap.get("errcode").toString())) {
                throw new RuntimeException("获取手机号失败: " + resultMap.get("errmsg"));
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> phoneInfo = (Map<String, Object>) resultMap.get("phone_info");
            if (phoneInfo == null) {
                throw new RuntimeException("获取手机号失败: phone_info为空");
            }
            return phoneInfo.get("purePhoneNumber").toString();
        } catch (Exception e) {
            log.error("获取手机号失败", e);
            throw new RuntimeException("获取手机号失败");
        }
    }
}