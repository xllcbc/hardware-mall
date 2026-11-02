# 批发商专属价 Implementation Plan

> **状态：Superseded / 废案。** 本计划仅用于历史追溯，不再执行。生产未采用 SKU 级两档批发价模型；正式方案见 `docs/多级用户定价设计文档.md`。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让管理员指定的批发商按每 SKU 的批发价结算，其余用户按零售价；批发价仅批发商可见，不设起批量。

**Architecture:** 新增纯函数 `PriceService` 作为唯一定价入口；用户分组经 JWT claim → `UserContext` 贯通；下单/购物车在服务端按身份取价并落 `order_item.price_type`；商品接口下发「双价格并存」的共享缓存 VO，由小程序按身份选择渲染。

**Tech Stack:** Java 17 / Spring Boot 3.2 / MyBatis-Plus / Redis / JUnit 5 + Mockito + AssertJ；Vue 3 + Element Plus（管理端）；uni-app Vue 3（小程序）。

**Spec:** `docs/superpowers/specs/2026-09-17-wholesale-pricing-design.md`

**Build note (AGENTS.md):** 必须 JDK 17 构建。后端命令统一在 `hardware-mall-backend/` 下执行：
`cmd.exe /c 'set JAVA_HOME=C:\Progra~1\Java\jdk-17.0.3.1&& mvn -q ...'`

---

## File Structure

**后端新增**
- `src/main/resources/db/migration_2026-09-17_wholesale_pricing.sql` — 三列迁移
- `src/main/java/com/example/mystore/service/PriceService.java` — 定价接口 + `PriceResult` record
- `src/main/java/com/example/mystore/service/impl/PriceServiceImpl.java` — 纯函数实现
- `src/test/java/com/example/mystore/service/impl/PriceServiceImplTest.java`

**后端修改**
- `entity/db/Sku.java`、`entity/db/User.java`、`entity/db/OrderItem.java` — 新字段
- `common/constant/StatusConstants.java` — 分组/价格类型常量
- `util/JwtUtil.java`、`util/UserContext.java`、`interceptor/JwtInterceptor.java`、`controller/user/UserController.java`、`service/impl/UserServiceImpl.java` — 身份贯通
- `service/impl/OrderServiceImpl.java`、`service/impl/CartServiceImpl.java` — 取价
- `entity/vo/ProductListVO.java`、`service/impl/SpuServiceImpl.java` — 列表批发价区间
- `service/UserService.java`、`service/impl/UserServiceImpl.java`、`controller/admin/AdminUserController.java` — 分组管理 API
- `service/impl/SkuServiceImpl.java` — 批发价写入与校验
- `src/main/resources/db/init.sql` — 新装 schema 同步

**管理端（Vue）**
- `src/constants/status.ts`、`src/api/admin/user.ts`、`src/api/admin/sku.ts`、`src/views/user/index.vue`、`src/views/spu/index.vue`

**小程序（uni-app）**
- `src/types/index.ts`、`src/pages/product/detail.vue`、`src/pages/product/list.vue`、`src/pages/index/index.vue`、`src/pages/category/index.vue`、`src/pages/search/index.vue`、`src/components/common/ProductCard.vue`

---

## Task 1: 数据地基（SQL + 实体 + 常量）

**Files:**
- Create: `hardware-mall-backend/src/main/resources/db/migration_2026-09-17_wholesale_pricing.sql`
- Modify: `hardware-mall-backend/src/main/resources/db/init.sql`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/entity/db/Sku.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/entity/db/User.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/entity/db/OrderItem.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/common/constant/StatusConstants.java`

- [ ] **Step 1: 写迁移脚本**

`migration_2026-09-17_wholesale_pricing.sql`:
```sql
-- 批发商专属价: 新增用户分组、SKU 批发价、订单价格类型
ALTER TABLE `sku`        ADD COLUMN `wholesale_price` DECIMAL(10,2) NULL COMMENT '批发价, NULL=不设';
ALTER TABLE `user`       ADD COLUMN `user_group`      TINYINT NOT NULL DEFAULT 1 COMMENT '用户分组: 1-零售 2-批发商';
ALTER TABLE `order_item` ADD COLUMN `price_type`      TINYINT NOT NULL DEFAULT 1 COMMENT '价格类型: 1-零售价 2-批发价';
```

- [ ] **Step 2: 同步 `init.sql` 新装 schema**

在 `sku` 建表语句的 `price` 列后加：
```sql
    `wholesale_price` DECIMAL(10,2) NULL COMMENT '批发价, NULL=不设',
```
在 `user` 建表语句的 `role` 列后加：
```sql
    `user_group` TINYINT NOT NULL DEFAULT 1 COMMENT '用户分组：1-零售，2-批发商',
```
在 `order_item` 建表语句的 `price` 列后加：
```sql
    `price_type` TINYINT NOT NULL DEFAULT 1 COMMENT '价格类型：1-零售价，2-批发价',
```

- [ ] **Step 3: 实体加字段**

`Sku.java` 在 `private BigDecimal price;` 后加：
```java
    private BigDecimal wholesalePrice;
```
`User.java` 在 `private Integer role;` 后加：
```java
    private Integer userGroup;
