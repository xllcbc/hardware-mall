package com.example.mystore.config;

import com.example.mystore.interceptor.AdminRoleInterceptor;
import com.example.mystore.interceptor.JwtInterceptor;
import com.example.mystore.interceptor.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final AdminRoleInterceptor adminRoleInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    @Value("${cors.allowed-origins:http://localhost:*}")
    private String allowedOrigins;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**");
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                    "/api/user/login", "/api/admin/login",
                    "/api/user/category/**", "/api/user/product/**", "/api/user/logistics/**",
                    "/api/user/pay/callback", "/api/user/pay/callback/refund"
                );
        registry.addInterceptor(adminRoleInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/admin/login");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
