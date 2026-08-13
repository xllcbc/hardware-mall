#!/usr/bin/env bash
set -e

# ==========================================
# 五金商城 - 服务器初始化脚本 (步骤 5-8)
# 使用: bash setup-server.sh
# ==========================================

GITHUB_REPO="https://github.com/xllcbc/hardware-mall.git"
SERVER_IP="182.92.105.45"

echo "=========================================="
echo "  五金商城服务器初始化 - 开始"
echo "=========================================="
echo ""

echo "[5/8] 创建 deploy 用户与目录结构..."

useradd -m -s /bin/bash deploy 2>/dev/null && echo "  deploy 用户已创建" || echo "  deploy 用户已存在，跳过"
usermod -aG docker deploy

mkdir -p /opt/hardware-mall
chown deploy:deploy /opt/hardware-mall

echo ""
echo "  请输入 GitHub Actions 公钥内容（ssh-ed25519 AAA... 整行）："
read -r ACTIONS_PUBKEY

sudo -u deploy mkdir -p /home/deploy/.ssh
echo "$ACTIONS_PUBKEY" >> /home/deploy/.ssh/authorized_keys
chmod 600 /home/deploy/.ssh/authorized_keys
chown -R deploy:deploy /home/deploy/.ssh

echo "  公钥已配置"
echo ""

# ==================== [6/8] ====================
echo "[6/8] 克隆代码 + 配置 .env..."
echo ""

sudo -u deploy git clone "$GITHUB_REPO" /opt/hardware-mall

echo ""
echo "  --- 请依次输入以下配置 ---"
echo ""

read -rp "  业务数据库密码: " DB_PASSWORD
read -rp "  Redis 密码: " REDIS_PASSWORD
read -rp "  管理后台 ADMIN 登录密码: " ADMIN_PASSWORD

echo "  正在生成 JWT_SECRET..."
JWT_SECRET=$(openssl rand -base64 32)

#sudo -u deploy cp /opt/hardware-mall/.env.example /opt/hardware-mall/.env
sudo -u deploy bash -c "cat > /opt/hardware-mall/.env <<ENVEOF
DB_HOST=localhost
DB_PORT=3306
DB_NAME=hardware_mall
DB_USERNAME=hardware_mall
DB_PASSWORD=$DB_PASSWORD

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=$REDIS_PASSWORD

JWT_SECRET=$JWT_SECRET
JWT_EXPIRATION=86400000

ADMIN_USERNAME=admin
ADMIN_PASSWORD=$ADMIN_PASSWORD

WECHAT_APPID=
WECHAT_SECRET=
WECHAT_MCH_ID=
WECHAT_API_V3_KEY=
WECHAT_PRIVATE_KEY=
WECHAT_PUBLIC_KEY=
WECHAT_PUBLIC_KEY_ID=
WECHAT_MCH_SERIAL_NO=
WECHAT_PAY_NOTIFY_URL=

OSS_ACCESS_KEY_ID=
OSS_ACCESS_KEY_SECRET=
OSS_BUCKET_NAME=
OSS_REGION=cn-beijing
OSS_DOMAIN=

CORS_ALLOWED_ORIGINS=*

DINGTALK_WEBHOOK=
DINGTALK_SECRET=

SPRING_PROFILES_ACTIVE=prod
ENVEOF"

chmod 600 /opt/hardware-mall/.env

echo ""
echo "  .env 已生成"
echo ""

# ==================== [7/8] ====================
echo "[7/8] htpasswd + 证书目录..."

mkdir -p /etc/nginx/certs

echo "  请输入 malladmin 的 Basic Auth 密码（访问管理后台的第一道锁）："
htpasswd -c /etc/nginx/.htpasswd malladmin
chmod 644 /etc/nginx/.htpasswd

echo "  htpasswd 已配置"
echo "  /etc/nginx/certs 已创建（证书待 Phase 6' certbot 生成）"
echo ""

# ==================== [8/8] ====================
echo "[8/8] 验收..."
echo ""

echo -n "  MySQL: "
mysql -uhardware_mall -p"$DB_PASSWORD" -h127.0.0.1 hardware_mall -e "SELECT VERSION();" 2>/dev/null && echo "✅" || echo "❌"

echo -n "  Redis: "
redis-cli -h 127.0.0.1 -a "$REDIS_PASSWORD" ping 2>/dev/null | grep -q PONG && echo "✅" || echo "❌"

echo -n "  防火墙: "
ufw status | head -1

echo -n "  .env 权限: "
ls -la /opt/hardware-mall/.env | awk '{print $1, $3":"$4}'

echo ""
echo "=========================================="
echo "  服务器初始化完成"
echo "=========================================="
echo ""
echo "  项目目录: /opt/hardware-mall"
echo "  服务器 IP: $SERVER_IP"
echo "  管理员账号: admin"
echo ""
echo "  下一步: 在 GitHub repo Settings 里配置 Secrets，"
echo "  然后手动触发 backend-admin-deploy workflow 完成首次部署"
