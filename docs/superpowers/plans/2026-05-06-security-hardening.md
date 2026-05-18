# 安全加固 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 P0 安全漏洞修复：密钥环境变量化、管理端角色权限拦截、异常信息防泄漏、生产环境配置隔离、CORS 修复。

**Architecture:** 基于现有 Spring Boot 拦截器链扩展，新增 `AdminRoleInterceptor` 在 `JwtInterceptor` 之后校验管理端角色。密钥全部迁移为 Spring `${ENV_VAR}` 占位符。不改动业务逻辑，仅加固安全层。

**Tech Stack:** Spring Boot 2.7.18, JJWT, MyBatis-Plus, Lombok

---

### Task 1: 创建根目录 .gitignore 和 .env.example

**Files:**
- Create: `.gitignore`
- Create: `.env.example`

- [ ] **Step 1: 创建根目录 .gitignore**

```bash
cat > /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/.gitignore << 'GITEOF'
# 环境变量文件 (含密钥)
.env
.env.local
.env.*.local

# 生产环境配置 (含密钥)
application-prod.yml

# IDE
.idea/
*.iml
.vscode/

# OS
.DS_Store
Thumbs.db

# Build
target/
dist/
node_modules/
*.log
GITEOF
```

- [ ] **Step 2: 创建根目录 .env.example**

```bash
cat > /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/.env.example << 'ENVEOF'
# ==========================================
# 五金商城 - 环境变量配置模板
# 复制此文件为 .env 并填入真实值
# 警告: .env 文件绝不能提交到 git
# ==========================================

# 数据库
DB_HOST=localhost
DB_PORT=3306
DB_NAME=hardware_mall
DB_USERNAME=hardware_mall
DB_PASSWORD=CHANGE_ME

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# RabbitMQ
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=admin
RABBITMQ_PASSWORD=CHANGE_ME

# JWT (用 openssl rand -base64 32 生成)
JWT_SECRET=CHANGE_ME_TO_32_BYTE_RANDOM_STRING
JWT_EXPIRATION=86400000

# 管理员
ADMIN_USERNAME=admin
ADMIN_PASSWORD=CHANGE_ME

# 微信
WECHAT_APPID=CHANGE_ME
WECHAT_SECRET=CHANGE_ME

# 阿里云 OSS
OSS_ACCESS_KEY_ID=CHANGE_ME
OSS_ACCESS_KEY_SECRET=CHANGE_ME
OSS_BUCKET_NAME=CHANGE_ME
OSS_REGION=cn-beijing
OSS_DOMAIN=CHANGE_ME

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:*
ENVEOF
```

- [ ] **Step 3: 提交**

```bash
cd /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake
git add .gitignore .env.example
git commit -m "chore: add root .gitignore and .env.example template"
```

---

### Task 2: 更新 backend .gitignore

**Files:**
- Modify: `hardware-mall-backend/.gitignore`

- [ ] **Step 1: 追加敏感文件排除规则**

Current `hardware-mall-backend/.gitignore`:
```
target/
*.class
*.jar
*.war
*.log
.idea/
*.iml
.DS_Store
```

Edit to append these lines at the end:

```diff
target/
*.class
*.jar
*.war
*.log
.idea/
*.iml
.DS_Store
+.env
+.env.local
+.env.*.local
+application-prod.yml
```

- [ ] **Step 2: 提交**

```bash
cd /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend
git add .gitignore
git commit -m "chore: exclude .env and application-prod.yml from backend git tracking"
```

---

### Task 3: application.yml 密钥环境变量化

**Files:**
- Modify: `hardware-mall-backend/src/main/resources/application.yml`

- [ ] **Step 1: 将所有硬编码密钥替换为环境变量占位符**

将 `hardware-mall-backend/src/main/resources/application.yml` 替换为以下内容：

