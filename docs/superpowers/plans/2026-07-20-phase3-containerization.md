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
- Create: `docker-compose.yml` （项目根目录，dev 用）
- Create: `docker-compose.prod.yml` （项目根目录，prod 用，仅 backend + admin 两服务）
- Create: `.dockerignore` （项目根目录）
- Create: `docs/server-setup.md` （apt 安装 MySQL/Redis、my.cnf/redis.conf 模板、防火墙、systemd）
- Create: `scripts/deploy/up.sh` / `down.sh` / `logs.sh`

---

### Task 1: 准备分支

- [ ] **Step 1: 切分支**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git checkout main && git pull && git checkout -b phase3-containerization
```

- [ ] **Step 2: 检查 docker / compose 版本**

Run:
```bash
docker --version && docker compose version
```

Expected: docker 24+，compose v2。

---

### Task 2: 后端 Dockerfile（多阶段 + JVM 内存调小）

**Files:**
- Create: `hardware-mall-backend/Dockerfile`
- Create: `hardware-mall-backend/.dockerignore`

- [ ] **Step 1: 创建 .dockerignore**

Create: `hardware-mall-backend/.dockerignore`

```
target/
.idea/
*.iml
.env
*.log
C:\Users\
```

- [ ] **Step 2: 创建多阶段 Dockerfile（2g 服务器内存调优）**

Create: `hardware-mall-backend/Dockerfile`

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

# 2g 服务器资源预算：留 ~500MB 给 JVM，余下给 MySQL 与系统
# MaxRAMPercentage=60 让 JVM 在容器限额下自动按比例取堆
ENV JAVA_OPTS="-Xms256m -Xmx384m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=60.0"
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fs http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

- [ ] **Step 3: 验证后端 docker build**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
docker build -t hardware-mall-backend:phase3-test .
```

Expected: 构建成功。

- [ ] **Step 4: 测试启动（host MySQL/Redis）**

> 前提：宿主机 MySQL/Redis 已按 `docs/server-setup.md`（Task 9 创建）安装好。本步骤在 dev 机上可跳过；上生产服务器后再做端到端启动测试。

```bash
docker run --rm -d --name hw-test \
  --add-host=host.docker.internal:host-gateway \
  -e ADMIN_PASSWORD="TestStrongPwd_2026" \
  -e JWT_SECRET="0123456789ABCDEF0123456789ABCDEF" \
  -e DB_HOST=host.docker.internal \
  -e DB_NAME=hardware_mall \
  -e DB_USERNAME=hardware_mall \
  -e DB_PASSWORD=your_db_pwd \
  -e REDIS_HOST=host.docker.internal \
  -e REDIS_PASSWORD=your_redis_pwd \
  -p 8080:8080 \
  hardware-mall-backend:phase3-test
sleep 30
curl -s http://localhost:8080/actuator/health
docker logs hw-test | tail -20
docker stop hw-test
```

Expected: 返回 `{"status":"UP"}`。

- [ ] **Step 5: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-backend/Dockerfile hardware-mall-backend/.dockerignore
git commit -m "build(backend): 多阶段 Dockerfile (maven build → jre-alpine runtime, 2g 内存调优)"
```

---

### Task 3: admin Dockerfile + 双子域名 Nginx 配置

**Files:**
- Create: `hardware-mall-admin/Dockerfile`
- Create: `hardware-mall-admin/nginx.conf`（含 shop / admin 双 server_name + Basic Auth + limit_req）
- Create: `hardware-mall-admin/.dockerignore`

- [ ] **Step 1: 创建 .dockerignore**

Create: `hardware-mall-admin/.dockerignore`

```
node_modules/
dist/
.env
.env.*
*.log
.vscode/
.idea/
```

- [ ] **Step 2: 创建 nginx.conf（双子域名 + Basic Auth + 限流）**

Create: `hardware-mall-admin/nginx.conf`

```nginx
# ───────── 限流区 ─────────
# admin 路径 Basic Auth 慢速撞库防护: 5 次/分钟/IP
limit_req_zone $binary_remote_addr zone=admin_basic:10m rate=5r/m;
# /api/ 后端接口限流 (基础防爆破, 后端已有 RateLimit 注解做精细控制)
limit_req_zone $binary_remote_addr zone=api_general:10m rate=30r/m;

