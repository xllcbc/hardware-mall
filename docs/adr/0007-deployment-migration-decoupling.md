# ADR-0007：生产部署与数据库迁移解耦

- 状态：已接受
- 日期：2026-11-18
- 相关：`docs/superpowers/specs/2026-11-18-deploy-migration-decoupling-design.md`、`docs/release-runbook.md`

## 背景

生产部署曾把「多级用户定价发布」的临时逻辑固化进 `.github/workflows/backend-admin-deploy.yml`：每次发布停写、全库 `mysqldump`、执行定价专用 SQL 与 verify、清缓存，失败时用发布前备份整库覆盖还原。PR #59 又把 `order_idempotency` 表检测和两个 SQL 文件名硬编码进同一工作流。

结果是：任何无关 issue 的结构变更都可能被 `schema_state()` 的定价指纹判为 `partial` 并阻断部署；一次漏打包 SQL 的失败会用整库覆盖回滚，放大事故面。

## 决策

采用 **Flyway CLI 作为部署步骤的一次性执行**：

- 发布包携带 `db/migration/`，在换镜像之前用固定版本 Flyway 容器执行 `flyway migrate`。
- 有 pending 时先全库备份，再恢复到 `<DB_NAME>_release_preflight` 做预检，最后迁移生产。
- 无 pending 时完全跳过备份、迁移、校验、清缓存。
- 回滚只回镜像，数据库只向前（expand/contract）。
- workflow 只引用目录，不出现任何具体 SQL 文件名；CI 策略 job 强制该约束。

## 考虑过的替代方案

| 方案 | 否决原因 |
|------|----------|
| Flyway 在应用启动时执行 | 后端运行账号需要 DDL 权限；迁移失败会让新容器 crash-loop，而旧容器已被替换；单机 compose 无编排层兜底 |
| 自建 `schema_migrations` + 脚本 | checksum、并发、失败语义都要自研；已有 `pricing-release.sh` 因自研 + issue 耦合失控 |
| 只做路径过滤止血 | 不解决根因，下一个 issue 仍会重演，`partial` 判据仍在 |

## 回滚语义

- 迁移失败且镜像未换：直接退出，旧版本继续服务；不自动 restore。
- 换镜像后失败：自动回滚镜像并 smoke；不覆盖数据库。
- `db-restore.sh` 只能人工执行，必须 `--yes`，部署流程永不调用。
- 备份只在有 pending 时创建，用于人工事故恢复。

## 后果

- 生产机必须能从私有仓库拉取 Flyway 镜像；镜像版本与应用测试依赖保持一致（13.12.2）。
- MySQL DDL 非事务，迁移作者必须保证 expand/contract，失败后按 runbook 处理。
- 首次切换需要一次 `baseline`，由 `baseline-check.sh` 证明 V1 与生产结构一致。
- 新增迁移不需要改流水线；PR 模板与 `AGENTS.md` 防止临时发布逻辑回流。
