# Phase 2: 工程化清理 + admin Pinia + uniapp 体验 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 清理"装而不用"的代码（admin Pinia 空目录 / uniapp uview-plus 不集成 / dist 误入库 / ESLint script 失效），落地 admin `useAuthStore`（替代裸 localStorage、接通 refresh token、修多标签登出不同步），并补齐 uniapp 体验断点（BASE_URL env 化、loading/骨架、列表下拉刷新、搜索分页）。

**Architecture:** 不动业务 API。前端工程化层面：admin 新增 `stores/auth.ts` ~40 行 + 5 处文件改读 store；uniapp `.env` 引入 + `utils/request.ts` 读取 `import.meta.env`；删死代码 + 补 4 处下拉刷新 + 1 处搜索 scrolltolower + 列表页 loading 骨架。所有改动可独立构建通过。

**Tech Stack:** Vue3 + Pinia + Element Plus + Vite + axios + uni-app

**前置约束：** Phase 1 已 merge。在 `phase2-engineering-pinia` 分支执行。

---

## 文件结构（Phase 2 涉及）

**admin：**
- Create: `hardware-mall-admin/src/stores/auth.ts`
- Modify: `hardware-mall-admin/src/utils/request.ts` — 改读 store + refresh 流程
- Modify: `hardware-mall-admin/src/router/index.ts` — 守卫改读 store + storage 事件
- Modify: `hardware-mall-admin/src/views/login/index.vue` — 调 `authStore.setAuth`
- Modify: `hardware-mall-admin/src/layouts/index.vue` — 用户名读 store + 登出调后端
- Modify: `hardware-mall-admin/.gitignore` — 加 `dist/`
- Modify: `hardware-mall-admin/package.json` — 删 lint script
- Modify: `hardware-mall-admin/vite.config.ts` — env 注入 + manualChunks

**uniapp：**
- Modify: `hardware-mall-uniapp/src/utils/request.ts` — BASE_URL env 化
- Modify: `hardware-mall-uniapp/vite.config.ts` — env 支持
- Modify: `hardware-mall-uniapp/package.json` — 删 `uview-plus`/`@vueuse/core`
- Modify: `hardware-mall-uniapp/src/pages/product/detail.vue` — 加 loading 骨架
- Modify: `hardware-mall-uniapp/src/pages/order/detail.vue` — 加 loading 骨架
- Modify: `hardware-mall-uniapp/src/pages/address/edit.vue` — 加 loading 骨架
- Modify: `hardware-mall-uniapp/src/pages.json` — enablePullDownRefresh
- Modify: `hardware-mall-uniapp/src/pages/cart/index.vue` — onPullDownRefresh
- Modify: `hardware-mall-uniapp/src/pages/category/index.vue` — onPullDownRefresh
- Modify: `hardware-mall-uniapp/src/pages/product/list.vue` — onPullDownRefresh + sort 透传
- Modify: `hardware-mall-uniapp/src/pages/search/index.vue` — scrolltolower 分页
- Delete: `hardware-mall-uniapp/src/utils/index.ts` — 未用补丁
- Delete: `hardware-mall-uniapp/stores/user.ts` 中 `loginResult/consumeLoginResult`

**后端（仅删除死代码）：**
- Delete: `hardware-mall-backend/src/main/java/com/example/mystore/controller/user/ProductController.java`
- Modify: `.env.example` — 删 RabbitMQ 死配置

---

### Task 1: 准备分支

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git checkout main && git pull && git checkout -b phase2-engineering-pinia
```

---

### Task 2: 创建 useAuthStore

**Files:** Create `hardware-mall-admin/src/stores/auth.ts`

```typescript
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { refresh as refreshApi } from '@/api/admin/auth'

export interface AdminUserInfo {
  id: number; username: string; role: number
}