# ───── shop 子域名: 小程序后端 API 反代 ─────
server {
    listen 80;
    server_name shop.yourdomain.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name shop.yourdomain.com;

    ssl_certificate     /etc/nginx/certs/shop.fullchain.pem;
    ssl_certificate_key /etc/nginx/certs/shop.privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_prefer_server_ciphers off;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 1d;

    # 仅暴露 /api/, 不暴露静态文件
    location /api/ {
        limit_req zone=api_general burst=60 nodelay;
        proxy_pass http://backend:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_read_timeout 60s;
        proxy_send_timeout 60s;
    }

    # 微信支付回调路径 (无需 client cert, 走后端验签)
    location /api/user/pay/callback {
        proxy_pass http://backend:8080/api/user/pay/callback;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }

    location / {
        return 404;
    }

    server_tokens off;
}

# ───── admin 子域名: 管理端 SPA + Basic Auth ─────
server {
    listen 80;
    server_name admin.yourdomain.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name admin.yourdomain.com;

    ssl_certificate     /etc/nginx/certs/admin.fullchain.pem;
    ssl_certificate_key /etc/nginx/certs/admin.privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_prefer_server_ciphers off;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 1d;

    root /usr/share/nginx/html;
    index index.html;

    # 安全头
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header Referrer-Policy "no-referrer-when-downgrade" always;

    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml;
    gzip_min_length 1000;

    # 静态资源缓存
    location ~* \.(?:css|js|woff2?|ttf|eot|svg|png|jpg|jpeg|gif|webp)$ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    # /api/ 反代 (不走 Basic Auth, 让后端用 JWT 鉴权)
    location /api/ {
        limit_req zone=api_general burst=60 nodelay;
        proxy_pass http://backend:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_read_timeout 60s;
        proxy_send_timeout 60s;
    }

    # SPA 路径走 Basic Auth (P1 第一层)
    location / {
        limit_req zone=admin_basic burst=10 nodelay;
        auth_basic "Admin Area";
        auth_basic_user_file /etc/nginx/.htpasswd;
        try_files $uri $uri/ /index.html;
    }

    server_tokens off;
}
```

> 重要安全点：
> - `/api/` 路径**不走** Basic Auth，否则小程序 SPA 与 admin SPA 调登录接口会被双重校验混乱
> - 静态 SPA 文件走 Basic Auth（攻击者必须先过这一层才能看到登录页）
> - nginx limit_req 在 Basic Auth 之前执行，慢速撞库会被 nginx 直接 503

- [ ] **Step 3: 创建多阶段 Dockerfile**

Create: `hardware-mall-admin/Dockerfile`

```dockerfile
# syntax=docker/dockerfile:1.6
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

- [ ] **Step 4: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-admin/Dockerfile hardware-mall-admin/nginx.conf hardware-mall-admin/.dockerignore
git commit -m "build(admin): 双子域名 nginx + Basic Auth + limit_req Dockerfile"
```

---

### Task 4: docker-compose 文件

**Files:**
- Create: `docker-compose.yml` （dev 用：build 模式）
- Create: `docker-compose.prod.yml` （prod 用：image 模式，仅 backend + admin 两服务）
- Create: `.dockerignore`（根目录）

- [ ] **Step 1: 创建根 .dockerignore**

Create: `.dockerignore`

```
.git/
.opencode/
.sisyphus/
.worktrees/
docs/
scripts/
apifox/
*.md
.idea/
*.xlsx
get-docker.sh
```

- [ ] **Step 2: 创建 docker-compose.yml（dev）**

Create: `docker-compose.yml`

```yaml
name: hardware-mall

services:
  backend:
    build:
      context: ./hardware-mall-backend
      dockerfile: Dockerfile
    container_name: hw-backend
    restart: unless-stopped
    extra_hosts:
      - "host.docker.internal:host-gateway"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_HOST: host.docker.internal
      DB_PORT: 3306
      DB_NAME: ${DB_NAME:-hardware_mall}
      DB_USERNAME: ${DB_USERNAME:-hardware_mall}
      DB_PASSWORD: ${DB_PASSWORD}
      REDIS_HOST: host.docker.internal
      REDIS_PORT: 6379
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      JWT_EXPIRATION: 86400000
      ADMIN_USERNAME: ${ADMIN_USERNAME:-admin}
      ADMIN_PASSWORD: ${ADMIN_PASSWORD}
      WECHAT_APPID: ${WECHAT_APPID:-}
      WECHAT_SECRET: ${WECHAT_SECRET:-}
      WECHAT_PAY_MCH_ID: ${WECHAT_PAY_MCH_ID:-}
      WECHAT_PAY_API_V3_KEY: ${WECHAT_PAY_API_V3_KEY:-}
      WECHAT_PAY_PRIVATE_KEY: ${WECHAT_PAY_PRIVATE_KEY:-}
      WECHAT_PAY_PUBLIC_KEY: ${WECHAT_PAY_PUBLIC_KEY:-}
      WECHAT_PAY_PUBLIC_KEY_ID: ${WECHAT_PAY_PUBLIC_KEY_ID:-}
      WECHAT_PAY_MCH_SERIAL_NO: ${WECHAT_PAY_MCH_SERIAL_NO:-}
      WECHAT_PAY_NOTIFY_URL: ${WECHAT_PAY_NOTIFY_URL:-}
      OSS_ACCESS_KEY_ID: ${OSS_ACCESS_KEY_ID:-}
      OSS_ACCESS_SECRET: ${OSS_ACCESS_SECRET:-}
      OSS_BUCKET_NAME: ${OSS_BUCKET_NAME:-}
      OSS_REGION: ${OSS_REGION:-cn-beijing}
      OSS_DOMAIN: ${OSS_DOMAIN:-}
      CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS:-*}
      DINGTALK_WEBHOOK: ${DINGTALK_WEBHOOK:-}
      DINGTALK_SECRET: ${DINGTALK_SECRET:-}
    ports:
      - "8080:8080"
    networks:
      - hw-net

  admin:
    build:
      context: ./hardware-mall-admin
      dockerfile: Dockerfile
    container_name: hw-admin
    restart: unless-stopped
    depends_on:
      - backend
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - /etc/nginx/certs:/etc/nginx/certs:ro
      - /etc/nginx/.htpasswd:/etc/nginx/.htpasswd:ro
    networks:
      - hw-net

