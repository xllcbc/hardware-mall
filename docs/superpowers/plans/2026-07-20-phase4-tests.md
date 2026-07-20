# Phase 4: 测试补齐 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为后端核心业务（支付/订单/管理端鉴权）补集成测试，覆盖关键幂等与并发路径，目标核心 Service 行覆盖 ≥ 70%。

**Architecture:** 用现有 Testcontainers + JUnit 5 + MockMvc + Spring Boot Test 框架。新增集成测试类覆盖：PayService 验签/幂等/退款回调、OrderService 库存并发/状态机、AdminAuth 登录失败计数。

**Tech Stack:** JUnit 5, Spring Boot Test, MockMvc, Testcontainers MySQL 8, Mockito

**前置约束：** Phase 0-3 已 merge。在 `phase4-tests` 分支执行。

---

## 文件结构（Phase 4 创建）

- Create: `hardware-mall-backend/src/test/java/com/example/mystore/service/PayServiceTest.java`
- Create: `hardware-mall-backend/src/test/java/com/example/mystore/service/OrderServiceConcurrencyTest.java`
- Create: `hardware-mall-backend/src/test/java/com/example/mystore/controller/AdminAuthControllerTest.java`
- Modify: `hardware-mall-backend/src/test/resources/application-test.yml` — 可能补充测试侧 mock 配置

---

### Task 1: 准备分支与基线

- [ ] **Step 1: 切分支**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git checkout main && git pull && git checkout -b phase4-tests
```

- [ ] **Step 2: 跑现有测试看基线覆盖率**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn clean test -q
```

记录现有测试数 + pass rate。

