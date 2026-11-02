# Wholesale User-Group Pricing Design

> Status: Superseded / 废案
>
> This design is retained only for historical traceability. Production never adopted
> the two-tier SKU wholesale-price model. Do not use it as an alternative to the
> level-category pricing model in `docs/多级用户定价设计文档.md`.

## Goal

Let a subset of users (wholesalers) pay a different, per-SKU price while everyone
else (retail customers, guests) keeps paying the retail price. Wholesaler status is
assigned by an admin. No minimum order quantity. The wholesale price is visible only
to wholesalers.

## Current State

- Single-price model: `sku.price` is the only purchasable price.
- Order pricing is server-authoritative: `CreateOrderRequest` carries only
  `skuId` + `quantity`; `OrderServiceImpl.validateAndBuildSnapshot` re-reads each SKU
  and computes `OrderItem.price/subtotal`, `Order.totalAmount == Order.payAmount`,
  then WeChat prepay charges `order.payAmount`.
- `user.role` is auth-only (1 regular, 2 admin). There is no grouping dimension.
- Product list/detail are anonymous and cached user-agnostically in Redis
  (`product:list:*`, `product:detail:*`, `sku:info:*`).

## Data Model

`sku.wholesale_price DECIMAL(10,2) NULL` — NULL means no wholesale price (fallback to retail).
`user.user_group TINYINT NOT NULL DEFAULT 1` — 1 retail, 2 wholesale.
`order_item.price_type TINYINT NOT NULL DEFAULT 1` — 1 retail, 2 wholesale.

Migration script `hardware-mall-backend/src/main/resources/db/migration_2026-09-17_wholesale_pricing.sql`;
`init.sql` updated in parallel for fresh installs. No Flyway in this project.

Constants (kept physically separate from auth `role`), in `StatusConstants`:
`USER_GROUP_RETAIL=1`, `USER_GROUP_WHOLESALE=2`, `PRICE_TYPE_RETAIL=1`,
`PRICE_TYPE_WHOLESALE=2`. Mirrored in admin `src/constants/status.ts`.

## Pricing Service

New `service/PriceService.java` (interface with nested record) + `service/impl/PriceServiceImpl.java`.
No fields, no DB/Redis/transaction dependency — a pure function.

```java
record PriceResult(BigDecimal unitPrice, int priceType) {}
PriceResult resolve(Sku sku, Integer userGroup);
```

Rule: `userGroup == USER_GROUP_WHOLESALE && sku.wholesalePrice != null` returns the
wholesale price/type; otherwise retail. A `null` userGroup (legacy token, no request
context) resolves to retail.

## Identity Plumbing

- Login/refresh JWT claims gain `userGroup` (`UserController.login`, `UserServiceImpl.refreshToken`).
- `JwtUtil.getUserGroupFromToken` defaults to retail when the claim is absent.
- `JwtInterceptor` reads it into `UserContext`; `UserContext` gains a `userGroup`
  ThreadLocal cleared in `clear()`.

## Pricing Seams (all server-side)

- Order: `OrderServiceImpl.validateAndBuildSnapshot` replaces `sku.getPrice()` with
  `priceService.resolve(...)` and persists `order_item.price_type`.
- Cart: `CartServiceImpl.getCartList` prices `CartItemVO` by the caller's group
  (cart endpoints are authenticated, not cached).
- Product list: `ProductListVO` gains `wholesaleMinPrice/wholesaleMaxPrice`, computed
  over SKUs that have a wholesale price; null when none do.
- Product detail: `ProductDetailVO` serializes `Sku` entities, so `wholesalePrice`
  flows automatically once the entity field exists.

Both prices ship together in shared cache entries; the client chooses which to render
by user group, so no cache split is needed.

## Admin

- `PUT /api/admin/user/{id}/group` (`UserService.updateUserGroup`); the user list
  returns `Page<User>` so `userGroup` flows automatically.
- Admin user page shows a wholesaler tag + toggle (same pattern as the status switch).
- Admin SKU editor gains an optional wholesale price column. `SkuServiceImpl` writes
  the field and validates `wholesalePrice == null || wholesalePrice < price`.

## Miniprogram

- `UserInfo` type gains `userGroup` (login already returns the `User` entity).
- Product detail/list/category/search render the wholesale price first for
  wholesalers, with a "批发价" label; retail users and guests see retail only.
- Cart/checkout need no change: the server already returns group-priced values.

## Non-Goals

Wholesaler application/review flow, quantity tier pricing, membership levels,
per-identity cache splitting, backfilling historical orders.

## Compatibility

Legacy tokens without the `userGroup` claim resolve to retail, so no forced re-login.
Historical order snapshots are unchanged; only new orders carry `price_type`. WeChat
prepay/refund amount chains are untouched. Stale Redis entries
(`sku:info:*`, `product:list:*`, `product:detail:*`) expire within 1h or can be
cleared on deploy.

## Verification

- `PriceServiceImplTest`: all four quadrants plus `userGroup = null`.
- `OrderServiceImplTest`: wholesale identity yields wholesale price and `price_type=2`;
  retail yields retail price and `price_type=1`.
- `CartServiceImplTest`: per-group prices.
- Existing test suite passes; build compiles with JDK 17 (Lombok compatibility).
- Manual: a wholesale account and a retail account see different prices and are
  charged different amounts end to end.
