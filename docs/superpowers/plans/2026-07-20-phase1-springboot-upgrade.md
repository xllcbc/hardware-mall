# Phase 1: Spring Boot 2.7 → 3.2 升级 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 后端 Spring Boot 从 2.7.18 升至 3.2.x（jakarta 命名空间迁移），同步升级 jjwt/fastjson2/Redisson/MyBatis-Plus/spring-dotenv 到兼容 SB3 的版本，保持全部现有测试通过，不破坏业务逻辑。

**Architecture:** 单边纯依赖升级路径。先升级依赖版本，再用全量 `import` 替换 `javax.*` → `jakarta.*`，最后修复破环性 API 变更（jjwt 0.12 builder/parser 改名、Redisson SB starter 切换）。所有改动只针对编译通过 + 现有测试通过，不动业务接口契约。

**Tech Stack:** Spring Boot 3.2.x, Java 17, MyBatis-Plus 3.5.7+, jjwt 0.12.x, Redisson 3.31+, fastjson2 2.0.53+, Testcontainers 1.21.3

**前置约束：** Phase 0 已 merge 回主开发分支。在 `phase1-sb-upgrade` 分支执行。

---

## 升级版本对照表

| 依赖 | 当前版本 | 目标版本 | 备注 |
|---|---|---|---|
| spring-boot-starter-parent | 2.7.18 | 3.2.12 | 3.2.x 最后稳定版 |
| mybatis-plus-boot-starter | 3.5.5 | 3.5.7 | 兼容 SB3 |
| jjwt-api/impl/jackson | 0.11.5 | 0.12.6 | builder/parser API 改名 |
| fastjson2 + extension-spring5 | 2.0.43 | 2.0.53 | extension artifact 改名 `-spring6` |
| redisson-spring-boot-starter | 3.20.1 | 3.31.1 | 改用 SB3 兼容版 |
| spring-dotenv | 4.0.0 | 4.0.0 | 已支持 SB3 |
| wechatpay-java | 0.2.17 | 0.2.17 | 不动 |
| Testcontainers | 1.21.3 | 1.21.3 | 不动 |

---

## 文件结构（Phase 1 涉及）

- `hardware-mall-backend/pom.xml` — 升级所有依赖版本
- `hardware-mall-backend/src/main/java/com/example/mystore/util/JwtUtil.java` — 适配 jjwt 0.12 API
- `hardware-mall-backend/src/main/java/**/*.java` — 所有 `javax.*` → `jakarta.*` import 替换
- `hardware-mall-backend/src/test/java/**/*.java` — 测试中的 `javax.*` → `jakarta.*`
- `hardware-mall-backend/src/main/resources/application.yml` — spring.redis → spring.data.redis
- `hardware-mall-backend/src/main/resources/application-prod.yml` — 同

---

### Task 1: 准备分支

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git checkout main && git pull && git checkout -b phase1-sb-upgrade
mvn clean test -q  # 确认基线
```

---

### Task 2: 升级 pom.xml 依赖版本

**Files:** `hardware-mall-backend/pom.xml`

- [ ] **Step 1: 升级 spring-boot-starter-parent**

```xml
<version>3.2.12</version>
```

- [ ] **Step 2: 升级 properties**

```xml
<mybatis-plus.version>3.5.7</mybatis-plus.version>
<jjwt.version>0.12.6</jjwt.version>
```

- [ ] **Step 3: 升级 fastjson2（artifact 改名）**

```xml
<artifactId>fastjson2</artifactId>
<version>2.0.53</version>
...
<artifactId>fastjson2-extension-spring6</artifactId>
<version>2.0.53</version>
```

- [ ] **Step 4: 升级 Redisson**

```xml
<artifactId>redisson-spring-boot-starter</artifactId>
<version>3.31.1</version>
<!-- exclude redisson-spring-data-32, 改用 -33 -->
```

- [ ] **Step 5: 编译验证**

```bash
mvn dependency:resolve -q && mvn compile -q
```

---

### Task 3: 全量 import 替换 javax → jakarta

```bash
# javax.servlet → jakarta.servlet
find src/main/java src/test/java -name "*.java" -exec sed -i 's/import javax\.servlet\./import jakarta.servlet./g' {} +