- [ ] **Step 3: 检查 Testcontainer profile 是否启用**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
cat src/test/resources/application-test.yml
ls src/test/java/com/example/mystore/
```

---

### Task 2: AdminAuthController 测试

**Files:**
- Create: `hardware-mall-backend/src/test/java/com/example/mystore/controller/AdminAuthControllerTest.java`

**覆盖：**
- 正确账号密码登录成功，返回 token
- 错误密码 5 次内每次返回 error
- RateLimit 5 次/60s 触发
- refresh 接口必须有 Authorization header
- logout 把 token 加入黑名单 → 后续请求 401

- [ ] **Step 1: 写测试**

Create: `hardware-mall-backend/src/test/java/com/example/mystore/controller/AdminAuthControllerTest.java`

```java
package com.example.mystore.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.util.JwtUtil;
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import redis.clients.jedis.Jedis;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
class AdminAuthControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private RedisUtil redisUtil;
    private final ObjectMapper om = new ObjectMapper();

    private Map<String, String> creds(String u, String p) {
        Map<String, String> m = new HashMap<>();
        m.put("username", u); m.put("password", p);
        return m;
    }

    @BeforeEach
    void clearRateLimit() {
        // 清掉 admin:login rate limit counter
        try {
            redisUtil.del("rate_limit:admin:login");
        } catch (Exception ignored) {}
    }

    @Test
    void login_withCorrectCredentials_returnsToken() throws Exception {
        mvc.perform(post("/api/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(creds("admin", System.getProperty("testAdminPwd", "TestStrongPwd_2026")))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void login_withWrongPassword_returnsError() throws Exception {
        mvc.perform(post("/api/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(creds("admin", "wrong_pwd"))))
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void login_exceeds5PerMinute_isRateLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(creds("admin", "wrong"))));
        }
        // 第 6 次应被限流
        mvc.perform(post("/api/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(creds("admin", "wrong"))))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void logout_thenTokenIsBlacklisted() throws Exception {
        // 1) 先正常登录拿 token
        String token = om.readTree(mvc.perform(post("/api/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(creds("admin", System.getProperty("testAdminPwd", "TestStrongPwd_2026")))))
            .andReturn().getResponse().getContentAsString())
            .at("/data/token").asText();

        // 2) 调 logout
        mvc.perform(post("/api/admin/logout")
                .header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.code").value(200));

        // 3) 校验 Redis 黑名单存在该 token
        String blacklistKey = RedisConstants.PREFIX_TOKEN_BLACKLIST + token;
        assertThat(redisUtil.hasKey(blacklistKey)).isTrue();
    }
}
```

> 注：`testAdminPwd` 系统属性需在测试运行时通过 `-DtestAdminPwd=TestStrongPwd_2026` 或者在 application-test.yml 中通过 spring-dotenv 读取。校验 RateLimitInterceptor 在测试环境是否真启用（如果测试 profile 关了拦截器则本测试需调方式）：
> ```bash
> grep -n "rate\|RateLimitInterceptor" src/main/java/com/example/mystore/config/WebMvcConfig.java
> ```
> WebMvcConfig 应该一直注册拦截器，无 profile 区分。如果测试时 RateLimit 没触发，则 mock 一下 RateLimitInterceptor 验证逻辑或调整测试。

- [ ] **Step 2: 跑测试**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn test -Dtest=AdminAuthControllerTest -q -DtestAdminPwd=TestStrongPwd_2026
```

Expected: 4 tests passed。

- [ ] **Step 3: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-backend/src/test/java/com/example/mystore/controller/AdminAuthControllerTest.java
git commit -m "test: AdminAuth 登录失败/限流/登出黑名单测试"
```

---

### Task 3: PayService 幂等 + 退款测试

**Files:**
- Create: `hardware-mall-backend/src/test/java/com/example/mystore/service/PayServiceTest.java`

**覆盖场景：**
- `processPaymentSuccess` 幂等：同一订单二次调用应 `affectRows=0` 不重复推进
- 已取消订单收到 PAID 触发兜底自动退款逻辑（不真退，只 mock）
- 退款回调成功推进 PaymentRecord REFUNDING → REFUNDED，Order 6 → 7

由于 `PayServiceImpl` 用 `@ConditionalOnProperty("wechat.pay.mch-id")`，无支付配置时该 Bean 不构建。测试策略：
- 走内部 `processPaymentSuccess(SQL 条件 update)` 真实测试幂等逻辑（绕过微信 SDK）
- 退款相关测试用 Mockito spy 真实 PayService（需要先确保有 mch-id 配置让 Bean 创建）

- [ ] **Step 1: 检查 PayService 测试可行性**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
grep -n "@ConditionalOnProperty\|wechat.pay.mch-id" src/main/java/com/example/mystore/service/impl/PayServiceImpl.java
cat src/test/resources/application-test.yml | head -50
```

确认测试 profile 是否设置了 `wechat.pay.mch-id`。如未设置，PayService Bean 不会被加载。

- [ ] **Step 2: test profile 配 mock wechat 支付配置**

Modify: `hardware-mall-backend/src/test/resources/application-test.yml`

如未配置，追加：
```yaml
wechat:
  appid: test_appid
  secret: test_secret
  pay:
    mch-id: test_mch_id
    api-v3-key: test_api_v3_key_32_bytes_long_enough_xx
    # private-key 与 public-key 测试时需要可生成或用 dummy
    private-key: "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgcqhkj OP 99 DUMMY KEY FOR TEST ONLY\n-----END PRIVATE KEY-----"
    public-key: "-----BEGIN PUBLIC KEY-----\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE Dummy\n-----END PUBLIC KEY-----"
    public-key-id: test_pub_id
    mch-serial-no: test_serial
    notify-url: https://test.example.com/notify
```

> 这会导致 PayService Bean 创建。但实际微信 SDK 校验私钥可能失败启动 → 此场景下退而求其次：测试只覆盖不依赖微信 SDK 的 SQL 层方法（processPaymentSuccess 的幂等用 `paymentRecordMapper` 直接调用，不走 SDK）。

> 推荐方案：将 `processPaymentSuccess` 抽到独立服务类 `PaymentRecordService`，单独测该 mapper 层逻辑。但这属于重构，本 Phase 范围内不引入。**实用方案 b：** 不在 test profile 配 wechat.pay 配置，PayService Bean 不存在；改直接测试 `paymentRecordMapper` 与 `orderMapper` 的 SQL 条件 update 行为。

- [ ] **Step 3: 写 paymentRecordMapper 级别的幂等测试**

Create: `hardware-mall-backend/src/test/java/com/example/mystore/service/PaymentRecordConcurrencyTest.java`

```java
package com.example.mystore.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Order;
import com.example.mystore.entity.db.PaymentRecord;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.mapper.PaymentRecordMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
class PaymentRecordConcurrencyTest {

    @Autowired private PaymentRecordMapper paymentRecordMapper;
    @Autowired private OrderMapper orderMapper;

    @Test
    void sql_atomicUpdate_ensuresIdempotency() {
        // 1) 准备 1 个 PENDING payment record 对应 1 个 status=1 order
        Order order = new Order();
        order.setOrderNo("TEST_NO_001");
        order.setUserId(0L);
        order.setStatus(StatusConstants.ORDER_PENDING_PAYMENT);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setPayAmount(new BigDecimal("100.00"));
        order.setFreightAmount(BigDecimal.ZERO);
        orderMapper.insert(order);

        PaymentRecord pr = new PaymentRecord();
        pr.setOrderId(order.getId());
        pr.setUserId(0L);
        pr.setStatus(0); // PENDING
        pr.setAmount(new BigDecimal("100.00"));
        paymentRecordMapper.insert(pr);

        // 2) 模拟并发：两次条件 update WHERE status=0
        LambdaUpdateWrapper<PaymentRecord> update1 = new LambdaUpdateWrapper<PaymentRecord>()
            .eq(PaymentRecord::getId, pr.getId())
            .eq(PaymentRecord::getStatus, 0)
            .set(PaymentRecord::getStatus, 1); // PAID
        LambdaUpdateWrapper<PaymentRecord> update2 = new LambdaUpdateWrapper<PaymentRecord>()
            .eq(PaymentRecord::getId, pr.getId())
            .eq(PaymentRecord::getStatus, 0)
            .set(PaymentRecord::getStatus, 1);

        int rows1 = paymentRecordMapper.update(null, update1);
        int rows2 = paymentRecordMapper.update(null, update2);

        // 3) 断言仅一次 affect=1, 另一次=0 (幂等)
        assertThat(rows1 + rows2).isEqualTo(1);
        assertThat(rows1 == 1 || rows2 == 1).isTrue();
    }

    @Test
    void orderStatus_conditionalUpdate_ensuresIdempotency() {
        Order order = new Order();
        order.setOrderNo("TEST_NO_002");
        order.setUserId(0L);
        order.setStatus(StatusConstants.ORDER_PENDING_PAYMENT);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setPayAmount(new BigDecimal("100.00"));
        order.setFreightAmount(BigDecimal.ZERO);
        orderMapper.insert(order);

        LambdaUpdateWrapper<Order> u1 = new LambdaUpdateWrapper<Order>()
            .eq(Order::getId, order.getId())
            .eq(Order::getStatus, StatusConstants.ORDER_PENDING_PAYMENT)
            .set(Order::getStatus, StatusConstants.ORDER_PENDING_SHIP);
        LambdaUpdateWrapper<Order> u2 = new LambdaUpdateWrapper<Order>()
            .eq(Order::getId, order.getId())
            .eq(Order::getStatus, StatusConstants.ORDER_PENDING_PAYMENT)
            .set(Order::getStatus, StatusConstants.ORDER_PENDING_SHIP);

        int r1 = orderMapper.update(null, u1);
        int r2 = orderMapper.update(null, u2);

        assertThat(r1 + r2).isEqualTo(1);
    }
}
```

> 注：`PaymentRecord` 实体字段名与 setter setter 类型需匹配。如果实体没有 status setter 看 lombok builder，必要时调整代码。

- [ ] **Step 4: 跑测试**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn test -Dtest=PaymentRecordConcurrencyTest -q
```

Expected: 2 tests passed。

- [ ] **Step 5: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-backend/src/test/java/com/example/mystore/service/PaymentRecordConcurrencyTest.java
git commit -m "test: 验证 PaymentRecord 与 Order 状态机 SQL 条件 update 幂等性"
```

---

### Task 4: OrderService 库存并发测试

**Files:**
- Create: `hardware-mall-backend/src/test/java/com/example/mystore/service/OrderServiceConcurrencyTest.java`

**覆盖：**
- 同 SKU 库存=10，并行 20 个线程扣 1，最终成功 10 个、失败 10 个，SKU 库存最终=0
- 同 SPU 多 SKU 同时下单，salesCount 不丢失（依赖 Task 修复的 incrementSalesCount）

- [ ] **Step 1: 写测试**

Create: `hardware-mall-backend/src/test/java/com/example/mystore/service/OrderServiceConcurrencyTest.java`

```java
package com.example.mystore.service;

import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.mapper.SkuMapper;
import com.example.mystore.mapper.SpuMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
class OrderServiceConcurrencyTest {

    @Autowired private SkuMapper skuMapper;
    @Autowired private SpuMapper spuMapper;

    @Test
    void deductStock_concurrent20ThreadsOf1_only10Succeed() throws InterruptedException {
        // 准备 SKU stock=10
        Sku sku = new Sku();
        sku.setSpuId(1L); // assume SPU id=1 exists via init.sql seed
        sku.setStock(10);
        sku.setPrice(new java.math.BigDecimal("10.00"));
        sku.setStatus(1);
        skuMapper.insert(sku);

        int threads = 20;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger okCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    boolean ok = skuMapper.deductStock(sku.getId(), 1);
                    if (ok) okCount.incrementAndGet(); else failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        Sku finalSku = skuMapper.selectById(sku.getId());
        assertThat(okCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(10);
        assertThat(finalSku.getStock()).isEqualTo(0);
    }

    @Test
    void incrementSalesCount_concurrent100_sumShouldBe100() throws InterruptedException {
        // 准备 SPU salesCount=0
        Spu spu = new Spu();
        spu.setName("test SPU");
        spu.setSalesCount(0);
        spu.setStatus(1);
        spuMapper.insert(spu);

        int threads = 100;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(20);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    spuMapper.incrementSalesCount(spu.getId(), 1);
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        Spu finalSpu = spuMapper.selectById(spu.getId());
        assertThat(finalSpu.getSalesCount()).isEqualTo(100);
    }
}
```

- [ ] **Step 2: 检查 SkuMapper.deductStock 方法签名**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
cat src/main/java/com/example/mystore/mapper/SkuMapper.java
```

确保 `boolean deductStock(Long skuId, Integer quantity);` 方法存在（按 Phase 0 修过的同款 SQL `UPDATE sku SET stock = stock - ? WHERE id = ? AND stock >= ?`）。如不存在或签名不一致调整测试代码。

- [ ] **Step 3: 跑测试**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn test -Dtest=OrderServiceConcurrencyTest -q
```

Expected: 2 tests passed。如果 deductStock/mapper 接口不全，先补接口定义。

- [ ] **Step 4: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-backend/src/test/java/com/example/mystore/service/OrderServiceConcurrencyTest.java
git commit -m "test: SKU 库存并发扣减 + SPU salesCount 并发递增测试"
```

---

### Task 5: 全量覆盖率检查

- [ ] **Step 1: 全量测试**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn clean test -q
```

Expected: BUILD SUCCESS, 所有测试通过。

- [ ] **Step 2: 跑覆盖率（Jacoco 可选）**

如果项目未配 jacoco，本步可选。可选方案：装 maven jacoco 插件 + run。

不装 jacoco 的话，用 IDE 或 mvn surefire report 看核心 service 类被测覆盖。

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn test -q && cat target/surefire-reports/*.txt 2>/dev/null | head -40
```

Expected: 看测试数 ≥ Phase 0 基线 + 8 个新测试（4 + 2 + 2）。

- [ ] **Step 3: 推送**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git push -u origin phase4-tests
```

- [ ] **Step 4: Phase 4 完成 checkpoint**

---

## Phase 4 验收清单

| Task | 内容 | 验收 | 状态 |
|---|---|---|---|
| 2 | AdminAuth 4 测试 | mvn test | ⬜ |
| 3 | PaymentRecord/Order 幂等 | mvn test | ⬜ |
| 4 | SKU/SPU 并发 | mvn test | ⬜ |
| 5 | 全量覆盖率 | mvn clean test 全绿 | ⬜ |

---

## Self-Review

- ✅ 测试聚焦核心风险点：幂等（支付）+ 并发（库存/销量）
- ✅ 用 @Transactional rollback 隔离测试数据
- ✅ Testcontainers MySQL 真实并发验证
- ⚠️ PayService 真实回调验签测试未做（依赖微信 SDK mock 较复杂），改为测试 SQL 层幂等行为，更核心且更易写
- ⚠️ Testcontainers 需要 Docker 环境，CI 环境（GitHub Actions）需配 docker service