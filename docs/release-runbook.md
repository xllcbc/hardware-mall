# 发布与数据库迁移 Runbook

> 适用对象：运维、后端值班。本文描述 `main` → `ci` → `deploy-prod` 的发布流程。
> 最后更新：2026-11-18。历史的多级定价发布流程见 `docs/pricing-release-runbook.md`（仅归档）。

## 1. 发布模型

- 每次发布使用 **commit sha 作为镜像 tag**：`hardware-mall-backend:<sha>`、`hardware-mall-admin:<sha>`。
- `ci` 工作流（`.github/workflows/test.yml`）在 PR 与 push `main` 时运行全部门禁；成功后 `deploy-prod` 通过 `workflow_run` 触发。
- 服务器 `/opt/hardware-mall/.current-image-tag` 记录当前线上 sha。部署脚本在开始前校验它与 `hw-backend` / `hw-admin` 实际镜像一致，不一致直接退出。
- 普通发布只做：拉镜像 → `docker compose up -d` → smoke。**不停写、不全库备份、不清缓存。**
- 服务中断仅为容器重建的秒级窗口。

## 2. 迁移规则

- 结构变更只通过 `db/migration/V*.sql`，由 Flyway CLI 执行。
- **已发布的迁移文件永不修改**；后续变更新增 `V2__...sql`、`V3__...sql`。
- 迁移必须 **expand/contract**：先加新结构，待旧镜像下线后再删旧结构。迁移执行时旧容器仍在服务。
- 迁移不得包含 `CREATE DATABASE` / `USE`（预检库会因此写穿到生产库）。
- 建库单独使用 `db/bootstrap/00_create_database.sql`。
- 应用启动不执行迁移，后端运行账号不需要 DDL 权限。
- `schema_history` 表名为 `flyway_schema_history`。

## 3. 首次 baseline

只在把既有生产库纳入 Flyway 时执行一次：

```bash
cd /opt/hardware-mall
export FLYWAY_IMAGE="$REGISTRY/$NAMESPACE/flyway:13.12.2"
export DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=hardware_mall
export DB_USERNAME="${RELEASE_DB_USERNAME:-$DB_USERNAME}"
export MYSQL_PWD="${RELEASE_MYSQL_PWD:-$DB_PASSWORD}"

# 1) 结构对比：空库执行 V1，与生产 --no-data 对比
bash scripts/release/baseline-check.sh

# 2) 只写 history 表，不执行 DDL
bash scripts/release/db-migrate.sh baseline

# 3) 确认版本 1、无 pending
bash scripts/release/db-migrate.sh status
```

`baseline-check.sh` 必须输出 `PASS: baseline schema matches production` 才能继续；有 diff 时修正 V1（或补 V2），不要 baseline。

全新环境：先 `mysql < db/bootstrap/00_create_database.sql`，再 `db-migrate.sh migrate`（空库会执行全部 V*）。

## 4. 普通发布与有迁移发布的区别

无 pending 时，部署日志中**不应出现**：

- `mysqldump` / `db-backup.sh`
- `flyway migrate` / `preflight` / `docker pull .../flyway`
- `docker compose stop`
- `clear-cache.sh`

有 pending 时，顺序固定为：

1. `db-migrate.sh pending` 输出待执行版本；
2. 全库备份到 `/secure-backups/release-<sha>-<timestamp>.dump` + `.sha256`；
3. 恢复备份到 `<DB_NAME>_release_preflight`，在 scratch 库执行迁移并校验；
4. 生产库执行 `flyway migrate`；
5. 换镜像 → smoke。

任何 `.sql` 文件名都不应出现在 `backend-admin-deploy.yml`；`scripts/ci/check-deploy-workflow.sh` 会在 `ci` 的 `workflow-policy` job 中强制检查。

备份前 `db-backup.sh` 会查询库大小并要求目标磁盘剩余空间 ≥ 2.5 倍库大小（`RELEASE_MIN_FREE_PERCENT` 可覆盖），否则直接失败，避免在 2c/2g 主机上把磁盘写满。

