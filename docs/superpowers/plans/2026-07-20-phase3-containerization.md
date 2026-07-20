# Phase 3: 容器化 + 服务端准备 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让生产服务器（2c/2g 云主机）一键 `docker compose up -d` 起后端 + admin Nginx 两个容器，MySQL 与 Redis 通过 apt 直装在宿主机上（方案 C，省内存）；为管理端前置 nginx Basic Auth + 双子域名（`shop.*` 给小程序、`admin.*` 给管理端）+ nginx 限流防止慢速撞库。

**Architecture:**
- 后端 Spring Boot 容器化（多阶段：maven build → jre-alpine runtime），通过 `host.docker.internal` 访问宿主机 MySQL/Redis
- admin Nginx 容器化（node build → nginx:alpine 静态托管），单 nginx.conf 同时托管两个子域名 server block
- MySQL 8.0 + Redis 7 通过 apt 直装在宿主机，仅监听 127.0.0.1，由 systemd 管理
- 端口暴露：admin 容器 80/443 → 宿主机；backend 8080 不对外暴露（仅由 nginx 反代）
- 资源预算：MySQL ~700MB + Redis ~80MB + Spring ~500MB + Nginx ~20MB + 系统 ~250MB ≈ 1.55GB（2g 服务器留 450MB 缓冲）

**Tech Stack:** Docker BuildKit multi-stage, eclipse-temurin:17-jre-alpine, nginx:alpine, MySQL 8.0 (apt), Redis 7 (apt), Docker Compose v2, htpasswd (apache2-utils)

**前置约束：** Phase 0-2 已 merge。在 `phase3-containerization` 分支执行。

---

## 文件结构（Phase 3 创建/修改）

- Create: `hardware-mall-backend/Dockerfile`
- Create: `hardware-mall-backend/.dockerignore`
- Create: `hardware-mall-admin/Dockerfile`
- Create: `hardware-mall-admin/nginx.conf`（双 server_name + Basic Auth + limit_req）
- Create: `hardware-mall-admin/.dockerignore`
- Create: `docker-compose.yml` （dev 用）
- Create: `docker-compose.prod.yml` （prod 用，仅 backend + admin 两服务）
- Create: `.dockerignore`（根目录）
- Create: `docs/server-setup.md` （apt 安装 MySQL/Redis、my.cnf/redis.conf 模板、防火墙、systemd）
- Create: `scripts/deploy/up.sh` / `down.sh` / `logs.sh`

---

### Task 1: 准备分支

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git checkout main && git pull && git checkout -b phase3-containerization
docker --version && docker compose version
```

---

### Task 2: 后端 Dockerfile（多阶段 + 2g 内存调优）

**Files:** `hardware-mall-backend/Dockerfile`, `.dockerignore`

```dockerfile
# syntax=docker/dockerfile:1.6
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:resolve
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone
COPY --from=builder /build/target/hardware-mall-*.jar app.jar

# 2g 服务器资源预算：留 ~384MB 给 JVM，余下给 MySQL 与系统
ENV JAVA_OPTS="-Xms256m -Xmx384m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=60.0"
ENV SPRING_PROFILES_ACTIVE=prod

RUN mkdir -p /var/log/hardware-mall && chmod 777 /var/log/hardware-mall
VOLUME ["/var/log/hardware-mall"]

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fs http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

- [ ] **Step 2: 验证构建**

```bash
docker build -t hardware-mall-backend:phase3-test .
```

- [ ] **Step 3: Commit**

```bash
git add hardware-mall-backend/Dockerfile hardware-mall-backend/.dockerignore
git commit -m "build(backend): 多阶段 Dockerfile (maven → jre-alpine, 2g 调优)"
```

---

### Task 3: admin Dockerfile + 双子域名 Nginx 配置

**Files:** `hardware-mall-admin/Dockerfile`, `nginx.conf`, `.dockerignore`

nginx.conf 核心结构（完整内容参考对话历史）：

