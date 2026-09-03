package com.example.mystore.interceptor;

import com.example.mystore.annotation.RateLimit;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.util.RedisUtil;
import com.example.mystore.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisUtil redisUtil;

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

        // 按"谁"隔离计数：登录用户按 userId, 匿名(如登录接口本身)按客户端 IP, 避免全站共享计数器互相挤兑
        String rateKey = RedisConstants.PREFIX_RATE_LIMIT + rateLimit.key() + ":" + resolveIdentity(request);
        long current;
        try {
            // Lua 原子 INCR + 首次 EXPIRE, 消除两步之间崩溃导致 key 永不过期的窗口
            current = redisUtil.incrWithExpire(rateKey, rateLimit.time());
        } catch (Exception e) {
            log.error("限流计数失败，Redis可能不可用, key={}", rateKey, e);
            return true;
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

    /**
     * 限流身份：优先 userId（jwt 拦截器须先于本拦截器执行，见 WebMvcConfig 注册顺序）；
     * 匿名请求取 X-Forwarded-For 首段（反代场景 getRemoteAddr 是代理 IP），无则用 remoteAddr
     */
    private String resolveIdentity(HttpServletRequest request) {
        Long userId = UserContext.getUserId();
        if (userId != null) {
            return "u" + userId;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return "ip" + xff.split(",")[0].trim();
        }
        return "ip" + request.getRemoteAddr();
    }
}