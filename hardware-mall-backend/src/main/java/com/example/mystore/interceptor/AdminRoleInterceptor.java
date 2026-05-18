package com.example.mystore.interceptor;

import com.example.mystore.annotation.RequireAdmin;
import com.example.mystore.util.JwtUtil;
import com.example.mystore.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminRoleInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RequireAdmin classAnnotation = handlerMethod.getBeanType().getAnnotation(RequireAdmin.class);
        RequireAdmin methodAnnotation = handlerMethod.getMethodAnnotation(RequireAdmin.class);

        if (classAnnotation == null && methodAnnotation == null) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (StringUtil.isEmpty(token)) {
            log.warn("AdminRole拦截-Token为空 | URI: {}", request.getRequestURI());
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录\"}");
            return false;
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        try {
            Integer role = jwtUtil.getRoleFromToken(token);
            if (role == null || role != 2) {
                log.warn("AdminRole拦截-权限不足 | URI: {} | role: {}", request.getRequestURI(), role);
                response.setStatus(403);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"message\":\"无权限访问\"}");
                return false;
            }
        } catch (Exception e) {
            log.warn("AdminRole拦截-Token解析失败 | URI: {}", request.getRequestURI(), e);
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"无权限访问\"}");
            return false;
        }

        return true;
    }
}
