# 五金商城生产落地方案 — Master Index

> **For agentic workers:** 本目录下每个 Phase 计划文档可独立执行。推荐使用 `superpowers:subagent-driven-development` 按 Phase 派遣子代理，Phase 内按 Task 顺序串联执行，每个 Task 完成后做 checkpoint review。

**总目标：** 将现有 MVP 代码推进至可上线（自用商城场景，1 台 **2c2g** 云服务器 + 已 ICP 备案一级域名 + 微信小程序 + 微信支付商户号开通）。

**关键决策（已与用户确认）：**
1. Spring Boot 2.7.18 → 3.2.x（jakarta 化）
2. uniapp 收藏/足迹暂不后端同步（本地存储保留）
3. admin 落地 Pinia `useAuthStore`（替代裸 localStorage，接通 `refresh()` + 修多标签登出）
4. ICP 备案本次启动（Day 0 即发起，**仅备一级域名 yourdomain.com**，子域名免重复备案）
5. **部署方案 C**：MySQL/Redis apt 直装宿主机（被 docker 容器通过 `host.docker.internal` 访问），仅 Spring+admin 走 docker；2g 服务器资源预算 MySQL 700MB + Redis 80MB + JVM 500MB + Nginx 20MB + 系统 250MB ≈ 1.55GB（留 450MB 缓冲）
6. **子域名命名**：`shop.yourdomain.com`（小程序 API）+ `admin.yourdomain.com`（管理端 SPA），业务对仗清晰，不与后端 `/api/user/` 路径字面冲突
7. **SSL 证书**：Let's Encrypt 两张单域名证书（HTTP-01 standalone 模式，简单 30 秒一张），各子域名独立续期
8. **admin 防护**：nginx Basic Auth 用户名 `malladmin`（避开常见撞库）+ Basic Auth 密码 P1 与后端应用 admin 密码 P2 **不同** + nginx `limit_req` 5r/m 防 Basic Auth 慢速撞库 + 后端登录限流 5/60s + 钉钉告警登录失败

**关键路径（预计 15-21 工作日 + ICP 等待 2-4 周）：**

```
Day 0:  ── 发起 ICP 备案（用户主办，代码端并行）
Phase 0: ── P0+P1 BUG 修复（1 天）
Phase 1: ── Spring Boot 3.2 升级（2-3 天）─────────┐
                                                   │
Phase 2: ── 工程化 + Pinia + uniapp 体验（1-2 天）│ 阶段1完成后开始
                                                   │
Phase 3: ── 容器化（方案 C）（1-2 天）──┐          │
                              │          │
Phase 4: ── 测试补齐（2-3 天）│ 与 3 并行 │
                              │          │
Phase 5: ── 可观测性（1-2 天）│          │
                              │          │
Phase 6: ── CI/CD（1-2 天）───┘ 与 5 并行 │
                                                   │
Phase 6': ── ICP 通过后 HTTPS+域名白名单（1天）阻塞依赖 ICP
                                                   │
Phase 7: ── 生产验收（1 天）                       │
                                                   │
Phase 8: └─ 小程序审核发布（微信审 1-7 天）────────┘
```

---

## Phase 计划清单

| Phase | 文档 | 目标 | 工时 | 是否依赖 ICP |
|---|---|---|---|---|
| 0 | [phase0-bug-fixes.md](./phase0-bug-fixes.md) | P0 越权 BUG + P1 业务 BUG 修复 | 1 天 | 否 |
| 1 | [phase1-springboot-upgrade.md](./phase1-springboot-upgrade.md) | Spring Boot 2.7→3.2 升级 + 依赖联动 | 2-3 天 | 否 |
| 2 | [phase2-engineering-pinia.md](./phase2-engineering-pinia.md) | .env 真实化 + 删死代码 + admin Pinia auth store + uniapp BASE_URL env + 体验补齐 | 1-2 天 | 否 |
| 3 | [phase3-containerization.md](./phase3-containerization.md) | 方案 C：Spring+admin docker 容器化，MySQL/Redis apt 直装；admin 双子域名 nginx + Basic Auth + limit_req；新增 docs/server-setup.md | 1-2 天 | 否 |
| 4 | [phase4-tests.md](./phase4-tests.md) | PayService/OrderService/AdminAuth 集成测试，目标 70% | 2-3 天 | 否 |
| 5 | [phase5-observability.md](./phase5-observability.md) | Actuator + Prometheus + traceId MDC + 钉钉告警全分支 + DB 备份（走 127.0.0.1 mysqldump） | 1-2 天 | 否 |
| 6 | [phase6-cicd.md](./phase6-cicd.md) | GitHub Actions backend/admin 部署 + uniapp 体验版 CI | 1-2 天 | 否 |
| 6' | [phase6p-https-domain.md](./phase6p-https-domain.md) | ICP 通过后：DNS 双子域 A 记录 + Let's Encrypt 两单域名证书 + nginx HTTPS + htpasswd（用户名 malladmin）+ 小程序合法域名（shop.*）+ 微信支付 notify_url 切 shop.* + certbot 续期 cron | 1 天 | **是** |
| 7 | [phase7-production-checklist.md](./phase7-production-checklist.md) | 端到端真实流程验收 + 备份恢复演练（方案 C：2 容器 + systemctl active mysql/redis） | 1 天 | 是 |

---

## 执行守则

1. **每个 Phase 一份 git 分支**：`phase0-bug-fixes`、`phase1-sb-upgrade`...，Phase 完成后 merge 回主开发分支。
2. **每个 Task 内遵守 TDD**（如适用）：先写测试 → 运行失败 → 实现 → 运行通过 → commit。
3. **每个 Task 完成后必须**：跑 `mvn test`（后端）或 `npm run build`（admin/uniapp）确保无新增错误。
4. **测试命令**：
   - 后端：`workdir="hardware-mall-backend"` 运行 `mvn test`
   - admin：`workdir="hardware-mall-admin"` 运行 `npm run build`（含 vue-tsc 类型检查）
   - uniapp：`workdir="hardware-mall-uniapp"` 运行 `npm run build:mp-weixin`
5. **不提交密钥**：`.env` 永不入库（已在 .gitignore）；只提交 `.env.example` 模板。
6. **Commit message 风格**：`, feat/fix/refactor/docs/chore(scope): 中文简述`，参考现有 git log。
7. **Phase 0 优先做**：它是上线硬阻塞，1 天内可消除。

---

## ICP 备案并行任务（用户主办，Day 0 启动）

- [ ] 选定 cloud 服务商（阿里云/腾讯云）
- [ ] 注册或转入域名（.com / .cn）
- [ ] 提交 ICP 备案申请（个人主体或企业主体）
- [ ] 同步提交小程序服务器域名备案（小程序后台 request 合法域名需 ICP 通过的 HTTPS 域名）
- [ ] ICP 通过后通知开发人员进入 Phase 6'

**ICP 等待期（2-4 周）开发端不闲着**：继续 Phase 1-6 的代码工作。

---

## 与 Phase 关系图

```
Phase 0 ──> Phase 1 ──> Phase 2 ──> Phase 3 ──> Phase 6' ──> Phase 7 ──> Phase 8
                                  ├──> Phase 4 (并行)
                                  ├──> Phase 5 (并行)
                                  └──> Phase 6 (并行)

Day 0 ICP ────────────────────────────────────────────> 通过 ─┘
```

---

## 何时开始执行

建议从 Phase 0 开始，每个 Phase 完成后 review checkpoint，确认通过再启动下一 Phase。Phase 0 工时仅 1 天，可立即产出收益。
