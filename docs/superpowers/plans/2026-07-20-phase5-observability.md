# Phase 5: 可观测性 & 告警 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 后端接入 actuator + Prometheus exporter；为 PayService 所有异常分支补钉钉告警；统一 traceId（MDC + log pattern）；MySQL 每日 cron 备份 + OSS 冷备脚本。

**Architecture:** 简化方案：Actuator + micrometer-prometheus 让 Prometheus 抓取 metrics；logback-spring.xml 引入 MDC `%X{traceId}` 模式 + 全局拦截器注入 traceId；扩展现有 `DingTalkAlertService` 覆盖 PayService lookup 失败/退款受理失败/退款回调失败所有异常分支；mysqldump cron + ossutil 冷备脚本。

**Tech Stack:** Spring Boot Actuator, Micrometer Prometheus, Logback, Aliyun OSS, mysqldump, cron

**前置约束：** Phase 0-4 已 merge。在 `phase5-observability` 分支执行。

---

## 文件结构（Phase 5 创建/修改）

- Modify: `hardware-mall-backend/pom.xml` — 加 actuator + micrometer-prometheus 依赖
- Modify: `hardware-mall-backend/src/main/resources/application.yml` — 暴露 metrics
- Create: `hardware-mall-backend/src/main/resources/logback-spring.xml` — 含 traceId MDC pattern
- Create: `hardware-mall-backend/src/main/java/com/example/mystore/interceptor/TraceIdInterceptor.java` — 注入 traceId
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/config/WebMvcConfig.java` — 注册 TraceIdInterceptor
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/PayServiceImpl.java` — 所有异常分支补 alert
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/DingTalkAlertService.java` — alert 接口扩展示例（如已支持 alert(name, content) 跳过）
- Create: `scripts/backup/db-daily-backup.sh` — MySQL dump 备份
- Create: `scripts/backup/oss-sync.sh` — 备份文件同步到 OSS
- Modify: `docs/PRODUCTION_DEPLOYMENT_PLAN.md` — 更新监控与备份章节

---

### Task 1: 准备分支

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git checkout main && git pull && git checkout -b phase5-observability
```

---

### Task 2: Actuator + Prometheus metrics 接入

**Files:**
- Modify: `hardware-mall-backend/pom.xml`
- Modify: `hardware-mall-backend/src/main/resources/application.yml`
- Modify: `hardware-mall-backend/src/main/resources/application-prod.yml`

- [ ] **Step 1: 加依赖**

Modify: `hardware-mall-backend/pom.xml`

在 dependencies 末尾追加：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

> SB3 BOM 已包含 micrometer 1.12.x，无需写版本号。

- [ ] **Step 2: application.yml 暴露 metrics endpoint**

Modify: `hardware-mall-backend/src/main/resources/application.yml`

追加：
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
    prometheus:
      enabled: true
  metrics:
    tags:
      application: hardware-mall
```

- [ ] **Step 3: 校验启动后能访问 metrics**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
ADMIN_PASSWORD=TestStrongPwd_2026 JWT_SECRET=0123456789ABCDEF0123456789ABCDEF mvn spring-boot:run &
sleep 30
curl -s http://localhost:8080/actuator/health | head -c 100
curl -s http://localhost:8080/actuator/prometheus | head -c 500
```

Expected: health `{"status":"UP"}`，prometheus 输出 metric lines like `jvm_memory_used_bytes{...}`。

Ctrl-C 停止。

- [ ] **Step 4: application-prod.yml 收敛暴露**

Modify: `hardware-mall-backend/src/main/resources/application-prod.yml`

确保 prod profile 暴露端点严格：
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
      base-path: /actuator
  endpoint:
    health:
      show-details: never
```

> 关键：prod 不暴露 info/metrics（敏感），仅 health + prometheus，且 health 不展示 detail 防泄漏内部信息。

- [ ] **Step 5: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-backend/pom.xml hardware-mall-backend/src/main/resources/application.yml hardware-mall-backend/src/main/resources/application-prod.yml
git commit -m "feat(observability): actuator + micrometer-prometheus metrics 暴露"
```

