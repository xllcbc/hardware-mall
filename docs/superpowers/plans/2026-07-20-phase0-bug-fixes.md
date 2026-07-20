# Phase 0: P0 + P1 BUG 修复 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 一次性消除上线硬阻塞：P0 越权 BUG（管理端订单详情硬编码 userId=1L、支付查询无归属校验、admin 明文密码 + 默认密码）+ P1 业务 BUG（salesCount 并发丢失、checkout 静默下单、物流页缺失、user/edit 未注册 pages.json、购物车乐观更新无回滚）。

**Architecture:** 不动业务流程、不动 DB schema、不动整体架构。仅针对具体 BUG 点做最小修复。后端补 1 个 mapper 方法（`updateSalesCount`），uniapp 新增 1 个页面（`pages/logistics/index.vue`）、补 1 个 pages.json 注册、补 1 处 pages.json 主包注册（user/edit），admin 改登录页占位符与默认表单值。所有修复完成后必须通过现有编译/构建/测试。

**Tech Stack:** Spring Boot 2.7.18 + MyBatis-Plus 3.5.5 + Vue3 + uni-app + Element Plus

**前置约束：** 在 `phase0-bug-fixes` 分支执行。先 `git checkout -b phase0-bug-fixes`。

---

## 文件结构（))/本 Phase 涉及）

**后端修改：**
- `hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/AdminOrderController.java` — 修硬编码 userId=1L
- `hardware-mall-backend/src/main/java/com/example/mystore/controller/user/PayController.java` — 加 orderId 归属校验
- `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/PayServiceImpl.java` — 加 userId 过滤的查询方法
- `hardware-mall-backend/src/main/java/com/example/mystore/service/PayService.java` — 新增 queryByOrderIdAndUserId
- `hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/AdminAuthController.java` — 移除默认 admin 密码、启动日志告警
- `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/OrderServiceImpl.java` — 修 salesCount 并发丢失
- `hardware-mall-backend/src/main/java/com/example/mystore/mapper/SpuMapper.java` — 新增原子更新方法
- `hardware-mall-backend/src/main/resources/application.yml` — admin.password 移除默认值

**admin 前端修改：**
- `hardware-mall-admin/src/views/login/index.vue` — 移除硬编码 admin/123456 默认值，移除页脚提示

**uniapp 修改：**
- `hardware-mall-uniapp/src/pages/checkout/index.vue` — 选中项为空时抛错而非 `.slice(0,2)`
- `hardware-mall-uniapp/src/stores/cart.ts` — API 失败回滚 state + toast
- `hardware-mall-uniapp/src/pages.json` — 注册 `pages/logistics/index` 与 `pages/user/edit`
- `hardware-mall-uniapp/src/pages/logistics/index.vue` — 新增物流展示页
- `hardware-mall-uniapp/src/api/logistics.ts` — 已存在无需新增

---

### Task 1: 创建 Phase 0 分支

**Files:** N/A

- [ ] **Step 1: 创建并切换分支**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git checkout -b phase0-bug-fixes
```

Expected: `Switched to a new branch 'phase0-bug-fixes'`

- [ ] **Step 2: 确认当前状态**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git status
```

Expected: `On branch phase0-bug-fixes` + nothing to commit or untracked files only

---

### Task 2: 修复 AdminOrderController 硬编码 userId=1L BUG

**Files:**
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/AdminOrderController.java:32-39`

**BUG 描述：** 第 32-39 行 `getOrderById` 把 path 中的数字 `id`（订单主键）当 `orderNo` 传给 `getOrderByOrderNo`，查不到后又用**硬编码 `userId=1L`** 兜底调用 `getOrderById(1L, id)`。结果：管理员看任何订单详情都查不到；如果有 userId=1 的用户，会返回该用户视角的订单（漏权限信息）。

**修复策略：** 管理端查询订单详情不应带 userId 过滤，直接按订单主键 id 查。需要 `OrderService` 新增方法或扩展现有方法签名。

- [ ] **Step 1: 检查 OrderService/OrderServiceImpl 当前 getById 签名**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
grep -n "getOrderById\|getOrderByOrderNo" src/main/java/com/example/mystore/service/OrderService.java src/main/java/com/example/mystore/service/impl/OrderServiceImpl.java
```