networks:
  hw-net:
    driver: bridge
```

> 关键点：
> - 无 mysql/redis service（方案 C）
> - backend 用 `extra_hosts` 加 `host.docker.internal:host-gateway` 让容器能访问宿主机
> - admin 容器把宿主机 `/etc/nginx/certs/` 与 `/etc/nginx/.htpasswd` 只读挂载进来
> - 80/443 暴露给 nginx

- [ ] **Step 3: 创建 docker-compose.prod.yml（CI 用预构建 image）**

Create: `docker-compose.prod.yml`

```yaml
name: hardware-mall-prod

services:
  backend:
    image: ${DOCKER_REGISTRY}/hardware-mall-backend:${IMAGE_TAG:-latest}
    container_name: hw-backend
    restart: unless-stopped
    extra_hosts:
      - "host.docker.internal:host-gateway"
    env_file:
      - /opt/hardware-mall/.env
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_HOST: host.docker.internal
      REDIS_HOST: host.docker.internal
    networks:
      - hw-net

  admin:
    image: ${DOCKER_REGISTRY}/hardware-mall-admin:${IMAGE_TAG:-latest}
    container_name: hw-admin
    restart: unless-stopped
    depends_on:
      - backend
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - /etc/nginx/certs:/etc/nginx/certs:ro
      - /etc/nginx/.htpasswd:/etc/nginx/.htpasswd:ro
    networks:
      - hw-net

networks:
  hw-net:
    driver: bridge
```

- [ ] **Step 4: 验证 compose config**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
docker compose -f docker-compose.yml config --quiet
docker compose -f docker-compose.prod.yml config --quiet
```

Expected: 无输出（语法正确）。

- [ ] **Step 5: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add docker-compose.yml docker-compose.prod.yml .dockerignore
git commit -m "build: docker-compose (方案 C, 仅 backend+admin 两容器, host.docker.internal 访问宿主 MySQL/Redis)"
```

---

### Task 5: 服务端准备文档（新增 docs/server-setup.md）

**Files:**
- Create: `docs/server-setup.md`

> **必读：** 本 Task 仅是创建文档，不实际执行安装。生产服务器按此文档操作一次即可。

- [ ] **Step 1: 写服务器初始化文档**

Create: `docs/server-setup.md`

````markdown
# 生产服务器初始化（方案 C：MySQL/Redis apt 直装）

> 适用：2c/2g 云服务器（Ubuntu 22.04+ / Debian 12+）
> 执行时机：服务器首次开通后、部署 docker compose 之前

## 1. 系统准备

```bash
# 更新系统
sudo apt update && sudo apt upgrade -y

