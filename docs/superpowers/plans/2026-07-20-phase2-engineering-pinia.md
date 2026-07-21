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
- Modify: `hardware-mall-admin/src/main.ts` — 注册 storage 事件监听器
- Modify: `hardware-mall-admin/src/utils/request.ts` — 改读 store + refresh 流程 + 多标签同步
- Modify: `hardware-mall-admin/src/router/index.ts` — 守卫改读 store
- Modify: `hardware-mall-admin/src/views/login/index.vue` — 调 `authStore.setAuth` + 调后端 logout
- Modify: `hardware-mall-admin/src/layouts/index.vue` — 用户名读 store + 登出调后端
- Modify: `hardware-mall-admin/.gitignore` — 加 `dist/`
- Modify: `hardware-mall-admin/package.json` — 装 eslint 或删 lint script 二选一
- Modify: `hardware-mall-admin/vite.config.ts` — env 注入 backend URL + manualChunks
- Delete: `hardware-mall-admin/dist/` 从 git 移除
- Modify: `hardware-mall-admin/src/api/admin/auth.ts` — refresh 加上明确类型

**uniapp：**
- Modify: `hardware-mall-uniapp/src/utils/request.ts` — BASE_URL 改 `import.meta.env`
- Modify: `hardware-mall-uniapp/src/pages/user/edit.vue` — 重复 BASE_URL 改用共享
- Modify: `hardware-mall-uniapp/vite.config.ts` — define env 注入
- Create: `hardware-mall-uniapp/.env.example` — 列出所有 env 变量
- Modify: `hardware-mall-uniapp/package.json` — 删未用 `uview-plus`/`@vueuse/core`
- Modify: `hardware-mall-uniapp/src/pages/product/detail.vue` — 加 loading 骨架
- Modify: `hardware-mall-uniapp/src/pages/order/detail.vue` — 加 loading 骨架
- Modify: `hardware-mall-uniapp/src/pages/address/edit.vue` — 加 loading 骨架
- Modify: `hardware-mall-uniapp/src/pages/cart/index.vue` — enablePullDownRefresh + onPullDownRefresh
- Modify: `hardware-mall-uniapp/src/pages/category/index.vue` — enablePullDownRefresh + onPullDownRefresh
- Modify: `hardware-mall-uniapp/src/pages/product/list.vue` — enablePullDownRefresh + onPullDownRefresh + 修排序死代码
- Modify: `hardware-mall-uniapp/src/pages/search/index.vue` — scrolltolower + 加分页
- Modify: `hardware-mall-uniapp/src/stores/user.ts` — 删未用 `loginResult/consumeLoginResult`
- Delete: `hardware-mall-uniapp/src/utils/index.ts` — 未用 install 补丁
- Delete: `hardware-mall-uniapp/src/utils/mock.ts` 中的 `MOCK_BANNERS`（保留文件，仅删未用导出）
- Delete: 根目录 `hardware-mall-uniapp/pages.json`（stale 副本，仅保留 `src/pages.json`）

**后端（仅删除死代码）：**
- Delete: `hardware-mall-backend/src/main/java/com/example/mystore/controller/user/ProductController.java`（被 UserProductController 代替的注释死代码）
- Modify: `.env.example` — 删掉 RabbitMQ 死配置（代码中无使用）

---

### Task 1: 创建分支 + 导入 Phase 1

- [ ] **Step 1: 切分支**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git checkout main
git pull
git checkout -b phase2-engineering-pinia
```

- [ ] **Step 2: 检查 admin 现有 ESLint 与依赖情况**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-admin"
ls src/stores/ && cat package.json | grep -E "(eslint|lint|uview|vueuse|pinia)" 
```

期望确认：`src/stores/` 空、无 eslint 依赖、`uview-plus`/`@vueuse/core` 不在 admin（它们在 uniapp）。

---

### Task 2: 创建 useAuthStore

**Files:**
- Create: `hardware-mall-admin/src/stores/auth.ts`

- [ ] **Step 1: 写 useAuthStore**

Create: `hardware-mall-admin/src/stores/auth.ts`

