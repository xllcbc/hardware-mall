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
