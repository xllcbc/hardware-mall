<template>
  <div class="layout-container">
    <el-aside class="sidebar" :class="{ 'is-collapsed': isCollapsed }">
      <div class="logo">
        <div class="logo-icon">
          <div class="logo-icon-inner">
            <el-icon><Tools /></el-icon>
          </div>
          <div class="logo-accent"></div>
        </div>
        <div class="logo-text">
          <span class="logo-title">五金商城</span>
          <span class="logo-subtitle">Hardware Mall</span>
        </div>
      </div>
      
      <div class="sidebar-divider"></div>
      
<el-menu 
        :default-active="$route.path" 
        router 
        class="sidebar-menu"
        :collapse="isCollapsed"
        :collapse-transition="false"
      >
        <el-menu-item 
          v-for="(item, index) in menuItems" 
          :key="item.path"
          :index="item.path"
          class="menu-item"
          :class="{ 'is-active': $route.path === item.path }"
          :style="{ animationDelay: `${index * 0.06}s` }"
        >
          <div class="menu-item-bg"></div>
          <el-icon class="menu-icon"><component :is="item.icon" /></el-icon>
          <template #title>
            <span class="menu-text">{{ item.title }}</span>
          </template>
        </el-menu-item>
      </el-menu>
      
      <div class="sidebar-footer">
        <div class="sidebar-footer-text">v1.0.0</div>
      </div>
    </el-aside>

    <el-container class="main-container">
      <el-header class="header">
        <div class="header-left">
          <button class="collapse-btn" @click="toggleCollapse">
            <el-icon v-if="!isCollapsed"><Fold /></el-icon>
            <el-icon v-else><Expand /></el-icon>
          </button>
          <div class="breadcrumb-wrapper">
            <el-breadcrumb separator="/">
              <el-breadcrumb-item :to="{ path: '/dashboard' }">
                <el-icon><HomeFilled /></el-icon>
                首页
              </el-breadcrumb-item>
              <el-breadcrumb-item>{{ currentPageTitle }}</el-breadcrumb-item>
            </el-breadcrumb>
          </div>
        </div>
        
        <div class="header-right">
          <button class="header-btn theme-btn" @click="toggleTheme" :title="theme === 'light' ? '切换深色模式' : '切换浅色模式'">
            <el-icon v-if="theme === 'light'"><Moon /></el-icon>
            <el-icon v-else><Sunny /></el-icon>
          </button>
          
          <el-dropdown trigger="click" @command="handleCommand">
            <button class="user-info">
              <el-avatar :size="36" class="user-avatar">
                <el-icon><UserFilled /></el-icon>
              </el-avatar>
              <div class="user-details">
                <span class="user-name">{{ authStore.userInfo?.username || '管理员' }}</span>
                <span class="user-role">Administrator</span>
              </div>
              <el-icon class="user-arrow"><CaretBottom /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu class="user-dropdown">
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>
                  <span>个人中心</span>
                </el-dropdown-item>
                <el-dropdown-item command="settings">
                  <el-icon><Setting /></el-icon>
                  <span>系统设置</span>
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><Switch /></el-icon>
                  <span>退出登录</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view v-slot="{ Component, route }">
          <transition name="page" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { logout as logoutApi } from '@/api/admin/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const isCollapsed = ref(false)
const theme = ref(localStorage.getItem('theme') || 'light')

const menuItems = [
  { path: '/dashboard', title: '仪表盘', icon: 'DataAnalysis' },
  { path: '/order', title: '订单管理', icon: 'List' },
  { path: '/spu', title: '商品管理', icon: 'Goods' },
  { path: '/spec', title: '规格管理', icon: 'SetUp' },
  { path: '/category', title: '分类管理', icon: 'Grid' },
  { path: '/logistics', title: '物流管理', icon: 'Van' },
  { path: '/user', title: '用户管理', icon: 'User' }
]

const currentPageTitle = computed(() => {
  const item = menuItems.find(m => m.path === route.path)
  return item?.title || '仪表盘'
})

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
}

const toggleTheme = () => {
  theme.value = theme.value === 'light' ? 'dark' : 'light'
  localStorage.setItem('theme', theme.value)
  document.documentElement.setAttribute('data-theme', theme.value)
}

watch(() => route.path, () => {
  document.documentElement.setAttribute('data-theme', theme.value)
}, { immediate: true })

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
</script>

<style scoped>
.layout-container {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--bg-page);
}

/* ─────────────────────────────────────────────────────────
   Sidebar - Industrial Refined
   ───────────────────────────────────────────────────────── */

.sidebar {
  width: var(--sidebar-width);
  background: var(--bg-sidebar);
  display: flex;
  flex-direction: column;
  transition: width var(--transition-slow);
  overflow: hidden;
  position: relative;
}

