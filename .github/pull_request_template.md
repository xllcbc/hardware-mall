## 检查

- [ ] 若本 PR 有数据库变更，已新增 `db/migration/V*.sql`，未修改已发布迁移
- [ ] 若本 PR 有临时发布逻辑，已在该 issue 收尾时删除或转正，未写入 `backend-admin-deploy.yml`
- [ ] `bash scripts/ci/check-deploy-workflow.sh` 通过
- [ ] 迁移向后兼容旧镜像（expand/contract）