const TOKEN_KEY = 'token'
const USER_INFO_KEY = 'userInfo'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const userInfo = ref<AdminUserInfo | null>(
    (() => {
      const raw = localStorage.getItem(USER_INFO_KEY)
      if (!raw) return null
      try { return JSON.parse(raw) as AdminUserInfo } catch { return null }
    })()
  )

  const isLoggedIn = computed(() => !!token.value)

  function setAuth(newToken: string, info: AdminUserInfo) {
    token.value = newToken; userInfo.value = info
    localStorage.setItem(TOKEN_KEY, newToken)
    localStorage.setItem(USER_INFO_KEY, JSON.stringify(info))
  }

  function clearAuth() {
    token.value = null; userInfo.value = null
    localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(USER_INFO_KEY)
  }

  async function refreshToken(): Promise<string | null> {
    if (!token.value) return null
    try {
      const newToken = await refreshApi()
      token.value = newToken; localStorage.setItem(TOKEN_KEY, newToken)
      return newToken
    } catch { clearAuth(); return null }
  }

  return { token, userInfo, isLoggedIn, setAuth, clearAuth, refreshToken }
})

/** 跨标签登出同步: 监听 storage 事件, token 被清空则同步本标签 */
export function setupAuthStorageSync(router: { push: (path: string) => void }) {
  window.addEventListener('storage', (e) => {
    if (e.key === TOKEN_KEY && !e.newValue) {
      const store = useAuthStore()
      if (store.isLoggedIn) { store.clearAuth(); router.push('/login') }
    }
  })
}
```

---

### Task 3: 改造 axios 拦截器接入 store + refresh 流程

**Files:** Modify `hardware-mall-admin/src/utils/request.ts`

- 请求拦截器改读 `useAuthStore().token`
- 响应拦截器 401 时先 `refreshToken()`，成功则重放原请求
- 加 `refreshing` 互斥锁防并发 401 触发多次 refresh

---

### Task 4: 改造路由守卫 + storage sync

**Files:** `hardware-mall-admin/src/router/index.ts`

- `beforeEach` 改读 `useAuthStore().isLoggedIn`
- 在 export router 之前调 `setupAuthStorageSync(router)`

---

### Task 5: 改造登录页 + 布局登出

**Files:** `hardware-mall-admin/src/views/login/index.vue`, `layouts/index.vue`

- login：`authStore.setAuth(res.token, res.userInfo)`
- layouts：登出调后端 `POST /admin/logout` + `authStore.clearAuth()`
- 顶栏用户名改读 `authStore.userInfo?.username`

---

### Task 6: admin 工程清理（dist + lint script）

- `.gitignore` 加 `dist/`
- `git rm --cached -r dist/`
- `package.json` 删 `"lint": "eslint . --fix"`
- vite.config.ts 加 `manualChunks: { 'element-plus': ['element-plus', '@element-plus/icons-vue'] }`

---

### Task 7: uniapp BASE_URL env 化 + 删未用依赖

- `request.ts`：`export const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'`
- `package.json` 删 `uview-plus` / `@vueuse/core`
- 新建 `.env.example` 含 `VITE_API_BASE_URL`

---

### Task 8: uniapp 删死代码

- `git rm src/utils/index.ts`
- 删 `stores/user.ts` 中 `loginResult/consumeLoginResult`
- 删根目录 stale `pages.json`
- 后端 `git rm .../ProductController.java`
- `.env.example` 删 RabbitMQ 配置段

---

### Task 9: uniapp 加 loading 骨架

`product/detail.vue` / `order/detail.vue` / `address/edit.vue` 三个页面 `<template>` 最外层包：
```vue
<view v-if="loading" class="loading-wrap"><LoadingState text="加载中..." /></view>
<view v-else class="xxx-container">...</view>
```

---

### Task 10: uniapp 列表页下拉刷新

pages.json 对 `cart/index`、`category/index`、`product/list` 追加 `"enablePullDownRefresh": true`。
各页面 script 中 `import { onPullDownRefresh } from '@dcloudio/uni-app'` + handler。

---

### Task 11: uniapp 搜索分页 + product-list 排序透传

- `search/index.vue`：`scroll-view` 加 `@scrolltolower`、page/limit 状态
- `product/list.vue`：`currentSort` 透传到 `getProductList` 参数

---

### Task 12: Phase 2 全量验收

- `mvn clean test -q`（后端）
- `npm run build`（admin）
- `npm run build:mp-weixin`（uniapp）
- 手工冒烟：admin 多标签登出同步、refresh token 流程、uniapp 下拉刷新与搜索分页