Expected: 找到 `OrderVO getOrderById(Long userId, Long orderId);` 签名

- [ ] **Step 2: 新增 admin 用方法签名**

Modify: `hardware-mall-backend/src/main/java/com/example/mystore/service/OrderService.java`

在接口末尾追加：
```java
/**
 * 管理端按订单 ID 查询订单（不按 userId 过滤）
 */
OrderVO getAdminOrderById(Long orderId);
```

- [ ] **Step 3: 实现 getAdminOrderById**

Modify: `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/OrderServiceImpl.java`

在现有 `getOrderById(Long userId, Long orderId)` 方法附近新增方法，**复用现有 `getOrderVO` 内部组装逻辑**：

```java
@Override
public OrderVO getAdminOrderById(Long orderId) {
    return getOrderVO(orderId, null);
}
```

> 注：`getOrderVO(orderId, userId)` 现有实现需检查是否对 userId=null 友好；若现有实现用 `userId` 做过滤需调整为 null 时跳过过滤。检查后用 grep 验证：

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
grep -n "getOrderVO" src/main/java/com/example/mystore/service/impl/OrderServiceImpl.java | head -20
```

如果 `getOrderVO` 内对 userId 非 null 直接关联过滤，确认 null 时不影响订单本身组装。否则改为：
```java
@Override
public OrderVO getAdminOrderById(Long orderId) {
    Order order = orderMapper.selectById(orderId);
    if (order == null) {
        throw new RuntimeException("订单不存在");
    }
    OrderVO vo = new OrderVO();
    // 复制必要字段（参照现有 getOrderVO 实现）
    // ...
    return vo;
}
```

- [ ] **Step 4: 修改 AdminOrderController.getOrderById**

Modify: `hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/AdminOrderController.java:32-39`

Replace 整个 `getOrderById` 方法：
```java
@GetMapping("/{id}")
public Result<OrderVO> getOrderById(@PathVariable Long id) {
    return Result.success(orderService.getAdminOrderById(id));
}
```

- [ ] **Step 5: 编译验证**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: 跑测试**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn test -q
```

Expected: BUILD SUCCESS（现有测试不破坏）

- [ ] **Step 7: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/AdminOrderController.java hardware-mall-backend/src/main/java/com/example/mystore/service/OrderService.java hardware-mall-backend/src/main/java/com/example/mystore/service/impl/OrderServiceImpl.java
git commit -m "fix(admin): 修复订单详情硬编码 userId=1L 越权 BUG (B1)"
```

---

### Task 3: 修复 PayController.queryPayStatus 越权查询

**Files:**
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/controller/user/PayController.java:59-65`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/service/PayService.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/PayServiceImpl.java`

**BUG 描述：** `GET /api/user/pay/query/{orderId}` 仅校验 JWT，不校验 orderId 归属。任意登录用户枚举 orderId 可查他人支付记录（含金额、微信交易号、状态）。

**修复策略：** Service 层加 `queryByOrderIdAndUserId`，按 `(orderId, userId)` 双条件过滤；Controller 提取 userId 后传入。

- [ ] **Step 1: 检查现有 queryByOrderId 实现**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
grep -n "queryByOrderId\|queryByOrderIdAndUserId" src/main/java/com/example/mystore/service/PayService.java src/main/java/com/example/mystore/service/impl/PayServiceImpl.java
```

期望：找到 `queryByOrderId(Long orderId)` 的接口签名与实现。

- [ ] **Step 2: 新增 queryByOrderIdAndUserId 接口**

Modify: `hardware-mall-backend/src/main/java/com/example/mystore/service/PayService.java`

在接口中追加：
```java
/**
 * 用户端按 orderId + userId 查询支付记录（防越权）
 */
PaymentRecord queryByOrderIdAndUserId(Long orderId, Long userId);
```

- [ ] **Step 3: 实现 queryByOrderIdAndUserId**

Modify: `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/PayServiceImpl.java`

