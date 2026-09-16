# hardware-mall-uniapp

用户端微信小程序（uni-app + Vue3 + Pinia + uView UI）。

## 目录结构

```
src/
├── api/          # 接口封装
├── components/   # 通用组件
├── composables/  # 组合式函数
├── hooks/        # 业务 hooks
├── pages/        # 页面（首页/分类/购物车/我的/订单/地址/物流/搜索 等）
├── stores/       # Pinia 状态
├── styles/       # 样式
├── types/        # 类型定义
├── utils/        # 工具
├── manifest.json # 小程序配置
└── pages.json    # 页面路由与导航
```

## 运行与构建

```bash
npm install
npm run dev:mp-weixin     # 微信小程序开发
npm run build:mp-weixin   # 微信小程序构建
npm run dev:h5            # H5 预览（可选）
```

也可使用 HBuilderX 打开本项目运行。

## 环境变量

| 变量 | 说明 |
|------|------|
| `VITE_API_BASE_URL` | 后端 API 地址；生产环境必须为 HTTPS，并加入小程序后台 request 合法域名白名单 |

## 主要功能

- 商品：分类、搜索、详情（SPU + SKU 规格选择）、收藏、浏览足迹
- 交易：购物车、收货地址、下单、微信支付、订单列表/详情、取消、申请退款、确认收货
- 物流：同城配送 / 到店自提方式选择
