package com.example.mystore.util;

import com.example.mystore.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;

class WechatUtilTest {

    private WechatUtil newWechatUtil(boolean mockCodeEnabled) {
        WechatUtil util = new WechatUtil(mock(RedisUtil.class));
        ReflectionTestUtils.setField(util, "appid", "wx-test-appid");
        ReflectionTestUtils.setField(util, "secret", "test-secret");
        ReflectionTestUtils.setField(util, "mockCodeEnabled", mockCodeEnabled);
        return util;
    }

    @Test
    void getSessionKey_mockEnabled_testCodeReturnsMockOpenid() {
        WechatUtil util = newWechatUtil(true);

        Map<String, String> result = util.getSessionKey("test_user1");

        assertThat(result.get("openid")).isEqualTo("test_user1");
        assertThat(result.get("session_key")).startsWith("test_session_key_");
    }

    @Test
    void getSessionKey_mockDisabled_testCodeGoesRealWechatPath() {
        WechatUtil util = newWechatUtil(false);

        // 开关关闭后 test_ code 不再走后门，而是走真实微信鉴权（此处 mock 微信返回错误码）
        try (MockedStatic<HttpUtil> http = Mockito.mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.get(anyString(), anyMap()))
                    .thenReturn("{\"errcode\":40029,\"errmsg\":\"invalid code\"}");

            assertThatThrownBy(() -> util.getSessionKey("test_user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("微信登录失败");
        }
    }

    @Test
    void getSessionKey_emptyCode_rejectedBeforeAnyHttpCall() {
        WechatUtil util = newWechatUtil(true);

        assertThatThrownBy(() -> util.getSessionKey(""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("微信授权码不能为空");
    }

    @Test
    void getPhoneNumber_mockDisabled_testCodeGoesRealWechatPath() {
        WechatUtil util = newWechatUtil(false);

        // 开关关闭后 test_ phoneCode 不再返回模拟手机号，getAccessToken 走真实微信接口（mock 返回错误码）
        try (MockedStatic<HttpUtil> http = Mockito.mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.get(anyString(), anyMap()))
                    .thenReturn("{\"errcode\":40013,\"errmsg\":\"invalid appid\"}");

            assertThatThrownBy(() -> util.getPhoneNumber("test_13800138000"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("获取access_token失败");
        }
    }

    @Test
    void getPhoneNumber_mockEnabled_testCodeReturnsMockPhone() {
        WechatUtil util = newWechatUtil(true);

        assertThat(util.getPhoneNumber("test_13800138000")).isEqualTo("13800138000");
    }
}
