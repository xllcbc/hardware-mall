# Phase 1: Spring Boot 2.7 → 3.2 升级 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 后端 Spring Boot 从 2.7.18 升至 3.2.x（jakarta 命名空间迁移），同步升级 jjwt/fastjson2/Redisson/MyBatis-Plus/spring-dotenv 到兼容 SB3 的版本，保持全部现有测试通过，不破坏业务逻辑。

**Architecture:** 单边纯依赖升级路径。先升级依赖版本，再用全量 `import` 替换 `javax.*` → `jakarta.*`，最后修复破环性 API 变更（jjwt 0.12 builder/parser 改名、Redisson SB starter 切换）。所有改动只针对编译通过 + 现有测试通过，不动业务接口契约。

**Tech Stack:** Spring Boot 3.2.x, Java 17, MyBatis-Plus 3.5.7+, jjwt 0.12.x, Redisson 3.31+, fastjson2 2.0.53+, Testcontainers 1.21.3

**前置约束：** Phase 0 已 merge 回主开发分支。在 `phase1-sb-upgrade` 分支执行。先 `git checkout -b phase1-sb-upgrade`。

---

## 升级版本对照表

| 依赖 | 当前版本 | 目标版本 | 备注 |
|---|---|---|---|
| spring-boot-starter-parent | 2.7.18 | 3.2.12 | 3.2.x 最后稳定版 |
| mybatis-plus-boot-starter | 3.5.5 | 3.5.7 | 兼容 SB3 |
| jjwt-api/impl/jackson | 0.11.5 | 0.12.6 | builder/parser API 改名 |
| fastjson2 + extension-spring5 | 2.0.43 | 2.0.53 | extension artifact 改名 `-spring6` |
| redisson-spring-boot-starter | 3.20.1 | 3.31.1 | 改用 `redisson-spring-boot-starter` SB3 兼容版 |
| spring-dotenv | 4.0.0 | 4.0.0 | 已支持 SB3，无需动 |
| wechatpay-java | 0.2.17 | 0.2.17 | 不动 |
| Testcontainers | 1.21.3 | 1.21.3 | 不动 |

---

## 文件结构（Phase 1 涉及）

- `hardware-mall-backend/pom.xml` — 升级所有依赖版本
- `hardware-mall-backend/src/main/java/com/example/mystore/util/JwtUtil.java` — 适配 jjwt 0.12 API
- `hardware-mall-backend/src/main/java/**/*.java` — 所有 `javax.*` → `jakarta.*` import 替换
- `hardware-mall-backend/src/test/java/**/*.java` — 测试中的 `javax.*` → `jakarta.*`
- `hardware-mall-backend/src/main/java/com/example/mystore/config/RedisConfig.java` — 验证 Redisson SB3 兼容
- `hardware-mall-backend/src/main/resources/application-prod.yml` — 验证 actuator SB3 路径

---

### Task 1: 准备分支

- [ ] **Step 1: 切出新分支**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git checkout main
git pull
git checkout -b phase1-sb-upgrade
```

- [ ] **Step 2: 确认基线测试全部通过**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn clean test -q
```

Expected: BUILD SUCCESS（所有现有测试通过；若有失败先把 Phase 0 merge 进来再开始）

---

### Task 2: 升级 pom.xml 依赖版本

**Files:**
- Modify: `hardware-mall-backend/pom.xml`

- [ ] **Step 1: 升级 spring-boot-starter-parent**

Modify: `hardware-mall-backend/pom.xml:7-12`

Replace:
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>2.7.18</version>
    <relativePath/>
</parent>
```

为:
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.12</version>
    <relativePath/>
</parent>
```

- [ ] **Step 2: 升级 properties 中的版本号**

Modify: `hardware-mall-backend/pom.xml:21-25`

Replace:
```xml
<properties>
    <java.version>17</java.version>
    <mybatis-plus.version>3.5.5</mybatis-plus.version>
    <jjwt.version>0.11.5</jjwt.version>
</properties>
```

为:
```xml
<properties>
    <java.version>17</java.version>
    <mybatis-plus.version>3.5.7</mybatis-plus.version>
    <jjwt.version>0.12.6</jjwt.version>
</properties>
```

- [ ] **Step 3: 升级 fastjson2（artifact 改名）**