---

### Task 3: traceId MDC 注入

**Files:**
- Create: `hardware-mall-backend/src/main/java/com/example/mystore/interceptor/TraceIdInterceptor.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/config/WebMvcConfig.java`
- Create: `hardware-mall-backend/src/main/resources/logback-spring.xml`

- [ ] **Step 1: 写 TraceIdInterceptor**

Create: `hardware-mall-backend/src/main/java/com/example/mystore/interceptor/TraceIdInterceptor.java`

```java
package com.example.mystore.interceptor;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

@Component
public class TraceIdInterceptor implements HandlerInterceptor {

    public static final String TRACE_ID = "traceId";
    public static final String HEADER_NAME = "X-Trace-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = request.getHeader(HEADER_NAME);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(TRACE_ID, traceId);
        response.setHeader(HEADER_NAME, traceId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MDC.remove(TRACE_ID);
    }
}
```

> 注：SB3 用 jakarta.servlet，但拦截器签名一直用 javax.servlet（不是 jakarta）。检查 WebMvcConfig 中现有拦截器签名是否一致：
> ```bash
> grep -n "import javax\|import jakarta" hardware-mall-backend/src/main/java/com/example/mystore/interceptor
> ```
> Phase 1 全量 jakarta 化后，本文件也应使用 jakarta 导入（自动适用）。

- [ ] **Step 2: 注册到 WebMvcConfig**

Modify: `hardware-mall-backend/src/main/java/com/example/mystore/config/WebMvcConfig.java`

在现有 `addInterceptors` 中追加注册（确保 TraceId 拦截器最先注册）：
```java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    // TraceId 必须最先注册, 在 rate-limit与jwt 之前
    registry.addInterceptor(traceIdInterceptor).addPathPatterns("/api/**").order(-1);
    registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**").order(1);
    registry.addInterceptor(jwtInterceptor).addPathPatterns("/api/**").order(2);
    registry.addInterceptor(adminRoleInterceptor).addPathPatterns("/api/**").order(3);
}
```

在 WebMvcConfig 顶部注入 `private final TraceIdInterceptor traceIdInterceptor;`（假定已用 @RequiredArgsConstructor 否则 @Autowired 字段）。

- [ ] **Step 3: 写 logback-spring.xml**

Create: `hardware-mall-backend/src/main/resources/logback-spring.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <property name="LOG_PATH" value="${LOG_PATH:-/var/log/hardware-mall}"/>
    <property name="LOG_PATTERN"
              value="%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId:-}] %-5level %logger{36} - %msg%n"/>

    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
    </appender>

    <springProfile name="dev">
        <root level="INFO">
            <appender-ref ref="STDOUT"/>
        </root>
    </springProfile>

    <springProfile name="prod">
        <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
            <file>${LOG_PATH}/application.log</file>
            <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
                <fileNamePattern>${LOG_PATH}/application.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
                <maxFileSize>100MB</maxFileSize>
                <maxHistory>30</maxHistory>
                <totalSizeCap>5GB</totalSizeCap>
            </rollingPolicy>
            <encoder>
                <pattern>${LOG_PATTERN}</pattern>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="STDOUT"/>
            <appender-ref ref="FILE"/>
        </root>
    </springProfile>
</configuration>
```

- [ ] **Step 4: 测试启动 + 验证日志含 traceId**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn clean compile -q
SPRING_PROFILES_ACTIVE=dev ADMIN_PASSWORD=TestStrongPwd_2026 JWT_SECRET=0123456789ABCDEF0123456789ABCDEF mvn spring-boot:run 2>&1 | head -40 &
sleep 30
curl -s -H "X-Trace-Id: TEST123ABC" http://localhost:8080/api/user/product/list > /dev/null
```

观察日志行含 `TEST123ABC` 在 `[%X{traceId:-}]` 位置。

Ctrl-C。

- [ ] **Step 5: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-backend/src/main/java/com/example/mystore/interceptor/TraceIdInterceptor.java hardware-mall-backend/src/main/java/com/example/mystore/config/WebMvcConfig.java hardware-mall-backend/src/main/resources/logback-spring.xml
git commit -m "feat(observability): TraceId 拦截器 + MDC 日志模式 + logback 并 GZ 滚动"
```