参照现有 `queryByOrderId` 实现（在 PayServiceImpl.java:247-257 附近），新增方法：
```java
@Override
public PaymentRecord queryByOrderIdAndUserId(Long orderId, Long userId) {
    LambdaQueryWrapper<PaymentRecord> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(PaymentRecord::getOrderId, orderId)
           .eq(PaymentRecord::getUserId, userId)
           .orderByDesc(PaymentRecord::getCreateTime)
           .last("LIMIT 1");
    return paymentRecordMapper.selectOne(wrapper);
}
```

> 注：若 `PaymentRecord` 实体无 `userId` 字段，需先确认字段名（grep 检查）：
> ```bash
> workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
> grep -n "userId\|user_id" src/main/java/com/example/mystore/entity/db/PaymentRecord.java
> ```
> 若无 userId 字段，则需通过 `Order.orderId == orderId AND Order.userId = ?` JOIN 查询替代。先注入 `OrderMapper`，按 orderId 查 Order 校验 `order.userId == userId`，不匹配抛 `BusinessException`。

- [ ] **Step 4: 修改 PayController.queryPayStatus**

Modify: `hardware-mall-backend/src/main/java/com/example/mystore/controller/user/PayController.java:59-65`

Replace:
```java
@GetMapping("/query/{orderId}")
public Result<PaymentRecord> queryPayStatus(
        @RequestHeader("Authorization") String authHeader,
        @PathVariable Long orderId) {
    Long userId = extractUserId(authHeader);
    PaymentRecord record = payService.queryByOrderIdAndUserId(orderId, userId);
    return Result.success(record);
}
```

- [ ] **Step 5: 编译 + 测试**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn test -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-backend/src/main/java/com/example/mystore/controller/user/PayController.java hardware-mall-backend/src/main/java/com/example/mystore/service/PayService.java hardware-mall-backend/src/main/java/com/example/mystore/service/impl/PayServiceImpl.java
git commit -m "fix(pay): queryPayStatus 加 userId 归属校验防越权 (B2)"
```

---

### Task 4: 移除 admin 明文密码 + 默认密码

**Files:**
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/AdminAuthController.java:26-38`
- Modify: `hardware-mall-backend/src/main/resources/application.yml:53-55`
- Modify: `.env.example`

**BUG 描述：** `AdminAuthController:38` 用明文 `adminPassword.equals(password)` 比对，且默认值 `123456`，部署即暴露。

**修复策略：** 由于本系统管理员账号只有 1 个，自用场景下保持明文配置（env 注入）但移除默认值；启动时检测到默认/空密码强制告警（log.warn + 钉钉告警）。BCrypt 哈希作为后续可选项（涉及手工生成 hash 的运维成本，自用场景延后）。

> 决策记录：自用 1-2 人管理端 + 强 env 密码，明文比对可接受。BCrypt 仅在用户管理计划加入时再做。

- [ ] **Step 1: 移除 application.yml 默认密码**

Modify: `hardware-mall-backend/src/main/resources/application.yml:53-55`

原：
```yaml
admin:
  username: ${ADMIN_USERNAME:admin}
  password: ${ADMIN_PASSWORD:123456}
```

改为：
```yaml
admin:
  username: ${ADMIN_USERNAME:admin}
  password: ${ADMIN_PASSWORD}
```

> 移除默认值后，未配置 `ADMIN_PASSWORD` 启动会抛 `${ADMIN_PASSWORD}` 占位符解析异常 → Spring 启动失败，强制运维必填。

- [ ] **Step 2: 移除 AdminAuthController 默认值并加启动告警**

Modify: `hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/AdminAuthController.java:26-30`

Replace:
```java
@Value("${admin.username:admin}")
private String adminUsername;

@Value("${admin.password:123456}")
private String adminPassword;
```

为：
```java
@Value("${admin.username:admin}")
private String adminUsername;

@Value("${admin.password}")
private String adminPassword;
```

- [ ] **Step 3: 更新 .env.example 注释**

Modify `.env.example`（在项目根目录）

定位 admin 块，确认 `ADMIN_PASSWORD=CHANGE_ME` 且注释里说明"无默认值，必须填写"。

如果当前 .env.example 已包含 `ADMIN_USERNAME=admin` 和 `ADMIN_PASSWORD=CHANGE_ME`，无需改动。
如果用到 `CHANGE_ME` 默认占位，补充注释 `# 强密码：至少 12 位包含大小写数字符号`。