# javax.validation → jakarta.validation
find src/main/java src/test/java -name "*.java" -exec sed -i 's/import javax\.validation\./import jakarta.validation./g' {} +

# javax.persistence → jakarta.persistence
find src/main/java src/test/java -name "*.java" -exec sed -i 's/import javax\.persistence\./import jakarta.persistence./g' {} +

# javax.annotation → jakarta.annotation
find src/main/java src/test/java -name "*.java" -exec sed -i 's/import javax\.annotation\./import jakarta.annotation./g' {} +
```

验证无残留：
```bash
grep -rn "^import javax\.\(servlet\|validation\|persistence\|annotation\)\." src/main/java src/test/java
# 期望: 无输出
```

---

### Task 4: 适配 jjwt 0.12 API

**Files:** `hardware-mall-backend/src/main/java/com/example/mystore/util/JwtUtil.java`

关键 API 变化：
- `setClaims` → `claims`
- `setIssuedAt` → `issuedAt`
- `setExpiration` → `expiration`
- `signWith(key, SignatureAlgorithm.HS256)` → `signWith(key)`
- `Jwts.parserBuilder().setSigningKey(...).build().parseClaimsJws(...).getBody()` → `Jwts.parser().verifyWith(...).build().parseSignedClaims(...).getPayload()`

完整替换 JwtUtil.java 内容：

```java
package com.example.mystore.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Map<String, Object> claims) {
        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserIdFromToken(String token) {
        return Long.valueOf(parseToken(token).get("userId").toString());
    }

    public Integer getRoleFromToken(String token) {
        Object role = parseToken(token).get("role");
        return (role instanceof Integer) ? (Integer) role : null;
    }

    public Long getExpirationFromToken(String token) {
        return parseToken(token).getExpiration().getTime();
    }

    public boolean isTokenExpired(String token) {
        try {
            return parseToken(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}
```

---

### Task 5: 修复 Redisson + spring.data.redis

**Files:** `hardware-mall-backend/src/main/resources/application.yml`、`application-prod.yml`

```yaml
# spring.redis → spring.data.redis
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      ...
```

---

### Task 6: 修复 application-prod.yml

确保 prod 配置无硬编码值，全部 `${ENV_VAR}` env 占位。

---

### Task 7: 全量测试 + 启动冒烟

```bash
mvn clean test -q
```

启动：
```bash
ADMIN_PASSWORD="TestStrongPwd_2026" JWT_SECRET="0123456789ABCDEF0123456789ABCDEF" mvn spring-boot:run &
sleep 30
curl -s http://localhost:8080/api/user/product/list | head -c 200
# 期望: {"code":200,...}
```

---

### Task 8: Commit & push

```bash
git add -A
git commit -m "build(deps): 升级 SB2.7→3.2.12, jjwt 0.12.6, fastjson2 2.0.53, Redisson 3.31.1"
git push -u origin phase1-sb-upgrade
```

---

## Phase 1 验收清单

| Task | 内容 | 验收 | 状态 |
|---|---|---|---|
| 2 | pom 依赖升级 | `mvn dependency:resolve` | ⬜ |
| 3 | javax → jakarta 全量替换 | grep 无残留 | ⬜ |
| 4 | JwtUtil 适配 0.12 API | 编译通过 | ⬜ |
| 5 | Redisson + spring data redis 迁移 | 测试通过 | ⬜ |
| 6 | application yml 配置 | 启动成功 | ⬜ |
| 7 | 全量验收 | `mvn clean test` 全绿 + curl 200 | ⬜ |
