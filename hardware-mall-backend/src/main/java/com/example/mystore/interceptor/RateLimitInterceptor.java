package com.example.mystore.interceptor;

import com.example.mystore.annotation.RateLimit;
import com.example.mystore.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisUtil redisUtil;
    private static final String RATE_LIMIT_PREFIX = "rate:limit:";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RateLimit rateLimit = handlerMethod.getMethod().getAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }

        String rateKey = RATE_LIMIT_PREFIX + rateLimit.key();
        long current;
        try {
            current = redisUtil.incr(rateKey);
        } catch (Exception e) {
            log.error("限流计数失败，Redis可能不可用, key={}", rateKey, e);
            return true;
        }

        if (current == 1) {
            redisUtil.expire(rateKey, rateLimit.time(), TimeUnit.SECONDS);
        }

        if (current > rateLimit.count()) {
            log.warn("接口限流触发 | key={} | count={} | limit={}", rateLimit.key(), current, rateLimit.count());
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
            return false;
        }

        return true;
    }
}