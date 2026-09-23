package com.example.mystore.interceptor;

import com.example.mystore.annotation.OptionalAuth;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.util.JwtUtil;
import com.example.mystore.util.RedisUtil;
import com.example.mystore.util.StringUtil;
import com.example.mystore.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");
        String uri = request.getRequestURI();

        if (StringUtil.isEmpty(token)) {
            if (isOptionalAuth(handler)) {
                UserContext.setUserGroup(StatusConstants.USER_GROUP_RETAIL);
                log.debug("JWT拦截-匿名访问 | URI: {}", uri);
                return true;
            }
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
            Integer role = jwtUtil.getRoleFromToken(token);
            Integer userGroup = jwtUtil.getUserGroupFromToken(token);
            UserContext.setUserId(userId);
            UserContext.setRole(role);
            UserContext.setUserGroup(StatusConstants.normalizeUserGroup(userGroup));
            log.debug("JWT验证通过 | URI: {} | userId: {} | role: {} | userGroup: {}", uri, userId, role, userGroup);
            return true;
        } catch (Exception e) {
            log.warn("JWT拦截-Token无效 | URI: {} | 原因: {}", uri, e.getMessage());
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"无效的认证信息\"}");
            return false;
        }
    }

    private boolean isOptionalAuth(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return false;
        }
        return handlerMethod.getBeanType().isAnnotationPresent(OptionalAuth.class)
                || handlerMethod.getMethodAnnotation(OptionalAuth.class) != null;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
