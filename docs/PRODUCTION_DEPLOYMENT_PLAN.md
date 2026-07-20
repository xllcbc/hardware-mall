# 五金商城系统 - 生产级落地执行计划书

## 一、项目现状总结

| 维度 | 状态 | 说明 |
|------|------|------|
| 技术栈 | ✅ 良好 | Spring Boot 2.7.18 + MyBatis-Plus + MySQL + Redis |
| 业务功能 | ✅ 完整 | 用户端、管理端核心功能已基本实现 |
| API规范 | ✅ 良好 | Apifox文档完整，统一响应格式 |
| 架构设计 | ⚠️ 需优化 | 单体架构，缺少生产级基础设施 |

### 关键风险 (CRITICAL)

| 风险等级 | 问题 | 建议 |
|----------|------|------|
| P0 | 密钥硬编码 (OSS密钥、微信Secret、JWT密钥) | 立即移除，改用环境变量或密钥管理服务 |
| P0 | 数据库密码 `123456` | 立即修改为强密码 |
| P0 | 无Docker配置 | 添加容器化部署 |
| P1 | 无多环境配置 (dev/prod) | 拆分配置文件 |
| P1 | 无单元测试 | 添加测试覆盖 |
| P1 | 无API限流/熔断 | 添加安全防护 |
| P2 | 无监控告警 | 添加Actuator + Prometheus |
| P2 | 库存扣减无乐观锁 | 修复并发问题 |
| P2 | 日志配置缺失 | 添加生产级日志 |

---

## 二、分阶段实施计划

### 阶段一：安全加固 (优先级 P0)

#### 1.1 密钥与环境变量管理

**目标**: 移除所有硬编码密钥，改为环境变量

**执行步骤**:

1. **修改 application.yml**

```yaml
# 移除所有硬编码密钥，改用占位符
aliyun:
  oss:
    access-key-id: ${OSS_ACCESS_KEY_ID}
    access-key-secret: ${OSS_ACCESS_KEY_SECRET}
    bucket-name: ${OSS_BUCKET_NAME}
    region: ${OSS_REGION}
    domain: ${OSS_DOMAIN}

spring:
  datasource:
    password: ${DB_PASSWORD}
  redis:
    password: ${REDIS_PASSWORD:}

jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION:86400000}

wechat:
  appid: ${WECHAT_APPID}
  secret: ${WECHAT_SECRET}
```

2. **创建配置文件模板** `application.yml.template`

```yaml
# 生产环境配置模板 (不包含真实密钥)
aliyun:
  oss:
    access-key-id: YOUR_OSS_ACCESS_KEY_ID
    access-key-secret: YOUR_OSS_ACCESS_KEY_SECRET
    bucket-name: your-bucket-name
    region: cn-beijing
    domain: https://your-bucket.oss-cn-beijing.aliyuncs.com

spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/hardware_mall?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: CHANGE_ME
  redis:
    host: localhost
    port: 6379
    database: 0
    password: CHANGE_ME

jwt:
  secret: CHANGE_TO_256_BIT_RANDOM_SECRET
  expiration: 86400000

wechat:
  appid: your-wechat-appid
  secret: your-wechat-secret
```

3. **创建 .env.example**

```bash
# 数据库
DB_PASSWORD=your_strong_password_here

# Redis
REDIS_PASSWORD=your_redis_password

# JWT
JWT_SECRET=your_256_bit_random_secret_here

# 阿里云OSS
OSS_ACCESS_KEY_ID=your_access_key_id
OSS_ACCESS_KEY_SECRET=your_access_key_secret
OSS_BUCKET_NAME=your-bucket-name
OSS_REGION=cn-beijing
OSS_DOMAIN=https://your-bucket.oss-cn-beijing.aliyuncs.com

# 微信
WECHAT_APPID=your_wechat_appid
WECHAT_SECRET=your_wechat_secret
```

4. **添加 .gitignore**

```
# 环境变量文件
.env
.env.local
.env.*.local

# 敏感配置文件
application-prod.yml
```

**验收标准**:
- [ ] 搜索项目无任何真实密钥字符串
- [ ] `git log` 确认历史密钥已清除或提交

---

#### 1.2 强密码策略

**执行步骤**:
1. 修改MySQL数据库密码为强密码 (16位以上，含大小写字母、数字、特殊字符)
2. 修改JWT密钥为256位随机字符串
3. 更新OSS AccessKey，使用RAM子账号密钥而非主账号密钥
4. 微信密钥重新申请或确认当前密钥可安全使用