### 清理 scratch 库

预检成功会自动删除 `${DB_NAME}_release_preflight`；失败会保留供排查。`baseline-check.sh` 成功时删除 `${DB_NAME}_baseline_check`，`rollback-drill.sh` 成功时删除 `${DB_NAME}_rollback_drill`。如果失败后需要人工清理：

```sql
DROP DATABASE IF EXISTS `hardware_mall_release_preflight`;
DROP DATABASE IF EXISTS `hardware_mall_baseline_check`;
DROP DATABASE IF EXISTS `hardware_mall_rollback_drill`;
```

清理前先确认库名以 `${DB_NAME}_` 开头，绝不能对 `hardware_mall` 执行。

## 5. 失败处理

| 阶段 | 行为 | 人工动作 |
|------|------|----------|
| 迁移失败（镜像未换） | 脚本退出，旧容器继续服务 | 查看 flyway 输出；必要时在 PENDING/FAILED 记录上执行 `flyway repair`，修好后重新发布 |
| 换镜像后 smoke 失败 | 自动回滚**镜像**到 `.current-image-tag`，再跑 smoke | 数据库不回滚；按日志修复 |
| 迁移成功但需要撤结构 | 不回滚数据库 | 上线一个 contract 迁移（V2+），保持向后兼容 |

注意：MySQL DDL 隐式提交，失败不能事务回滚。备份仅用于人工事故恢复，**部署脚本永不自动 restore**。

`db-restore.sh` 必须显式 `--yes`，且只允许人工在维护窗口执行：

```bash
bash scripts/release/db-restore.sh --yes /secure-backups/release-<sha>-<timestamp>.dump
```

## 6. 回滚演练

在维护窗口执行，验证镜像回滚和备份可用，不触碰生产库：

```bash
cd /opt/hardware-mall
bash scripts/release/rollback-drill.sh /secure-backups/release-<sha>-<timestamp>.dump \
  "$REGISTRY/$NAMESPACE/hardware-mall-backend:<previous-sha>"

prev="$(cat .current-image-tag)"
IMAGE_TAG="<previous-sha>" docker compose -f docker-compose.prod.yml up -d backend admin
RELEASE_API_BASE=http://127.0.0.1:8080 RELEASE_ADMIN_BASE=http://127.0.0.1 \
  bash scripts/release/smoke.sh
IMAGE_TAG="$prev" docker compose -f docker-compose.prod.yml up -d backend admin
RELEASE_API_BASE=http://127.0.0.1:8080 RELEASE_ADMIN_BASE=http://127.0.0.1 \
  bash scripts/release/smoke.sh
```

两次 smoke 都应通过；数据库保持当前结构。演练成功后脚本会自动删除 `${DB_NAME}_rollback_drill`。

## 7. Flyway 镜像进入私有仓库

生产机不能直连 Docker Hub，Flyway 镜像必须先进入私有仓库（与 `redis` 同级）：

```bash
REGISTRY=<private-registry>
NS=hardware-mall

docker pull --platform linux/amd64 flyway/flyway:13.12.2
docker tag flyway/flyway:13.12.2 "$REGISTRY/$NS/flyway:13.12.2"
docker push "$REGISTRY/$NS/flyway:13.12.2"
```

网络受限时在能连 Docker Hub 的机器上：

```bash
docker save flyway/flyway:13.12.2 | gzip -1 | ssh deploy@server 'gunzip | docker load'
# 在服务器上 tag + push
```

推完后用 `docker buildx imagetools inspect "$REGISTRY/$NS/flyway:13.12.2"` 确认 manifest 存在。

## 8. 证据文件

每次发布在 `/opt/hardware-mall/release-evidence-<sha>.txt` 写入：

```text
release_tag=<sha>
previous_tag=<sha>
pending_migrations=none|ALL|NEED_BASELINE|<versions>
backup=/secure-backups/... 或 none
completed_at=<UTC timestamp>
```

如需把关键日志、baseline 输出与回滚演练结果归档，写入 `docs/release/` 下带日期的文件，并在 PR / issue 中链接。
