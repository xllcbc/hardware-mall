# 支付可靠性修复 — 实施 Spec（剩余阶段）

**日期**: 2026-07-19
**背景**: natapp 内网穿透过期导致微信支付回调丢失，订单付款后仍显示"待付款"。已完成回调核心修复 ②⑤⑨④，本文档定义剩余阶段的分步实施方案。
**前置状态**: 已提交 `417cb3a`(②⑤) / `7235dc3`(⑨) / `28ca4ef`(④)，分支 `refactor/remove-mq`

---

## 一、优化决策（本次审查采纳）

| # | 优化点 | 采纳 |
|---|---|---|
| O1 | 抽取共享方法 `processPaymentSuccess`（callback/⑥/⑦ 三处复用，避免三份重复逻辑） | ✅ |
| O2 | ⑧ 告警异步化（CompletableFuture）+ webhook 未配置时静默降级 | ✅ |
| O3 | ⑦ lazy sync 加 Redis 节流（每订单 30 秒最多查一次微信，防刷 API 限频） | ✅ |
| O4 | ⑥ 微信查单异常时本轮跳过不取消（宁可晚取消 10 分钟，不可错取消） | ✅ |
| O5 | ⑪ checkout success 延迟后先主动查一次订单再跳转（单次查询，非轮询） | ✅ |

**明确不做**（防过度设计）：version 乐观锁字段 / 独立对账表+每日全量对账任务 / 退款失败自动重试 / 拆分 PayServiceImpl

---

## 二、已完成的修复（存档）

| 项 | 内容 | commit |
|---|---|---|
| ② | 回调返回 V3 规范 JSON（`Map<String,String>` + `produces=application/json`） | `417cb3a` |
| ⑤ | SQL 条件 update 代替 select-then-update（`WHERE status=0/1` 原子更新，affect=0 幂等跳过） | `417cb3a` |
| ⑨ | 退款状态机修正（新增 REFUNDING=4 中间态）+ 退款回调端点 `/api/user/pay/callback/refund` | `7235dc3` |
| ④ | 已取消订单收到支付回调 → 自动退款（直接调 refund() 跳过库存恢复） | `28ca4ef` |

### 已知折衷与 backlog（不处理，仅记录）

- ④ 中 refund() 在 callback 事务内调微信退款 API，事务多持有几百毫秒（50 单/天无影响）
- ⑨ refundCallback 非 SUCCESS 直接 Ack 微信，状态停 REFUNDING，依赖 ⑧ 告警发现
- `AdminOrderController.java:33-39` getOrderById 可疑回退（硬编码 userId=1），与支付无关，backlog

---

## 三、实施步骤

### Step 0：init.sql 注释同步（2 分钟）

- **文件**: `hardware-mall-backend/src/main/resources/db/init.sql:265`
- **改动**: payment_record status 注释 `'0待支付 1已支付 2已关闭 3已退款'` → 加 `4退款中`
- **验收**: 文本检查

### Step 1：抽取 processPaymentSuccess（30 分钟，O1）

- **文件**: `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/PayServiceImpl.java`
- **动作**:
  1. 抽取方法 `public boolean processPaymentSuccess(String outTradeNo, String transactionId)`：
     - 按 outTradeNo 查 payment_record
     - SQL 条件 update `WHERE id=? AND status=0` 置 PAID（含 transactionId/payTime/updateTime）
     - affect=0 → 返回 false（已被并发处理）
     - 订单条件 update `WHERE id=? AND status=1` 置 2 待发货
     - 返回 true
  2. callback 中⑤那段改为调用此方法
- **验收**: 编译通过；真实小额支付一笔，订单正常 1→2；重复回调日志出现"已被并发处理"

### Step 2：钉钉告警（1 小时，⑧ 含 O2）

- **新建**: `service/impl/DingTalkAlertService.java`
  - `alert(String type, String message)`：
    - webhook 为空 → 直接 return（本地未配置时静默降级）
    - Redis `setIfAbsent("alert:dingtalk:"+type, "1", 5, MINUTES)` 防抖，已存在跳过
    - `CompletableFuture.runAsync` 异步发送，不占回调线程
    - 加签：`HMAC-SHA256(timestamp + "\n" + secret)` → Base64 → URLEncoder，拼 `&timestamp=&sign=`
    - 复用现有 `HttpUtil.post(url, jsonBody)`
    - 全程 try-catch，失败只 log 不 throw，绝不影响支付主流程
  - 消息格式：`[告警-{type}]\n时间: ...\n{message}`
- **改**: `application.yml` 加 `dingtalk.webhook: ${DINGTALK_WEBHOOK:}` / `dingtalk.secret: ${DINGTALK_SECRET:}`
- **改**: `.env.example` 加 `DINGTALK_WEBHOOK=` / `DINGTALK_SECRET=` 占位
- **改**: `PayServiceImpl.java` 3 处注入告警：
  1. callback catch → `"PAY_CALLBACK_FAIL"`（支付回调异常）
  2. ④ 分支 catch → `"AUTO_REFUND_FAIL"`（自动退款发起失败）
  3. refundCallback 非 SUCCESS → `"REFUND_CONFIRM_FAIL"`（退款确认失败）
- **验收**: `.env` 填真实 webhook 后，curl 伪造回调触发验签失败 → 钉钉群收到消息；5 分钟内第二次不发（防抖生效）；告警发送失败不影响主流程

### Step 3：自动取消前查微信查单（45 分钟，⑥ 含 O4）