.sidebar.is-collapsed {
  width: 72px;
}

.logo {
  height: var(--header-height);
  display: flex;
  align-items: center;
  padding: 0 var(--space-lg);
  gap: var(--space-md);
  position: relative;
  transition: padding var(--transition-slow), justify-content var(--transition-slow);
}

.sidebar.is-collapsed .logo {
  padding: 0;
  justify-content: center;
}

.sidebar.is-collapsed .logo-text {
  display: none;
}

.sidebar.is-collapsed .logo-accent {
  display: none;
}

.sidebar::before {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  width: 1px;
  height: 100%;
  background: linear-gradient(
    180deg,
    transparent 0%,
    var(--primary-color) 20%,
    var(--primary-color) 80%,
    transparent 100%
  );
  opacity: 0.3;
}

.logo-icon {
  position: relative;
  flex-shrink: 0;
  transition: margin var(--transition-slow);
}

.logo-icon-inner {
  width: 40px;
  height: 40px;
  background: var(--gradient-primary);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 20px;
  box-shadow: 0 4px 12px var(--primary-glow);
}

.logo-accent {
  position: absolute;
  bottom: -2px;
  right: -2px;
  width: 12px;
  height: 12px;
  background: var(--accent-color);
  border-radius: 50%;
  border: 2px solid var(--bg-sidebar);
}

.logo-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.logo-title {
  font-family: var(--font-display);
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: white;
  letter-spacing: 0.5px;
  line-height: 1.2;
}

.logo-subtitle {
  font-family: var(--font-body);
  font-size: 10px;
  color: var(--text-tertiary);
  letter-spacing: 1px;
  text-transform: uppercase;
}

.sidebar-divider {
  height: 1px;
  margin: 0 var(--space-lg);
  background: linear-gradient(
    90deg,
    transparent,
    rgba(255, 255, 255, 0.1) 20%,
    rgba(255, 255, 255, 0.1) 80%,
    transparent
  );
  transition: margin var(--transition-slow);
}

.sidebar.is-collapsed .sidebar-divider {
  margin: 0 var(--space-md);
}

.sidebar-menu {
  flex: 1;
  border: none;
  background: transparent;
  padding: var(--space-md) 0;
  overflow-y: auto;
}

.menu-item {
  height: 52px;
  margin: 4px 12px;
  border-radius: var(--radius-lg);
  position: relative;
  overflow: hidden;
  opacity: 0;
  animation: fadeInLeft 0.4s cubic-bezier(0.16, 1, 0.3, 1) forwards;
  transition: all var(--transition-base);
  color: rgba(255, 255, 255, 0.9);
}

.menu-item-bg {
  position: absolute;
  inset: 0;
  background: transparent;
  border-radius: var(--radius-lg);
  transition: all var(--transition-base);
}

.menu-item:hover .menu-item-bg {
  background: rgba(255, 255, 255, 0.12);
}

.menu-item.is-active .menu-item-bg {
  background: var(--gradient-primary);
}

.menu-item:hover {
  color: white !important;
}

.menu-item.is-active {
  color: white !important;
  box-shadow: 0 4px 16px var(--primary-glow);
}

.menu-icon {
  position: relative;
  z-index: 1;
  font-size: 18px;
  margin-right: var(--space-md);
  transition: transform var(--transition-spring);
}

.menu-item:hover .menu-icon {
  transform: scale(1.1);
}

.menu-item.is-active .menu-icon {
  animation: pulseScale 2s ease-in-out infinite;
}

.menu-text {
  position: relative;
  z-index: 1;
  font-size: var(--font-size-sm);
  font-weight: 500;
  letter-spacing: 0.3px;
  color: inherit;
  white-space: nowrap;
}

.sidebar-footer {
  padding: var(--space-md) var(--space-lg);
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  transition: padding var(--transition-slow);
}

.sidebar-footer-text {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.5);
  text-align: center;
  letter-spacing: 1px;
}

.sidebar.is-collapsed .sidebar-footer {
  padding: var(--space-md);
}

.sidebar.is-collapsed .sidebar-footer-text {
  display: none;
}

.sidebar-menu {
  flex: 1;
  border: none;
  background: transparent;
  padding: var(--space-md) 0;
  overflow-y: auto;
}

.sidebar-menu:not(.el-menu--collapse) {
  width: 240px;
}

.menu-item {
  height: 52px;
  margin: 4px 12px;
  border-radius: var(--radius-lg);
  position: relative;
  overflow: hidden;
  opacity: 0;
  animation: fadeInLeft 0.4s cubic-bezier(0.16, 1, 0.3, 1) forwards;
  transition: all var(--transition-base);
  color: rgba(255, 255, 255, 0.85);
}

.menu-item-bg {
  position: absolute;
  inset: 0;
  background: transparent;
  border-radius: var(--radius-lg);
  transition: all var(--transition-base);
}