```
`OrderItem.java` 在 `private BigDecimal price;` 后加：
```java
    private Integer priceType;
```

- [ ] **Step 4: 常量**

`StatusConstants.java` 在「用户角色」段后新增：
```java
    // ==================== 用户分组(定价维度, 与鉴权 role 物理隔离) ====================
    /** 零售用户 */
    public static final int USER_GROUP_RETAIL = 1;
    /** 批发商 */
    public static final int USER_GROUP_WHOLESALE = 2;

    // ==================== 订单价格类型 ====================
    /** 零售价 */
    public static final int PRICE_TYPE_RETAIL = 1;
    /** 批发价 */
    public static final int PRICE_TYPE_WHOLESALE = 2;
```

- [ ] **Step 5: 编译验证**

Run (workdir `hardware-mall-backend`):
`cmd.exe /c 'set JAVA_HOME=C:\Progra~1\Java\jdk-17.0.3.1&& mvn -q -DskipTests compile'`
Expected: BUILD SUCCESS（无 Lombok getter/setter 报错）

- [ ] **Step 6: 提交**

```bash
git add hardware-mall-backend/src/main/resources/db/migration_2026-09-17_wholesale_pricing.sql \
        hardware-mall-backend/src/main/resources/db/init.sql \
        hardware-mall-backend/src/main/java/com/example/mystore/entity/db/Sku.java \
        hardware-mall-backend/src/main/java/com/example/mystore/entity/db/User.java \
        hardware-mall-backend/src/main/java/com/example/mystore/entity/db/OrderItem.java \
        hardware-mall-backend/src/main/java/com/example/mystore/common/constant/StatusConstants.java
git commit -m "feat(pricing): 新增用户分组/SKU批发价/订单价格类型字段与常量"
```

---

## Task 2: PriceService（TDD）

**Files:**
- Create: `hardware-mall-backend/src/main/java/com/example/mystore/service/PriceService.java`
- Create: `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/PriceServiceImpl.java`
- Test: `hardware-mall-backend/src/test/java/com/example/mystore/service/impl/PriceServiceImplTest.java`

- [ ] **Step 1: 写失败测试**

`PriceServiceImplTest.java`:
```java
package com.example.mystore.service.impl;

