<template>
  <view class="user-container">
    <view class="user-header">
      <view class="header-bg"></view>
      <view class="user-info" @tap="handleUserTap">
        <image
          class="avatar"
          :src="userStore.isLoggedIn ? (userStore.userInfo?.avatarUrl || '/static/images/face.jpg') : '/static/images/face.jpg'"
          mode="aspectFill"
        />
        <view class="info-text">
          <text class="nickname">{{ userStore.isLoggedIn ? (userStore.userInfo?.nickname || '用户') : '点击登录' }}</text>
          <text v-if="userStore.isLoggedIn" class="login-tip">会员</text>
          <text v-else class="login-tip">登录后享受更多服务</text>
        </view>
        <view v-if="userStore.isLoggedIn" class="edit-btn" @tap.stop="goEdit">
          <text class="edit-icon">✏️</text>
          <text class="edit-text">编辑</text>
        </view>
      </view>
    </view>

    <view class="user-orders">
      <view class="section-header">
        <text class="section-title">我的订单</text>
        <view class="section-more" @tap="goPage('/pages/order/list')">
          <text>全部订单</text>
          <text class="arrow">›</text>
        </view>
      </view>
      <view class="order-icons">
        <view class="icon-item" @tap="goPage('/pages/order/list?status=1')">
          <text class="icon-text">⏱</text>
          <text class="icon-label">待付款</text>
        </view>
        <view class="icon-item" @tap="goPage('/pages/order/list?status=2')">
          <text class="icon-text">📦</text>
          <text class="icon-label">待发货</text>
        </view>
        <view class="icon-item" @tap="goPage('/pages/order/list?status=3')">
          <text class="icon-text">🚚</text>
          <text class="icon-label">已发货</text>
        </view>
        <view class="icon-item" @tap="goPage('/pages/order/list?status=4')">
          <text class="icon-text">✓</text>
          <text class="icon-label">已完成</text>
        </view>
      </view>
    </view>

    <view class="user-menu">
      <view class="menu-item" @tap="goPage('/pages/address/list')">
        <text class="menu-icon">📍</text>
        <text class="menu-text">收货地址</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @tap="goPage('/pages/favorites/index')">
        <text class="menu-icon">❤️</text>
        <text class="menu-text">我的收藏</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @tap="goPage('/pages/footprint/index')">
        <text class="menu-icon">👀</text>
        <text class="menu-text">浏览足迹</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @tap="goPage('/pages/search/index')">
        <text class="menu-icon">🔍</text>
        <text class="menu-text">搜索</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <view v-if="userStore.isLoggedIn" class="logout-section">
      <view class="logout-btn" @tap="handleLogout">退出登录</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'

const userStore = useUserStore()
const appStore = useAppStore()

onShow(() => {
  const raw = uni.getStorageSync('LOGIN_RESULT')
  if (raw) {
    try {
      const data = JSON.parse(raw)
      if (data.token) userStore.setToken(data.token)
      if (data.userInfo) userStore.setUserInfo(data.userInfo)
      uni.showToast({ title: '登录成功', icon: 'success' })
      uni.removeStorageSync('LOGIN_RESULT')
    } catch (e) {
      console.error('处理登录结果失败:', e)
    }
  }
})

const handleUserTap = () => {
  if (!userStore.isLoggedIn) {
    uni.navigateTo({ url: '/pages/login/index' })
  }
}

const goEdit = () => {
  uni.navigateTo({ url: '/pages/user/edit' })
}

const goPage = (url: string) => {
  if (!userStore.isLoggedIn) {
    uni.navigateTo({ url: '/pages/login/index' })
    return
  }
  uni.navigateTo({ url })
}

const handleLogout = () => {
  uni.showModal({
    title: '提示',
    content: '确定退出登录？',
    success: (res) => {
      if (res.confirm) {
        userStore.logout()
        uni.showToast({ title: '已退出登录', icon: 'success' })
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.user-container {
  min-height: 100vh;
  background: #FAFAFA;
  padding-bottom: env(safe-area-inset-bottom);
}

.user-header {
  position: relative;
  padding: calc(60rpx + env(safe-area-inset-top)) 32rpx 40rpx;
}

.header-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: calc(240rpx + env(safe-area-inset-top));
  background: linear-gradient(135deg, #C9A86C 0%, #E5D4B8 100%);
  border-radius: 0 0 40rpx 40rpx;
}

.user-info {
  position: relative;
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin-top: 20rpx;
}

.avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 60rpx;
  background: #FFFFFF;
  border: 4rpx solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.1);
}

.info-text {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.nickname {
  font-size: 36rpx;
  font-weight: 600;
  color: #FFFFFF;
}

.login-tip {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.8);
}

.edit-btn {
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  gap: 4rpx;
  padding: 8rpx 16rpx;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 20rpx;

  .edit-icon {
    font-size: 24rpx;
  }

  .edit-text {
    font-size: 24rpx;
    color: #FFFFFF;
  }
}

.user-orders {
  background: #FFFFFF;
  margin: 0 24rpx;
  border-radius: 16rpx;
  padding: 24rpx;
  box-shadow: 0 4rpx 20rpx rgba(201, 168, 108, 0.08);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
}

.section-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #2C2C2C;
}

.section-more {
  display: flex;
  align-items: center;
  font-size: 24rpx;
  color: #999999;

  .arrow {
    font-size: 28rpx;
    margin-left: 4rpx;
  }
}

.order-icons {
  display: flex;
}

.icon-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.icon-text {
  font-size: 48rpx;
}

.icon-label {
  font-size: 24rpx;
  color: #666666;
}

.user-menu {
  background: #FFFFFF;
  margin: 24rpx;
  border-radius: 16rpx;
  overflow: hidden;
  box-shadow: 0 4rpx 20rpx rgba(201, 168, 108, 0.08);
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 32rpx 24rpx;
  border-bottom: 1rpx solid #F0F0F0;

  &:last-child {
    border-bottom: none;
  }
}

.menu-icon {
  font-size: 40rpx;
  margin-right: 16rpx;
}

.menu-text {
  flex: 1;
  font-size: 28rpx;
  color: #2C2C2C;
}

.menu-arrow {
  font-size: 32rpx;
  color: #CCCCCC;
}

.logout-section {
  margin: 24rpx;
  padding-top: 24rpx;
}

.logout-btn {
  height: 88rpx;
  background: #FFFFFF;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  color: #666666;
  box-shadow: 0 4rpx 20rpx rgba(201, 168, 108, 0.08);
}
</style>