---

### Task 4: PayService 所有异常分支补钉钉告警

**Files:**
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/PayServiceImpl.java`

**目标：** 探查发现 `refundCallback` 异常未发告警（行 245-247），补齐。同时确认 `refund` 受理失败应有告警（已有，校验）。

- [ ] **Step 1: 检查 DingTalkAlertService alert 接口签名**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
cat src/main/java/com/example/mystore/service/impl/DingTalkAlertService.java
```

期望找到 `public void alert(String title, String content)` 或类似方法。若无，加一个公共方法。

- [ ] **Step 2: 检查 PayServiceImpl 所有 catch 分支**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
grep -n "catch\|log.error\|alertService" src/main/java/com/example/mystore/service/impl/PayServiceImpl.java
```

列出所有 catch 块，比对哪些已有 alert 哪些没有。

预期未覆盖的点：
- `refundCallback` catch 异常
- `queryWechatOrder` 异常上抛前没看告警（用于"查单失败"监控）
- `callback` 验签失败 catch

- [ ] **Step 3: 给未覆盖的 catch 加 alert**

Modify: `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/PayServiceImpl.java`

针对每个未告警的 catch 块加一行（示例）：
```java
} catch (Exception e) {
    log.error("退款回调处理异常, paymentNo={}", paymentNo, e);
    dingTalkAlertService.alert("退款确认失败",
        "paymentNo=" + paymentNo + "; error=" + e.getMessage());
    // 原有逻辑保留...
}
```

每个 catch 点根据上下文调整 title 与 content。

- [ ] **Step 4: 测试构建**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn test -q
```

Expected: 测试全绿。

- [ ] **Step 5: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-backend/src/main/java/com/example/mystore/service/impl/PayServiceImpl.java
git commit -m "feat(alert): PayService 所有异常分支补钉钉告警 (退款回调/查单/验签)"
```

---

### Task 5: MySQL 每日备份脚本

**Files:**
- Create: `scripts/backup/db-daily-backup.sh`
- Create: `scripts/backup/oss-sync.sh`
- Modify: `docs/PRODUCTION_DEPLOYMENT_PLAN.md` (可选)

- [ ] **Step 1: 创建 db-daily-backup.sh**

Create: `scripts/backup/db-daily-backup.sh`

```bash
#!/usr/bin/env bash
set -e

BACKUP_DIR="${BACKUP_DIR:-/var/backups/hardware-mall}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-hardware_mall}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:?需设置 DB_PASSWORD}"

mkdir -p "$BACKUP_DIR"

DATE=$(date +%Y%m%d_%H%M%S)
FILE="$BACKUP_DIR/${DB_NAME}_${DATE}.sql.gz"

echo "[backup] 开始备份 $DB_NAME -> $FILE"
mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" \
  --single-transaction --routines --triggers --events "$DB_NAME" | gzip > "$FILE"

echo "[backup] 完成大小 $(du -h "$FILE" | awk '{print $1}')"

echo "[backup] 清理 $RETENTION_DAYS 天前的备份"
find "$BACKUP_DIR" -name "${DB_NAME}_*.sql.gz" -mtime +$RETENTION_DAYS -print -delete

echo "[backup] done"
```

- [ ] **Step 2: 创建 oss-sync.sh**

Create: `scripts/backup/oss-sync.sh`

```bash
#!/usr/bin/env bash
set -e

BACKUP_DIR="${BACKUP_DIR:-/var/backups/hardware-mall}"
OSS_BUCKET="${OSS_BUCKET:?需设置 OSS_BUCKET}"
OSS_PATH="${OSS_PATH:-backups/hardware-mall/}"
OSS_REGION="${OSS_REGION:-cn-beijing}"

if ! command -v ossutil >/dev/null 2>&1; then
  echo "[oss-sync] ossutil 未安装, 跳过. 请参考 https://help.aliyun.com/zh/oss/developer-reference/ossutil"
  exit 0
