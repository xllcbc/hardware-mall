import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue')
  },
  {
    path: '/',
    component: () => import('@/layouts/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '仪表盘' }
      },
      {
        path: 'order',
        name: 'Order',
        component: () => import('@/views/order/index.vue'),
        meta: { title: '订单管理' }
      },
      {
        path: 'spu',
        name: 'Spu',
        component: () => import('@/views/spu/index.vue'),
        meta: { title: '商品管理' }
      },
      {
        path: 'spec',
        name: 'Spec',
        component: () => import('@/views/spec/index.vue'),
        meta: { title: '规格模板管理' }
      },
      {
        path: 'category',
        name: 'Category',
        component: () => import('@/views/category/index.vue'),
        meta: { title: '分类管理' }
      },
      {
        path: 'logistics',
        name: 'Logistics',
        component: () => import('@/views/logistics/index.vue'),
        meta: { title: '物流管理' }
      },
      {
        path: 'user',
        name: 'User',
        component: () => import('@/views/user/index.vue'),
        meta: { title: '用户管理' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

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

import { setupAuthStorageSync } from '@/stores/auth'
setupAuthStorageSync(router)

export default router