# 装 docker 与 compose（如未装）
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
# 重新登录使 docker 组生效

# 装工具
sudo apt install -y mysql-server redis-server apache2-utils ufw chron
```

## 2. MySQL 8.0 安装与配置

### 2.1 初始化 root 密码与业务账号

```bash
sudo mysql <<SQL
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'YOUR_ROOT_PASSWORD';
CREATE DATABASE hardware_mall CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'hardware_mall'@'127.0.0.1' IDENTIFIED BY 'YOUR_APP_DB_PASSWORD';
GRANT ALL PRIVILEGES ON hardware_mall.* TO 'hardware_mall'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL
```

### 2.2 导入初始 schema

```bash
# 把 init.sql 复制到服务器
scp hardware-mall-backend/src/main/resources/db/init.sql deploy@server:~/
# 真实商品种子数据
scp hardware-mall-backend/src/main/resources/db/seed_real_products.sql deploy@server:~/

mysql -uhardware_mall -p -h127.0.0.1 hardware_mall < ~/init.sql
mysql -uhardware_mall -p -h127.0.0.1 hardware_mall < ~/seed_real_products.sql
```

### 2.3 调优 my.cnf（2g 服务器）

```bash
sudo tee /etc/mysql/mysql.conf.d/zz-hardware-mall.cnf > /dev/null <<'CNF'
[mysqld]
# 仅监听本机 (docker 容器通过 host.docker.internal 走宿主回环)
bind-address = 127.0.0.1
port = 3306

# 字符集
character-set-server = utf8mb4
collation-server = utf8mb4_unicode_ci

# 内存限制 (2g 服务器)
innodb_buffer_pool_size = 512M
innodb_log_file_size = 64M
innodb_flush_log_at_trx_commit = 2

# 连接数限制 (自用低并发)
max_connections = 50
max_user_connections = 30

# 慢查询日志
slow_query_log = 1
long_query_time = 1
slow_query_log_file = /var/log/mysql/slow.log

# 时区
default-time-zone = '+08:00'
CNF
```

```bash
sudo systemctl restart mysql
sudo systemctl enable mysql
```

## 3. Redis 7 安装与配置

### 3.1 设密码

```bash
sudo tee /etc/redis/redis-custom.conf > /dev/null <<'CNF'
bind 127.0.0.1
port 6379
protected-mode yes

# 内存限制 (2g 服务器)
maxmemory 128mb
maxmemory-policy allkeys-lru

# 密码 (与 .env REDIS_PASSWORD 一致)
requirepass YOUR_REDIS_PASSWORD

# 持久化
appendonly yes
appendfilename "appendonly.aof"
dir /var/lib/redis
CNF
```

### 3.2 启用配置

```bash
# 修改 /etc/redis/redis.conf 末尾 include 自定义配置
echo "include /etc/redis/redis-custom.conf" | sudo tee -a /etc/redis/redis.conf

# 启动
sudo systemctl restart redis-server
sudo systemctl enable redis-server
```

## 4. 防火墙（ufw）

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
# 仅放行 SSH / HTTP / HTTPS
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

> 关键：**不放行 3306 / 6379** — MySQL/Redis 仅靠 127.0.0.1 回环给 docker 容器访问。

## 5. 创建 deploy 用户与目录结构

```bash
# 创建专用 deploy 用户 (避免 root 操作)
sudo useradd -m -s /bin/bash deploy
sudo usermod -aG docker deploy

# 切到 deploy 设置 SSH key
sudo -u deploy mkdir -p /home/deploy/.ssh
# 把 GitHub Actions 公钥加入 ~/.ssh/authorized_keys

# 部署目录
sudo mkdir -p /opt/hardware-mall
sudo chown deploy:deploy /opt/hardware-mall

# 初始化 git
sudo -u deploy bash -c "cd /opt/hardware-mall && git clone <your-repo-url> ."

# 配 .env (从 .env.example 复制后填实值)
sudo -u deploy cp /opt/hardware-mall/.env.example /opt/hardware-mall/.env
sudo -u deploy nano /opt/hardware-mall/.env
# 关键填实:
#   DB_PASSWORD=YOUR_APP_DB_PASSWORD
#   REDIS_PASSWORD=YOUR_REDIS_PASSWORD
#   JWT_SECRET=$(openssl rand -base64 32)
#   ADMIN_PASSWORD=<16+ 位强密码, 不与 Basic Auth 同>
#   WECHAT_* 与 OSS_* 与 DINGTALK_* 视实际填
sudo chmod 600 /opt/hardware-mall/.env
```

## 6. 创建 nginx 证书目录与 htpasswd

```bash
sudo mkdir -p /etc/nginx/certs

