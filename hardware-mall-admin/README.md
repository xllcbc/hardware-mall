# hardware-mall-admin

商家管理后台（Vue3 + TypeScript + Element Plus + Vite + Pinia）。

## 目录结构

```
src/
├── api/          # 接口封装（复用 utils/request.ts）
├── components/   # 通用组件
├── layouts/      # 布局
├── router/       # 路由
├── stores/       # Pinia 状态
├── types/        # 类型定义
├── utils/        # 请求实例等
└── views/        # 页面
    ├── dashboard/  # 仪表盘
    ├── spu/        # 商品 SPU
    ├── spec/       # 规格模板/规格项
    ├── category/   # 分类
    ├── order/      # 订单（发货 / 退款审核）
    ├── logistics/  # 物流
    ├── user/       # 用户
    └── login/      # 登录
```

## 运行

```bash
npm install
npm run dev      # 开发
npm run build    # 构建（vue-tsc + vite build）
```

## 环境变量

| 变量 | 说明 |
|------|------|
| `VITE_BACKEND_URL` | 开发环境后端地址（开发经 Vite 代理；生产由 Nginx 代理，可留空） |
