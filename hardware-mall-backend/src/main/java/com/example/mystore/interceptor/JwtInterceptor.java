package com.example.mystore.interceptor;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.util.JwtUtil;
import com.example.mystore.util.RedisUtil;
import com.example.mystore.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        String uri = request.getRequestURI();

        if (StringUtil.isEmpty(token)) {
            log.warn("JWT拦截-Token为空 | URI: {}", uri);
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录\"}");
            return false;
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        try {
            if (jwtUtil.isTokenExpired(token)) {
                log.warn("JWT拦截-Token已过期 | URI: {}", uri);
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"登录已过期\"}");
                return false;
            }
            if (redisUtil.hasKey(RedisConstants.PREFIX_TOKEN_BLACKLIST + token)) {
                log.warn("JWT拦截-Token已被拉黑 | URI: {}", uri);
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"登录已失效\"}");
                return false;
            }
            Long userId = jwtUtil.getUserIdFromToken(token);
            request.setAttribute("userId", userId);
            log.debug("JWT验证通过 | URI: {} | userId: {}", uri, userId);
            return true;
        } catch (Exception e) {
            log.warn("JWT拦截-Token无效 | URI: {} | 原因: {}", uri, e.getMessage());
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"无效的认证信息\"}");
            return false;
        }
    }
}
