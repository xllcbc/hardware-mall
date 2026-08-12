package com.example.mystore.interceptor;

import com.example.mystore.annotation.RequireAdmin;
import com.example.mystore.util.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Slf4j
@Component
public class AdminRoleInterceptor implements HandlerInterceptor {

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

        Integer role = UserContext.getRole();
        if (role == null || role != 2) {
            log.warn("AdminRole拦截-权限不足 | URI: {} | role: {}", request.getRequestURI(), role);
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"无权限访问\"}");
            return false;
        }

        return true;
    }
}