- [ ] **Step 4: 编译 + 启动词法校验**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: 启动测试（验证未配置 ADMIN_PASSWORD 时启动失败）**

临时清空 env：
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
ADMIN_PASSWORD="" mvn spring-boot:run 2>&1 | head -30
```

Expected: 启动失败，日志包含 `Could not resolve placeholder 'admin.password'`

恢复 env：
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
ADMIN_PASSWORD="Test_Strong_Pwd_2026" mvn spring-boot:run 2>&1 | head -30 &
```

Expected: 正常启动（看到 `Started HardwareMallApplication`）

> 测试后 Ctrl-C 停止启动。

- [ ] **Step 6: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/AdminAuthController.java hardware-mall-backend/src/main/resources/application.yml .env.example
git commit -m "fix(admin): 移除明文 admin 默认密码, 强制 env 注入 (B4 部分)"
```

---

### Task 5: 修复 admin 登录页硬编码 admin/123456

**Files:**
- Modify: `hardware-mall-admin/src/views/login/index.vue:67-69` （页脚提示）
- Modify: `hardware-mall-admin/src/views/login/index.vue:91-94` （表单默认值）

- [ ] **Step 1: 移除页脚默认账号提示**

Modify: `hardware-mall-admin/src/views/login/index.vue:67-69`

删除：
```html
<div class="login-footer">
  <span class="footer-text">默认账号: admin / 123456</span>
</div>
```

整段删除（含外层 `<div class="login-footer">`）。可保留 `<div class="login-footer">` 但内容改为版权声明或留空，对应 CSS `.login-footer` / `.footer-text` 保留。

简化方案：直接把 `<span class="footer-text">默认账号: admin / 123456</span>` 内容替换为空字符串或留空标签。最干净是整段 `<div class="login-footer">...</div>` 删除。

- [ ] **Step 2: 移除表单默认值**

Modify: `hardware-mall-admin/src/views/login/index.vue:91-94`

Replace:
```javascript
const form = reactive({
  username: 'admin',
  password: '123456'
})
```

为：
```javascript
const form = reactive({
  username: '',
  password: ''
})
```

- [ ] **Step 3: 类型检查 + 构建**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-admin"
npm run build
```

Expected: vue-tsc 0 errors + vite build success

- [ ] **Step 4: 手工冒烟**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-admin"
npm run dev
```

打开 `http://localhost:3000`，确认：
- 登录页表单 username/password 为空
- 页脚不再显示默认账号提示

测试后 Ctrl-C 停止 dev server。

- [ ] **Step 5: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-admin/src/views/login/index.vue
git commit -m "fix(admin): 移除登录页硬编码默认账密 + 页脚提示 (B4 部分)"
```

---

### Task 6: 修复 OrderService.createOrder salesCount 并发丢失更新

**Files:**
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/mapper/SpuMapper.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/OrderServiceImpl.java:104,137-139`

**BUG 描述：** `OrderServiceImpl.java:137` 在循环内 `spu.setSalesCount(spu.getSalesCount() + quantity)` 然后 `spuMapper.updateById(spu)` ——并发下单同 SPU 会读-改-写丢失更新。

**修复策略：** 改用 SQL 原子 `UPDATE spu SET sales_count = sales_count + ? WHERE id = ?`，不走 MyBatis-Plus `updateById` 的全字段更新。