**工具推荐**:
- JWT密钥生成: `openssl rand -base64 32`
- 密码生成: `pwgen -s 32 1`

---

### 阶段二：配置与环境分离 (优先级 P0-P1)

#### 2.1 多环境配置

**执行步骤**:

1. **拆分配置文件**

```
src/main/resources/
├── application.yml          # 公共配置
├── application-dev.yml      # 开发环境
├── application-test.yml     # 测试环境
└── application-prod.yml     # 生产环境
```

2. **application.yml** (公共部分)

```yaml
spring:
  profiles:
    active: @spring.profiles.active@
  application:
    name: hardware-mall-backend
```

3. **application-dev.yml**

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/hardware_mall?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
  redis:
    host: localhost

logging:
  level:
    com.example: DEBUG
    com.baomidou.mybatisplus: DEBUG
```

4. **application-prod.yml**

```yaml
server:
  port: 8080
  tomcat:
    threads:
      max: 200
      min-spare: 10
    connection-timeout: 20000

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/hardware_mall?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=true
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    lettuce:
      pool:
        max-active: 50
        max-idle: 20
        min-idle: 5
        max-wait: 3000

logging:
  level:
    com.example: INFO
    com.baomidou.mybatisplus: WARN
  file:
    name: /var/log/hardware-mall/application.log
  logback:
    rollingpolicy:
      max-file-size: 100MB
      max-history: 30

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl
```

---

#### 2.2 敏感信息加密 (可选增强)

**推荐**: 使用 Jasypt 或 Spring Cloud Config KMS

```xml
<!-- pom.xml 添加依赖 -->
<dependency>
    <groupId>com.github.ulisesbocchio</groupId>
    <artifactId>jasypt-spring-boot-starter</artifactId>
    <version>3.0.5</version>
