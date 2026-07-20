# Phase 0: P0 + P1 BUG 修复 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 一次性消除上线硬阻塞：P0 越权 BUG（管理端订单详情硬编码 userId=1L、支付查询无归属校验、admin 明文密码 + 默认密码）+ P1 业务 BUG（salesCount 并发丢失、checkout 静默下单、物流页缺失、user/edit 未注册 pages.json、购物车乐观更新无回滚）。

**Architecture:** 不动业务流程、不动 DB schema、不动整体架构。仅针对具体 BUG 点做最小修复。后端补 1 个 mapper 方法（`updateSalesCount`），uniapp 新增 1 个页面（`pages/logistics/index.vue`）、补 1 个 pages.json 注册、补 1 处 pages.json 主包注册（user/edit），admin 改登录页占位符与默认表单值。所有修复完成后必须通过现有编译/构建/测试。

**Tech Stack:** Spring Boot 2.7.18 + MyBatis-Plus 3.5.5 + Vue3 + uni-app + Element Plus

**前置约束：** 在 `phase0-bug-fixes` 分支执行。先 `git checkout -b phase0-bug-fixes`。

---

## 文件结构（本 Phase 涉及）

**后端修改：**
- `hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/AdminOrderController.java` — 修硬编码 userId=1L
- `hardware-mall-backend/src/main/java/com/example/mystore/controller/user/PayController.java` — 加 orderId 归属校验
- `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/PayServiceImpl.java` — 加 userId 过滤的查询方法
- `hardware-mall-backend/src/main/java/com/example/mystore/service/PayService.java` — 新增 queryByOrderIdAndUserId
- `hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/AdminAuthController.java` — 移除默认 admin 密码
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

---

### Task 1: 创建 Phase 0 分支

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
OrderVO getAdminOrderById(Long orderId);
```

- [ ] **Step 3: 实现 getAdminOrderById**

Modify: `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/OrderServiceImpl.java`

新增方法：
```java
@Override
public OrderVO getAdminOrderById(Long orderId) {
    return getOrderVO(orderId, null);
}
```

> 注：`getOrderVO(orderId, userId)` 现有实现需检查是否对 userId=null 友好；若现有实现用 `userId` 做过滤需调整为 null 时跳过过滤。

- [ ] **Step 4: 修改 AdminOrderController.getOrderById**

Modify: `hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/AdminOrderController.java:32-39`

Replace 整个 `getOrderById` 方法：
```java
@GetMapping("/{id}")
public Result<OrderVO> getOrderById(@PathVariable Long id) {
    return Result.success(orderService.getAdminOrderById(id));
}
```

- [ ] **Step 5: 编译 + 测试**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn compile -q && mvn test -q
```

- [ ] **Step 6: Commit**

```bash
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

- [ ] **Step 1: 新增 queryByOrderIdAndUserId 接口**

Modify: `hardware-mall-backend/src/main/java/com/example/mystore/service/PayService.java`

追加：
```java
PaymentRecord queryByOrderIdAndUserId(Long orderId, Long userId);
```

- [ ] **Step 2: 实现 queryByOrderIdAndUserId**

Modify: `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/PayServiceImpl.java`

新增方法：
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

- [ ] **Step 3: 修改 PayController.queryPayStatus**

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

- [ ] **Step 4: 编译 + 测试**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn test -q
```

- [ ] **Step 5: Commit**

```bash
git add hardware-mall-backend/src/main/java/com/example/mystore/controller/user/PayController.java hardware-mall-backend/src/main/java/com/example/mystore/service/PayService.java hardware-mall-backend/src/main/java/com/example/mystore/service/impl/PayServiceImpl.java
git commit -m "fix(pay): queryPayStatus 加 userId 归属校验防越权 (B2)"
```

---

### Task 4: 移除 admin 明文密码 + 默认密码

**Files:**
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/AdminAuthController.java:26-38`
- Modify: `hardware-mall-backend/src/main/resources/application.yml:53-55`

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

- [ ] **Step 2: 移除 AdminAuthController 默认值**

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

- [ ] **Step 3: 编译 + 启动校验**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/AdminAuthController.java hardware-mall-backend/src/main/resources/application.yml
git commit -m "fix(admin): 移除明文 admin 默认密码, 强制 env 注入 (B4)"
```

---

### Task 5: 修复 admin 登录页硬编码 admin/123456

**Files:**
- Modify: `hardware-mall-admin/src/views/login/index.vue:67-69` （页脚提示）
- Modify: `hardware-mall-admin/src/views/login/index.vue:91-94` （表单默认值）

- [ ] **Step 1: 移除页脚默认账号提示**

