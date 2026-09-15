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

## 构建环境
- **必须使用 JDK 17 构建**。Lombok 1.18.30 不兼容 JDK 21+，在 JDK 25 上注解处理会静默失效，导致全项目出现大量「找不到 getter/setter」编译错误
- 若本机 `JAVA_HOME` 指向 JDK 25，需临时切换后再构建（Windows/WSL 示例）：
  ```
  cmd.exe /c 'set JAVA_HOME=C:\Progra~1\Java\jdk-17.0.3.1&& mvn -DskipTests compile'
  ```

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
10. **常量组织** — 第三方协议常量（微信/支付等）按域拆分为嵌套静态类分组（参照 `WechatConstants` 的 `Api`/`Event`/`OrderState`/`Fields`/`Common`/`Alert`），禁止硬编码字符串与魔数；跨域同名不同义的常量（如微信 `order_state` 与本地订单状态）必须物理隔离在不同类中