</dependency>
```

---

### 阶段三：容器化部署 (优先级 P0)

#### 3.1 后端 Dockerfile

**文件**: `hardware-mall-backend/Dockerfile`

```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/*.jar app.jar

RUN apk add --no-cache tzdata \
    && ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

#### 3.2 前端 Dockerfile

**管理端** `hardware-mall-admin/Dockerfile`

```dockerfile
FROM node:18-alpine AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci --only=production=false

COPY . .
RUN npm run build

FROM nginx:alpine

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
```

**nginx.conf**

```nginx
server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

---

#### 3.3 docker-compose.yml

**根目录** `docker-compose.yml`

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: hardware-mall-mysql
    restart: always
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}
      MYSQL_DATABASE: hardware_mall
      TZ: Asia/Shanghai
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./hardware-mall-backend/src/main/resources/db/init.sql:/docker-entrypoint-initdb.d/init.sql
    command: --default-authentication-plugin=mysql_native_password --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: hardware-mall-redis
    restart: always
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    command: redis-server --appendonly yes
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  backend:
    build:
      context: ./hardware-mall-backend
      dockerfile: Dockerfile
    container_name: hardware-mall-backend
    restart: always
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_PASSWORD: ${DB_PASSWORD}
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      OSS_ACCESS_KEY_ID: ${OSS_ACCESS_KEY_ID}
      OSS_ACCESS_KEY_SECRET: ${OSS_ACCESS_KEY_SECRET}
      OSS_BUCKET_NAME: ${OSS_BUCKET_NAME}
      OSS_REGION: ${OSS_REGION}
      OSS_DOMAIN: ${OSS_DOMAIN}
      JWT_SECRET: ${JWT_SECRET}
      WECHAT_APPID: ${WECHAT_APPID}
      WECHAT_SECRET: ${WECHAT_SECRET}
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy

  admin-frontend:
    build:
      context: ./hardware-mall-admin
      dockerfile: Dockerfile
    container_name: hardware-mall-admin
    restart: always
    ports:
      - "80:80"
    depends_on:
      - backend

volumes:
  mysql_data:
  redis_data:
```

#### 3.4 环境变量文件

**根目录** `.env`

```bash
# 数据库 - 必须修改
DB_PASSWORD=change_me_to_strong_password

# Redis - 如需认证
REDIS_PASSWORD=

# JWT - 必须修改 (256位随机字符串)
JWT_SECRET=your_256_bit_random_secret_generate_with_openssl

# 阿里云OSS
OSS_ACCESS_KEY_ID=your_access_key_id
OSS_ACCESS_KEY_SECRET=your_access_key_secret
OSS_BUCKET_NAME=java0251014
OSS_REGION=cn-beijing
OSS_DOMAIN=https://java0251014.oss-cn-beijing.aliyuncs.com

# 微信
WECHAT_APPID=wxea074cd649f1fbff
WECHAT_SECRET=your_wechat_secret
```

---

### 阶段四：高并发与安全防护 (优先级 P1)

#### 4.1 库存并发处理

**问题**: 订单创建时库存扣减无乐观锁，高并发下可能超卖

**修复方案**:

1. **方案一: 数据库乐观锁**

```sql
ALTER TABLE product ADD COLUMN version INT DEFAULT 0;
```

```java
// ProductMapper.xml
<update id="decreaseStock">
    UPDATE product
    SET stock = stock - #{quantity},
        version = version + 1
    WHERE id = #{productId}
      AND stock >= #{quantity}
      AND version = #{version}
</update>
```

2. **方案二: Redis分布式锁**

```java
@Service
public class DistributedLockService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public boolean tryLock(String key, String value, long expireTime) {
        Boolean result = redisTemplate.opsForValue()
            .setIfAbsent(key, value, expireTime, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    public void unlock(String key, String value) {
        String current = redisTemplate.opsForValue().get(key);
        if (value.equals(current)) {
            redisTemplate.delete(key);
        }
    }
}
```

#### 4.2 API限流

**添加依赖**:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
<dependency>
    <groupId>com.github.whvcse</groupId>
    <artifactId>easy-captcha</artifactId>
    <version>1.6.2</version>
</dependency>
```

**限流注解**:

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    int value() default 100;      // 每秒请求数
    int timeout() default 1;      // 锁超时时间
}
```

---

#### 4.3 熔断器 (Resilience4j)

**添加依赖**:

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot2</artifactId>
    <version>1.7.1</version>
</dependency>
```

---

### 阶段五：监控与日志 (优先级 P1-P2)

#### 5.1 Actuator 端点

**application-prod.yml 添加**:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when_authorized
  metrics:
    export:
      prometheus:
        enabled: true
```

#### 5.2 Logback 配置

**resources/logback-spring.xml**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <springProperty scope="context" name="APP_NAME" source="spring.application.name"/>

    <property name="LOG_PATTERN" value="%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg%n"/>
    <property name="LOG_FILE" value="/var/log/${APP_NAME}/application.log"/>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
    </appender>

    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_FILE}</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_FILE}.%d{yyyy-MM-dd}.%i.gz</fileNamePattern>
            <maxHistory>30</maxHistory>
            <totalSizeCap>3GB</totalSizeCap>
            <timeBasedFileNamingAndTriggeringPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
                <maxFileSize>100MB</maxFileSize>
            </timeBasedFileNamingAndTriggeringPolicy>
        </rollingPolicy>
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
    </appender>

    <appender name="ERROR_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
            <level>ERROR</level>
        </filter>
        <file>${LOG_FILE}.error</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_FILE}.error.%d{yyyy-MM-dd}.gz</fileNamePattern>
            <maxHistory>90</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
    </appender>

    <springProfile name="prod">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
            <appender-ref ref="FILE"/>
            <appender-ref ref="ERROR_FILE"/>
        </root>
    </springProfile>
</configuration>
```

---

### 阶段六：测试体系建设 (优先级 P1)

#### 6.1 添加单元测试

**添加依赖**:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-tenant</artifactId>
    <version>3.5.5</version>
    <scope>test</scope>
</dependency>
```

**测试示例** `ProductServiceTest.java`:

```java
@SpringBootTest
class ProductServiceTest {

    @Autowired
    private ProductService productService;

    @Test
    void testGetProductById() {
        Product product = productService.getById(1L);
        assertNotNull(product);
    }

    @Test
    void testDecreaseStockConcurrency() {
        // 并发测试
    }
}
```

#### 6.2 集成测试

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testCreateOrder() {
        // 集成测试
    }
}
```

---

### 阶段七：CI/CD 流水线 (优先级 P2)

#### 7.1 GitHub Actions

**`.github/workflows/deploy.yml`**:

```yaml
name: Build and Deploy

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

env:
  JAVA_VERSION: '17'

jobs:
  build-backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'
          cache: 'maven'

      - name: Build with Maven
        working-directory: hardware-mall-backend
        run: mvn clean package -DskipTests

      - name: Upload artifact
        uses: actions/upload-artifact@v4
        with:
          name: backend-jar
          path: hardware-mall-backend/target/*.jar

  build-admin:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup Node
        uses: actions/setup-node@v4
        with:
          node-version: '18'
          cache: 'npm'
          cache-dependency-path: hardware-mall-admin/package-lock.json

      - name: Install and Build
        working-directory: hardware-mall-admin
        run: |
          npm ci
          npm run build

      - name: Upload artifact
        uses: actions/upload-artifact@v4
        with:
          name: admin-dist
          path: hardware-mall-admin/dist

  deploy:
    needs: [build-backend, build-admin]
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - name: Deploy to server
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.DEPLOY_HOST }}
          username: ${{ secrets.DEPLOY_USER }}
          key: ${{ secrets.DEPLOY_KEY }}
          script: |
            cd /opt/hardware-mall
            docker-compose pull
            docker-compose up -d
```

---

### 阶段八：数据库优化 (优先级 P2)

#### 8.1 添加索引

```sql
-- 商品表索引
ALTER TABLE product ADD INDEX idx_category_id (category_id);
ALTER TABLE product ADD INDEX idx_status (status);
ALTER TABLE product ADD INDEX idx_create_time (create_time);

-- 订单表索引
ALTER TABLE shop_order ADD INDEX idx_user_id (user_id);
ALTER TABLE shop_order ADD INDEX idx_status (status);
ALTER TABLE shop_order ADD INDEX idx_create_time (create_time);

-- 购物车索引
ALTER TABLE cart ADD INDEX idx_user_id (user_id);
```

#### 8.2 数据库连接池优化

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 60000
```

---

## 三、部署检查清单

### 部署前检查

- [ ] 所有密钥已移至环境变量
- [ ] 数据库密码已修改为强密码
- [ ] JWT密钥已更换为随机256位密钥
- [ ] OSS使用RAM子账号密钥
- [ ] 生产环境配置已验证
- [ ] 单元测试通过率 > 70%
- [ ] 数据库索引已添加
- [ ] 日志配置已验证
- [ ] 监控端点已启用

### 生产环境验证

- [ ] HTTPS已配置
- [ ] 域名已备案
- [ ] CDN已配置 (可选)
- [ ] 防火墙规则已设置
- [ ] 备份策略已配置
- [ ] 灾备方案已验证

---

## 四、里程碑计划

| 阶段 | 工期 | 产出物 |
|------|------|--------|
| 阶段一 安全加固 | 1-2天 | 密钥移除完成 |
| 阶段二 环境配置 | 0.5天 | 多环境配置完成 |
| 阶段三 容器部署 | 1-2天 | Docker部署完成 |
| 阶段四 安全防护 | 2-3天 | 限流熔断完成 |
| 阶段五 监控日志 | 1-2天 | 监控告警完成 |
| 阶段六 测试体系 | 2-3天 | 测试覆盖完成 |
| 阶段七 CI/CD | 1-2天 | 自动化流水线 |
| 阶段八 数据库优化 | 0.5天 | 性能优化完成 |

**总计**: 约 9-17 个工作日

---

## 五、附录

### 5.1 快速启动脚本

**`scripts/start-production.sh`**:

```bash
#!/bin/bash
set -e

# 加载环境变量
if [ -f .env ]; then
    export $(cat .env | grep -v '^#' | xargs)
fi

# 构建后端
cd hardware-mall-backend
mvn clean package -DskipTests
cd ..

# 构建并启动
docker-compose up -d --build

# 检查状态
docker-compose ps
```

### 5.2 密钥轮换建议

| 密钥类型 | 轮换周期 | 说明 |
|----------|----------|------|
| OSS AccessKey | 90天 | 使用RAM子账号 |
| JWT密钥 | 30天 | 需配合黑名单机制 |
| 数据库密码 | 90天 | 使用高强度密码 |
| 微信Secret | 180天 | 敏感操作时更换 |

### 5.3 推荐监控方案

| 组件 | 工具 | 说明 |
|------|------|------|
| 应用监控 | Prometheus + Grafana | 性能指标 |
| 日志聚合 | ELK (Elasticsearch + Logstash + Kibana) | 日志分析 |
| 链路追踪 | Skywalking | 请求链路 |
| 告警 | AlertManager + 钉钉/飞书 | 及时通知 |

---

## 备份策略

每日凌晨 2:30 执行 MySQL 逻辑备份, 凌晨 3:00 同步到 OSS 冷备。cron 模板:

```cron
30 2 * * * /opt/hardware-mall/scripts/backup/db-daily-backup.sh >> /var/log/hardware-mall/backup.log 2>&1
0 3 * * * /opt/hardware-mall/scripts/backup/oss-sync.sh >> /var/log/hardware-mall/backup-oss.log 2>&1
```

恢复演练: 每月一次
  - `gunzip < backups/hardware_mall_YYYYMMDD_HHMMSS.sql.gz | mysql -u ... hardware_mall_test`