Modify: `hardware-mall-admin/src/views/login/index.vue:67-69`

整段删除 `<div class="login-footer">` 及其子元素。

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

- [ ] **Step 3: 构建验证**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-admin"
npm run build
```

- [ ] **Step 4: Commit**

```bash
git add hardware-mall-admin/src/views/login/index.vue
git commit -m "fix(admin): 移除登录页硬编码默认账密 + 页脚提示 (B4)"
```

---

### Task 6: 修复 OrderService.createOrder salesCount 并发丢失更新

**Files:**
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/mapper/SpuMapper.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/OrderServiceImpl.java:104,137-139`

**BUG 描述：** `OrderServiceImpl.java:137` 在循环内 `spu.setSalesCount(spu.getSalesCount() + quantity)` 然后 `spuMapper.updateById(spu)` ——并发下单同 SPU 会读-改-写丢失更新。

**修复策略：** 改用 SQL 原子 `UPDATE spu SET sales_count = sales_count + ? WHERE id = ?`。

- [ ] **Step 1: 新增 incrementSalesCount 方法**

Modify: `hardware-mall-backend/src/main/java/com/example/mystore/mapper/SpuMapper.java`

在接口中追加：
```java
@org.apache.ibatis.annotations.Update("UPDATE spu SET sales_count = sales_count + #{quantity}, update_time = NOW() WHERE id = #{spuId}")
int incrementSalesCount(@org.apache.ibatis.annotations.Param("spuId") Long spuId, @org.apache.ibatis.annotations.Param("quantity") Integer quantity);
```

- [ ] **Step 2: 修改 OrderServiceImpl 替换读-改-写**

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

- [ ] **Step 3: 编译 + 测试**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn test -q
```

- [ ] **Step 4: Commit**

```bash
git add hardware-mall-backend/src/main/java/com/example/mystore/mapper/SpuMapper.java hardware-mall-backend/src/main/java/com/example/mystore/service/impl/OrderServiceImpl.java
git commit -m "fix(order): salesCount 改 SQL 原子更新防并发丢失 (B3)"
```

---

### Task 7: 修复 uniapp checkout 选中项为空静默下单 BUG

**Files:**
- Modify: `hardware-mall-uniapp/src/pages/checkout/index.vue:100-117`

**BUG 描述：** 第 113-117 行，当 `cartStore.selectedItems.length === 0` 但 `cartStore.items` 非空时，会**静默取前 2 项** 下单，用户未感知。

- [ ] **Step 1: 修改 orderItems computed**

Modify: `hardware-mall-uniapp/src/pages/checkout/index.vue:100-117`

Replace:
```typescript
return cartStore.selectedItems.length > 0
    ? cartStore.selectedItems
    : cartStore.items.slice(0, 2)
```

为：
```typescript
return cartStore.selectedItems
```

- [ ] **Step 2: 在 submitOrder 入口加选中项校验**

Modify: `hardware-mall-uniapp/src/pages/checkout/index.vue:157-162`

在 `const submitOrder = async()` 方法体开头，`submitted.value` 校验之后、地址校验之后，追加：
```typescript
if (!isDirectBuy.value && orderItems.value.length === 0) {
    uni.showToast({ title: '请先在购物车选择商品', icon: 'none' })
    setTimeout(() => { uni.redirectTo({ url: '/pages/cart/index' }) }, 1500)
    return
}
```

- [ ] **Step 3: 构建**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
npm run build:mp-weixin
```

- [ ] **Step 4: Commit**

```bash
git add hardware-mall-uniapp/src/pages/checkout/index.vue
git commit -m "fix(uniapp): checkout 选中项为空时抛错回购物车, 不再静默下单 (B5)"
```

---

### Task 8: 修复 uniapp cart store 乐观更新无回滚

**Files:**
- Modify: `hardware-mall-uniapp/src/stores/cart.ts:33-57`

- [ ] **Step 1: 改 updateQuantity 带回滚**

Modify: `hardware-mall-uniapp/src/stores/cart.ts:33-44`

在 `updateQuantity` 方法中，行 36-37 乐观变更之前先拍旧值 `oldQuantity` / `oldSubtotal`，catch 分支中回滚并 `uni.showToast({ title: '更新数量失败, 请稍后重试', icon: 'none' })`。

- [ ] **Step 2: 改 removeItem 带回滚**

Modify: `hardware-mall-uniapp/src/stores/cart.ts:46-57`

同理：splice 之后 catch 把已删除的 item 放回原位。

