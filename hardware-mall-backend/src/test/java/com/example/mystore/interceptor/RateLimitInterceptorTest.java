package com.example.mystore.interceptor;

import com.example.mystore.annotation.RateLimit;
import com.example.mystore.util.RedisUtil;
import com.example.mystore.util.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private RedisUtil redisUtil;

    private RateLimitInterceptor interceptor;

    static class DummyController {
        @RateLimit(key = "pay", count = 10, time = 60)
        public void limited() {
        }

        public void plain() {
        }
    }

    private HandlerMethod limitedHandler;
    private HandlerMethod plainHandler;

    @BeforeEach
    void setUp() throws Exception {
        interceptor = new RateLimitInterceptor(redisUtil);
        Method limited = DummyController.class.getMethod("limited");
        Method plain = DummyController.class.getMethod("plain");
        limitedHandler = new HandlerMethod(new DummyController(), limited);
        plainHandler = new HandlerMethod(new DummyController(), plain);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private MockHttpServletRequest request(String remoteAddr, String xff) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        if (xff != null) {
            request.addHeader("X-Forwarded-For", xff);
        }
        return request;
    }

    @Test
    void authenticatedUser_keyContainsUserId() throws Exception {
        UserContext.setUserId(123L);
        when(redisUtil.incrWithExpire(anyString(), anyLong())).thenReturn(1L);

        boolean pass = interceptor.preHandle(request("1.2.3.4", null), new MockHttpServletResponse(), limitedHandler);

        assertThat(pass).isTrue();
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisUtil).incrWithExpire(keyCaptor.capture(), eq(60L));
        assertThat(keyCaptor.getValue()).isEqualTo("rate:limit:pay:u123");
    }

    @Test
    void anonymous_keyUsesXffFirstIp() throws Exception {
        when(redisUtil.incrWithExpire(anyString(), anyLong())).thenReturn(1L);

        boolean pass = interceptor.preHandle(request("10.0.0.1", "9.9.9.9, 10.0.0.1"), new MockHttpServletResponse(), limitedHandler);

        assertThat(pass).isTrue();
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisUtil).incrWithExpire(keyCaptor.capture(), eq(60L));
        // 反代场景下优先取 X-Forwarded-For 首段(真实客户端), 而非代理 IP
        assertThat(keyCaptor.getValue()).isEqualTo("rate:limit:pay:ip9.9.9.9");
    }

    @Test
    void anonymous_noXff_usesRemoteAddr() throws Exception {
        when(redisUtil.incrWithExpire(anyString(), anyLong())).thenReturn(1L);

        boolean pass = interceptor.preHandle(request("1.2.3.4", null), new MockHttpServletResponse(), limitedHandler);

        assertThat(pass).isTrue();
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisUtil).incrWithExpire(keyCaptor.capture(), eq(60L));
        assertThat(keyCaptor.getValue()).isEqualTo("rate:limit:pay:ip1.2.3.4");
    }

    @Test
    void overLimit_returns429AndBlocks() throws Exception {
        when(redisUtil.incrWithExpire(anyString(), anyLong())).thenReturn(11L);

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean pass = interceptor.preHandle(request("1.2.3.4", null), response, limitedHandler);

        assertThat(pass).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("请求过于频繁");
    }

    @Test
    void withinLimit_passes() throws Exception {
        when(redisUtil.incrWithExpire(anyString(), anyLong())).thenReturn(10L);

        boolean pass = interceptor.preHandle(request("1.2.3.4", null), new MockHttpServletResponse(), limitedHandler);

        assertThat(pass).isTrue();
    }

    @Test
    void redisFailure_failsOpen() throws Exception {
        when(redisUtil.incrWithExpire(anyString(), anyLong())).thenThrow(new RuntimeException("redis down"));

        boolean pass = interceptor.preHandle(request("1.2.3.4", null), new MockHttpServletResponse(), limitedHandler);

        // Redis 不可用时放行, 不因限流组件故障打死全站
        assertThat(pass).isTrue();
    }

    @Test
    void methodWithoutRateLimitAnnotation_passesWithoutRedisCall() throws Exception {
        boolean pass = interceptor.preHandle(request("1.2.3.4", null), new MockHttpServletResponse(), plainHandler);

        assertThat(pass).isTrue();
        verifyNoInteractions(redisUtil);
    }
}