# 安装 htpasswd 工具 (apache2-utils 已在步骤 1 装)
# 生成 Basic Auth 凭证 - **用户名不用 admin**, 用 malladmin
sudo htpasswd -c /etc/nginx/.htpasswd malladmin
# 输入 16+ 位强密码 P1 (与后端应用 admin 密码 P2 不同)

sudo chmod 644 /etc/nginx/.htpasswd
```

> 注意：证书文件在 Phase 6' 由 certbot 生成并复制到 `/etc/nginx/certs/`，本步骤先建空目录。

## 7. 验收清单

```bash
# MySQL 健康
systemctl is-active mysql
# 期望: active

mysql -uhardware_mall -p -h127.0.0.1 -e "SELECT VERSION();"
# 期望: 8.0.x

# Redis 健康
systemctl is-active redis-server
# 期望: active

redis-cli -h 127.0.0.1 -a YOUR_REDIS_PASSWORD ping
# 期望: PONG

# 防火墙
sudo ufw status
# 期望: 22/80/443 ALLOW, 其他 DENY

# 部署目录与 env
ls -la /opt/hardware-mall/.env
# 期望: 权限 600, deploy:deploy

# htpasswd
ls -la /etc/nginx/.htpasswd
sudo cat /etc/nginx/.htpasswd
# 期望: malladmin:$apr1$... 开头
```

## 8. 升级与备份

### 升级 MySQL/Redis

```bash
sudo apt update && sudo apt upgrade mysql-server redis-server
sudo systemctl restart mysql redis-server
```

### 日常备份（与 Phase 5 备份脚本配合）

mysqldump 通过 127.0.0.1 访问本地 MySQL：

```bash
DB_PASSWORD=YOUR_APP_DB_PASSWORD /opt/hardware-mall/scripts/backup/db-daily-backup.sh
```

写入 root cron 自动化（在 Phase 5 已规划）。
````

- [ ] **Step 2: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add docs/server-setup.md
git commit -m "docs: 新增 server-setup.md 生产服务器初始化指南 (方案 C: apt 直装 MySQL/Redis)"
```

---

### Task 6: 生成 htpasswd 演示 + 验证 admin build

- [ ] **Step 1: 本地生成一份测试 .htpasswd 验证 admin build**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
sudo apt install -y apache2-utils 2>/dev/null || echo "在 WSL/Ubuntu 已装"
sudo mkdir -p /etc/nginx/certs
sudo htpasswd -bc /etc/nginx/.htpasswd malladmin TestPassWord_P1_16Chars
```

> 注：测试用密码仅本机验证用，生产服务器请用强密码 P1。

- [ ] **Step 2: admin docker build**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-admin"
docker build -t hardware-mall-admin:phase3-test .
```

Expected: 构建成功。

- [ ] **Step 3: 启动 admin 容器（自签证书占位测试）**

由于 certs 目录暂无 LE 证书，nginx 会因找不到证书 fail。先生成自签占位：

```bash
openssl req -x509 -nodes -days 1 \
  -newkey rsa:2048 \
  -keyout /etc/nginx/certs/shop.privkey.pem \
  -out /etc/nginx/certs/shop.fullchain.pem \
  -subj "/CN=shop.yourdomain.com" 2>&1 | tail -3

openssl req -x509 -nodes -days 1 \
  -newkey rsa:2048 \
  -keyout /etc/nginx/certs/admin.privkey.pem \
  -out /etc/nginx/certs/admin.fullchain.pem \
  -subj "/CN=admin.yourdomain.com" 2>&1 | tail -3
```

```bash
docker run --rm -d --name admin-test \
  -p 443:443 \
  -v /etc/nginx/certs:/etc/nginx/certs:ro \
  -v /etc/nginx/.htpasswd:/etc/nginx/.htpasswd:ro \
  --add-host=host.docker.internal:host-gateway \
  hardware-mall-admin:phase3-test
sleep 3
curl -kI https://localhost/ 2>&1 | head -5
docker stop admin-test
```