- [ ] **Step 3: 构建**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
npm run build:mp-weixin
```

- [ ] **Step 4: Commit**

```bash
git add hardware-mall-uniapp/src/stores/cart.ts
git commit -m "fix(uniapp): cart store API 失败回滚 state + toast 提示 (B8)"
```

---

### Task 9: 新增 uniapp 物流展示页

**Files:**
- Create: `hardware-mall-uniapp/src/pages/logistics/index.vue`
- Modify: `hardware-mall-uniapp/src/pages.json`

- [ ] **Step 1: 创建物流页**

Create: `hardware-mall-uniapp/src/pages/logistics/index.vue`

```vue
<template>
  <view class="logistics-container">
    <view v-if="loading" class="loading-wrap"><LoadingState text="加载中..." /></view>
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
  id: number; name: string; code?: string; phones?: string[]; city?: string; status?: number
}

const list = ref<LogisticsItem[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    const data = await getLogisticsList()
    list.value = (data || []).filter((item: LogisticsItem) => item.status === 1)
  } catch (e: any) {
    uni.showToast({ title: e.message || '加载物流失败', icon: 'none' })
  } finally { loading.value = false }
})

const callPhone = (phone: string) => { uni.makePhoneCall({ phoneNumber: phone, fail: () => {} }) }
</script>

<style scoped>
.logistics-container { min-height: 100vh; background: #FAFAFA; padding: 20rpx; }
.loading-wrap { padding: 80rpx 0; }
.logistics-list { display: flex; flex-direction: column; gap: 20rpx; }
.logistics-item { background: #fff; border-radius: 16rpx; padding: 32rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.04); }
.logistics-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16rpx; }
.logistics-name { font-size: 30rpx; font-weight: 600; color: #333; }
.logistics-code { font-size: 24rpx; color: #999; }
.logistics-phones { display: flex; flex-wrap: wrap; align-items: center; margin: 12rpx 0; }
.phone-label { font-size: 26rpx; color: #666; margin-right: 16rpx; }
.phone-text { font-size: 26rpx; color: #1890ff; margin-right: 20rpx; padding: 4rpx 16rpx; background: #f0f8ff; border-radius: 8rpx; }
.logistics-city { margin-top: 12rpx; }
.city-text { font-size: 26rpx; color: #666; }
</style>
```

- [ ] **Step 2: 注册到 pages.json**

Modify: `hardware-mall-uniapp/src/pages.json`，在 `pages` 数组末尾追加：
```json
    {
      "path": "pages/logistics/index",
      "style": { "navigationBarTitleText": "物流方式" }
    },
```
同时检查 `pages/user/edit` 是否注册；未注册也一并追加。

- [ ] **Step 3: 构建**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
npm run build:mp-weixin
```

- [ ] **Step 4: Commit**

```bash
git add hardware-mall-uniapp/src/pages/logistics/index.vue hardware-mall-uniapp/src/pages.json
git commit -m "fix(uniapp): 新增物流展示页 + pages/user/edit 注册 (B6/B7)"
```

---

### Task 10: Phase 0 全量验收

- [ ] **Step 1: 后端编译 + 全部测试**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn clean test -q
```

- [ ] **Step 2: admin 构建**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-admin"
npm run build
```

- [ ] **Step 3: uniapp 构建**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
npm run build:mp-weixin
```

- [ ] **Step 4: 推送**

```bash
git push -u origin phase0-bug-fixes
```

---

## Phase 0 验收清单

| Task | 涉及 BUG | 验收命令 | 状态 |
|---|---|---|---|
| 2 | B1 AdminOrder 硬编码 userId=1L | `mvn test` | ⬜ |
| 3 | B2 PayController 越权查询 | `mvn test` | ⬜ |
| 4 | B4 admin 默认密码 | 编译 + 启动失败测试 | ⬜ |
| 5 | B4 admin 登录页默认值 | `npm run build` | ⬜ |
| 6 | B3 salesCount 并发丢失 | `mvn test` | ⬜ |
| 7 | B5 checkout 静默下单 | `npm run build:mp-weixin` | ⬜ |
| 8 | B8 cart 无回滚 | `npm run build:mp-weixin` | ⬜ |
| 9 | B6/B7 物流页+user/edit | `npm run build:mp-weixin` | ⬜ |
| 10 | 全量验收 | 3 个构建全绿 | ⬜ |

---

## Self-Review

- ✅ 覆盖 B1/B2/B3/B5/B6/B7/B8 + 部分 B4
- ⚠️ B4 仅做了"移除默认值"，BCrypt 哈希暂未做（决策记录：自用 1-2 人延后）
- ✅ 每个 Task 都有具体改代码、git 命令、验收命令
- ✅ 测试策略复用现有 mvn test / npm run build
