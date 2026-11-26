# ADR-0011：先上线后升级 Spring Boot 至 4.1.3

- 状态：Accepted
- 日期：2026-11-23
- 关联：docs/security/上线前安全检查清单.md（EOL 发现）

## 背景

后端锁定在 Spring Boot 3.2.12，其 OSS 支持已于 2024-12-31 结束，3.2 线不再获得任何安全补丁。经核查：

- 3.2 线已知 CVE 中，CVE-2025-22235 需同时使用 Spring Security 与 `EndpointRequest.to()` 才成立，本应用未使用 Spring Security（自定义 JWT 拦截器 + MyBatis-Plus），故**当前没有能打中本应用的已知 CVE**。
- 本应用未使用 Spring Security / Spring Data JPA / Hibernate / Spring Batch，避开了 Spring Boot 4 的三大破坏源，迁移面实测很小（3 个 artifact 替换 + 2 个版本升级 + 2 个文件 import 改动）。
- 因此"迁移工期"与"上线时机"可以解耦，不必为了升级推迟上线。

## 决策

1. **先以 3.2.12 上线，升级后置。** 上线后 **4 周内**完成升级，此截止日硬性约束，防止"后置"滑成"无限期搁置"。
2. **目标版本 4.1.3**（4.1 线支持至 2027-07；4.0 已于 2026-12 EOL，3.5 已 EOL，均不可作为目标）。
3. **一步到位 3.2 → 4.1.3**，辅以 OpenRewrite 迁移 recipe；不做逐 minor 台阶（中间版本均已 EOL、无停留点）。
4. **Jackson 2 → Jackson 3**：`SpecVOTypeHandler`、`DingTalkAlertService` 两处 `com.fasterxml.jackson.databind.ObjectMapper` 迁到 `tools.jackson`，并用 MyBatis-Plus 的 `Jackson3TypeHandler` 替换自定义 TypeHandler；`@JsonInclude` 注解（`jackson-annotations` 为 Jackson 2/3 共享）不改。
5. **上线期间补偿控制**：OWASP Dependency-Check 进 CI 自动告警 + 每两周人工 review 新 CVE；不引入 WAF（框架类 CVE 在应用层，WAF 拦不住）。
6. **验收门槛**：编译通过 + 全量单测 + 集成/H2 测试绿 + 手动回归「登录 → 下单 → 支付 → 退款 → 超时取消」全链路。
7. **回滚预案**：升级前给当前镜像打稳定 tag，写清回滚命令。
8. **迁移范围**：严格只做 Spring Boot 升级，不夹带审计报告"低危清单"的清理（低危项另行排期）。

## 后果

- 上线初期运行在 EOL 框架上，安全依赖第 5 条补偿控制，风险为"未来新 CVE 无补丁"而非"已知漏洞"。
- 升级后 Spring Boot 4.1 父 POM 将 Lombok 升至 1.18.46/48，`AGENTS.md` 中「Lombok 1.18.30 不兼容 JDK 21+」的约束与相关排查说明需同步更新。
- 依赖目标版本：`mybatis-plus-spring-boot4-starter` 3.5.18.1、`fastjson2`/`fastjson2-extension-spring6` 2.0.66（勿用 2.0.62–2.0.64）、`redisson-spring-boot-starter` 4.8.0、`springboot4-dotenv` 5.1.0；`wechatpay-java` 0.2.17 不受影响（用 Gson、无 Spring/Jackson 依赖）。
- 迁移虽小但触碰支付链路，手动回归不可省略。

## 备选方案

- **升级为上线硬门槛**：未采用。无已知 CVE 命中本应用，且迁移与上线可解耦，硬阻塞会无谓推迟上线。
- **目标 4.0.x**：未采用。2026-12-31 即 EOL，迁完等于立刻再迁。
- **目标 3.5.x**：未采用。2026-06-30 已 EOL。
- **渐进台阶（3.2→3.3→3.4→3.5→4.0→4.1）**：未采用。中间版本均 EOL、无安全停留点，徒增工作量。
- **购买商业 EOL 支持（HeroDevs NES / TuxCare / OSSeva）**：未采用。需持续付费且依赖第三方，迁移面小的情况下不值得。
- **顺带清理低危项**：未采用。升级已动框架地基，叠加业务清理会扩大回归面、混淆问题归因。