Expected: 返回 `401 Unauthorized`（Basic Auth 触发，未带凭证）。

带凭证测试：
```bash
curl -kI -u malladmin:TestPassWord_P1_16Chars https://localhost/ 2>&1 | head -5
```

Expected: 返回 `200 OK`（静态文件返回）。

> 注：502 是正常的（backend 容器没起来，/api/ 反代失败），我们只验证 admin 静态 + Basic Auth。

清理测试证书：
```bash
sudo rm /etc/nginx/certs/*.pem
```

- [ ] **Step 4: Commit（如有变更）**

如果上一步 build 流程未改文件，跳过 commit；否则：
```bash
git status  # 检查有无未提交变更
```

---

### Task 7: 部署脚本

**Files:**
- Create: `scripts/deploy/up.sh`
- Create: `scripts/deploy/down.sh`
- Create: `scripts/deploy/logs.sh`

- [ ] **Step 1: 创建脚本**

Create: `scripts/deploy/up.sh`

```bash
#!/usr/bin/env bash
set -e

cd "$(dirname "$0")/../.."

if [ ! -f .env ]; then
  echo "错误: .env 不存在, 请 cp .env.example .env 并填入真实值"
  exit 1
fi

# 依赖检查: MySQL/Redis 必须在宿主机上跑
if ! systemctl is-active --quiet mysql; then
  echo "错误: mysql 服务未运行, 请参考 docs/server-setup.md 安装与启动"
  exit 1
fi
if ! systemctl is-active --quiet redis-server; then
  echo "错误: redis-server 服务未运行, 请参考 docs/server-setup.md 安装与启动"
  exit 1
fi

# 证书目录检查
if [ ! -d /etc/nginx/certs ] || [ -z "$(ls /etc/nginx/certs 2>/dev/null)" ]; then
  echo "警告: /etc/nginx/certs 下未发现证书, 请先执行 Phase 6' certbot 签发"
fi

# htpasswd 检查
if [ ! -f /etc/nginx/.htpasswd ]; then
  echo "警告: /etc/nginx/.htpasswd 不存在, admin Basic Auth 会失效, 请参考 docs/server-setup.md 生成"
fi

echo "[1/2] 拉取最新代码..."
git pull --ff-only

echo "[2/2] 构建并启动..."
docker compose -f docker-compose.prod.yml up -d --build

echo "完成。状态:"
docker compose -f docker-compose.prod.yml ps
```

Create: `scripts/deploy/down.sh`

```bash
#!/usr/bin/env bash
cd "$(dirname "$0")/../.."
docker compose -f docker-compose.prod.yml down
```

Create: `scripts/deploy/logs.sh`

```bash
#!/usr/bin/env bash
cd "$(dirname "$0")/../.."
TARGET="${1:-backend}"
docker compose -f docker-compose.prod.yml logs -f --tail=200 "$TARGET"
```

