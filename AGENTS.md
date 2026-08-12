# 五金商城 AGENTS.md

## 技术栈
- Java 17, Spring Boot 3.2.12, Spring Framework 6.1.13
- MyBatis-Plus 3.5.7 + HikariCP 5.0.1 + MySQL 8.0
- Lettuce 6.3.2 (Redis) + Redisson 3.27.0 (分布式锁)
- Fastjson2 2.0.53 (Redis 序列化) / Jackson 2.15.4 (DB JSON 列)
- jjwt 0.12.6 (JWT 认证)
- Spring Boot Actuator + Micrometer Prometheus
- Lombok 1.18.30, Hibernate Validator 8.0.1, SLF4J + Logback
- JUnit 5 + Mockito 5 + Testcontainers 1.21.3

## 项目结构
- `hardware-mall-backend/` — Spring Boot 后端
- `hardware-mall-admin/` — 管理后台 (Vue 3 + Element Plus + Vite + TS)
- `hardware-mall-uniapp/` — 小程序/H5 (uni-app Vue 3)

## 后端包结构
```
controller/admin/  — 管理端接口
controller/user/   — 小程序用户端接口
service/           — 业务接口 (interface + impl)
entity/db/         — 数据库实体
entity/dto/        — 数据传输对象
entity/vo/         — 返回视图对象
mapper/            — MyBatis-Plus Mapper (extends BaseMapper)
util/              — 工具类
config/            — Spring 配置类
common/constant/   — 常量类
common/result/     — 统一返回结果
annotation/        — 自定义注解
interceptor/       — 拦截器
event/             — Spring 事件
runner/            — 启动任务
job/               — 定时任务
```

## 编码规范
1. **修改前必须先 Read** — 不得凭记忆猜测方法签名、类名或字段名
2. **新增 Service 方法** — 先在 interface 加签名，再在 ServiceImpl 实现
3. **MyBatis-Plus 查询** — 使用 `LambdaQueryWrapper`，禁止字符串硬编码列名
4. **Controller 不直接注入 Mapper** — 必须通过 Service 层
5. **Redis 操作** — 统一通过 `RedisUtil`，不得直接使用 `RedisTemplate.opsForXxx()`
6. **分布式锁** — 统一通过 `RedisLockUtil`
7. **序列化规范** — Redis 用 Fastjson2，DB JSON 列用 Jackson
8. **遵循已有模式** — 修改已存在的类时，先观察该类已有的代码风格并保持一致
9. **修改后验证** — 改动完成后运行 `mvn compile` 确认无编译错误