```yaml
server:
  port: 8080
  address: 0.0.0.0

spring:
  application:
    name: hardware-mall
  profiles:
    active: dev
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:hardware_mall}?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD}
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    database: 0
    password: ${REDIS_PASSWORD:}
    timeout: 10s
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 2
        max-wait: 3s
    redisson:
      config: |
        singleServerConfig:
          address: "redis://${REDIS_HOST:localhost}:${REDIS_PORT:6379}"
          database: 0
  task:
    execution:
      pool:
        core-size: 5
        max-size: 20
        queue-capacity: 100
        keep-alive: 60s
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:guest}
    password: ${RABBITMQ_PASSWORD:guest}
    publisher-confirm-type: correlated
    publisher-returns: true
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 1

mybatis-plus:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.example.mystore.entity
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto

jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION:86400000}

admin:
  username: ${ADMIN_USERNAME:admin}
  password: ${ADMIN_PASSWORD}

wechat:
  appid: ${WECHAT_APPID}
  secret: ${WECHAT_SECRET}

aliyun:
  oss:
    access-key-id: ${OSS_ACCESS_KEY_ID}
    access-key-secret: ${OSS_ACCESS_KEY_SECRET}
    bucket-name: ${OSS_BUCKET_NAME}
    region: ${OSS_REGION:cn-beijing}
    domain: ${OSS_DOMAIN}

cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:*}

logging:
  level:
    com.example.mystore: debug
```

- [ ] **Step 2: 创建本地开发用 .env (从模板复制)**

```bash
cd /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend
cp ../.env.example .env
```

编辑 `hardware-mall-backend/.env`，填入后端当前开发用的真实值（从 git 历史中找回当前的开发配置值填入 `DB_PASSWORD=123456` 等），注意此文件不会被 git 追踪。

- [ ] **Step 3: 验证编译通过**

```bash
cd /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend
export $(grep -v '^#' .env | xargs) && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
cd /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend
git add src/main/resources/application.yml
git commit -m "security: migrate all secrets to environment variables"
```

---

### Task 4: 创建多环境配置文件

**Files:**
- Create: `hardware-mall-backend/src/main/resources/application-dev.yml`
- Create: `hardware-mall-backend/src/main/resources/application-prod.yml`

- [ ] **Step 1: 创建 application-dev.yml**

```bash
cat > /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend/src/main/resources/application-dev.yml << 'EOF'
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:hardware_mall}?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5

logging:
  level:
    com.example.mystore: debug
    com.baomidou.mybatisplus: debug

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
EOF
```

- [ ] **Step 2: 创建 application-prod.yml**

```bash
cat > /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend/src/main/resources/application-prod.yml << 'EOF'
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:hardware_mall}?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=true
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  redis:
    lettuce:
      pool:
        max-active: 50
        max-idle: 20
        min-idle: 5
        max-wait: 3000
  rabbitmq:
    listener:
      simple:
        prefetch: 10

logging:
  level:
    com.example.mystore: info
    com.baomidou.mybatisplus: warn
  file:
    name: /var/log/hardware-mall/application.log
  logback:
    rollingpolicy:
      max-file-size: 100MB
      max-history: 30

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: when_authorized
EOF
```

- [ ] **Step 3: 更新 .env.example 添加 spring.profiles.active**

根目录 `.env.example` 末尾追加：

```bash
echo "" >> /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/.env.example
echo "# Spring Profile (dev / prod)" >> /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/.env.example
echo "SPRING_PROFILES_ACTIVE=dev" >> /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/.env.example
```

- [ ] **Step 4: 提交**

```bash
cd /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend
git add src/main/resources/application-dev.yml
# application-prod.yml is in .gitignore, verify:
git status -- src/main/resources/application-prod.yml
# Should show nothing (ignored)
git commit -m "chore: add dev/prod environment config profiles"
```

---

### Task 5: JwtUtil 添加 getRoleFromToken 方法

**Files:**
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/util/JwtUtil.java:45-48`

- [ ] **Step 1: 添加 getRoleFromToken 方法**

在 `getUserIdFromToken` 方法之后（第 48 行之后）添加：

```java
    public Integer getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        Object role = claims.get("role");
        if (role instanceof Integer) {
            return (Integer) role;
        }
        return null;
    }
