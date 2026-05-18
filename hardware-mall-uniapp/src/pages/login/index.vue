<template>
  <view class="login-container">
    <view class="login-header">
      <image class="logo" src="/static/images/placeholder.svg" mode="aspectFill" />
      <text class="app-name">五金商城</text>
      <text class="app-slogan">品质五金 畅心购</text>
    </view>

    <view class="login-content">
      <button class="login-btn" type="primary" @tap="handleWechatLogin" :loading="loading">
        <text class="btn-icon">📱</text>
        <text class="btn-text">{{ loading ? '登录中...' : '微信一键登录' }}</text>
      </button>

      <view class="login-tip">
        <text class="tip-text">登录即表示同意</text>
        <text class="link">《用户服务协议》</text>
        <text class="tip-text">和</text>
        <text class="link">《隐私政策》</text>
      </view>
    </view>

    <view class="login-footer">
      <text class="footer-text">如有疑问，请联系客服</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { login } from '@/api/user'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)

const handleWechatLogin = async () => {
  if (loading.value) return
  loading.value = true

  try {
    // Step 1: wx.getUserProfile() 直接获取用户信息（必须在点击上下文中立即调用）
    const profileRes = await new Promise<UniApp.GetUserProfileRes>((resolve, reject) => {
      uni.getUserProfile({
        desc: '用于完善用户资料',
        success: resolve,
        fail: reject
      })
    })

    const nickname = profileRes.userInfo?.nickName || profileRes.userInfo?.nickname || ''
    const avatarUrl = profileRes.userInfo?.avatarUrl || ''

    // Step 2: wx.login() 获取 code
    const loginRes = await new Promise<UniApp.LoginRes>((resolve, reject) => {
      uni.login({
        provider: 'weixin',
        success: resolve,
        fail: reject
      })
    })

    const code = loginRes.code
    if (!code) {
      throw new Error('获取登录凭证失败')
    }

    // Step 3: 发送到后端
    const result = await login({
      code,
      nickname,
      avatarUrl
    })

    // Step 4: 保存登录状态
    userStore.setToken(result.token)
    userStore.setUserInfo({
      ...result.userInfo,
      nickname: nickname || result.userInfo.nickname,
      avatarUrl: avatarUrl || result.userInfo.avatarUrl
    })

    uni.showToast({ title: '登录成功', icon: 'success' })

    setTimeout(() => {
      const pages = getCurrentPages()
      const prevPage = pages[pages.length - 2]
      if (prevPage) {
        uni.navigateBack()
      } else {
        uni.switchTab({ url: '/pages/user/index' })
      }
    }, 1500)
  } catch (e: any) {
    console.error('Login failed:', e)
    uni.showToast({ title: e.message || '登录失败，请重试', icon: 'none' })
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.login-container {
  min-height: 100vh;
  background: linear-gradient(180deg, #C9A86C 0%, #E5D4B8 40%, #FAFAFA 100%);
  display: flex;
  flex-direction: column;
  padding: calc(120rpx + env(safe-area-inset-top)) 48rpx 64rpx;
  padding-bottom: calc(64rpx + env(safe-area-inset-bottom));
}

.login-header {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.logo {
  width: 160rpx;
  height: 160rpx;
  border-radius: 32rpx;
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.15);
}

.app-name {
  margin-top: 32rpx;
  font-size: 48rpx;
  font-weight: 700;
  color: #FFFFFF;
  font-family: 'Georgia', serif;
  letter-spacing: 4rpx;
}

.app-slogan {
  margin-top: 8rpx;
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.85);
  letter-spacing: 2rpx;
}

.login-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

.login-btn {
  width: 100%;
  height: 96rpx;
  background: #07C160;
  border-radius: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  border: none;
  box-shadow: 0 8rpx 24rpx rgba(7, 193, 96, 0.3);

  &::after {
    border: none;
  }

  &:active {
    opacity: 0.9;
    transform: scale(0.98);
  }

  .btn-icon {
    font-size: 40rpx;
  }

  .btn-text {
    font-size: 32rpx;
    color: #FFFFFF;
    font-weight: 500;
  }
}

.login-tip {
  margin-top: 32rpx;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 4rpx;
}

.tip-text {
  font-size: 20rpx;
  color: #999999;
}

.link {
  font-size: 20rpx;
  color: #C9A86C;
}

.login-footer {
  padding-bottom: 64rpx;
  display: flex;
  justify-content: center;
}

.footer-text {
  font-size: 24rpx;
  color: #999999;
}
</style>