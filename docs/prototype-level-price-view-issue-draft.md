# Implementation issue draft: 分级价格视图与用户等级筛选

推荐标题：

```text
feat(admin): 增加商品分级价格视图与用户等级筛选
```

推荐标签：

```text
ready-for-agent
```

推荐状态：

```text
open
```

## What to build

在现有商品管理和用户管理页面中增加用户等级价格视角，不新建独立业务页面。

- 商品管理支持切换零售价和启用中的用户等级价格。
- 商品列表展示零售价区间、等级结算价、倍率、缺口和 `excludeDiscount` 状态。
- 用户分级页跳转到当前等级的商品价格视图和用户名单。
- 用户管理支持从 URL 读取 `levelId` 并按等级过滤。
- 默认使用无横向滚动的紧凑表格，同时保留“完整字段”切换。

## Acceptance criteria

- [ ] 商品管理默认展示零售价格视角，可切换任一启用等级。
- [ ] 商品价格同时显示零售区间和等级结算区间。
- [ ] `excludeDiscount = 1` 明确显示“不参与折扣”，所有等级按 SKU 零售价。
- [ ] 分类倍率缺失明确显示“未配置，零售兜底”，不能与显式 1.0000 混淆。
- [ ] 价格视角、分类、关键词、状态和分页均由服务端处理。
- [ ] 商品管理复用最新 `main` 的两步式 SKU 配置，不恢复旧版笛卡尔积入口。
- [ ] 用户管理增加用户等级筛选，支持 URL 深链 `/user?levelId={id}`。
- [ ] 商品和用户默认紧凑视图在约 1280px 视口内无横向滚动。
- [ ] 紧凑视图保留全部原操作，使用带 Tooltip 和 `aria-label` 的图标按钮。
- [ ] 可切换“完整字段”视图查看当前全部列。
- [ ] 删除全部原型组件、mock 数据和开发环境免登录代码。
- [ ] 后端相关测试、JDK 17 编译和管理端构建通过。

## API scope

建议新增只读接口：

```text
GET /api/admin/spu/price-view
  ?levelId={levelId}
  &categoryId={categoryId}
  &keyword={keyword}
  &status={status}
  &page={page}
  &limit={limit}
```

返回内容至少包括：

```text
id, name, subtitle, images, categoryId, categoryName
skuCount, salesCount, isRecommend, status
excludeDiscount, discountRate, rateMissing
retailMinPrice, retailMaxPrice
levelMinPrice, levelMaxPrice
```

用户列表扩展：

```text
GET /api/admin/user/list
  ?levelId={levelId}
  &keyword={keyword}
  &province={province}
  &city={city}
  &status={status}
  &page={page}
  &limit={limit}
```

## Definition notes

- `sku.price` 是唯一零售基价。
- 等级结算价使用 `PriceService.resolveUnitPrice`。
- 倍率查询使用 `PricingRuleService`。
- `spu.exclude_discount` 是商品级开关，不是 SKU 级开关。
- 不增加审计日志，遵循设计文档 D13。

## Out of scope

- 审计日志和规则版本。
- SKU 级独立折扣开关。
- 用户端展示等级名称、倍率或划线价。
- 将原型 mock 数据或原型路由加载逻辑合并到主分支。

## Prototype context

- Local branch: `codex/prototype-level-price-view`
- Commit: `7dd484baf82d3a07c0d17af5eef9ead495fe6931`
- Handoff document: `docs/prototype-level-price-view-handoff.md`

Review routes:

```text
/user-level?prototype=1&variant=B
/spu?prototype=price-view&levelId=3&layout=compact
/user?prototype=price-view&levelId=3&layout=compact
```

## Related

- `docs/多级用户定价设计文档.md`
- `docs/商品缓存边界与依赖.md`
- #9 商品详情首屏价格正确
- #10 倍率修改影响确认
