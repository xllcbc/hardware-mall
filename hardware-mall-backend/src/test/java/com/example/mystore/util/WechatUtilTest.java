package com.example.mystore.util;

import com.example.mystore.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;

class WechatUtilTest {

    private WechatUtil newWechatUtil() {
        WechatUtil util = new WechatUtil(mock(RedisUtil.class));
        ReflectionTestUtils.setField(util, "appid", "wx-test-appid");
        ReflectionTestUtils.setField(util, "secret", "test-secret");
        return util;
    }

    @Test
    void getSessionKey_testCodeAlwaysGoesToRealWechatPath() {
        WechatUtil util = newWechatUtil();

        // 生产代码不存在模拟登录分支，test_ code 同样走真实微信鉴权。
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
        WechatUtil util = newWechatUtil();

        assertThatThrownBy(() -> util.getSessionKey(""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("微信授权码不能为空");
    }

    @Test
    void getPhoneNumber_testCodeAlwaysGoesToRealWechatPath() {
        WechatUtil util = newWechatUtil();

        // 生产代码不存在模拟手机号分支，getAccessToken 走真实微信接口。
        try (MockedStatic<HttpUtil> http = Mockito.mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.get(anyString(), anyMap()))
                    .thenReturn("{\"errcode\":40013,\"errmsg\":\"invalid appid\"}");

            assertThatThrownBy(() -> util.getPhoneNumber("test_13800138000"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("获取access_token失败");
        }
    }

}