- [ ] **Step 1: 检查 SpuMapper 当前方法**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
cat src/main/java/com/example/mystore/mapper/SpuMapper.java
```

- [ ] **Step 2: 新增 incrementSalesCount 方法**

Modify: `hardware-mall-backend/src/main/java/com/example/mystore/mapper/SpuMapper.java`

在接口中追加（保留现有所有方法）：
```java
@org.apache.ibatis.annotations.Update("UPDATE spu SET sales_count = sales_count + #{quantity}, update_time = NOW() WHERE id = #{spuId}")
int incrementSalesCount(@org.apache.ibatis.annotations.Param("spuId") Long spuId, @org.apache.ibatis.annotations.Param("quantity") Integer quantity);
```

- [ ] **Step 3: 修改 OrderServiceImpl 替换读-改-写**

Modify: `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/OrderServiceImpl.java:137-139`

删掉：
```java
spu.setSalesCount(spu.getSalesCount() + cartItem.getQuantity());
spu.setUpdateTime(LocalDateTime.now());
spuMapper.updateById(spu);
```

替换为：
```java
spuMapper.incrementSalesCount(spu.getId(), cartItem.getQuantity());
```

> 注意：`spu` 变量在此处之前已用于 `item.setProductName(spu.getName())` 与 `item.setProductImage(getFirstImage(spu.getImages()))`（看 OrderServiceImpl.java:104-124），保留 `spu` 的 selectById 不动；仅删除 update 操作。

- [ ] **Step 4: 编译 + 测试**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn test -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-backend/src/main/java/com/example/mystore/mapper/SpuMapper.java hardware-mall-backend/src/main/java/com/example/mystore/service/impl/OrderServiceImpl.java
git commit -m "fix(order): salesCount 改 SQL 原子更新防并发丢失 (B3)"
```

---

### Task 7: 修复 uniapp checkout 选中项为空静默下单 BUG

**Files:**
- Modify: `hardware-mall-uniapp/src/pages/checkout/index.vue:100-117`

**BUG 描述：** 第 113-117 行，当 `cartStore.selectedItems.length === 0` 但 `cartStore.items` 非空时，会**静默取前 2 项** 下单，用户未感知。

**修复策略：** 该 fallback 分支会下单未选中的商品，是真实业务 BUG。直接改为抛错并返回购物车页。

- [ ] **Step 1: 修改 orderItems computed 抛错**

Modify: `hardware-mall-uniapp/src/pages/checkout/index.vue:100-117`

Replace:
```typescript
const orderItems = computed(() => {
  if (isDirectBuy.value) {
    const item = preOrderStore.item
    return [{
      skuId: item.skuId,
      productId: item.productId,
      productName: item.productName,
      productImage: item.productImage,
      spec: item.spec,
      price: item.price,
      quantity: item.quantity,
      subtotal: item.price * item.quantity
    }]
  }
  return cartStore.selectedItems.length > 0
    ? cartStore.selectedItems
    : cartStore.items.slice(0, 2)
})
```

为：
```typescript
const orderItems = computed(() => {
  if (isDirectBuy.value) {
    const item = preOrderStore.item
    if (!item) return []
    return [{
      skuId: item.skuId,
      productId: item.productId,
      productName: item.productName,
      productImage: item.productImage,
      spec: item.spec,
      price: item.price,
      quantity: item.quantity,
      subtotal: item.price * item.quantity
    }]
  }
  // 购物车路径：必须有选中项；fallback 不再静默取前 N 项
  return cartStore.selectedItems
})
```

- [ ] **Step 2: 在 submitOrder 入口加选中项校验**

Modify: `hardware-mall-uniapp/src/pages/checkout/index.vue:157-162`

Replace:
```typescript
const submitOrder = async () => {
  if (submitted.value) return
  if (!selectedAddress.value) {
    uni.showToast({ title: '请选择收货地址', icon: 'none' })
    return
  }

  submitted.value = true
  try {
    const items = isDirectBuy.value
      ? [{ skuId: preOrderStore.item.skuId, quantity: preOrderStore.item.quantity }]
      : orderItems.value.map(item => ({ skuId: item.skuId, quantity: item.quantity }))
```

为：
```typescript
const submitOrder = async () => {
  if (submitted.value) return
  if (!selectedAddress.value) {
    uni.showToast({ title: '请选择收货地址', icon: 'none' })
    return
  }
  if (!isDirectBuy.value && orderItems.value.length === 0) {
    uni.showToast({ title: '请先在购物车选择商品', icon: 'none' })
    setTimeout(() => {
      uni.redirectTo({ url: '/pages/cart/index' })
    }, 1500)
    return
  }

  submitted.value = true
  try {
    const items = isDirectBuy.value
      ? [{ skuId: preOrderStore.item!.skuId, quantity: preOrderStore.item!.quantity }]
      : orderItems.value.map(item => ({ skuId: item.skuId, quantity: item.quantity }))
```