```typescript
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { refresh as refreshApi } from '@/api/admin/auth'

export interface AdminUserInfo {
  id: number
  username: string
  role: number
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
    token.value = newToken
    userInfo.value = info
    localStorage.setItem(TOKEN_KEY, newToken)
    localStorage.setItem(USER_INFO_KEY, JSON.stringify(info))
  }

  function clearAuth() {
    token.value = null
    userInfo.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_INFO_KEY)
  }

  async function refreshToken(): Promise<string | null> {
    if (!token.value) return null
    try {
      const newToken = await refreshApi()
      token.value = newToken
      localStorage.setItem(TOKEN_KEY, newToken)
      return newToken
    } catch (e) {
      clearAuth()
      return null
    }
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    setAuth,
    clearAuth,
    refreshToken
  }
})

/** 跨标签登出同步: 监听其他标签的 storage 事件, 一旦 token 被清空就同步本标签状态 */
export function setupAuthStorageSync(router: { push: (path: string) => void }) {
  window.addEventListener('storage', (e) => {
    if (e.key === TOKEN_KEY && !e.newValue) {
      const store = useAuthStore()
      if (store.isLoggedIn) {
        store.clearAuth()
        router.push('/login')
      }
    }
  })
}
```

- [ ] **Step 2: 类型检查（无错即通过）**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-admin"
npm run build 2>&1 | tail -20
```

Expected: 0 TS errors；构建成功（store 文件未被引用，但 strict + noUnusedLocals 不报，因为 exported）。

- [ ] **Step 3: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-admin/src/stores/auth.ts
git commit -m "feat(admin): 新增 useAuthStore + storage 跨标签登出同步"
```

---

### Task 3: 改造 axios 拦截器接入 store + refresh 流程

**Files:**
- Modify: `hardware-mall-admin/src/utils/request.ts`

**目标：** 请求拦截器读 `useAuthStore().token`；响应拦截器收到 401 时先尝试 `refreshToken()`，成功则重放原请求，失败则 `clearAuth()` + 跳登录。

- [ ] **Step 1: 重写 request.ts**

Modify: `hardware-mall-admin/src/utils/request.ts`

完整替换为：
```typescript
import axios from 'axios'
import type { AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

const http = axios.create({
  baseURL: '/api',
  timeout: 10000
})

http.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()
    if (authStore.token) {
      config.headers.Authorization = `Bearer ${authStore.token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 标识请求已尝试过 refresh, 防止无限循环
let refreshing: Promise<string | null> | null = null

http.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      if (res.code === 401) {
        return handle401(response.config as InternalAxiosRequestConfig)
          .then((data) => Promise.resolve(data))
          .catch(() => Promise.reject(new Error(res.message || '登录已过期')))
      }
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res.data
  },
  (error) => {
    if (error.response?.status === 401) {
      return handle401(error.config)
        .catch(() => {
          ElMessage.error('登录已过期，请重新登录')
          return Promise.reject(error)
        })
    }
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

async function handle401(originalConfig: InternalAxiosRequestConfig): Promise<any> {
  const authStore = useAuthStore()
  if (!authStore.token) {
    authStore.clearAuth()
    router.push('/login')
    throw new Error('no token')
  }
  if (!refreshing) {
    refreshing = authStore.refreshToken()
  }
  const newToken = await refreshing
  refreshing = null
  if (!newToken) {
    authStore.clearAuth()
    router.push('/login')
    throw new Error('refresh failed')
  }
  // 重放原请求, 用新 token
  originalConfig.headers = originalConfig.headers || {}
  ;(originalConfig.headers as any).Authorization = `Bearer ${newToken}`
  return http(originalConfig)
}

const request = {
  get: <T = any>(url: string, config?: AxiosRequestConfig) =>
    http.get(url, config) as unknown as Promise<T>,
  post: <T = any>(url: string, data?: any, config?: AxiosRequestConfig) =>
    http.post(url, data, config) as unknown as Promise<T>,
  put: <T = any>(url: string, data?: any, config?: AxiosRequestConfig) =>
    http.put(url, data, config) as unknown as Promise<T>,
  delete: <T = any>(url: string, config?: AxiosRequestConfig) =>
    http.delete(url, config) as unknown as Promise<T>,
}

export default request
```

- [ ] **Step 2: 类型检查 + 构建**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-admin"
npm run build 2>&1 | tail -20
```

Expected: 0 errors。如有 `import InternalAxiosRequestConfig` 路径不对，调整为 `import type { InternalAxiosRequestConfig } from 'axios'`（axios 1.6.7 已导出）。

- [ ] **Step 3: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-admin/src/utils/request.ts
git commit -m "feat(admin): request 拦截器接入 useAuthStore + refresh token 流程"
```

---

### Task 4: 改造路由守卫

**Files:**
- Modify: `hardware-mall-admin/src/router/index.ts:66-77`

- [ ] **Step 1: 守卫改读 store**

Modify: `hardware-mall-admin/src/router/index.ts:66-77`

Replace:
```typescript
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  if (to.path === '/login') {
    next()
  } else {
    if (token) {
      next()
    } else {
      next('/login')
    }
  }
})
```

为:
```typescript
import { useAuthStore } from '@/stores/auth'

