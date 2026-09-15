package com.example.mystore.util;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.constant.WechatConstants;
import com.example.mystore.common.exception.BusinessException;
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

    /**
     * test_ 开头的模拟 code 后门开关：仅供本地开发使用，默认关闭，生产环境必须保持 false
     */
    @Value("${wechat.mock-code-enabled:false}")
    private boolean mockCodeEnabled;

    public String getOpenid(String code) {
        return getSessionKey(code).get(WechatConstants.Fields.OPENID);
    }

    public Map<String, String> getSessionKey(String code) {
        Map<String, String> result = new HashMap<>();
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("微信授权码不能为空");
        }

        if (mockCodeEnabled && code.startsWith("test_")) {
            log.info("测试模式：模拟微信登录，code={}", code);
            result.put(WechatConstants.Fields.OPENID, code);
            result.put(WechatConstants.Fields.SESSION_KEY, "test_session_key_" + System.currentTimeMillis());
            return result;
        }

        String url = WechatConstants.Api.JSCODE2SESSION;
        Map<String, String> params = new HashMap<>();
        params.put(WechatConstants.Fields.APPID, appid);
        params.put(WechatConstants.Fields.SECRET, secret);
        params.put(WechatConstants.Fields.JS_CODE, code);
        params.put(WechatConstants.Fields.GRANT_TYPE, WechatConstants.Common.GRANT_TYPE_AUTHORIZATION_CODE);

        try {
            String response = HttpUtil.get(url, params);
            Map<String, Object> resultMap = JsonUtil.parse(response);

            if (resultMap.containsKey(WechatConstants.Fields.ERRCODE)
                    && !WechatConstants.Common.ERRCODE_SUCCESS.equals(resultMap.get(WechatConstants.Fields.ERRCODE).toString())) {
                throw new BusinessException("微信登录失败: " + resultMap.get(WechatConstants.Fields.ERRMSG));
            }

            result.put(WechatConstants.Fields.OPENID, resultMap.get(WechatConstants.Fields.OPENID).toString());
            result.put(WechatConstants.Fields.SESSION_KEY, resultMap.get(WechatConstants.Fields.SESSION_KEY).toString());
            return result;
        } catch (Exception e) {
            log.error("微信登录失败", e);
            throw new BusinessException("微信登录失败");
        }
    }

    public String getAccessToken() {
        Object cached = redisUtil.get(RedisConstants.PREFIX_WECHAT_ACCESS_TOKEN);
        if (cached != null) {
            return cached.toString();
        }

        String url = WechatConstants.Api.ACCESS_TOKEN;
        Map<String, String> params = new HashMap<>();
        params.put(WechatConstants.Fields.GRANT_TYPE, WechatConstants.Common.GRANT_TYPE_CLIENT_CREDENTIAL);
        params.put(WechatConstants.Fields.APPID, appid);
        params.put(WechatConstants.Fields.SECRET, secret);

        try {
            String response = HttpUtil.get(url, params);
            Map<String, Object> resultMap = JsonUtil.parse(response);

            if (resultMap.containsKey(WechatConstants.Fields.ERRCODE)
                    && !WechatConstants.Common.ERRCODE_SUCCESS.equals(resultMap.get(WechatConstants.Fields.ERRCODE).toString())) {
                throw new BusinessException("获取access_token失败: " + resultMap.get(WechatConstants.Fields.ERRMSG));
            }

            String accessToken = resultMap.get(WechatConstants.Fields.ACCESS_TOKEN).toString();
            redisUtil.set(RedisConstants.PREFIX_WECHAT_ACCESS_TOKEN, accessToken,
                    RedisConstants.WECHAT_ACCESS_TOKEN_TTL, TimeUnit.SECONDS);
            return accessToken;
        } catch (Exception e) {
            log.error("获取access_token失败", e);
            throw new BusinessException("获取access_token失败");
        }
    }

    public String getPhoneNumber(String phoneCode) {
        if (!StringUtils.hasText(phoneCode)) {
            throw new BusinessException("手机号授权码不能为空");
        }

        if (mockCodeEnabled && phoneCode.startsWith("test_")) {
            log.info("测试模式：模拟获取手机号，phoneCode={}", phoneCode);
            return "13800138000";
        }

        String accessToken = getAccessToken();
        String url = WechatConstants.Api.PHONE_NUMBER + "?access_token=" + accessToken;

        try {
            String body = "{\"" + WechatConstants.Fields.CODE + "\":\"" + phoneCode + "\"}";
            String response = HttpUtil.post(url, body);
            Map<String, Object> resultMap = JsonUtil.parse(response);

            if (resultMap.containsKey(WechatConstants.Fields.ERRCODE)
                    && !WechatConstants.Common.ERRCODE_SUCCESS.equals(resultMap.get(WechatConstants.Fields.ERRCODE).toString())) {
                throw new BusinessException("获取手机号失败: " + resultMap.get(WechatConstants.Fields.ERRMSG));
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> phoneInfo = (Map<String, Object>) resultMap.get(WechatConstants.Fields.PHONE_INFO);
            if (phoneInfo == null) {
                throw new BusinessException("获取手机号失败: phone_info为空");
            }
            return phoneInfo.get(WechatConstants.Fields.PURE_PHONE_NUMBER).toString();
        } catch (Exception e) {
            log.error("获取手机号失败", e);
            throw new BusinessException("获取手机号失败");
        }
    }
}