# scripts/debug — 人工调试脚本（不参与构建 / CI）

这里的脚本是**手动排查用的**，不会被 `package.json` 的 scripts、GitHub Actions（`test.yml` / `uniapp-experience.yml` / `backend-admin-deploy.yml`）或 `scripts/build-uni.js` 调用。删掉它们不影响任何构建和发布。

## 为什么会有这些脚本（产生原因）

2026-11 用户反馈：小程序分类页部分分类两列商品错乱——左列被撑得特别大、右列显示不全；智能锁具只是左右不等宽；看起来毫无规律。

根因（已用生产备份数据复现并确认）：

1. 分类页两列网格是 `grid-template-columns: repeat(2, 1fr)`；
2. 微信小程序里 `ProductCard` 是自定义组件，**真正作为网格项的是组件宿主节点 `<product-card>`**，它没有 `min-width: 0`（组件内部 `.product-card` 上的 `min-width:0` 管不到宿主节点）；
3. `1fr` 的最小宽度取网格项的内容最小宽度；商品副标题 `.product-subtitle` 设了 `white-space: nowrap`，遇到底部色号清单这类无空格长文案时无法换行，就把所在列撑宽、挤掉另一列；
4. 是否异常取决于该分类商品的副标题长度，所以「看起来没规律」，并且滚动分页加载到长副标题商品时会「突然」异常。

修复：三处商品网格（分类页 / 首页 / 搜索页）改用 `repeat(2, minmax(0, 1fr))`，让列宽无视内容最小宽度，副标题由组件内的省略号截断。

## 脚本

### `parse-prod.mjs`

解析生产备份 SQL（`category` + `spu` 表），按分类输出商品数量、最长名称、最长副标题，并标出副标题偏长、可能撑破两列网格的分类。

```bash
node scripts/debug/parse-prod.mjs <备份.sql>
```

只读，不会连接数据库，也不需要起后端。

### `gen-harness.mjs`

用**当前编译产物**（`dist/build/mp-weixin` 的 WXSS）和商品数据生成 `scripts/debug/harness.html`，按真实 DOM 结构（含 `<product-card>` 宿主节点）渲染每个分类的两列网格，并在页面顶部输出每个分类两列的实测宽度和 PASS/FAIL。

```bash
npm run build:mp-weixin                       # 先编译，确保 harness 反映当前样式
node scripts/debug/gen-harness.mjs            # 用仓库种子数据
node scripts/debug/gen-harness.mjs <备份.sql>  # 用生产备份的真实名称/副标题
# 打开 scripts/debug/harness.html 查看结果
```

判定标准：两列宽度差 ≤ 1px 且无横向溢出 = PASS。每个分类取「副标题最长的商品」作为最坏用例，所以能直接暴露撑破风险。

- `harness.html` 是生成物，已在 `.gitignore` 中忽略，不要提交。
- 这是浏览器近似验证（列宽机制与微信渲染器一致），最终发布前仍建议在微信开发者工具里看一眼真实页面。

## 守护的不变量

- 商品两列网格必须用 `repeat(2, minmax(0, 1fr))`（不能用裸 `1fr`）。
- 商品副标题允许单行省略号截断，不设置成可换行的多行。
