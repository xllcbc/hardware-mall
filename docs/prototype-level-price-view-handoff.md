# 用户分级价格视图原型交接

状态：产品交互已验证，可进入正式实现

日期：2026-10-29

原型分支：`codex/prototype-level-price-view`

Implementation issue：[#11](https://github.com/xllcbc/hardware-mall/issues/11)

## 原型要回答的问题

如何在不新增独立页面的前提下，让管理员：

1. 从用户分级直接查看该等级下的商品价格。
2. 在商品管理中自由切换零售价和任一级价格视角。
3. 查看该等级的归属用户。
4. 在常见桌面宽度下，不再依赖左右滚动完成日常浏览和操作。

## 已验证的产品结论

### 页面边界

- 商品价格视图必须复用现有“商品管理”页面，不新建独立商品页。
- 归属用户必须复用现有“用户管理”页面，不新建独立客户页。
- 用户分级页只保留跳转入口。

### 商品管理

- 搜索区增加“价格视角”选择器，默认零售用户。
- 商品列表同时提供零售价和当前等级结算价。
- 点击“归属用户”进入带 `levelId` 的用户管理筛选。
- `excludeDiscount = 1` 是商品级开关，适用于固定控价、特价清仓等商品。
- `excludeDiscount = 1` 表示所有等级都使用 SKU 零售价。
- 分类倍率缺失是另一种状态，应显示“未配置，零售兜底”，不能显示“不参与折扣”。
- 规格配置以最新 `main` 的两步式为基线：
  1. 选择规格维度与规格值。
  2. 生成规格组合并勾选保留。
  3. 支持批量设置价格和库存。
  4. 未保留组合在保存时软删除。
- 旧版“一键笛卡尔积生成 SKU”不得进入正式实现。

### 用户管理

- 复用现有用户表格、分页、订单、地区、状态和所属群体操作。
- 增加“用户等级”筛选，并从 URL 的 `levelId` 初始化。
- 用户管理是归属用户的唯一正式入口。

### 横向滚动

- 默认使用紧凑视图。
- 低频字段合并进主信息：
  - 商品：ID、SKU 数量、销量、推荐合并到商品信息和状态列。
  - 用户：ID、企业、角色、注册时间合并到用户信息和手机号单元格。
- 操作列使用带 Tooltip 的图标按钮，完整保留原操作。
- 提供“完整字段”切换，允许管理员恢复原始宽表。
- 在约 1280px 视口中，商品和用户紧凑表格均满足 `scrollWidth === clientWidth`。

### 明确不做

- 不增加审计日志。现有设计文档 D13 明确不记录规则版本或操作历史。
- 不为 SKU 单独设置折扣开关，`exclude_discount` 只存在于 SPU。
- 不保留纯展示用 mock 名单、mock 商品或假数据落到正式包。
- 不把原型查询参数和开发环境免登录逻辑带入主分支。

## 正式实现建议

### 基线

必须从最新 `main` 开始，不要直接基于当前原型分支的旧商品页工作。最新 `main` 已有选择规格值、生成组合、勾选保留和批量编辑的新版 SKU 配置。

原型分支只用于：

- 查看最终交互和视觉密度。
- 比较紧凑视图与完整字段。
- 复用样式思路和字段合并方式。

不要直接复制原型中的内存数据、条件加载组件或开发环境路由旁路。

### 商品价格读取

建议新增独立管理端只读接口：

```text
GET /api/admin/spu/price-view
  ?levelId={levelId}
  &categoryId={categoryId}
  &keyword={keyword}
  &status={status}
  &page={page}
  &limit={limit}
```

返回字段至少包含：

```text
id, name, subtitle, images, categoryId, categoryName
skuCount, salesCount, isRecommend, status
excludeDiscount, discountRate, rateMissing
retailMinPrice, retailMaxPrice
levelMinPrice, levelMaxPrice
```

规则：

- 不传 `levelId` 时按零售视角返回。
- 倍率读取复用 `PricingRuleService`。
- 价格计算复用纯函数 `PriceService.resolveUnitPrice`。
- `rateMissing` 必须由后端明确返回，不能把缺失倍率和显式 1.0000 混为一谈。
- 保持现有 `/api/admin/spu/list` CRUD 契约不变。

### 用户等级筛选

现有 `/api/admin/user/list` 增加可选项：

```text
levelId
keyword
```

- `levelId` 过滤 `user.level_id`。
- `keyword` 可匹配昵称、手机号。
- 用户分级页跳转到：

```text
/user?levelId={levelId}
```

### 前端正式改造

- 在最新 `main` 的商品页内增加价格视角，不创建新的顶层页面。
- 紧凑/完整字段状态可以保存在用户偏好或 URL 中，默认使用紧凑视图。
- 图标操作必须保留 Tooltip、键盘可聚焦和 `aria-label`。
- 价格视角、分类、关键词和分页变化要拆成并行请求，避免旧请求覆盖新结果。
- 用户页从路由读取 `levelId`，并把筛选值写回 URL，便于交接和定位。

## 原型入口

开发模式：

```text
/user-level?prototype=1&variant=A
/user-level?prototype=1&variant=B
/user-level?prototype=1&variant=C
/spu?prototype=price-view&levelId=3&layout=compact
/spu?prototype=price-view&levelId=3&layout=full
/user?prototype=price-view&levelId=3&layout=compact
/user?prototype=price-view&levelId=3&layout=full
```

推荐评审入口：

```text
/user-level?prototype=1&variant=B
/spu?prototype=price-view&levelId=3&layout=compact
/user?prototype=price-view&levelId=3&layout=compact
```

## 正式实现清理项

实施完成后删除：

- `hardware-mall-admin/src/views/user-level/__prototype/`
- `hardware-mall-admin/src/views/spu/__prototype/`
- `hardware-mall-admin/src/views/user/__prototype/`
- 商品页、用户页和用户分级页中的原型条件加载。
- 路由中仅开发环境有效的原型免登录判断。
- 原型专用的 `components.d.ts` 自动生成变更，除非正式页面仍使用对应组件。

## 主 Session 交接提示

```text
Implementation issue: https://github.com/xllcbc/hardware-mall/issues/11
先读取 docs/prototype-level-price-view-handoff.md。
正式实现必须从最新 main 开始，不要复制原型 mock 数据或原型路由旁路。
商品页以最新 main 的两步式 SKU 配置为基线，只增加价格视角、后端价格字段和紧凑列布局。
用户页增加 levelId 筛选并复用现有表格与操作。
完成后删除全部 __prototype 文件和开发环境入口。
```

## 相关背景

- `docs/多级用户定价设计文档.md`
- `docs/商品缓存边界与依赖.md`
- GitHub Issue #10：倍率修改前的二次确认
- GitHub Issue #9：小程序商品详情首屏价格