如果 TS 类型严格（`preOrderStore.item` 可能是 null），保留 `!` 非空断言或加 `, quantity: preOrderStore.item ? preOrderStore.item.quantity : 0` 的安全访问。看 `preOrderStore` 的类型定义。

- [ ] **Step 3: 类型检查 + 构建**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
npm run build:mp-weixin
```

Expected: build success without TS errors

- [ ] **Step 4: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-uniapp/src/pages/checkout/index.vue
git commit -m "fix(uniapp): checkout 选中项为空时抛错回购物车, 不再静默下单 (B5)"
```

---

### Task 8: 修复 uniapp cart store 乐观更新无回滚

**Files:**
- Modify: `hardware-mall-uniapp/src/stores/cart.ts:33-57`

**BUG 描述：** `updateQuantity` / `removeItem` 失败后只 `console.error`，state 已变但 server 未变更，下次进购物车时 onShow 重拉 list 才会"恢复"，期间用户看到错误数量。改回滚 + toast 提示。

- [ ] **Step 1: 改 updateQuantity 带回滚**

Modify: `hardware-mall-uniapp/src/stores/cart.ts:33-44`

Replace:
```typescript
async function updateQuantity(skuId: number, quantity: number) {
  const item = items.value.find(i => i.skuId === skuId)
  if (item) {
    item.quantity = quantity
    item.subtotal = item.price * quantity
    try {
      await updateCartQuantity(item.cartId!, quantity)
    } catch (e) {
      console.error('更新购物车数量失败:', e)
    }
  }
}
```

为：
```typescript
async function updateQuantity(skuId: number, quantity: number) {
  const item = items.value.find(i => i.skuId === skuId)
  if (!item) return
  const oldQuantity = item.quantity
  const oldSubtotal = item.subtotal
  item.quantity = quantity
  item.subtotal = item.price * quantity
  try {
    await updateCartQuantity(item.cartId!, quantity)
  } catch (e) {
    // 回滚
    item.quantity = oldQuantity
    item.subtotal = oldSubtotal
    uni.showToast({ title: '更新数量失败, 请稍后重试', icon: 'none' })
    console.error('更新购物车数量失败:', e)
  }
}
```

- [ ] **Step 2: 改 removeItem 带回滚**

Modify: `hardware-mall-uniapp/src/stores/cart.ts:46-57`

Replace:
```typescript
async function removeItem(skuId: number) {
  const index = items.value.findIndex(i => i.skuId === skuId)
  if (index > -1) {
    const item = items.value[index]
    items.value.splice(index, 1)
    try {
      await removeFromCart(item.cartId!)
    } catch (e) {
      console.error('删除购物车商品失败:', e)
    }
  }
}
```

为：
```typescript
async function removeItem(skuId: number) {
  const index = items.value.findIndex(i => i.skuId === skuId)
  if (index === -1) return
  const item = items.value[index]
  items.value.splice(index, 1)
  try {
    await removeFromCart(item.cartId!)
  } catch (e) {
    // 回滚：把 item 放回原位
    items.value.splice(index, 0, item)
    uni.showToast({ title: '删除失败, 请稍后重试', icon: 'none' })
    console.error('删除购物车商品失败:', e)
  }
}
```

- [ ] **Step 3: 类型检查 + 构建**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
npm run build:mp-weixin
```

Expected: build success

- [ ] **Step 4: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-uniapp/src/stores/cart.ts
git commit -m "fix(uniapp): cart store API 失败回滚 state + toast 提示 (B8)"
```

---

### Task 9: 新增 uniapp 物流展示页

**Files:**
- Create: `hardware-mall-uniapp/src/pages/logistics/index.vue`
- Modify: `hardware-mall-uniapp/src/pages.json`

**BUG 描述：** `pages/order/list.vue:185` 跳 `/pages/logistics/index` 但该页不存在，运行时报错。

