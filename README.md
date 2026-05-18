# 五金商城系统

> 五金店自用商城小程序 + Web管理端

## 项目结构

```
mystoremake/
├── hardware-mall-backend/     # Spring Boot 后端
├── hardware-mall-admin/        # Vue3 管理端
├── hardware-mall-uniapp/      # uni-app 小程序
└── docs/                      # 项目文档
```

## 技术栈

| 模块 | 技术 |
|------|------|
| 后端 | Spring Boot 2.7 + MyBatis-Plus + MySQL + Redis + JWT |
| 小程序 | uni-app + Vue3 + uView UI + Pinia |
| 管理端 | Vue3 + Element Plus + Vite + Pinia |
| 数据库 | MySQL 8.0 |
| 部署 | Docker + Nginx |

## 开发环境准备

### 1. 后端开发环境
- JDK 17+
- Maven 3.8+
- MySQL 8.0

### 2. 前端开发环境
- Node.js 18+
- HBuilderX (小程序开发)

### 3. 微信小程序
- 微信开发者工具
- 微信小程序 AppID

## 快速开始

### 后端启动

```bash
cd hardware-mall-backend

# 创建数据库 + 建表 + 管理员/物流基础数据
mysql -u root -p < src/main/resources/db/init.sql

# 导入真实商品数据（来自Excel价格表）
mysql -u root -p < src/main/resources/db/seed_real_products.sql

# 修改配置
vim src/main/resources/application.yml

# 启动项目
mvn spring-boot:run
```

### 管理端启动

```bash
cd hardware-mall-admin

npm install

npm run dev
```

### 小程序开发

```bash
cd hardware-mall-uniapp

npm install

# 使用 HBuilderX 打开项目
# 或运行以下命令
npm run dev:mp-weixin
```

## 项目文档

- [项目背景](./docs/项目背景.md)
- [商城单体项目规划文档](./docs/商城单体项目规划文档.md)
- [数据库设计文档](./docs/数据库设计文档.md)

## 开发计划

| 阶段 | 内容 | 预计时间 |
|------|------|---------|
| 第一阶段 | 环境搭建 | 1周 |
| 第二阶段 | 后端开发 | 2-3周 |
| 第三阶段 | 小程序开发 | 2周 |
| 第四阶段 | 管理端开发 | 2周 |
| 第五阶段 | 联调上线 | 1周 |

## License

MIT