Modify: `hardware-mall-backend/pom.xml:113-122`

Replace:
```xml
<dependency>
    <groupId>com.alibaba.fastjson2</groupId>
    <artifactId>fastjson2</artifactId>
    <version>2.0.43</version>
</dependency>
<dependency>
    <groupId>com.alibaba.fastjson2</groupId>
    <artifactId>fastjson2-extension-spring5</artifactId>
    <version>2.0.43</version>
</dependency>
```

为:
```xml
<dependency>
    <groupId>com.alibaba.fastjson2</groupId>
    <artifactId>fastjson2</artifactId>
    <version>2.0.53</version>
</dependency>
<dependency>
    <groupId>com.alibaba.fastjson2</groupId>
    <artifactId>fastjson2-extension-spring6</artifactId>
    <version>2.0.53</version>
</dependency>
```

> 关键：`-extension-spring5` → `-extension-spring6`，对应 SB3 的 Spring Framework 6。

- [ ] **Step 4: 升级 Redisson（artifact 变化）**

Modify: `hardware-mall-backend/pom.xml:130-145`

Replace:
```xml
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.20.1</version>
    <exclusions>
        <exclusion>
            <groupId>org.redisson</groupId>
            <artifactId>redisson-spring-data-30</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-data-27</artifactId>
    <version>3.20.1</version>
</dependency>
```

为:
```xml
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.31.1</version>
    <exclusions>
        <exclusion>
            <groupId>org.redisson</groupId>
            <artifactId>redisson-spring-data-32</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-data-33</artifactId>
    <version>3.31.1</version>
</dependency>
```

> Redisson 3.31.1 的 starter 默认带入 `redisson-spring-data-32`（对应 Spring Data Redis 3.2，SB 3.2.x 用的是 Spring Data Redis 3.2/3.3）。需 exclude 后改为 `redisson-spring-data-33`，匹配 SB 3.2.12 用的 Spring Data Redis 版本。

> 若报错 Redisson 找不到 spring-data 版本，回退方案：先不 exclude，直接用 starter 内置 + 测试启动。如能跑通则说明 starter 已正确选 spring-data-3x。

- [ ] **Step 5: jjwt 在 properties 已升级到 0.12.6，无需再动 pom 依赖**

- [ ] **Step 6: mybatis-plus 在 properties 已升级到 3.5.7，无需再动**

- [ ] **Step 7: 验证依赖（download + tree**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn dependency:resolve -q
```

Expected: BUILD SUCCESS 无 align failure；如有冲突看 conflict 输出。

- [ ] **Step 8: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-backend/pom.xml
git commit -m "build(deps): 升级 SB2.7→3.2.12, jjwt 0.12.6, fastjson2 2.0.53, Redisson 3.31.1"
```

---

### Task 3: 全量 import 替换 javax → jakarta

**Files:** 所有 `src/main/java` 与 `src/test/java` 下的 .java 文件

- [ ] **Step 1: 统计受影响文件数**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
grep -rl "import javax\." src/main/java src/test/java | wc -l
```

Expected: 输出受影响文件总数（预计 5-20 个）

- [ ] **Step 2: 列出哪些 javax 包被使用**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
grep -rh "^import javax\." src/main/java src/test/java | sort -u
```

Core 预期会出现：
- `import javax.servlet.*` → `jakarta.servlet.*`
- `import javax.validation.*` → `jakarta.validation.*`
- `import javax.persistence.*` → `jakarta.persistence.*`（如果有）
- `import javax.annotation.*` → `jakarta.annotation.*`

特殊保留：
- `javax.crypto.*`（Java JDK 自带，SB3 不改名）
- `java.nio.charset.*` / `java.util.*` 等 JDK 内置不变

- [ ] **Step 3: 批量替换主要 import**

执行下面的批量替换（手动逐个或脚本均可）。安全做法：用 `find + sed` 限定到具体包名：

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"

# javax.servlet → jakarta.servlet
find src/main/java src/test/java -name "*.java" -exec sed -i 's/import javax\.servlet\./import jakarta.servlet./g' {} +

# javax.validation → jakarta.validation
find src/main/java src/test/java -name "*.java" -exec sed -i 's/import javax\.validation\./import jakarta.validation./g' {} +