- [ ] **Step 1: 检查 logistics API**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
cat src/api/logistics.ts
```

预期：导出 `getLogisticsList()` 接口（用户端可看物流方式列表）。

- [ ] **Step 2: 创建物流页**

Create: `hardware-mall-uniapp/src/pages/logistics/index.vue`

```vue
<template>
  <view class="logistics-container">
    <view v-if="loading" class="loading-wrap">
      <LoadingState text="加载中..." />
    </view>
    <EmptyState v-else-if="!list.length" text="暂无物流方式" />
    <view v-else class="logistics-list">
      <view v-for="item in list" :key="item.id" class="logistics-item">
        <view class="logistics-header">
          <text class="logistics-name">{{ item.name }}</text>
          <text v-if="item.code" class="logistics-code">代码: {{ item.code }}</text>
        </view>
        <view v-if="item.phones && item.phones.length" class="logistics-phones">
          <text class="phone-label">联系电话:</text>
          <text v-for="phone in item.phones" :key="phone" class="phone-text" @tap="callPhone(phone)">{{ phone }}</text>
        </view>
        <view v-if="item.city" class="logistics-city">
          <text class="city-text">配送区域: {{ item.city }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getLogisticsList } from '@/api/logistics'
import LoadingState from '@/components/common/LoadingState.vue'
import EmptyState from '@/components/common/EmptyState.vue'

interface LogisticsItem {
  id: number
  name: string
  code?: string
  phones?: string[]
  city?: string
  status?: number
}

const list = ref<LogisticsItem[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    const data = await getLogisticsList()
    list.value = (data || []).filter((item: LogisticsItem) => item.status === 1)
  } catch (e: any) {
    uni.showToast({ title: e.message || '加载物流失败', icon: 'none' })
  } finally {
    loading.value = false
  }
})

const callPhone = (phone: string) => {
  uni.makePhoneCall({ phoneNumber: phone, fail: () => {} })
}
</script>