```

完整修改后的 `JwtUtil.java` 关键部分：

```java
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.valueOf(claims.get("userId").toString());
    }

    public Integer getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        Object role = claims.get("role");
        if (role instanceof Integer) {
            return (Integer) role;
        }
        return null;
    }

    public Long getExpirationFromToken(String token) {
```

- [ ] **Step 2: 验证编译**

```bash
cd /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
cd /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend
git add src/main/java/com/example/mystore/util/JwtUtil.java
git commit -m "feat: add getRoleFromToken to JwtUtil for admin role checking"
```

---

### Task 6: 创建 @RequireAdmin 注解

**Files:**
- Create: `hardware-mall-backend/src/main/java/com/example/mystore/annotation/RequireAdmin.java`

- [ ] **Step 1: 创建注解**

```bash
cat > /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend/src/main/java/com/example/mystore/annotation/RequireAdmin.java << 'JAVAEOF'
package com.example.mystore.annotation;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireAdmin {
}
JAVAEOF
```

- [ ] **Step 2: 验证编译**

```bash
cd /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
cd /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend
git add src/main/java/com/example/mystore/annotation/RequireAdmin.java
git commit -m "feat: add @RequireAdmin annotation for admin API authorization"
```

---

### Task 7: 创建 AdminRoleInterceptor

**Files:**
- Create: `hardware-mall-backend/src/main/java/com/example/mystore/interceptor/AdminRoleInterceptor.java`

- [ ] **Step 1: 创建拦截器**

```bash
cat > /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend/src/main/java/com/example/mystore/interceptor/AdminRoleInterceptor.java << 'JAVAEOF'
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
JAVAEOF
```

- [ ] **Step 2: 验证编译**

```bash
cd /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
cd /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend
git add src/main/java/com/example/mystore/interceptor/AdminRoleInterceptor.java
git commit -m "feat: add AdminRoleInterceptor for @RequireAdmin authorization"
```

---

### Task 8: 修改 WebMvcConfig — 注册拦截器 + 修复 CORS

**Files:**
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/config/WebMvcConfig.java`

- [ ] **Step 1: 替换 WebMvcConfig.java**

```java
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
                    "/api/user/category/**", "/api/user/product/**", "/api/user/logistics/**"
                );
        registry.addInterceptor(adminRoleInterceptor)
                .addPathPatterns("/api/**");
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
```

变更要点：
- 注入 `AdminRoleInterceptor`（line 25）
- 注入 `allowedOrigins` 从环境变量（line 28-29）
- 注册 `adminRoleInterceptor` 在 JWT 之后（line 37-38）
- CORS 配置改为读取环境变量（line 44）

- [ ] **Step 2: 验证编译**

```bash
cd /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
cd /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend
git add src/main/java/com/example/mystore/config/WebMvcConfig.java
git commit -m "security: register AdminRoleInterceptor, fix CORS to use env variable"
```

---

### Task 9: 给所有 admin controller 添加 @RequireAdmin

**Files:**
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/AdminAuthController.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/AdminOrderController.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/AdminSpuController.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/AdminSkuController.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/AdminSpecController.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/AdminCategoryController.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/AdminLogisticsController.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/AdminUserController.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/DashboardController.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/UploadController.java`

- [ ] **Step 1: 在每个 Admin Controller 类上添加 `@RequireAdmin`**

在每个文件中的 `@RequiredArgsConstructor` 上面一行添加 `@RequireAdmin`。

**AdminAuthController.java** (line 18):
```diff
+import com.example.mystore.annotation.RequireAdmin;

 @RestController
 @RequestMapping("/api/admin")
 @RequiredArgsConstructor
+@RequireAdmin
 public class AdminAuthController {
```

**AdminOrderController.java** (line 14):
```diff
+import com.example.mystore.annotation.RequireAdmin;

 @RestController
 @RequestMapping("/api/admin/order")
 @RequiredArgsConstructor
+@RequireAdmin
 public class AdminOrderController {
```

依此类推，对以下文件做同样修改：
- `AdminSpuController.java` — `@RequestMapping("/api/admin/spu")`
- `AdminSkuController.java` — `@RequestMapping("/api/admin/sku")`
- `AdminSpecController.java` — `@RequestMapping("/api/admin/spec")`
- `AdminCategoryController.java` — `@RequestMapping("/api/admin/category")`
- `AdminLogisticsController.java` — `@RequestMapping("/api/admin/logistics")`
- `AdminUserController.java` — `@RequestMapping("/api/admin/user")`
- `DashboardController.java` — `@RequestMapping("/api/admin/dashboard")`
- `UploadController.java` — `@RequestMapping("/api/admin/upload")`

每个文件只需：
1. import 添加 `import com.example.mystore.annotation.RequireAdmin;`
2. 在 `@RequiredArgsConstructor` 下方添加 `@RequireAdmin`

- [ ] **Step 2: 验证编译**

```bash
cd /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
cd /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend
git add src/main/java/com/example/mystore/controller/admin/
git commit -m "security: add @RequireAdmin to all admin controllers"
```

---

### Task 10: 修复 GlobalExceptionHandler 异常信息泄漏

**Files:**
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/common/exception/GlobalExceptionHandler.java:40-45`

- [ ] **Step 1: 修复 RuntimeException handler**

将 RuntimeException 的 handler 中 `return Result.error(e.getMessage())` 改为返回通用消息：

```java
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        String uri = request.getRequestURI();
        log.warn("业务异常 at {} | 类型: {} | 消息: {}", uri, e.getClass().getSimpleName(), e.getMessage());
        return Result.error("系统繁忙，请稍后重试");
    }
```

`e.getMessage()` 仅保留在日志中用于排查，客户端只收到通用错误提示。

- [ ] **Step 2: 验证编译**

```bash
cd /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
cd /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend
git add src/main/java/com/example/mystore/common/exception/GlobalExceptionHandler.java
git commit -m "security: prevent internal error message leakage to API responses"
```

---

### Task 11: 创建 backend .env.example

**Files:**
- Create: `hardware-mall-backend/.env.example` (复制自根目录模板)

- [ ] **Step 1: 创建 backend .env.example**

```bash
cp /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/.env.example /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend/.env.example
```

- [ ] **Step 2: 提交**

```bash
cd /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend
git add .env.example
git commit -m "chore: add backend .env.example template"
```

---

### Task 12: 全量编译 + 测试验证

- [ ] **Step 1: 全量 Maven 编译**

```bash
cd /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend
mvn clean compile -q
```

Expected: BUILD SUCCESS (no errors)

- [ ] **Step 2: 运行现有测试**

```bash
cd /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend
mvn test
```

Expected: All existing tests pass (78 cases)

- [ ] **Step 3: 确认 .env 和 application-prod.yml 不在 git tracked 中**

```bash
cd /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend
git ls-files | grep -E '\.env$|application-prod.yml'
```

Expected: No output (这些文件被 .gitignore 排除了)

- [ ] **Step 4: 确认 application.yml 中无硬编码密钥**

```bash
cd /mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend
rg 'LTAI|ZQ0c|0e3e9d|123456|guest|your-256-bit' src/main/resources/application.yml
```

Expected: No matches (所有密钥已迁移为 `${ENV_VAR}` 占位符)

---

### Task 13: 根据实际情况执行密钥轮换

> **前置**: 此任务需在以上所有代码变更完成后执行。

- [ ] **Step 1: 阿里云 RAM 控制台创建子账号**

登录 [RAM 控制台](https://ram.console.aliyun.com)，创建子用户，勾选"Open API 调用访问"，分配策略 `AliyunOSSFullAccess`。获取新的 AccessKey ID 和 Secret。禁用/删除已泄露的主账号 AK。

更新 `hardware-mall-backend/.env` 中的 `OSS_ACCESS_KEY_ID` 和 `OSS_ACCESS_KEY_SECRET`。

- [ ] **Step 2: 生成新 JWT 密钥**

```bash
openssl rand -base64 32
```

将输出复制到 `hardware-mall-backend/.env` 中 `JWT_SECRET`。

- [ ] **Step 3: 重置微信小程序 Secret**

登录 [微信公众平台](https://mp.weixin.qq.com)，开发管理 → 开发设置 → 重置 AppSecret。将新 Secret 更新到 `.env` 中 `WECHAT_SECRET`。

- [ ] **Step 4: 修改管理员密码**

生成随机密码并更新到 `.env` 中 `ADMIN_PASSWORD`。告知自己的用户名密码。

- [ ] **Step 5: 修改数据库和 RabbitMQ 密码**

更新 `.env` 中 `DB_PASSWORD`、`RABBITMQ_PASSWORD` 为强密码。

---

### 完成检查清单

- [ ] `.gitignore` 已排除 `.env`、`application-prod.yml`
- [ ] `application.yml` 中无硬编码密钥（全部 `${}` 占位符）
- [ ] `application-dev.yml` 和 `application-prod.yml` 已创建
- [ ] 生产 profile 已关闭 SQL 日志 (`NoLoggingImpl`) 且日志级别为 `info`
- [ ] `JwtUtil.getRoleFromToken()` 方法已添加
- [ ] `@RequireAdmin` 注解已创建
- [ ] `AdminRoleInterceptor` 已创建并注册
- [ ] 10 个 admin controller 全部标注 `@RequireAdmin`
- [ ] CORS 改为从环境变量读取
- [ ] `RuntimeException` handler 不再返回 `e.getMessage()`
- [ ] `mvn compile` 编译通过
- [ ] `mvn test` 测试通过
- [ ] OSS AK 已轮换（RAM 子账号）
- [ ] JWT Secret 已轮换
- [ ] 微信 Secret 已轮换
- [ ] 管理员密码已改为强密码
- [ ] DB / RabbitMQ 密码已改为强密码
