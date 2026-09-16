# hardware-mall-backend

商城后端服务（Spring Boot 3.2 + Java 17 + MyBatis-Plus + MySQL 8 + Redis）。

## 目录结构

```
src/main/java/com/example/mystore/
├── HardwareMallApplication.java
├── annotation/     # 自定义注解（如 @RequireAdmin）
├── common/         # 统一响应、异常、常量
├── config/         # 配置类（微信支付、OSS、Redis、CORS 等）
├── controller/     # admin/ 与 user/ 两组 REST 接口
├── entity/         # 数据库实体
├── event/          # 应用事件（库存缓存同步等）
├── interceptor/    # JWT / 角色鉴权拦截器
├── job/            # 定时任务（超时取消、自动收货、退款对账、库存补偿）
├── mapper/         # MyBatis-Plus Mapper
├── runner/         # 启动初始化
├── service/        # 业务服务
└── util/           # 工具类
```

## 运行

```bash
# 1. 建表 + 初始化基础数据
mysql -u root -p < src/main/resources/db/init.sql

# 2. 配置环境变量（.env 不入库）
cp ../.env.example .env

# 3. 启动
mvn spring-boot:run
```

默认端口 `8080`，Swagger 不可用时可直接参考 `docs/商城单体项目接口规范文档.md`。

## 测试

```bash
mvn test
```

## 关键设计

- **订单状态机**：CAS 乐观锁（条件 UPDATE + affected rows）控制并发流转。
- **退款可靠性**：claim-first 先占位再外呼；退款回调三态自愈；定时对账兜底（分布式锁 + 分批扫描）。
- **缓存**：空值哨兵、互斥锁 + double-check、TTL 抖动；SKU 库存与元数据分离缓存。
- **定时任务**：`job/` 下超时取消、自动收货、库存缓存补偿、退款对账。
- **外部集成**：微信支付 / 退款回调、微信消息推送、阿里云 OSS。