import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.service.PriceService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PriceServiceImplTest {

    private final PriceService priceService = new PriceServiceImpl();

    private Sku sku(BigDecimal price, BigDecimal wholesalePrice) {
        Sku sku = new Sku();
        sku.setPrice(price);
        sku.setWholesalePrice(wholesalePrice);
        return sku;
    }

    @Test
    void wholesaleUserWithWholesalePrice_paysWholesale() {
        Sku sku = sku(new BigDecimal("25.00"), new BigDecimal("18.00"));
        PriceService.PriceResult r = priceService.resolve(sku, StatusConstants.USER_GROUP_WHOLESALE);
        assertThat(r.unitPrice()).isEqualByComparingTo("18.00");
        assertThat(r.priceType()).isEqualTo(StatusConstants.PRICE_TYPE_WHOLESALE);
    }

    @Test
    void wholesaleUserWithoutWholesalePrice_fallsBackToRetail() {
        Sku sku = sku(new BigDecimal("25.00"), null);
        PriceService.PriceResult r = priceService.resolve(sku, StatusConstants.USER_GROUP_WHOLESALE);
        assertThat(r.unitPrice()).isEqualByComparingTo("25.00");
        assertThat(r.priceType()).isEqualTo(StatusConstants.PRICE_TYPE_RETAIL);
    }

    @Test
    void retailUserAlwaysPaysRetail_evenIfWholesalePriceExists() {
        Sku sku = sku(new BigDecimal("25.00"), new BigDecimal("18.00"));
        PriceService.PriceResult r = priceService.resolve(sku, StatusConstants.USER_GROUP_RETAIL);
        assertThat(r.unitPrice()).isEqualByComparingTo("25.00");
        assertThat(r.priceType()).isEqualTo(StatusConstants.PRICE_TYPE_RETAIL);
    }

    @Test
    void nullUserGroup_treatedAsRetail() {
        Sku sku = sku(new BigDecimal("25.00"), new BigDecimal("18.00"));
        PriceService.PriceResult r = priceService.resolve(sku, null);
        assertThat(r.unitPrice()).isEqualByComparingTo("25.00");
        assertThat(r.priceType()).isEqualTo(StatusConstants.PRICE_TYPE_RETAIL);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cmd.exe /c 'set JAVA_HOME=C:\Progra~1\Java\jdk-17.0.3.1&& mvn -q -Dtest=PriceServiceImplTest test'`
Expected: 编译失败（`PriceServiceImpl` / `PriceService` 不存在）

- [ ] **Step 3: 写接口**

`PriceService.java`:
```java
package com.example.mystore.service;

import com.example.mystore.entity.db.Sku;

import java.math.BigDecimal;

/**
 * 定价入口: 按用户分组解析 SKU 成交价。
 * 所有成交价必须经此解析, 不得在别处直接读 sku.price。
 */
public interface PriceService {

    /**
     * @param unitPrice 成交单价
     * @param priceType 价格类型(StatusConstants.PRICE_TYPE_*)
     */
    record PriceResult(BigDecimal unitPrice, int priceType) {
    }

    /**
     * @param userGroup StatusConstants.USER_GROUP_*, 为 null 按零售处理
     */
    PriceResult resolve(Sku sku, Integer userGroup);
}
```

- [ ] **Step 4: 写实现**

`PriceServiceImpl.java`:
```java
package com.example.mystore.service.impl;

import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.service.PriceService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PriceServiceImpl implements PriceService {

    @Override
    public PriceResult resolve(Sku sku, Integer userGroup) {
        if (sku == null || sku.getPrice() == null) {
            throw new IllegalArgumentException("SKU 价格缺失");
        }
        if (userGroup != null
                && userGroup == StatusConstants.USER_GROUP_WHOLESALE
                && sku.getWholesalePrice() != null) {
            return new PriceResult(sku.getWholesalePrice(), StatusConstants.PRICE_TYPE_WHOLESALE);
        }
        return new PriceResult(sku.getPrice(), StatusConstants.PRICE_TYPE_RETAIL);
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `cmd.exe /c 'set JAVA_HOME=C:\Progra~1\Java\jdk-17.0.3.1&& mvn -q -Dtest=PriceServiceImplTest test'`
Expected: Tests run: 4, Failures: 0

- [ ] **Step 6: 提交**

```bash
git add hardware-mall-backend/src/main/java/com/example/mystore/service/PriceService.java \
        hardware-mall-backend/src/main/java/com/example/mystore/service/impl/PriceServiceImpl.java \
        hardware-mall-backend/src/test/java/com/example/mystore/service/impl/PriceServiceImplTest.java
git commit -m "feat(pricing): 新增 PriceService 纯函数定价入口"
```

---

## Task 3: 身份贯通（JWT claim → UserContext）

**Files:**
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/util/JwtUtil.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/util/UserContext.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/interceptor/JwtInterceptor.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/controller/user/UserController.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/UserServiceImpl.java`
- Test: `hardware-mall-backend/src/test/java/com/example/mystore/util/UserContextTest.java`（新增）

- [ ] **Step 1: `JwtUtil` 加读取方法**

在 `getRoleFromToken` 后加：
```java
    public Integer getUserGroupFromToken(String token) {
        Claims claims = parseToken(token);
        Object group = claims.get("userGroup");
        if (group instanceof Number) {
            return ((Number) group).intValue();
        }
        return null;
    }
```

- [ ] **Step 2: `UserContext` 加分组**

`UserContext.java` 完整替换为：
```java
package com.example.mystore.util;

public class UserContext {

    private static final ThreadLocal<Long> userIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<Integer> roleHolder = new ThreadLocal<>();
    private static final ThreadLocal<Integer> userGroupHolder = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        userIdHolder.set(userId);
    }

    public static Long getUserId() {
        return userIdHolder.get();
    }

    public static void setRole(Integer role) {
        roleHolder.set(role);
    }

    public static Integer getRole() {
        return roleHolder.get();
    }

    public static void setUserGroup(Integer userGroup) {
        userGroupHolder.set(userGroup);
    }

    public static Integer getUserGroup() {
        return userGroupHolder.get();
    }

    public static void clear() {
        userIdHolder.remove();
        roleHolder.remove();
        userGroupHolder.remove();
    }
}
```

- [ ] **Step 3: `JwtInterceptor` 读取并写入**

在 `JwtInterceptor.java` 的 `Integer role = jwtUtil.getRoleFromToken(token);` 后加：
```java
            Integer userGroup = jwtUtil.getUserGroupFromToken(token);
```
并把 `UserContext.setRole(role);` 后改为（新增一行）：
```java
            UserContext.setRole(role);
            UserContext.setUserGroup(userGroup != null ? userGroup : com.example.mystore.common.constant.StatusConstants.USER_GROUP_RETAIL);
```

- [ ] **Step 4: 登录/刷新写入 claim**

`UserController.login` 的 `claims.put("role", user.getRole());` 后加：
```java
        claims.put("userGroup", user.getUserGroup() != null
                ? user.getUserGroup()
                : com.example.mystore.common.constant.StatusConstants.USER_GROUP_RETAIL);
```
`UserServiceImpl.refreshToken` 的 `claims.put("role", user.getRole());` 后加同一段。

`UserServiceImpl.login` 新建用户分支（`user.setStatus(...)` 后）加：
```java
            user.setUserGroup(StatusConstants.USER_GROUP_RETAIL);
```

- [ ] **Step 5: 测试**

`UserContextTest.java`:
```java
package com.example.mystore.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void setAndGetUserGroup() {
        UserContext.setUserGroup(2);
        assertThat(UserContext.getUserGroup()).isEqualTo(2);
    }

    @Test
    void clearRemovesUserGroup() {
        UserContext.setUserGroup(2);
        UserContext.clear();
        assertThat(UserContext.getUserGroup()).isNull();
    }
}
```

- [ ] **Step 6: 运行测试 + 编译**

Run: `cmd.exe /c 'set JAVA_HOME=C:\Progra~1\Java\jdk-17.0.3.1&& mvn -q -Dtest=UserContextTest test'`
Expected: Tests run: 2, Failures: 0

- [ ] **Step 7: 提交**

```bash
git add hardware-mall-backend/src/main/java/com/example/mystore/util/JwtUtil.java \
        hardware-mall-backend/src/main/java/com/example/mystore/util/UserContext.java \
        hardware-mall-backend/src/main/java/com/example/mystore/interceptor/JwtInterceptor.java \
        hardware-mall-backend/src/main/java/com/example/mystore/controller/user/UserController.java \
        hardware-mall-backend/src/main/java/com/example/mystore/service/impl/UserServiceImpl.java \
        hardware-mall-backend/src/test/java/com/example/mystore/util/UserContextTest.java
git commit -m "feat(pricing): 用户分组经 JWT claim 贯通 UserContext(旧token默认零售)"
```

---

## Task 4: 下单按身份取价 + 落 priceType

**Files:**
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/OrderServiceImpl.java`
- Test: `hardware-mall-backend/src/test/java/com/example/mystore/service/impl/OrderServiceImplTest.java`

- [ ] **Step 1: 更新测试：加 PriceService mock**

`OrderServiceImplTest` 字段区加：
```java
    @Mock
    private PriceService priceService;
```
import 加 `import com.example.mystore.service.PriceService;` 与 `import com.example.mystore.util.UserContext;`。

`@BeforeEach` 初始化处加（真实实现，保证取价语义）：
```java
        when(priceService.resolve(any(Sku.class), any()))
                .thenAnswer(inv -> {
                    Sku s = inv.getArgument(0);
                    Integer group = inv.getArgument(1);
                    if (group != null && group == StatusConstants.USER_GROUP_WHOLESALE
                            && s.getWholesalePrice() != null) {
                        return new PriceService.PriceResult(s.getWholesalePrice(),
                                StatusConstants.PRICE_TYPE_WHOLESALE);
                    }
                    return new PriceService.PriceResult(s.getPrice(),
                            StatusConstants.PRICE_TYPE_RETAIL);
                });
```

- [ ] **Step 2: 写新失败测试**

```java
    @Test
    void wholesaleUser_getsWholesalePriceAndPriceType() {
        UserContext.setUserGroup(StatusConstants.USER_GROUP_WHOLESALE);
        try {
            Sku sku = new Sku();
            sku.setId(1L);
            sku.setSpuId(1L);
            sku.setStatus(1);
            sku.setStock(100);
            sku.setPrice(new BigDecimal("25.00"));
            sku.setWholesalePrice(new BigDecimal("18.00"));
            when(skuService.getSkuById(1L)).thenReturn(sku);

            // ... 复用本测试类现有的 address/logistics/spu/库存 mock 准备方式，构造 1 件下单 ...
            // 断言订单明细:
            ArgumentCaptor<List<OrderItem>> captor = ArgumentCaptor.forClass(List.class);
            verify(orderItemMapper).insertBatch(captor.capture());
            OrderItem item = captor.getValue().get(0);
            assertThat(item.getPrice()).isEqualByComparingTo("18.00");
            assertThat(item.getPriceType()).isEqualTo(StatusConstants.PRICE_TYPE_WHOLESALE);
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void retailUser_getsRetailPriceAndPriceType() {
        UserContext.setUserGroup(StatusConstants.USER_GROUP_RETAIL);
        try {
            // 同上, 同一 SKU(含批发价)
            // 断言 item.getPrice() == 25.00, item.getPriceType() == PRICE_TYPE_RETAIL
        } finally {
            UserContext.clear();
        }
    }
```
> 实施提示：本测试类已有若干 `createOrder` 正例，直接复制其中一个的 mock 准备块，只改价格断言部分。

- [ ] **Step 3: 运行确认失败**

Run: `cmd.exe /c 'set JAVA_HOME=C:\Progra~1\Java\jdk-17.0.3.1&& mvn -q -Dtest=OrderServiceImplTest test'`
Expected: 失败/编译失败（`priceType` 未写入、`priceService` 未注入）

- [ ] **Step 4: 实现**

`OrderServiceImpl` 字段区加：
```java
    private final PriceService priceService;
```
import 加 `com.example.mystore.service.PriceService` 与 `com.example.mystore.util.UserContext`。

`validateAndBuildSnapshot` 第 156 行把
```java
            BigDecimal subtotal = sku.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
```
改为
```java
            PriceService.PriceResult priceResult =
                    priceService.resolve(sku, UserContext.getUserGroup());
            BigDecimal subtotal = priceResult.unitPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
```
第 178 行 `item.setPrice(sku.getPrice());` 改为
```java
            item.setPrice(priceResult.unitPrice());
            item.setPriceType(priceResult.priceType());
```

- [ ] **Step 5: 运行确认通过**

Run: `cmd.exe /c 'set JAVA_HOME=C:\Progra~1\Java\jdk-17.0.3.1&& mvn -q -Dtest=OrderServiceImplTest test'`
Expected: 全绿

- [ ] **Step 6: 提交**

```bash
git add hardware-mall-backend/src/main/java/com/example/mystore/service/impl/OrderServiceImpl.java \
        hardware-mall-backend/src/test/java/com/example/mystore/service/impl/OrderServiceImplTest.java
git commit -m "feat(pricing): 下单按用户分组取价并落 order_item.price_type"
```

---

## Task 5: 购物车按身份取价

**Files:**
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/CartServiceImpl.java`
- Test: `hardware-mall-backend/src/test/java/com/example/mystore/service/impl/CartServiceImplTest.java`（若不存在则新建）

- [ ] **Step 1: 写失败测试**

```java
    @Test
    void wholesaleUser_cartShowsWholesalePriceAndSubtotal() {
        UserContext.setUserGroup(StatusConstants.USER_GROUP_WHOLESALE);
        try {
            // mock: 一条 cart(skuId=1, qty=2), sku(price=25, wholesale=18), spu 上架
            // 调用 cartService.getCartList(1L)
            // assertThat(vo.getPrice()).isEqualByComparingTo("18.00");
            // assertThat(vo.getSubtotal()).isEqualByComparingTo("36.00");
        } finally {
            UserContext.clear();
        }
    }
```
（按本类既有 mock 风格补全准备块。）

- [ ] **Step 2: 运行确认失败**

`mvn -q -Dtest=CartServiceImplTest test` → FAIL（价格仍为 25 / subtotal 50）

- [ ] **Step 3: 实现**

`CartServiceImpl` 字段区加 `private final PriceService priceService;`，import `PriceService`、`UserContext`。

`getCartList` 循环体把
```java
                vo.setPrice(sku.getPrice());
```
改为
```java
                PriceService.PriceResult priceResult =
                        priceService.resolve(sku, UserContext.getUserGroup());
                vo.setPrice(priceResult.unitPrice());
```
把
```java
                vo.setSubtotal(sku.getPrice().multiply(BigDecimal.valueOf(cart.getQuantity())));
```
改为
```java
                vo.setSubtotal(priceResult.unitPrice().multiply(BigDecimal.valueOf(cart.getQuantity())));
```
（`minPrice`/`maxPrice` 仍为 SPU 零售区间，保持不变。）

- [ ] **Step 4: 运行确认通过**

`mvn -q -Dtest=CartServiceImplTest test` → 全绿

- [ ] **Step 5: 提交**

```bash
git add hardware-mall-backend/src/main/java/com/example/mystore/service/impl/CartServiceImpl.java \
        hardware-mall-backend/src/test/java/com/example/mystore/service/impl/CartServiceImplTest.java
git commit -m "feat(pricing): 购物车按用户分组返回成交价与金额"
```

---

## Task 6: 商品列表下发批发价区间

**Files:**
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/entity/vo/ProductListVO.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/SpuServiceImpl.java`
- Test: `hardware-mall-backend/src/test/java/com/example/mystore/service/impl/SpuServiceImplTest.java`

- [ ] **Step 1: 写失败测试**

```java
    @Test
    void productList_includesWholesalePriceRangeWhenPresent() {
        // 两个 SKU: (price=25, wholesale=18), (price=30, wholesale=22)
        // 断言 vo.getWholesaleMinPrice() == 18.00, vo.getWholesaleMaxPrice() == 22.00
    }

    @Test
    void productList_wholesaleRangeNullWhenNoSkuHasWholesalePrice() {
        // SKU 都无批发价 → wholesaleMinPrice/wholesaleMaxPrice 均为 null, minPrice/maxPrice 仍为零售区间
    }
```

- [ ] **Step 2: 运行确认失败**

`mvn -q -Dtest=SpuServiceImplTest test` → 编译失败（无 getter）

- [ ] **Step 3: VO 加字段**

`ProductListVO.java` 在 `private BigDecimal maxPrice;` 后加：
```java
    private BigDecimal wholesaleMinPrice;
    private BigDecimal wholesaleMaxPrice;
```

- [ ] **Step 4: 实现计算**

`SpuServiceImpl` 的 `buildProductListPage` 中，SKU 价格区间计算块（约 235-243 行）改为：
```java
            List<Sku> skus = skusBySpu.getOrDefault(spu.getId(), Collections.emptyList());
            if (!skus.isEmpty()) {
                BigDecimal minPrice = skus.stream().map(Sku::getPrice).min(BigDecimal::compareTo).orElse(spu.getOriginalPrice());
                BigDecimal maxPrice = skus.stream().map(Sku::getPrice).max(BigDecimal::compareTo).orElse(spu.getOriginalPrice());
                vo.setMinPrice(minPrice);
                vo.setMaxPrice(maxPrice);
                List<BigDecimal> wholesalePrices = skus.stream()
                        .map(Sku::getWholesalePrice)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                if (!wholesalePrices.isEmpty()) {
                    vo.setWholesaleMinPrice(wholesalePrices.stream().min(BigDecimal::compareTo).orElse(null));
                    vo.setWholesaleMaxPrice(wholesalePrices.stream().max(BigDecimal::compareTo).orElse(null));
                }
            } else {
                vo.setMinPrice(spu.getOriginalPrice());
                vo.setMaxPrice(spu.getOriginalPrice());
            }
```
对 `buildRecommendVO` 中同样的价格块做同样改动（推荐列表也要有批发价区间）。import 确保存在 `java.util.Objects`（无则加）。

- [ ] **Step 5: 运行确认通过**

`mvn -q -Dtest=SpuServiceImplTest test` → 全绿

- [ ] **Step 6: 提交**

```bash
git add hardware-mall-backend/src/main/java/com/example/mystore/entity/vo/ProductListVO.java \
        hardware-mall-backend/src/main/java/com/example/mystore/service/impl/SpuServiceImpl.java \
        hardware-mall-backend/src/test/java/com/example/mystore/service/impl/SpuServiceImplTest.java
git commit -m "feat(pricing): 商品列表下发批发价区间(共享缓存, 前端按身份渲染)"
```

---

## Task 7: 管理端后端（分组管理 + 批发价校验）

**Files:**
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/service/UserService.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/UserServiceImpl.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/AdminUserController.java`
- Modify: `hardware-mall-backend/src/main/java/com/example/mystore/service/impl/SkuServiceImpl.java`
- Test: `hardware-mall-backend/src/test/java/com/example/mystore/service/impl/UserServiceImplTest.java`、`SkuServiceImplTest.java`

- [ ] **Step 1: 写失败测试**

`UserServiceImplTest`:
```java
    @Test
    void updateUserGroup_persistsWholesale() {
        User u = new User();
        u.setId(5L);
        when(userMapper.selectById(5L)).thenReturn(u);
        userService.updateUserGroup(5L, StatusConstants.USER_GROUP_WHOLESALE);
        assertThat(u.getUserGroup()).isEqualTo(StatusConstants.USER_GROUP_WHOLESALE);
        verify(userMapper).updateById(u);
    }

    @Test
    void updateUserGroup_rejectsUnknownValue() {
        assertThatThrownBy(() -> userService.updateUserGroup(5L, 99))
                .isInstanceOf(BusinessException.class);
    }
```
`SkuServiceImplTest`:
```java
    @Test
    void createSku_rejectsWholesalePriceNotBelowRetail() {
        Sku sku = new Sku();
        sku.setSpuId(1L);
        sku.setPrice(new BigDecimal("20.00"));
        sku.setWholesalePrice(new BigDecimal("20.00"));
        assertThatThrownBy(() -> skuService.createSku(sku))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("批发价");
    }
```

- [ ] **Step 2: 运行确认失败**

`mvn -q -Dtest=UserServiceImplTest,SkuServiceImplTest test` → 编译失败（方法不存在）

- [ ] **Step 3: UserService 接口 + 实现**

`UserService.java` 在 `updateUserStatus` 后加：
```java
    void updateUserGroup(Long id, Integer userGroup);
```
`UserServiceImpl` 实现（放在 `updateUserRegion` 附近）：
```java
    @Override
    public void updateUserGroup(Long id, Integer userGroup) {
        if (userGroup == null
                || (userGroup != StatusConstants.USER_GROUP_RETAIL
                    && userGroup != StatusConstants.USER_GROUP_WHOLESALE)) {
            throw new BusinessException("无效的用户分组");
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setUserGroup(userGroup);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }
```

- [ ] **Step 4: AdminUserController 加接口**

在 `updateUserRegion` 后加：
```java
    @PutMapping("/{id}/group")
    public Result<Void> updateUserGroup(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> params) {
        userService.updateUserGroup(id, params.get("userGroup"));
        return Result.success();
    }
```

- [ ] **Step 5: SkuServiceImpl 批发价写入与校验**

`SkuServiceImpl` 加私有方法：
```java
    private void validateWholesalePrice(BigDecimal retailPrice, BigDecimal wholesalePrice) {
        if (wholesalePrice == null) {
            return;
        }
        if (wholesalePrice.signum() < 0) {
            throw new BusinessException("批发价不能为负");
        }
        if (retailPrice != null && wholesalePrice.compareTo(retailPrice) >= 0) {
            throw new BusinessException("批发价必须低于零售价");
        }
    }
```
`createSku` 在 `skuMapper.insert(sku);` 前加：
```java
        validateWholesalePrice(sku.getPrice(), sku.getWholesalePrice());
```
`updateSku` 在 `if (sku.getPrice() != null)` 块后加：
```java
        BigDecimal effectivePrice = sku.getPrice() != null ? sku.getPrice() : exist.getPrice();
        validateWholesalePrice(effectivePrice, sku.getWholesalePrice());
        exist.setWholesalePrice(sku.getWholesalePrice());
```
> 语义：管理端提交整行 SKU，`wholesalePrice=null` 表示清除批发价。

- [ ] **Step 6: 运行确认通过**

`mvn -q -Dtest=UserServiceImplTest,SkuServiceImplTest test` → 全绿

- [ ] **Step 7: 提交**

```bash
git add hardware-mall-backend/src/main/java/com/example/mystore/service/UserService.java \
        hardware-mall-backend/src/main/java/com/example/mystore/service/impl/UserServiceImpl.java \
        hardware-mall-backend/src/main/java/com/example/mystore/controller/admin/AdminUserController.java \
        hardware-mall-backend/src/main/java/com/example/mystore/service/impl/SkuServiceImpl.java \
        hardware-mall-backend/src/test/java/com/example/mystore/service/impl/UserServiceImplTest.java \
        hardware-mall-backend/src/test/java/com/example/mystore/service/impl/SkuServiceImplTest.java
git commit -m "feat(pricing): 管理端用户分组接口与SKU批发价校验"
```

---

## Task 8: 管理端前端

**Files:**
- Modify: `hardware-mall-admin/src/constants/status.ts`
- Modify: `hardware-mall-admin/src/api/admin/user.ts`
- Modify: `hardware-mall-admin/src/api/admin/sku.ts`
- Modify: `hardware-mall-admin/src/views/user/index.vue`
- Modify: `hardware-mall-admin/src/views/spu/index.vue`

- [ ] **Step 1: 常量**

`status.ts` 在「用户状态」段后加：
```ts
// ==================== 用户分组(定价维度) ====================
export const USER_GROUP = {
  RETAIL: 1,     // 零售
  WHOLESALE: 2,  // 批发商
} as const

export const USER_GROUP_TEXT: Record<number, string> = {
  [USER_GROUP.RETAIL]: '零售',
  [USER_GROUP.WHOLESALE]: '批发商',
}
```

- [ ] **Step 2: API**

`api/admin/user.ts` 的 `User` 接口加 `userGroup: number`，并加：
```ts
export const updateUserGroup = (id: number, userGroup: number) => {
  return request.put(`/admin/user/${id}/group`, { userGroup })
}
```
`api/admin/sku.ts` 的 `Sku` 接口加 `wholesalePrice?: number`。

- [ ] **Step 3: 用户页加批发商标签与切换**

`views/user/index.vue`：
- import 加 `USER_GROUP, USER_GROUP_TEXT` 与 `updateUserGroup`
- 「角色」列后加一列：
```vue
        <el-table-column label="分组" width="110">
          <template #default="{ row }">
            <el-tag :type="row.userGroup === USER_GROUP.WHOLESALE ? 'success' : 'info'" size="small" effect="light">
              {{ USER_GROUP_TEXT[row.userGroup] || '零售' }}
            </el-tag>
          </template>
        </el-table-column>
```
- 操作列加按钮：
```vue
            <el-button link type="warning" @click="handleToggleGroup(row)">
              {{ row.userGroup === USER_GROUP.WHOLESALE ? '取消批发' : '设为批发商' }}
            </el-button>
```
- script 加处理方法（照现有 `handleEditRegion` 的成功/失败提示风格）：
```ts
const handleToggleGroup = async (row: any) => {
  const next = row.userGroup === USER_GROUP.WHOLESALE ? USER_GROUP.RETAIL : USER_GROUP.WHOLESALE
  await updateUserGroup(row.id, next)
  ElMessage.success('已更新用户分组')
  loadData()
}
```

- [ ] **Step 4: SKU 编辑加批发价列**

`views/spu/index.vue` SKU 表格「价格(元)」列后加：
```vue
        <el-table-column label="批发价(元)" width="150">
          <template #default="{ row }">
            <el-input-number v-model="row.wholesalePrice" :min="0" :precision="2" :controls="false" placeholder="不设" style="width: 100%" />
          </template>
        </el-table-column>
```
（`createSku`/`updateSku` 提交时 `row` / `sku` 已含 `wholesalePrice`，无需改提交代码。）

- [ ] **Step 5: 类型检查/构建**

Run (workdir `hardware-mall-admin`): `npm run build`
Expected: 构建成功

- [ ] **Step 6: 提交**

```bash
git add hardware-mall-admin/src/constants/status.ts \
        hardware-mall-admin/src/api/admin/user.ts \
        hardware-mall-admin/src/api/admin/sku.ts \
        hardware-mall-admin/src/views/user/index.vue \
        hardware-mall-admin/src/views/spu/index.vue
git commit -m "feat(admin): 用户分组切换与SKU批发价编辑"
```

---

## Task 9: 小程序按身份渲染批发价

**Files:**
- Modify: `hardware-mall-uniapp/src/types/index.ts`
- Modify: `hardware-mall-uniapp/src/pages/product/detail.vue`
- Modify: `hardware-mall-uniapp/src/components/common/ProductCard.vue`
- Modify: `hardware-mall-uniapp/src/pages/product/list.vue`
- Modify: `hardware-mall-uniapp/src/pages/index/index.vue`
- Modify: `hardware-mall-uniapp/src/pages/category/index.vue`
- Modify: `hardware-mall-uniapp/src/pages/search/index.vue`

- [ ] **Step 1: 类型**

`types/index.ts`：
- `UserInfo` 加 `userGroup?: number`
- `Sku` 接口加 `wholesalePrice?: number`
- 商品列表相关接口（含 `minPrice`/`maxPrice` 的那几处）加 `wholesaleMinPrice?: number`、`wholesaleMaxPrice?: number`

- [ ] **Step 2: 详情页**

`pages/product/detail.vue`：
- import 加 `import { useUserStore } from '@/stores/user'`，并 `const userStore = useUserStore()`
- `currentSkuPrice` 改为：
```ts
const isWholesaleUser = computed(() => userStore.userInfo?.userGroup === 2)

const showWholesalePrice = computed(() =>
  !!(currentSku.value && isWholesaleUser.value && currentSku.value.wholesalePrice != null)
)

const currentSkuPrice = computed(() => {
  const sku = currentSku.value
  if (sku && isWholesaleUser.value && sku.wholesalePrice != null) {
    return sku.wholesalePrice
  }
  return sku?.price || product.value.originalPrice || 0
})
```
- 价格区加标签：
```vue
          <text v-if="showWholesalePrice" class="wholesale-tag">批发价</text>
```
- 样式加：
```scss
.wholesale-tag {
  margin-left: 12rpx;
  padding: 2rpx 12rpx;
  font-size: 20rpx;
  color: #FFFFFF;
  background: #C9A86C;
  border-radius: 6rpx;
}
```

- [ ] **Step 3: ProductCard 支持批发区间**

`components/common/ProductCard.vue`：
- Props 加 `wholesaleMinPrice?: number | string`、`wholesaleMaxPrice?: number | string`
- `displayPrice` 计算改为优先用批发区间（仅当父组件传入时）：
```ts
const displayPrice = computed(() => {
  const base = props.wholesaleMinPrice != null ? props.wholesaleMinPrice : props.minPrice
  if (base != null && base !== props.originalPrice) {
    return String(base)
  }
  return String(props.price ?? props.originalPrice ?? '')
})
```
（保留原有 `formatPrice` 使用方式；若原 `displayPrice` 结构不同，按同语义合并。）

- [ ] **Step 4: 列表类页传参**

`pages/product/list.vue`、`pages/index/index.vue`、`pages/category/index.vue`、`pages/search/index.vue`：每处 import `useUserStore`，`const userStore = useUserStore()`，`const isWholesaleUser = computed(() => userStore.userInfo?.userGroup === 2)`，并在商品卡片上传：
```vue
          :wholesale-min-price="isWholesaleUser ? (product.wholesaleMinPrice ?? undefined) : undefined"
```
`product/list.vue:59` 的内联价格文本也改为优先批发价：
```vue
              <text class="price-value">{{ (isWholesaleUser && product.wholesaleMinPrice != null) ? product.wholesaleMinPrice : (product.minPrice ?? product.price ?? product.originalPrice) }}</text>
```

- [ ] **Step 5: 构建验证**

Run (workdir `hardware-mall-uniapp`): `npm run build:mp-weixin`
Expected: 构建成功

- [ ] **Step 6: 提交**

```bash
git add hardware-mall-uniapp/src/types/index.ts \
        hardware-mall-uniapp/src/pages/product/detail.vue \
        hardware-mall-uniapp/src/components/common/ProductCard.vue \
        hardware-mall-uniapp/src/pages/product/list.vue \
        hardware-mall-uniapp/src/pages/index/index.vue \
        hardware-mall-uniapp/src/pages/category/index.vue \
        hardware-mall-uniapp/src/pages/search/index.vue
git commit -m "feat(uniapp): 批发商按身份优先展示批发价(散户/游客无感知)"
```

---

## Task 10: 集成验收

- [ ] **Step 1: 后端全量测试**

Run (workdir `hardware-mall-backend`):
`cmd.exe /c 'set JAVA_HOME=C:\Progra~1\Java\jdk-17.0.3.1&& mvn -q test'`
Expected: 全绿

- [ ] **Step 2: 数据库迁移**

在目标库执行 `db/migration_2026-09-17_wholesale_pricing.sql`。
验证：`SHOW COLUMNS FROM sku LIKE 'wholesale_price';` 等三列存在。

- [ ] **Step 3: 清理价格相关缓存**

Redis 删除 `sku:info:*`、`product:list:*`、`product:detail:*`（或等 1h TTL）。

- [ ] **Step 4: 端到端手测**

1. 管理端给某用户设「批发商」、给某 SKU 填批发价。
2. 该用户小程序登录 → 详情/列表显示批发价（带标签）；购物车/结算金额为批发价。
3. 批发账号下单 → DB `order_item.price` = 批发价、`price_type = 2`，微信支付金额 = 批发价 × 数量。
4. 零售账号 / 游客 → 全程只见零售价、`price_type = 1`。
5. 删除批发价后 → 批发账号回退零售价。

- [ ] **Step 5: 勾选 spec 覆盖**

对照 `docs/superpowers/specs/2026-09-17-wholesale-pricing-design.md` 逐条确认：数据模型 / PriceService / 身份贯通 / 取价接缝 / 管理端 / 小程序 / 兼容性 / 测试 —— 均有对应任务完成。

---

## Self-Review Notes

- **Spec coverage:** 数据模型→T1；PriceService→T2；身份贯通→T3；下单→T4；购物车→T5；列表→T6；管理端 API/校验→T7；管理端 UI→T8；小程序→T9；兼容与验收→T10。历史订单回填、申请审核、起批量均按 spec 列为非目标，未排任务。
- **类型一致性：** 全程使用 `PriceService.PriceResult.unitPrice()/priceType()`、`USER_GROUP_*`、`PRICE_TYPE_*`、`wholesalePrice`、`wholesaleMinPrice/wholesaleMaxPrice`、`userGroup`、`priceType`，命名在各任务间保持一致。
- **风险点：** `OrderServiceImplTest` 注入新依赖后必须补 `PriceService` mock，否则 Mockito 注入 null 导致既有用例 NPE（T4 Step 1 已覆盖）。
