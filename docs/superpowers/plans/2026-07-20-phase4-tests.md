# Phase 4: 测试补齐 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为后端核心业务（支付/订单/管理端鉴权）补集成测试，覆盖关键幂等与并发路径，目标核心 Service 行覆盖 ≥ 70%。

**Architecture:** 用现有 Testcontainers + JUnit 5 + MockMvc + Spring Boot Test 框架。新增集成测试类覆盖：PayService 验签/幂等/退款回调、OrderService 库存并发/状态机、AdminAuth 登录失败计数。

**Tech Stack:** JUnit 5, Spring Boot Test, MockMvc, Testcontainers MySQL 8

**前置约束：** Phase 0-3 已 merge。在 `phase4-tests` 分支执行。

---

### Task 1: 准备分支

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git checkout main && git pull && git checkout -b phase4-tests
mvn clean test -q  # 基线记录
```

---

### Task 2: AdminAuth 登录 + 限流 + 登出黑名单测试

**Files:** Create `hardware-mall-backend/src/test/java/com/example/mystore/controller/AdminAuthControllerTest.java`

覆盖 4 个场景：
1. 正确账号密码 → 200 + token 非空
2. 错误密码 → 500 "用户名或密码错误"
3. 同一 IP 第 6 次登录失败 → 429
4. 登出后 token 进入黑名单 → Redis key 存在

```java
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers @Transactional
class AdminAuthControllerTest {
    @Autowired private MockMvc mvc;
    // 4 个 @Test 覆盖上述 4 场景
}
```

---

### Task 3: PaymentRecord + Order 状态机 SQL 条件 update 幂等测试

**Files:** Create `hardware-mall-backend/src/test/java/com/example/mystore/service/PaymentRecordConcurrencyTest.java`

由于 `PayServiceImpl` 用 `@ConditionalOnProperty("wechat.pay.mch-id")` 且测试环境无微信配置，直接测 mapper 层 SQL 条件 update 的幂等性（更核心、更易写）：

```java
@SpringBootTest @ActiveProfiles("test") @Testcontainers @Transactional
class PaymentRecordConcurrencyTest {
    @Autowired private PaymentRecordMapper paymentRecordMapper;
    @Autowired private OrderMapper orderMapper;
    // 测试: PENDING record → PAID update WHERE status=0, 两次调用 affect=1+0
    // 测试: Order status=1 → status=2 WHERE status=1, 两次调用 affect=1+0
}
```

---

### Task 4: SKU 库存并发扣减 + SPU salesCount 并发递增测试

**Files:** Create `hardware-mall-backend/src/test/java/com/example/mystore/service/OrderServiceConcurrencyTest.java`

```java
@SpringBootTest @ActiveProfiles("test") @Testcontainers @Transactional
class OrderServiceConcurrencyTest {
    @Autowired private SkuMapper skuMapper;
    @Autowired private SpuMapper spuMapper;

    // 测试: stock=10, 20线程扣1 → 10成功 + 10失败 + stock最终=0
    // 测试: salesCount=0, 100线程 incrementSalesCount(1) → 最终=100
}
```

---

### Task 5: 全量测试 + 覆盖率检查

```bash
mvn clean test -q
# 期望: 所有测试通过 (原基线 + 新增 8 个测试)
```

---

### Task 6: Commit & push

```bash
git push -u origin phase4-tests
```

---

## Phase 4 验收清单

| Task | 内容 | 验收 | 状态 |
|---|---|---|---|
| 2 | AdminAuth 4 测试 | mvn test | ⬜ |
| 3 | PaymentRecord/Order 幂等 | mvn test | ⬜ |
| 4 | SKU/SPU 并发 | mvn test | ⬜ |
| 5 | 全量覆盖率 | mvn clean test 全绿 | ⬜ |