```nginx
# 限流区
limit_req_zone $binary_remote_addr zone=admin_basic:10m rate=5r/m;
limit_req_zone $binary_remote_addr zone=api_general:10m rate=30r/m;

# shop 子域名
server { listen 443 ssl http2; server_name shop.yourdomain.com;
    location /api/ { proxy_pass http://backend:8080/api/; }
    location / { return 404; }
}

# admin 子域名 + Basic Auth
server { listen 443 ssl http2; server_name admin.yourdomain.com;
    root /usr/share/nginx/html;
    location /api/ { proxy_pass http://backend:8080/api/; }
    location / {
        limit_req zone=admin_basic burst=10 nodelay;
        auth_basic "Admin Area";
        auth_basic_user_file /etc/nginx/.htpasswd;
        try_files $uri $uri/ /index.html;
    }
    add_header Strict-Transport-Security "max-age=31536000" always;
}
```

Dockerfile：
```dockerfile
FROM node:18-alpine AS builder
WORKDIR /build
COPY package.json package-lock.json* ./
RUN npm config set registry https://registry.npmmirror.com && npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /build/dist /usr/share/nginx/html
EXPOSE 80 443
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost/ || exit 1
```

- [ ] **Step 2: Commit**

```bash
git add hardware-mall-admin/Dockerfile hardware-mall-admin/nginx.conf hardware-mall-admin/.dockerignore
git commit -m "build(admin): 双子域名 nginx + Basic Auth + limit_req Dockerfile"
```

---

### Task 4: docker-compose 文件

**Files:** `docker-compose.yml`（dev）, `docker-compose.prod.yml`（prod）

核心特点：
- **无 mysql/redis service**（方案 C）
- backend 用 `extra_hosts: ["host.docker.internal:host-gateway"]`
- admin 挂载宿主机证书 + htpasswd：`- /etc/nginx/certs:/etc/nginx/certs:ro` / `- /etc/nginx/.htpasswd:/etc/nginx/.htpasswd:ro`
- prod 用 image 而非 build

```bash
git add docker-compose.yml docker-compose.prod.yml .dockerignore
git commit -m "build: docker-compose (方案 C, 仅 backend+admin 两容器)"
```

---

### Task 5: 服务端准备文档

**Files:** `docs/server-setup.md`

内容包括：
1. 系统准备（apt update + docker + 工具）
2. MySQL 8.0 安装（root 密码 + 业务账号 + `bind-address=127.0.0.1` + `innodb_buffer_pool_size=512M` + `max_connections=50`）
3. Redis 7 安装（`requirepass` + `maxmemory 128mb` + `allkeys-lru` + `bind 127.0.0.1`）
4. 防火墙（ufw allow 22/80/443，拒绝所有其他入站）
5. 创建 deploy 用户 + `/opt/hardware-mall` 目录 + `.env` 配置
6. htpasswd 生成（用户名 `malladmin`，密码 P1 与后端 P2 不同）
7. 验收清单（systemctl is-active mysql redis-server）

```bash
git add docs/server-setup.md
git commit -m "docs: 新增 server-setup.md (方案 C: apt 直装 MySQL/Redis)"
```

---

### Task 6: 部署脚本

```bash
chmod +x scripts/deploy/*.sh
git add scripts/deploy/
git commit -m "chore(deploy): up/down/logs 部署脚本"
```

- `up.sh`：检查 systemctl is-active mysql redis-server → git pull → docker compose up
- `down.sh`：docker compose down
- `logs.sh`：docker compose logs -f

---

### Task 7: Phase 3 全量验收

1. `docker compose ps` → 2 容器 healthy
2. `systemctl is-active mysql redis-server` → active active
3. `curl -k -u malladmin:P1 https://admin.yourdomain.com/` → 200
4. `curl -kI https://admin.yourdomain.com/` → 401
5. 推送

---

## Phase 3 验收清单

| Task | 内容 | 验收 | 状态 |
|---|---|---|---|
| 2 | 后端 Dockerfile（2g 调优） | `docker build` 通过 | ⬜ |
| 3 | admin Dockerfile + 双子域名 nginx | build + 401/200 测试 | ⬜ |
| 4 | docker-compose（仅 backend+admin） | `compose config` 通过 | ⬜ |
| 5 | docs/server-setup.md | 文档完整 | ⬜ |
| 6 | 部署脚本 | 可执行 | ⬜ |
| 7 | 全量验收 | 2 容器 healthy + MySQL/Redis systemctl active | ⬜ |