# javax.persistence → jakarta.persistence (如有)
find src/main/java src/test/java -name "*.java" -exec sed -i 's/import javax\.Persistence\./import jakarta.persistence./g' {} +
find src/main/java src/test/java -name "*.java" -exec sed -i 's/import javax\.persistence\./import jakarta.persistence./g' {} +

# javax.annotation.Resource/PostConstruct/PreDestroy → jakarta.annotation.*
find src/main/java src/test/java -name "*.java" -exec sed -i 's/import javax\.annotation\./import jakarta.annotation./g' {} +
```

> ⚠️ 警告：`javax.crypto.SecretKey` 是 JDK 自带**不要替换**。
> 上面的 sed 命令只针对 `javax.servlet/validation/persistence/annotation` 这几个具体包名，所以 `javax.crypto` 不受影响。

- [ ] **Step 4: 验证无残留 javax.servlet/validation/persistence/annotation import**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
grep -rn "^import javax\.\(servlet\|validation\|persistence\|annotation\)\." src/main/java src/test/java
```

Expected: 无输出（全部已替换）

- [ ] **Step 5: 验证 javax.crypto 等保留项还在**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
grep -rn "^import javax\." src/main/java src/test/java
```

Expected: 仅剩 `import javax.crypto.*` 之类的 JDK 内置 javax。

- [ ] **Step 6: 编译**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn compile -q 2>&1 | head -50
```

Expected: 可能仍报 jjwt/Redisson 等的 API 错误（下一步处理）；javax → jakarta 错误应已解决。把首次错误日志记录下来。

- [ ] **Step 7: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-backend/src
git commit -m "refactor: javax → jakarta 全量 import 迁移 (SB3)"
```

---

### Task 4: 适配 jjwt 0.12 API

**Files:**
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/util/JwtUtil.java`

**jjwt 0.12 API 变化：**
- `SignatureAlgorithm.HS256` 已废弃 → 改用 `Jwts.SIG.HS256` 或直接 `signWith(key)` 让 SDK 自选
- `Jwts.builder().setClaims(...).setIssuedAt(...).setExpiration(...)` 中的 `setClaims` 被改成 `claims(...)`（仍向后兼容但 0.12 推荐用 `Jwts.claims().add(...)`）
- `Jwts.parserBuilder()` → `Jwts.parser()`（builder 已废弃）

- [ ] **Step 1: 改写 JwtUtil**

Modify: `hardware-mall-backend/src/main/java/com/example/mystore/util/JwtUtil.java`

完整替换文件内容：
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
        Claims claims = parseToken(token);
        return claims.getExpiration().getTime();
    }

    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}
```

> 关键 API 变化：
> - `setClaims` → `claims`
> - `setIssuedAt` → `issuedAt`
> - `setExpiration` → `expiration`
> - `signWith(key, SignatureAlgorithm.HS256)` → `signWith(key)`（SDK 自动选择 HMAC 算法，因为 key 是 SecretKey）
> - `Jwts.parserBuilder().setSigningKey(...).build().parseClaimsJws(...).getBody()` → `Jwts.parser().verifyWith(...).build().parseSignedClaims(...).getPayload()`

- [ ] **Step 2: 检查所有其他文件用 JwtUtil 的地方无需变动**

JwtUtil 对外暴露方法签名（generateToken/parseToken/getUserIdFromToken/getExpirationFromToken）都未变，调用方无需改动。

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
grep -rn "import io\.jsonwebtoken\." src/main/java src/test/java
```

Expected: 只在 `JwtUtil.java` 出现。

- [ ] **Step 3: 编译 + 测试**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn test -q 2>&1 | tail -40
```

Expected: jjwt 相关错误消除。若仍有 Redisson 或其他错误，进入下一 Task。

- [ ] **Step 4: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-backend/src/main/java/com/example/mystore/util/JwtUtil.java
git commit -m "refactor: JwtUtil 适配 jjwt 0.12 新 builder/parser API"
```

---

### Task 5: 修复 Redisson 与 Spring Data Redis 兼容性

**Files:**
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/config/RedisConfig.java`（如有）
- Modify: `hardware-mall-backend/src/main/resources/application.yml`

- [ ] **Step 1: 编译运行看具体错误**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn test -q 2>&1 | grep -iE "redisson|Redisson|redis" | head -30
```