- [ ] **Step 2: 加可执行权限**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
chmod +x scripts/deploy/*.sh
```

- [ ] **Step 3: Commit**

```bash
git add scripts/deploy/
git commit -m "chore(deploy): 添加 up/down/logs 一键部署脚本 (含 MySQL/Redis 依赖检查)"
```

---

### Task 8: application-prod.yml 校验

**Files:**
- Modify: `hardware-mall-backend/src/main/resources/application-prod.yml`

- [ ] **Step 1: 检查 prod 配置纯 env 化**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
cat src/main/resources/application-prod.yml
```

确认所有项 `${...}` env 占位，无硬编码。

- [ ] **Step 2: 确保 log 路径在容器内可写**

Modify: `hardware-mall-backend/Dockerfile`（在 Task 2 已建基础上）

在 runtime 阶段补：
```dockerfile
RUN mkdir -p /var/log/hardware-mall && chmod 777 /var/log/hardware-mall
VOLUME ["/var/log/hardware-mall"]
```

> 重新构建后 backend 容器写日志到 `/var/log/hardware-mall/application.log`。可用 docker volume 默认管理或挂载到宿主机 `/var/log/hardware-mall/`。

- [ ] **Step 3: Commit（如 Dockerfile 改动）**

```bash
git add hardware-mall-backend/Dockerfile hardware-mall-backend/src/main/resources/application-prod.yml
git commit -m "build(backend): /var/log 目录创建 + VOLUME 声明"
```

---

### Task 9: Phase 3 全量验收

**前置条件：** 必须在生产服务器（或一台已按 `docs/server-setup.md` 完整初始化的 Ubuntu 22.04+ 测试机）上执行。本机 WSL/Windows 无 systemd 与 apt 装 MySQL 路径，可能不符合。

- [ ] **Step 1: 服务器上预制证书占位（如 Phase 6' 还没跑）**

```bash
# ssh deploy@server
# 如 Phase 6' 已完成 LE 签发, 跳过本步
# 否则跑自签占位先让 admin 容器能启动
sudo mkdir -p /etc/nginx/certs
sudo openssl req -x509 -nodes -days 1 -newkey rsa:2048 \
  -keyout /etc/nginx/certs/shop.privkey.pem \
  -out /etc/nginx/certs/shop.fullchain.pem \
  -subj "/CN=shop.yourdomain.com"
sudo openssl req -x509 -nodes -days 1 -newkey rsa:2048 \
  -keyout /etc/nginx/certs/admin.privkey.pem \
  -out /etc/nginx/certs/admin.fullchain.pem \
  -subj "/CN=admin.yourdomain.com"
```

- [ ] **Step 2: 完整启动**

```bash
# ssh deploy@server
cd /opt/hardware-mall
git pull
./scripts/deploy/up.sh
sleep 60
docker compose -f docker-compose.prod.yml ps
```

Expected: 2 个容器 `Up (healthy)`。

- [ ] **Step 3: MySQL/Redis 状态**

```bash
systemctl is-active mysql redis-server
# 期望: active active
```

- [ ] **Step 4: 端到端 curl（自签证书阶段加 -k）**

```bash
# shop API 反代（443 走证书）
curl -k https://shop.yourdomain.com/api/user/product/list | head -c 200
# 期望: {"code":200,...}

# admin 走 Basic Auth
curl -kI https://admin.yourdomain.com/
# 期望: 401 Unauthorized

curl -kI -u malladmin:YOUR_P1 https://admin.yourdomain.com/
# 期望: 200 OK (静态文件)

# admin API 走 Basic Auth 后调登录
curl -k -u malladmin:YOUR_P1 -X POST https://admin.yourdomain.com/api/admin/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"YOUR_P2"}'
# 期望: {"code":200,"data":{"token":"..."}}
```

- [ ] **Step 5: 推送**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git push -u origin phase3-containerization
```

- [ ] **Step 6: Phase 3 完成 checkpoint**

---

## Phase 3 验收清单

| Task | 内容 | 验收 | 状态 |
|---|---|---|---|
| 2 | 后端 Dockerfile（2g 调优） | `docker build` 通过 | ⬜ |
| 3 | admin Dockerfile + 双子域名 nginx.conf + Basic Auth | build + 401/200 测试 | ⬜ |
| 4 | docker-compose（仅 backend+admin） | `compose config` 通过 | ⬜ |
| 5 | docs/server-setup.md | 文档完整 | ⬜ |
| 6 | htpasswd 生成与 admin 启动 | 401 → 200 | ⬜ |
| 7 | 部署脚本 | `./up.sh -h` 不报错 | ⬜ |
| 8 | prod 配置 + log 目录 | backend 启动无 permission denied | ⬜ |
| 9 | 全量验收 | 2 容器 healthy + MySQL/Redis systemctl active + curl 200 | ⬜ |

---

## Self-Review

- ✅ 方案 C 落地：MySQL/Redis 宿主机直装，省 ~350MB 内存
- ✅ 2g 服务器资源预算明确（MySQL 512MB innodb + JVM 384MB heap + Redis 128MB + 余下）
- ✅ admin 双子域名隔离：shop.* 仅暴露 /api/，admin.* 走 Basic Auth 后才返回 SPA
- ✅ Basic Auth 用户名 `malladmin` 与后端 admin 不同，减少撞库攻击面
- ✅ nginx limit_req 5r/m 防 Basic Auth 慢速撞库
- ✅ 防火墙仅放行 22/80/443，MySQL/Redis bind 127.0.0.1
- ⚠️ Phase 6' 必须先跑 LE 签发才能跑 Phase 3 Step 4 全量验收。可临时用自签证书占位跑 Phase 3 验收
- ⚠️ Testcontainers 测试仍用 docker 起的 MySQL 容器（CI 环境），不影响 prod 部署方案
- ⚠️ server-setup.md 是文档不是脚本，用户在生产服务器上手工执行一次