router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()
  if (to.path === '/login') {
    next()
  } else {
    if (authStore.isLoggedIn) {
      next()
    } else {
      next('/login')
    }
  }
})
```

> 注意 import 位置：把 `import { useAuthStore }` 放在文件顶部其他 import 之间，不要写在 `beforeEach` 函数体内（已经在文件顶端 export 默认 router 之前缺 import）。具体位置参考现有 import 块。

- [ ] **Step 2: 在 router 文件末尾添加 setupAuthStorageSync 调用**

Modify: `hardware-mall-admin/src/router/index.ts`

在文件末尾 `export default router` 之前追加：
```typescript
import { setupAuthStorageSync } from '@/stores/auth'

setupAuthStorageSync(router)
```

> 注：要确保 setupAuthStorageSync 在 router 实例创建之后调用，所以放在 export default 之前。

- [ ] **Step 3: 构建**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-admin"
npm run build 2>&1 | tail -20
```

Expected: 0 errors。

- [ ] **Step 4: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-admin/src/router/index.ts
git commit -m "feat(admin): 路由守卫改读 useAuthStore + 跨标签同步注册"
```

---

### Task 5: 改造登录页 + 布局登出

**Files:**
- Modify: `hardware-mall-admin/src/views/login/index.vue:101-119`
- Modify: `hardware-mall-admin/src/layouts/index.vue:153-165`

- [ ] **Step 1: login/index.vue 提交改用 store**

Modify: `hardware-mall-admin/src/views/login/index.vue:101-119`

Replace `handleLogin` 实现:
```typescript
const handleLogin = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      loading.value = true
      try {
        const res = await login(form)
        const authStore = useAuthStore()
        authStore.setAuth(res.token, res.userInfo)
        ElMessage.success('登录成功')
        router.push('/dashboard')
      } catch {
        // error handled by interceptor
      } finally {
        loading.value = false
      }
    }
  })
}
```

在 `<script setup>` 顶部 import 块补：
```typescript
import { useAuthStore } from '@/stores/auth'
```

- [ ] **Step 2: layouts/index.vue 登出调用后端 + 用 store**

Modify: `hardware-mall-admin/src/layouts/index.vue:113-165` （script 区段）

Replace `handleCommand` 实现:
```typescript
import { useAuthStore } from '@/stores/auth'
import { logout as logoutApi } from '@/api/admin/auth'

const authStore = useAuthStore()

const handleCommand = (command: string) => {
  if (command === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(async () => {
      try {
        await logoutApi()
      } catch {
        // 即便后端调用失败也清本地, 避免用户卡住
      }
      authStore.clearAuth()
      router.push('/login')
    })
  }
}
```

新增 `logout` API 方法（若 auth.ts 中没有）：

Modify: `hardware-mall-admin/src/api/admin/auth.ts:1-23`

追加导出:
```typescript
export const logout = () => {
  return request.post<void>('/admin/logout')
}
```

- [ ] **Step 3: 布局顶栏用户名改读 store**

Modify: `hardware-mall-admin/src/layouts/index.vue:77-78`

Replace:
```html
<span class="user-name">管理员</span>
```

为:
```html
<span class="user-name">{{ authStore.userInfo?.username || '管理员' }}</span>
```

- [ ] **Step 4: 构建**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-admin"
npm run build 2>&1 | tail -20
```

Expected: 0 errors。