观察错误类型：
- 错误 "Redisson not configured" → application.yml 中 `spring.redis` 路径需改 `spring.data.redis`（SB3 改名）
- 错误 "Could not autowire RedissonClient" → starter 自动装配可能失败

- [ ] **Step 2: 修改 application.yml 的 Redis 配置路径**

Modify: `hardware-mall-backend/src/main/resources/application.yml:15-31`

如果当前是：
```yaml
spring:
  redis:
    host: ...
    ...
```

改为：
```yaml
spring:
  data:
    redis:
      host: ...
      ...
```

> SB3 把 `spring.redis.*` 全部移到 `spring.data.redis.*` 下。

如果原本就用 `spring.redis.*` 路径，需整体加一层 `data:` 嵌套。

- [ ] **Step 3: 检查 RedisConfig 中是否手动绑定 spring.redis**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
grep -n "redis" src/main/java/com/example/mystore/config/RedisConfig.java 2>/dev/null
```

如有 `@Value("${spring.redis...")` 或 `@ConfigurationProperties` 用了 `spring.redis` 路径，需改 `spring.data.redis`。

- [ ] **Step 4: 启动 Redis 信心测试**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn test -q 2>&1 | tail -30
```

Expected: Redis 相关错误消除（若测试用 Redis Testcontainers 是 Testcontainers 启动，则换 spring data redis 路径也不影响 Testcontainers 自动注入；若是嵌入 H2 + Mock 则无 Redis 启动检测）。

- [ ] **Step 5: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-backend/src/main/resources/application.yml hardware-mall-backend/src/main/java/com/example/mystore/config
git commit -m "fix(redis): spring.redis → spring.data.redis 迁移 (SB3)"
```

---

### Task 6: 修复 application-prod.yml 与其他配置

**Files:**
- Modify: `hardware-mall-backend/src/main/resources/application-prod.yml`
- Modify: `hardware-mall-backend/src/main/resources/application-dev.yml`

- [ ] **Step 1: 检查 prod/dev 是否引用 spring.redis**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
grep -n "spring\.\(redis\|data\.redis\)" src/main/resources/application-prod.yml src/main/resources/application-dev.yml
```

若有 `spring.redis` → 改为 `spring.data.redis`。

- [ ] **Step 2: actuator 检查**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
grep -n "management\." src/main/resources/application-prod.yml
```

SB3 actuator 配置路径未变（`management.endpoints.web.*`），无需改动。

- [ ] **Step 3: 检查 spring-dotenv 是否仍正常工作**

`me.paulschwarz:spring-dotenv:4.0.0` 自 4.x 起兼容 SB3。无需改动。

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn dependency:tree | grep dotenv
```

Expected: 看到 `me.paulschwarz:spring-dotenv:jar:4.0.0:compile`。

- [ ] **Step 4: 全量构建**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn clean test -q
```

Expected: 全部测试通过。

- [ ] **Step 5: 启动冒烟**

临时配置完整 env（参考 `.env.example`），启动应用：

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
# 假定 MySQL 与 Redis 已在本地运行
ADMIN_PASSWORD="Test_Strong_Pwd_2026" \
JWT_SECRET="0123456789ABCDEF0123456789ABCDEF" \
DB_HOST=localhost \
DB_NAME=hardware_mall \
DB_USERNAME=root \
DB_PASSWORD= \
mvn spring-boot:run 2>&1 | head -50
```

Expected: 看到 `Started HardwareMallApplication` 与 Tomcat 已启动。

测试基本 API：
```bash
# 另开一个 terminal
curl -s http://localhost:8080/api/user/product/list | head -c 200
```

Expected: 返回 JSON 含 `"code":200`。

Ctrl-C 停止应用。

- [ ] **Step 6: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-backend/src/main/resources
git commit -m "fix(config): application-prod/dev 适配 Spring Boot 3 配置路径"
```

---

### Task 7: 修复 SpringBoot 拦截器注册 API 变化（如有）

**Files:**
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/config/WebMvcConfig.java`

