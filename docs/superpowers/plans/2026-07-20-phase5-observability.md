# Phase 5: 可观测性 & 告警 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 后端接入 actuator + Prometheus exporter；为 PayService 所有异常分支补钉钉告警；统一 traceId（MDC + log pattern）；MySQL 每日 cron 备份 + OSS 冷备脚本。

**Architecture:** 简化方案：Actuator + micrometer-prometheus 让 Prometheus 抓取 metrics；logback-spring.xml 引入 MDC `%X{traceId}` 模式 + 全局拦截器注入 traceId；扩展现有 `DingTalkAlertService` 覆盖 PayService 所有异常分支；mysqldump cron + ossutil 冷备脚本。

**Tech Stack:** Spring Boot Actuator, Micrometer Prometheus, Logback, Aliyun OSS, mysqldump, cron

**前置约束：** Phase 0-4 已 merge。在 `phase5-observability` 分支执行。

---

### Task 1: 准备分支

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git checkout main && git pull && git checkout -b phase5-observability
```

---

### Task 2: Actuator + Prometheus metrics 接入

**Files:** `hardware-mall-backend/pom.xml`, `application.yml`, `application-prod.yml`

pom.xml 追加：
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

application.yml 追加：
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

application-prod.yml 收敛：
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
```

验证：
```bash
curl -s http://localhost:8080/actuator/prometheus | head -5
# 期望: 输出 jvm_memory_used_bytes{...} 等 metric line
```

---

### Task 3: traceId MDC 注入

**Files:** Create `TraceIdInterceptor.java` + logback-spring.xml + WebMvcConfig 注册

```java
// TraceIdInterceptor.java
MDC.put("traceId", request.getHeader("X-Trace-Id") != null 
    ? request.getHeader("X-Trace-Id") : UUID.randomUUID().toString().replace("-", ""));
// afterCompletion: MDC.remove("traceId");
```

logback-spring.xml pattern：
```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId:-}] %-5level %logger{36} - %msg%n</pattern>
```

---

### Task 4: PayService 所有异常分支补钉钉告警

**Files:** `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/PayServiceImpl.java`

检查所有 `catch (Exception e)` 分支，未告警的补：
```java
dingTalkAlertService.alert("退款确认失败", "paymentNo=" + paymentNo + "; error=" + e.getMessage());
```

覆盖点：
- `refundCallback` catch
- `queryWechatOrder` 异常上抛前
- `callback` 验签失败

---

### Task 5: MySQL 每日备份脚本

**Files:** Create `scripts/backup/db-daily-backup.sh`, `scripts/backup/oss-sync.sh`

```bash
#!/usr/bin/env bash
BACKUP_DIR="/var/backups/hardware-mall"
DATE=$(date +%Y%m%d_%H%M%S)
mysqldump -h 127.0.0.1 -P 3306 -u "$DB_USER" -p"$DB_PASSWORD" \
    --single-transaction --routines --triggers --events "$DB_NAME" | gzip > "$BACKUP_DIR/${DB_NAME}_$DATE.sql.gz"
find "$BACKUP_DIR" -name "*.sql.gz" -mtime +30 -delete
```

oss-sync.sh：用 ossutil sync 到 OSS bucket 冷备。

cron：
```cron
30 2 * * * /opt/hardware-mall/scripts/backup/db-daily-backup.sh >> /var/log/hardware-mall/backup.log 2>&1
```

---

### Task 6: Phase 5 全量验收

```bash
mvn clean test -q
docker compose up -d --build
sleep 90
curl -s http://localhost:8080/actuator/prometheus | head -5
docker exec hw-backend sh -c 'tail -5 /var/log/hardware-mall/application.log'
# 期望: 日志含 traceId [xxxxxx]
```

```bash
git push -u origin phase5-observability
```

---

## Phase 5 验收清单

| Task | 内容 | 验收 | 状态 |
|---|---|---|---|
| 2 | actuator + prometheus | curl /actuator/prometheus | ⬜ |
| 3 | TraceId MDC + logback | 日志含 traceId | ⬜ |
| 4 | PayService 告警分支 | 钉钉收到手工触发 | ⬜ |
| 5 | 备份脚本 | 脚本可执行 | ⬜ |
| 6 | 全量验收 | 容器内日志 traceId 正常 | ⬜ |