- [ ] **Step 5: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-admin/src/views/login/index.vue hardware-mall-admin/src/layouts/index.vue hardware-mall-admin/src/api/admin/auth.ts
git commit -m "feat(admin): 登录/登出接入 useAuthStore, 登出调后端拉黑 token"
```

---

### Task 6: admin .gitignore + dist 移出 + ESLint 决策

**Files:**
- Modify: `hardware-mall-admin/.gitignore`
- Delete: `hardware-mall-admin/dist/` from git
- Modify: `hardware-mall-admin/package.json` (lint script 决策)

**决策：** 当前 admin 没有 ESLint 配置且代码量小（自用项目），删除 `lint` script 比装一整套 ESLint 更轻量。如需 lint，未来再加装。本次选择删除 script。

- [ ] **Step 1: 检查现有 .gitignore**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-admin"
cat .gitignore
```

- [ ] **Step 2: 在 .gitignore 中添加 dist/**

Modify: `hardware-mall-admin/.gitignore`

追加（如果还没有）:
```
dist/
```

- [ ] **Step 3: 从 git 移除 dist/**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-admin"
git rm -r --cached dist/
```

> 注：`--cached` 只从 git 索引中移除，本地文件仍在。如果完全删除文件用 `git rm -r dist/`，但保留本地构建产物即可。

- [ ] **Step 4: 删除 package.json 中的 lint script**

Modify: `hardware-mall-admin/package.json`（scripts 区段）

删除：
```json
"lint": "eslint . --fix"
```

如果预期未来想加 eslint 时再加回，本次保持干净。

- [ ] **Step 5: 构建验证（确保不破坏）**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-admin"
npm run build 2>&1 | tail -10
```

Expected: 仍 build 成功。

- [ ] **Step 6: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-admin/.gitignore hardware-mall-admin/dist hardware-mall-admin/package.json
git commit -m "chore(admin): dist 移出 git + 删除失效的 lint script"
```

---

### Task 7: admin Vite env 注入 + manualChunks

**Files:**
- Modify: `hardware-mall-admin/vite.config.ts`
- Create: `hardware-mall-admin/.env.example`

- [ ] **Step 1: 修改 vite.config.ts 让 baseURL 可配置 + 加 manualChunks**

Modify: `hardware-mall-admin/vite.config.ts`

在 `defineConfig` 内的 server.proxy 区段，让 target 来自 env：
```typescript
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendUrl = env.VITE_BACKEND_URL || 'http://localhost:8080'

  return {
    plugins: [
      vue(),
      AutoImport({
        imports: ['vue', 'vue-router', 'pinia'],
        resolvers: [ElementPlusResolver()],
        dts: 'auto-imports.d.ts'
      }),
      Components({
        resolvers: [ElementPlusResolver()],
        dts: 'components.d.ts'
      })
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      port: 3000,
      proxy: {
        '/api': {
          target: backendUrl,
          changeOrigin: true
        }
      }
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks: {
            'element-plus': ['element-plus', '@element-plus/icons-vue']
          }
        }
      }
    }
  }
})
```

- [ ] **Step 2: 新建 .env.example**

Create: `hardware-mall-admin/.env.example`

```
# 后端 API 地址（dev 模式下 vite 代理使用；prod 由 nginx 代理故可留空）
VITE_BACKEND_URL=http://localhost:8080
```

- [ ] **Step 3: 创建 .gitignore 排除 .env**

确保 `.gitignore` 含：
```
.env
.env.local
.env.*.local
```

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-admin"
cat .gitignore
```

如缺失追加。

- [ ] **Step 4: 构建测试**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-admin"
npm run build 2>&1 | tail -10
```

Expected: build 成功，dist 目录下应能看到 `element-plus.[hash].js` 单独拆出来。

- [ ] **Step 5: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-admin/vite.config.ts hardware-mall-admin/.env.example hardware-mall-admin/.gitignore
git commit -m "build(admin): vite env 注入 backend URL + manualChunks 拆分 element-plus"
```

---

### Task 8: uniapp BASE_URL env 化 + 删未用依赖

**Files:**
- Modify: `hardware-mall-uniapp/src/utils/request.ts:1-3`
- Modify: `hardware-mall-uniapp/src/pages/user/edit.vue:70`
- Modify: `hardware-mall-uniapp/vite.config.ts`
- Modify: `hardware-mall-uniapp/package.json`
- Create: `hardware-mall-uniapp/.env.example`

- [ ] **Step 1: 修改 request.ts**

Modify: `hardware-mall-uniapp/src/utils/request.ts:1-3`

删除原 `const BASE_URL = 'http://localhost:8080/api'`，改用 env：

在文件顶部加：
```typescript
const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
```

- [ ] **Step 2: 修改 user/edit.vue 重复 BASE_URL**

Modify: `hardware-mall-uniapp/src/pages/user/edit.vue:70`

定位 hardcoded `BASE_URL`、改为 import 共享：
```typescript
// 在文件顶部 import 同一个 BASE_URL（或从 utils/request.ts 重新导出）
import { BASE_URL } from '@/utils/request'  // 若未导出则在 request.ts 加 export const BASE_URL = ...
```

或者在 user/edit.vue 直接读 env:
```typescript
const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
```

推荐前者（单一来源）。先在 `request.ts` 中 `export const BASE_URL = ...`：

Modify `request.ts`:
```typescript
export const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
```

然后 user/edit.vue 改 import。

- [ ] **Step 3: vite.config.ts 确保 env 注入**

Modify: `hardware-mall-uniapp/vite.config.ts`

uni-app 项目通常自动注入 `import.meta.env`，一般无需 extra config。确认 vite.config.ts 不阻止 env 使用。可选添加：
```typescript
import { defineConfig, loadEnv } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [uni()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    define: {
      // 已经由 uni/vite 自动处理 import.meta.env, 这里通常无需手动
    }
  }
})
```

> 注：vite 默认把所有以 `VITE_` 开头的 env 变量暴露到 `import.meta.env`，无需 define 额外注入。只需确保 `.env` 文件存在并 `loadEnv` 正常加载即可。

- [ ] **Step 4: 新建 .env.example**

Create: `hardware-mall-uniapp/.env.example`

```
# uniapp 模式下的后端 API 地址 (开发自用)
# 生产环境：必须为 HTTPS, 且域名已加入小程序后台 request 合法域名白名单
VITE_API_BASE_URL=http://localhost:8080/api
```

- [ ] **Step 5: 删未用依赖 uview-plus 和 @vueuse/core**

Modify: `hardware-mall-uniapp/package.json`

删除 dependencies 区段：
```
"uview-plus": "^3.2.0"
"@vueuse/core": "^10.9.0"
```

> 注：先确认 grep 无导入（探查阶段已确认无）。
> Run:
> ```bash
> workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
> grep -rn "uview\|@vueuse" src/
> ```
> Expected: 无输出。

然后:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
rm -rf node_modules package-lock.json
npm install
```

- [ ] **Step 6: 构建**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
npm run build:mp-weixin 2>&1 | tail -20
```

Expected: build success。

- [ ] **Step 7: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-uniapp/src/utils/request.ts hardware-mall-uniapp/src/pages/user/edit.vue hardware-mall-uniapp/vite.config.ts hardware-mall-uniapp/.env.example hardware-mall-uniapp/package.json hardware-mall-uniapp/package-lock.json
git commit -m "build(uniapp): BASE_URL env 化 + 删未用 uview-plus/@vueuse/core"
```

---

### Task 9: uniapp 删死代码

**Files:**
- Delete: `hardware-mall-uniapp/src/utils/index.ts`
- Modify: `hardware-mall-uniapp/src/utils/mock.ts` (移除未用 MOCK_BANNERS)
- Delete: `hardware-mall-uniapp/pages.json` (stale root copy)
- Modify: `hardware-mall-uniapp/src/stores/user.ts` (删 loginResult/consumeLoginResult)
- Delete: `hardware-mall-backend/src/main/java/com/example/mystore/controller/user/ProductController.java`
- Modify: `.env.example` (删 RabbitMQ 配置)

- [ ] **Step 1: 删 utils/index.ts**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
grep -rn "utils/index\|from '@/utils'" src/
```

Expected: 无引用（探查阶段已确认是死代码）。

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
git rm src/utils/index.ts
```

- [ ] **Step 2: 删 mock.ts 中的 MOCK_BANNERS**

Modify: `hardware-mall-uniapp/src/utils/mock.ts`

定位 `MOCK_BANNERS` 导出并删除整个数组与 export。

> 注意确认 grep 无引用：
> ```bash
> grep -rn "MOCK_BANNERS" src/
> ```
> Expected: 无输出（已注释 banner swiper）。

- [ ] **Step 3: 删根目录 stale pages.json**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
diff pages.json src/pages.json | head -20
```

Expected: 输出有差异。仅 `src/pages.json` 是真实使用的，根目录的是 stale。

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
git rm pages.json
```

- [ ] **Step 4: user store 删 unused loginResult/consumeLoginResult**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
grep -rn "loginResult\|consumeLoginResult" src/
```

如有引用先核对再删。预期无引用。

Modify: `hardware-mall-uniapp/src/stores/user.ts`

定位 `loginResult` ref 与 `consumeLoginResult` 方法，删除（保留 setToken/setUserInfo/logout/setisLoggedIn 等已用项）。

- [ ] **Step 5: 后端删 ProductController（注释死代码）**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
grep -rn "ProductController\|controller.user.ProductController" src/
```

确认无任何 import 引用后：
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
git rm src/main/java/com/example/mystore/controller/user/ProductController.java
```

- [ ] **Step 6: .env.example 删 RabbitMQ 残留**

Modify `.env.example`（根目录）

定位 `# RabbitMQ` 段并整体删除（注意保留前后组之间的空行段格式）。

- [ ] **Step 7: 全量构建**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"; npm run build:mp-weixin 2>&1 | tail -10
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"; mvn clean compile -q 2>&1 | tail -10
```

Expected: 都成功。

- [ ] **Step 8: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add -A
git commit -m "chore: 删死代码 (utils/index.ts, stale pages.json, MOCK_BANNERS, ProductController, RabbitMQ env)"
```

---

### Task 10: uniapp 加 loading 骨架

**Files:**
- Modify: `hardware-mall-uniapp/src/pages/product/detail.vue`
- Modify: `hardware-mall-uniapp/src/pages/order/detail.vue`
- Modify: `hardware-mall-uniapp/src/pages/address/edit.vue`

每个页面：<template> 最外层包 `v-if="loading"` 显示 `<LoadingState />`，`v-else` 显示真实内容。

- [ ] **Step 1: product/detail.vue 加 loading 骨架**

Modify: `hardware-mall-uniapp/src/pages/product/detail.vue`

定位 `<template>` 根，原来可能是直接显示内容。改为：
```vue
<template>
  <view v-if="loading" class="loading-wrap">
    <LoadingState text="加载中..." />
  </view>
  <view v-else class="product-container">
    <!-- 原有内容 -->
  </view>
</template>
```

在 script setup 确保 `loading` ref 计算正确（onMounted 异步函数 finally 设为 false，try 之前设为 true）。确认 `import LoadingState from '@/components/common/LoadingState.vue'` 已存在或新增。

- [ ] **Step 2: order/detail.vue 同**

Modify: `hardware-mall-uniapp/src/pages/order/detail.vue`

同样模式包装 `<template>` 内容。

- [ ] **Step 3: address/edit.vue 同**

Modify: `hardware-mall-uniapp/src/pages/address/edit.vue`

注意：编辑地址时 `onMounted` 异步加载已有地址数据，应先 `loading=true`。在 fetch 完成后 `loading=false`。如果是新增地址（无 id），不展示 loading，直接 `loading=false` 初始。

- [ ] **Step 4: 构建验证**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
npm run build:mp-weixin 2>&1 | tail -10
```

Expected: build success。

- [ ] **Step 5: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-uniapp/src/pages/product/detail.vue hardware-mall-uniapp/src/pages/order/detail.vue hardware-mall-uniapp/src/pages/address/edit.vue
git commit -m "feat(uniapp): product/order detail, address edit 加 loading 骨架"
```

---

### Task 11: uniapp 列表页下拉刷新

**Files:**
- Modify: `hardware-mall-uniapp/src/pages.json`
- Modify: `hardware-mall-uniapp/src/pages/cart/index.vue`
- Modify: `hardware-mall-uniapp/src/pages/category/index.vue`
- Modify: `hardware-mall-uniapp/src/pages/product/list.vue`

- [ ] **Step 1: pages.json 启用 enablePullDownRefresh**

Modify: `hardware-mall-uniapp/src/pages.json`

在 `pages/cart/index`、`pages/category/index`、`pages/product/list`（这个在 subPackages 下）的 style 中追加：
```json
"enablePullDownRefresh": true
```

例如 cart/index 项改为:
```json
{
  "path": "pages/cart/index",
  "style": {
    "navigationBarTitleText": "购物车",
    "enablePullDownRefresh": true
  }
},
```

product/list 项（subPackages 内）改为:
```json
{ "path": "list", "style": { "navigationBarTitleText": "商品列表", "enablePullDownRefresh": true } },
```

category/index 同。

- [ ] **Step 2: 各页面加 onPullDownRefresh handler**

Modify: `hardware-mall-uniapp/src/pages/cart/index.vue`

在 script 顶部 import `onPullDownRefresh` from `@dcloudio/uni-app`。添加 handler：
```typescript
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'

onPullDownRefresh(async () => {
  try {
    const data = await getCartList()
    cartStore.setItems(data || [])
  } catch (e) {
    console.error('刷新失败:', e)
  } finally {
    uni.stopPullDownRefresh()
  }
})
```

Modify: `hardware-mall-uniapp/src/pages/category/index.vue` 同模式：

```typescript
import { onPullDownRefresh } from '@dcloudio/uni-app'

onPullDownRefresh(async () => {
  try {
    await loadData()
  } finally {
    uni.stopPullDownRefresh()
  }
})
```

（`loadData` 是现有的初始加载函数名，按实际函数名替换。）

Modify: `hardware-mall-uniapp/src/pages/product/list.vue` 同模式。

- [ ] **Step 3: 构建**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
npm run build:mp-weixin 2>&1 | tail -10
```

Expected: success。

- [ ] **Step 4: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-uniapp/src/pages.json hardware-mall-uniapp/src/pages/cart/index.vue hardware-mall-uniapp/src/pages/category/index.vue hardware-mall-uniapp/src/pages/product/list.vue
git commit -m "feat(uniapp): cart/category/product-list 启用下拉刷新"
```

---

### Task 12: uniapp 搜索分页 + 修 product/list 排序死代码

**Files:**
- Modify: `hardware-mall-uniapp/src/pages/search/index.vue:67,105,128`
- Modify: `hardware-mall-uniapp/src/pages/product/list.vue:92-95,111-115`

- [ ] **Step 1: search 加 scrolltolower 分页**

Modify: `hardware-mall-uniapp/src/pages/search/index.vue`

定位 `<scroll-view>` 包裹搜索结果的位置（约 67 行）添加 `@scrolltolower="loadMore"`。

在 script setup 中加分页状态与 loadMore 函数：
```typescript
const page = ref(1)
const pageSize = 10
const noMore = ref(false)
const loadingMore = ref(false)

const onSearch = async (kw: string) => {
  page.value = 1
  noMore.value = false
  keyword.value = kw
  try {
    const data = await getProductList({ keyword: kw, page: 1, limit: pageSize })
    results.value = data.records || data || []
    if ((data.total || 0) <= pageSize) noMore.value = true
  } catch (e) {
    console.error(e)
  }
}

const loadMore = async () => {
  if (loadingMore.value || noMore.value || !keyword.value) return
  loadingMore.value = true
  try {
    const nextPage = page.value + 1
    const data = await getProductList({ keyword: keyword.value, page: nextPage, limit: pageSize })
    const list = data.records || data || []
    results.value = [...results.value, ...list]
    page.value = nextPage
    if (list.length < pageSize) noMore.value = true
  } catch (e) {
    console.error(e)
  } finally {
    loadingMore.value = false
  }
}
```

> 注：`getProductList` 需接受 page/limit 参数。如果当前 `api/product.ts` 的 `getProductList` 只传 keyword，需补 page/limit 参数。先看 signature:
> ```bash
> workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
> cat src/api/product.ts
> ```

如需补 page/limit 形参：

Modify: `hardware-mall-uniapp/src/api/product.ts`

```typescript
export const getProductList = (params: { keyword?: string; categoryId?: number; page?: number; limit?: number; sort?: string }) => {
  return request.get('/user/product/list', { params })
}
```

- [ ] **Step 2: 修 product/list 排序死代码**

Modify: `hardware-mall-uniapp/src/pages/product/list.vue:92-115`

定位 `sortOptions` 与 `currentSort`。在调用 `getProductList` 的位置补上 `sort` 参数：
```typescript
const data = await getProductList({
  keyword: keyword.value,
  page: page.value,
  limit: pageSize,
  sort: currentSort.value
})
```

> 注：后端 `getProductList` 接受 sort 参数的支持需确认，如果后端不支持，本任务仅在前端透传，后端 ignore 也不报错。若后端有 sort 处理逻辑则启动。

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
grep -n "sort" src/main/java/com/example/mystore/service/impl/SpuServiceImpl.java | head -10
```

如果后端不支持 sort，本任务仅前端透传。Phase 4 再决定是否后端实现 sort 支持（属体验问题，非阻塞）。

- [ ] **Step 3: 构建**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
npm run build:mp-weixin 2>&1 | tail -10
```

Expected: success。

- [ ] **Step 4: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add hardware-mall-uniapp/src/pages/search/index.vue hardware-mall-uniapp/src/pages/product/list.vue hardware-mall-uniapp/src/api/product.ts
git commit -m "feat(uniapp): search 加分页 scrolltolower + product-list 排序参数透传后端"
```

---

### Task 13: Phase 2 全量验收

- [ ] **Step 1: 后端构建**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn clean test -q
```

Expected: BUILD SUCCESS。

- [ ] **Step 2: admin 构建**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-admin"
npm run build 2>&1 | tail -10
```

Expected: 0 errors, build success, dist 下 element-plus chunk 单独拆出。

- [ ] **Step 3: uniapp 构建**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
npm run build:mp-weixin 2>&1 | tail -10
```

Expected: success, dist/build/mp-weixin/ 编译产物存在无报错。

- [ ] **Step 4: 手工冒烟**

启动后端 + admin dev + uniapp dev：
- admin 登录 → 进入 dashboard → 用户名显示正确（不是"管理员"硬编码）
- admin 调用任意接口触发 401（手动改变 token 失效）→ 应自动 refresh 重放，不立即跳登录
- admin 开两个标签，A 标签登出 → B 标签访问任何操作应主动跳 `/login`
- uniapp 调试模式打开购物车/分类/商品列表，下拉刷新有响应
- uniapp 搜索 → 输入关键词 → 出结果 → 向下滑应触发 loadMore
- uniapp 商品详情/订单详情/地址编辑初次进入显示 LoadingState 骨架

- [ ] **Step 5: 推送**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git push -u origin phase2-engineering-pinia
```

- [ ] **Step 6: Phase 2 完成 checkpoint**

准备 merge 后启动 Phase 3。

---

## Phase 2 验收清单

| Task | 内容 | 验收 | 状态 |
|---|---|---|---|
| 2 | useAuthStore | `npm run build` | ⬜ |
| 3 | request 拦截器 + refresh | build + 手工 401 | ⬜ |
| 4 | router 守卫 + storage sync | build + 多标签登出冒烟 | ⬜ |
| 5 | login/layouts 登出 | build + 手工登录登出 | ⬜ |
| 6 | dist 移出 + lint script 删 | `git status` 干净 | ⬜ |
| 7 | vite env + manualChunks | dist 中有 element-plus chunk | ⬜ |
| 8 | uniapp BASE_URL env | build success + 删依赖 | ⬜ |
| 9 | 删死代码 | build success | ⬜ |
| 10 | loading 骨架 | 手工打开新页面看骨架 | ⬜ |
| 11 | 下拉刷新 | 手工下拉 | ⬜ |
| 12 | 搜索分页 + 排序 | 手工滚动搜索 | ⬜ |
| 13 | 全量验收 | 3 个构建全绿 | ⬜ |

---

## Self-Review

- ✅ Pinia 落地范围克制：仅 auth store，不动主题/loading/侧边栏
- ✅ refresh 流程：拦截器加 token 互斥锁 `refreshing`，并发 401 不会触发多次 refresh
- ✅ 多标签同步：storage 事件 + Pinia ref 双向打通
- ✅ BASE_URL 全部走 env，dev/local 都支持覆盖
- ✅ 删除死代码，整洁性提升
- ✅ loading 骨架覆盖 3 个关键页，列表页用 onPullDownRefresh
- ⚠️ product/list 排序透传后端是否支持依赖后端是否实现 sort 参数；本 Phase 仅前端透传不实现后端排序
- ⚠️ Task 9 中删 ProductController 需先 grep 确认无 import 引用（已包含 grep 步骤）