<style scoped>
.logistics-container {
  min-height: 100vh;
  background: #FAFAFA;
  padding: 20rpx;
}
.loading-wrap { padding: 80rpx 0; }
.logistics-list { display: flex; flex-direction: column; gap: 20rpx; }
.logistics-item {
  background: #fff;
  border-radius: 16rpx;
  padding: 32rpx;
  box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.04);
}
.logistics-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16rpx;
}
.logistics-name { font-size: 30rpx; font-weight: 600; color: #333; }
.logistics-code { font-size: 24rpx; color: #999; }
.logistics-phones {
  display: flex; flex-wrap: wrap; align-items: center;
  margin: 12rpx 0;
}
.phone-label { font-size: 26rpx; color: #666; margin-right: 16rpx; }
.phone-text {
  font-size: 26rpx; color: #1890ff; margin-right: 20rpx;
  padding: 4rpx 16rpx; background: #f0f8ff; border-radius: 8rpx;
}
.logistics-city { margin-top: 12rpx; }
.city-text { font-size: 26rpx; color: #666; }
</style>
```

- [ ] **Step 3: 注册到 pages.json**

Modify: `hardware-mall-uniapp/src/pages.json`

在 `pages` 数组（第 2-57 行）末尾、`subPackages` 之前追加：
```json
    {
      "path": "pages/logistics/index",
      "style": {
        "navigationBarTitleText": "物流方式"
      }
    },
```

同时也要把 `pages/user/edit` 注册（解决 Task 10 的 B7）。如果当前 `pages.json` 已有 `pages/user/edit` 注册跳过。

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
grep -n "user/edit" src/pages.json
```

若返回空，则本次也要补上。否则 Task 10 仅校验。

- [ ] **Step 4: 构建**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
npm run build:mp-weixin
```

Expected: build success，dist/build/mp-weixin/pages/logistics/index.{js,wxml,wxss,json} 文件存在

- [ ] **Step 5: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-uniapp/src/pages/logistics/index.vue hardware-mall-uniapp/src/pages.json
git commit -m "fix(uniapp): 新增物流方式展示页 + pages.json 注册 (B6)"
```

---

### Task 10: uniapp user/edit 注册到 pages.json

**Files:**
- Modify: `hardware-mall-uniapp/src/pages.json` （如 Task 9 未顺带处理）

- [ ] **Step 1: 检查 pages.json 是否已注册 user/edit**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
grep -n "user/edit\|pages/user/edit" src/pages.json
```

若返回空，进入 Step 2；若已有，跳过本 Task。

- [ ] **Step 2: 注册 pages/user/edit 到主包**

Modify: `hardware-mall-uniapp/src/pages.json`

在 `pages` 数组末尾追加：
```json
    {
      "path": "pages/user/edit",
      "style": {
        "navigationBarTitleText": "编辑资料"
      }
    },
```

> 注：保持逗号正确（前一项后加逗号，本项末尾不加逗号 if 放在数组最末）。

- [ ] **Step 3: 校验 user/edit 文件存在**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
ls -la src/pages/user/edit.vue
```

Expected: 文件存在。若不存在需先创建（应已存在，探查阶段确认过）。

- [ ] **Step 4: 构建**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
npm run build:mp-weixin
```

Expected: build success

- [ ] **Step 5: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-uniapp/src/pages.json
git commit -m "fix(uniapp): 注册 pages/user/edit 到 pages.json (B7)"
```

---

### Task 11: Phase 0 全量验收

- [ ] **Step 1: 后端编译 + 全部测试**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn clean test -q
```

Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 2: admin 构建**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-admin"
npm run build
```

Expected: vue-tsc 0 errors, vite build success

- [ ] **Step 3: uniapp 构建**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
npm run build:mp-weixin
```

Expected: build success, dist/build/mp-weixin/ 下含 logistics/index 和 user/edit 编译产物

- [ ] **Step 4: 手工端到端冒烟（可选但推荐）**

启动后端 + admin + uniapp dev，端到端跑：
- admin 登录（用 .env 中真实账号密码）
- admin 进入订单详情 → 正常显示（不再受硬编码 userId=1L 影响）
- uniapp 用户下单 → 走 checkout 流程
- 当购物车选中项为空时跳 checkout → 跳回购物车（不静默下单）
- 当购物车改数量时若 API 失败 → 数量回滚（手动制造失败可断网测试）
- 从订单 list 跳物流页 → 正常显示物流方式列表
- 从我的页面 → 编辑 → 跳转 user/edit 不报错
- 用 user A 的 token 调 `/api/user/pay/query/{user B 的 orderId}` → 返回 NULL（不再越权）

- [ ] **Step 5: 推送分支**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git push -u origin phase0-bug-fixes
```

- [ ] **Step 6: Phase 0 完成 checkpoint**

确认所有 Phase 0 Task 状态均为完成，分支已推送，准备启动 Phase 1。

---

## Phase 0 验收清单

| Task | 涉及 BUG | 验收命令 | 状态 |
|---|---|---|---|
| 2 | B1 AdminOrder 硬编码 userId=1L | `mvn test` | ⬜ |
| 3 | B2 PayController 越权查询 | `mvn test` + 手工越权调用 | ⬜ |
| 4 | B4 admin 默认密码 | 启动失败测试 | ⬜ |
| 5 | B4 admin 登录页默认值 | `npm run build` + 视觉验收 | ⬜ |
| 6 | B3 salesCount 并发丢失 | `mvn test` | ⬜ |
| 7 | B5 checkout 静默下单 | `npm run build:mp-weixin` + 手工 | ⬜ |
| 8 | B8 cart 无回滚 | `npm run build:mp-weixin` + 手工断网 | ⬜ |
| 9 | B6 物流页缺失 | `npm run build:mp-weixin` + 产物存在 | ⬜ |
| 10 | B7 user/edit 未注册 | `npm run build:mp-weixin` | ⬜ |
| 11 | 全量验收 | 上面 3 个构建全绿 | ⬜ |

---

## Self-Review

- ✅ 覆盖 B1/B2/B3/B5/B6/B7/B8 + 部分 B4
- ⚠️ B4 仅做了"移除默认值"，BCrypt 哈希暂未做（决策记录：自用 1-2 人延后）
- ⚠️ B9 sorting UI 不传后端是体验问题不属 P0/P1，转 Phase 2
- ⚠️ B10 收藏/足迹同步用户已确认延后，不做
- ✅ 每个 Task 都有具体改代码、git 命令、验收命令
- ✅ 测试策略复用现有 mvn test / npm run build
- ✅ Type 一致：B1 在 Service 新增 `getAdminOrderById` → Controller 调用，命名一致；B2 新增 `queryByOrderIdAndUserId` → Controller 调用，命名一致