.menu-item:hover .menu-item-bg {
  background: rgba(255, 255, 255, 0.1);
}

.menu-item.is-active .menu-item-bg {
  background: var(--gradient-primary);
}

.menu-item:hover {
  color: white !important;
}

.menu-item.is-active {
  color: white !important;
  box-shadow: 0 4px 16px var(--primary-glow);
}

.menu-icon {
  position: relative;
  z-index: 1;
  font-size: 18px;
  margin-right: var(--space-md);
  transition: transform var(--transition-spring);
}

.menu-item:hover .menu-icon {
  transform: scale(1.1);
}

.menu-item.is-active .menu-icon {
  animation: pulseScale 2s ease-in-out infinite;
}

.menu-text {
  position: relative;
  z-index: 1;
  font-size: var(--font-size-sm);
  font-weight: 500;
  letter-spacing: 0.3px;
  color: inherit;
}

.el-menu--collapse .menu-item {
  margin: 4px 0;
  padding: 0 !important;
  justify-content: center;
}

.el-menu--collapse .menu-icon {
  margin-right: 0;
}

.el-menu--collapse .menu-text {
  display: none;
}

.sidebar-footer {
  padding: var(--space-md) var(--space-lg);
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.sidebar-footer-text {
  font-size: 10px;
  color: var(--text-tertiary);
  text-align: center;
  letter-spacing: 1px;
}

/* ─────────────────────────────────────────────────────────
   Main Container
   ───────────────────────────────────────────────────────── */

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--bg-page);
}

/* ─────────────────────────────────────────────────────────
   Header
   ───────────────────────────────────────────────────────── */

.header {
  height: var(--header-height);
  background: var(--bg-header);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-xl);
  transition: background-color var(--transition-base), border-color var(--transition-base);
  position: relative;
}

.header::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent,
    var(--border-color) 20%,
    var(--border-color) 80%,
    transparent
  );
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
}

.collapse-btn {
  width: 40px;
  height: 40px;
  border: none;
  background: transparent;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.collapse-btn:hover {
  background: var(--bg-page);
  color: var(--text-primary);
}

.breadcrumb-wrapper {
  display: flex;
  align-items: center;
}

.breadcrumb-wrapper :deep(.el-breadcrumb) {
  display: flex;
  align-items: center;
}

.breadcrumb-wrapper :deep(.el-breadcrumb__item) {
  display: flex;
  align-items: center;
}

.breadcrumb-wrapper :deep(.el-breadcrumb__inner) {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  transition: color var(--transition-fast);
}

.breadcrumb-wrapper :deep(.el-breadcrumb__inner:hover) {
  color: var(--primary-color);
}

.breadcrumb-wrapper :deep(.el-breadcrumb__separator) {
  color: var(--text-tertiary);
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.header-btn {
  width: 40px;
  height: 40px;
  border: none;
  background: transparent;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.header-btn:hover {
  background: var(--bg-page);
  color: var(--primary-color);
}

.theme-btn:hover {
  color: var(--warning);
}

.user-info {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-xs) var(--space-sm);
  border: none;
  background: transparent;
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.user-info:hover {
  background: var(--bg-page);
}

.user-avatar {
  background: var(--gradient-primary);
  color: white;
  border: 2px solid var(--border-color);
  transition: border-color var(--transition-fast);
}

.user-info:hover .user-avatar {
  border-color: var(--primary-color);
}

.user-details {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1px;
}

.user-name {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.2;
}

.user-role {
  font-size: 10px;
  color: var(--text-tertiary);
  letter-spacing: 0.5px;
}

.user-arrow {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-left: var(--space-xs);
}

/* ─────────────────────────────────────────────────────────
   Main Content
   ───────────────────────────────────────────────────────── */

.main-content {
  flex: 1;
  padding: var(--space-xl);
  overflow-y: auto;
  background: var(--bg-page);
}

/* Page Transition - Spring Effect */
.page-enter-active {
  transition: opacity 0.4s cubic-bezier(0.34, 1.56, 0.64, 1), 
              transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.page-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(20px) scale(0.96);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.98);
}

/* Dropdown Menu Styling */
.user-dropdown :deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  font-size: var(--font-size-sm);
}

.user-dropdown :deep(.el-dropdown-menu__item .el-icon) {
  font-size: 16px;
  color: var(--text-secondary);
}

/* ─────────────────────────────────────────────────────────
   Responsive
   ───────────────────────────────────────────────────────── */

@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: var(--z-modal);
    transform: translateX(-100%);
    transition: transform var(--transition-slow);
  }
  
  .sidebar.is-open {
    transform: translateX(0);
  }
  
  .header-left {
    gap: var(--space-sm);
  }
  
  .user-details {
    display: none;
  }
}
</style>