- [ ] **Step 1: 检查 WebMvcConfig**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
cat src/main/java/com/example/mystore/config/WebMvcConfig.java
```

检查：
- 是否实现 `WebMvcConfigurer`（SB3 仍支持，无需变）
- `addInterceptors` 方法签名（SB3 仍兼容）
- `addCorsMappings`、`addResourceHandlers`（兼容）

SB3 主要破坏：
- `WebMvcConfigurer` 包路径：`org.springframework.web.servlet.config.annotation.WebMvcConfigurer`（不变）
- 若用 `WebSecurityConfigurerAdapter` 已删除（你项目没用 Spring Security，无需处理）

如果代码无改动需求，跳过 Task 7。

- [ ] **Step 2: 启动验证**

如果 Task 6 Step 5 已成功启动，本 Task 无需额外改动。否则补错。

- [ ] **Step 3: Commit（如有改动）**

仅当 WebMvcConfig 需要调整时才 commit；否则跳过。

---

### Task 8: 修复测试中的 SB3 兼容性

**Files:** 所有 `src/test/java` 下的测试

- [ ] **Step 1: 跑测试看错误**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn test -q 2>&1 | grep -E "(ERROR|FAIL)" | head -30
```

- [ ] **Step 2: 修复测试 fail**

逐项看 fail 原因。SB3 测试常见调整：
- `@SpringBootTest` 仍可用
- `MockMvc` 的 import jakarta.hmvc → 已替换 javax 包
- H2 兼容性应无问题
- Testcontainers MySQL `withDatabaseName` 等 API 不变
- `@EnabledIfDockerAvailable` 仍可用

- [ ] **Step 3: 测试通过验证**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn clean test -q
```

Expected: BUILD SUCCESS, 0 failures。

- [ ] **Step 4: Commit（如有改动）**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-backend/src/test
git commit -m "test: 适配 Spring Boot 3 测试兼容性"
```

---

### Task 9: Phase 1 全量验收

- [ ] **Step 1: 编译 + 测试**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn clean package -q -DskipTests=false
```

Expected: BUILD SUCCESS, all tests pass, 生成 `target/hardware-mall-1.0.0.jar`。

- [ ] **Step 2: 启动验证**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
ADMIN_PASSWORD="Test_Strong_Pwd_2026" \
JWT_SECRET="0123456789ABCDEF0123456789ABCDEF" \
DB_HOST=localhost \
DB_NAME=hardware_mall \
DB_USERNAME=root \
DB_PASSWORD= \
java -jar target/hardware-mall-1.0.0.jar &
sleep 15
curl -s http://localhost:8080/api/user/product/list | head -c 200
```

Expected: 返回 JSON `"code":200`，应用正常响应。

Ctrl-C 停止应用。

- [ ] **Step 3: 推送分支**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git push -u origin phase1-sb-upgrade
```

- [ ] **Step 4: Phase 1 完成 checkpoint**

准备 merge 回主分支后启动 Phase 2。

---

## Phase 1 验收清单

| Task | 内容 | 验收 | 状态 |
|---|---|---|---|
| 1 | 创建分支 + 基线测试 | `mvn test` 在 2.7 通过 | ⬜ |
| 2 | pom 依赖升级 | `mvn dependency:resolve` | ⬜ |
| 3 | javax → jakarta 全量替换 | grep 无残留 | ⬜ |
| 4 | JwtUtil 适配 0.12 API | 编译通过 | ⬜ |
| 5 | Redisson + spring data redis 迁移 | 测试通过 | ⬜ |
| 6 | application yml 与 actuator 配置 | 启动成功 | ⬜ |
| 7 | WebMvcConfig 兼容性 | - | ⬜ |
| 8 | 测试修复 | `mvn clean test` 全绿 | ⬜ |
| 9 | 全量验收 | `mvn package` + 启动 + curl 200 | ⬜ |

---

## Self-Review

- ✅ 覆盖 SB 升级、jjwt、fastjson2、Redisson、jakarta 命名空间、Redis 配置路径
- ⚠️ Task 5 针对 Redisson Spring Data 版本号可能需根据 SB 3.2.12 的 Spring Data Redis 版本微调（3.31.1 + spring-data-33 是合理初值，若仍报错改回 spring-data-32）
- ⚠️ Task 7 留作兼容兜底，多数情况无需改动
- ✅ 测试驱动：每个改完都跑 `mvn test`
- ✅ 关键 API 变化在示例代码中详细标注，工程师不必查文档
- ✅ Phase 0 已 merge 假设成立后再开始本 Phase