- **文件**: `hardware-mall-backend/src/main/java/com/example/mystore/job/order/OrderCancelStaleJob.java`
- **动作**: 循环内对每个超时订单：
  1. 查该订单最新 payment_record，无记录或非 PENDING → 照常 `autoCancelOrder`
  2. PENDING → 调 `payService.queryWechatOrder(outTradeNo)`（新帮助方法）
  3. tradeState=SUCCESS → 调 `processPaymentSuccess` 补单，**不取消**，记 log
  4. 非 SUCCESS → 照常取消
  5. 查询异常 → **跳过不取消**（O4）+ warn 日志，下轮再试
- **新增帮助方法**: `PayServiceImpl.queryWechatOrder(String outTradeNo)`，用 `JsapiServiceExtension.queryOrderByOutTradeNo`，包 try-catch
- **注意**: `PayService` 用 `@Autowired(required = false)` 注入（Bean 有 `@ConditionalOnProperty`，可能不存在；参照 `OrderServiceImpl:404` 的 `payService != null` 模式）
- **验收**:
  - DB 造一笔 status=1 超 30 分钟订单（无支付记录）→ 任务跑后正常取消
  - 造一笔 payment_record=PENDING 但微信侧已付 → 不取消，补单成 status=2

### Step 4：订单详情 lazy sync（45 分钟，⑦ 含 O3）

- **文件**: `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/OrderServiceImpl.java` 的 `getOrderById`
- **动作**: 查到订单后若 `status == ORDER_PENDING_PAYMENT`：
  1. Redis 节流：`setIfAbsent("lazysync:order:"+id, "1", 30, SECONDS)`，已存在直接返回（O3）
  2. 查最新 payment_record：
     - record=PAID 且 order=1 → 条件 update 订单为 2（DB 不一致修复，不调微信）
     - record=PENDING → 调 `queryWechatOrder` → SUCCESS → `processPaymentSuccess` → 重查订单返回
  3. 全程 try-catch，异常不影响详情正常返回
- **验收**:
  - 手动把一笔已支付订单 shop_order.status 改回 1 → 打开详情页 → 自动修正为 2
  - 30 秒内重复打开详情页，日志只有一次微信查单（节流生效）

### Step 5：用户端前端（30 分钟，⑪⑫ 含 O5）

- **文件**: `hardware-mall-uniapp/src/pages/checkout/index.vue`
  - success 回调改为：`await delay(1500)` → `getOrderDetail` 一次 → `uni.redirectTo`（O5，单次查询非轮询）
- **文件**: `hardware-mall-uniapp/src/pages/order/detail.vue`
  - 加 `onPullDownRefresh` 钩子：重新 `getOrderDetail` + `uni.stopPullDownRefresh()`
  - `pages.json` 该页加 `enablePullDownRefresh: true`
  - status=6 → 显示"退款处理中，请耐心等待"
  - status=7 → 显示"已退款，金额已原路退回"
- **验收**: 开发者工具/真机支付一笔 → 落地详情页显示"待发货"；下拉可手动刷新

### Step 6：管理端前端（20 分钟，⑬）

- **文件**: `hardware-mall-admin/src/views/order/index.vue`
  - 状态筛选加"退款中"(6) 选项
  - `handleRefund` 改 `ElMessageBox.prompt` 必填退款原因，传 `refundOrder(id, reason)`
- **文件**: 管理端 constants 补 `REFUNDING = 6`（如缺失）
- **验收**: 退款中可筛选；点退款不填原因无法提交

### Step 7：端到端验证（半天，⑭）

| # | 场景 | 预期 |
|---|---|---|
| 1 | 0.01 元下单 → 支付 | 5 秒内订单 1→2，无回调重试骚扰 |
| 2 | 管理端发货 → 用户确认收货 | 2→3→4 正常流转 |
| 3 | 管理端退款（填原因） | 订单 6 → 几分钟回调 → 7；payment_record 4→3 |
| 4 | 断 natapp → 支付 → 30 分钟订单被取消 → 恢复 natapp | 微信重试回调触发 ④ 自动退款；订单保持 5，payment_record 4→3 |
| 5 | 断 natapp → 支付 → 30 分钟内恢复 | ⑥ 任务跑到 → 不取消反补单 status=2 |
| 6 | 回调丢一笔 → 用户打开详情页 | ⑦ lazy sync 自动补单 |
| 7 | 伪造回调触发验签失败 | 钉钉收到告警，5 分钟内不重复 |

---

## 四、工时预估

| 步骤 | 工时 |
|---|---|
| Step 0 | 2 分钟 |
| Step 1 | 30 分钟 |
| Step 2 | 1 小时 |
| Step 3 | 45 分钟 |
| Step 4 | 45 分钟 |
| Step 5 | 30 分钟 |
| Step 6 | 20 分钟 |
| Step 7 | 半天 |
| **合计** | **约 3.5 小时代码 + 半天验证** |

---

## 五、依赖关系

```
Step 0 (顺手)
Step 1 (O1 抽取) ──┬──> Step 3 (⑥ 依赖 processPaymentSuccess)
                   └──> Step 4 (⑦ 依赖 processPaymentSuccess)
Step 2 (⑧ 告警) ───┬──> Step 3 的 warn 可复用告警
                   └──> ⑨ 的 REFUNDING 停滞依赖 ⑧ 发现
Step 5 / Step 6 独立，可与 2-4 并行
Step 7 最后做
```

**执行节奏**：每 Step 动手前先讲流程细节 → 等确认 → 写代码 → 编译验证 → 提交 git → 下一 Step。