fi

echo "[oss-sync] 同步 $BACKUP_DIR -> oss://$OSS_BUCKET/$OSS_PATH"
ossutil sync "$BACKUP_DIR/" "oss://$OSS_BUCKET/$OSS_PATH" \
  -e "oss-$OSS_REGION.aliyuncs.com" \
  --recursive --update

echo "[oss-sync] done"
```

- [ ] **Step 3: 加可执行权限**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
chmod +x scripts/backup/*.sh
```

- [ ] **Step 4: 写 cron 模板到 PRODUCTION_DEPLOYMENT_PLAN.md**

Modify: `docs/PRODUCTION_DEPLOYMENT_PLAN.md` （在合适章节追加）

```markdown
## 备份策略

每日凌晨 2:30 执行 MySQL 逻辑备份, 凌晨 3:00 同步到 OSS 冷备。cron 模板:

```cron
30 2 * * * /opt/hardware-mall/scripts/backup/db-daily-backup.sh >> /var/log/hardware-mall/backup.log 2>&1
0 3 * * * /opt/hardware-mall/scripts/backup/oss-sync.sh >> /var/log/hardware-mall/backup-oss.log 2>&1
```

恢复演练: 每月一次
  - `gunzip < backups/hardware_mall_YYYYMMDD_HHMMSS.sql.gz | mysql -u ... hardware_mall_test`
```

- [ ] **Step 5: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add scripts/backup/ docs/PRODUCTION_DEPLOYMENT_PLAN.md
git commit -m "feat(backup): mysqldump 每日备份 + OSS 冷备脚本 + cron 模板"
```

---

### Task 6: Phase 5 全量验收

- [ ] **Step 1: mvn clean test**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn clean test -q
```

Expected: 全绿。

- [ ] **Step 2: docker compose 重启验证 metrics endpoint**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
docker compose down && docker compose up -d --build
sleep 90
curl -s http://localhost:8080/actuator/prometheus | head -30
curl -s http://localhost:8080/actuator/health
# 验证 logback 日志含 traceId
docker exec hw-backend sh -c 'tail -5 /var/log/hardware-mall/application.log'
```

Expected:
- prometheus endpoint 输出 metric line
- health UP
- log 行含 `[xxxxxx]` traceId 占位（即使是空也证明 pattern 正常）

- [ ] **Step 3: 手工触发一次告警验证钉钉**

手动改 PayService 一行 `log.error` 为 `alert(...)` 后，在 dev 调用一次会失败的接口（如 `queryByOrderId(不存在的 id)`）触发告警，确认钉钉机器人收到。

> 此步骤需要 dingtalk webhook + secret 真实配置在 .env 中，且钉钉机器人在线。

- [ ] **Step 4: 推送**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git push -u origin phase5-observability
```

- [ ] **Step 5: Phase 5 完成 checkpoint**

---

## Phase 5 验收清单

| Task | 内容 | 验收 | 状态 |
|---|---|---|---|
| 2 | actuator + prometheus | curl /actuator/prometheus | ⬜ |
| 3 | TraceId MDC + logback | 日志含 traceId | ⬜ |
| 4 | PayService 告警分支 | 钉钉收到手工触发 | ⬜ |
| 5 | 备份脚本 | 脚本可执行 | ⬜ |
| 6 | 全量验收 | 容器内日志 traceId 正常 | ⬜ |

---

## Self-Review

- ✅ metrics 端点 prod 收敛暴露
- ✅ traceId MDC 跨 rate-limit、jwt 拦截器上下游
- ✅ 备份脚本含 retention 自动清理，避免无界增长
- ✅ OSS 同步用 ossutil sync --update 增量
- ⚠️ 未引入 Grafana 或 Prometheus 容器部署，属可选扩展，本 Phase 不做（用户自用可手工看 dashboard）
- ⚠️ TraceIdInterceptor import 在 Phase 1 全量 jakarta 化后是 jakarta.servlet（本计划写 javax 是因为 Phase 1 文档在先）