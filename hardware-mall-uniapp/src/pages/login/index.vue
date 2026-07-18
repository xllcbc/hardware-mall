<template>
  <view class="login-container">
    <view class="login-header">
      <image class="logo" src="/static/images/placeholder.svg" mode="aspectFill" />
      <text class="app-name">五金商城</text>
      <text class="app-slogan">品质五金 畅心购</text>
    </view>

    <view class="login-content">
      <button class="login-btn" open-type="chooseAvatar" @chooseavatar="onChooseAvatar" v-if="!avatarUrl">
        <text class="btn-icon">📷</text>
        <text class="btn-text">选择头像</text>
      </button>

      <view class="avatar-preview" v-if="avatarUrl" @tap="changeAvatar">
        <image class="avatar-img" :src="avatarUrl" mode="aspectFill" />
        <text class="avatar-tip">点击更换头像</text>
      </view>

      <view class="nickname-input-wrap" v-if="avatarUrl">
        <input
          class="nickname-input"
          type="nickname"
          v-model="nickname"
          placeholder="请输入昵称"
          :maxlength="20"
          @blur="onNicknameBlur"
        />
      </view>

      <button
        class="login-btn main-btn"
        open-type="getPhoneNumber"
        @getphonenumber="onGetPhoneNumber"
        v-if="avatarUrl"
        :disabled="!canLogin || loginLoading"
      >
        <text class="btn-icon">📱</text>
        <text class="btn-text">{{ loginLoading ? '登录中...' : '微信一键登录' }}</text>
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
import { ref, computed } from 'vue'
import { login, bindPhone, updateUserInfo } from '@/api/user'
import { useUserStore } from '@/stores/user'

const loginLoading = ref(false)
const avatarUrl = ref('')
const nickname = ref('')
const userStore = useUserStore()

const canLogin = computed(() => {
  return avatarUrl.value && nickname.value.trim().length > 0
})

const onChooseAvatar = (e: any) => {
  avatarUrl.value = e.detail.avatarUrl
}

const changeAvatar = () => {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: (res) => {
      avatarUrl.value = res.tempFilePaths[0]
    }
  })
}

const onNicknameBlur = () => {
  nickname.value = nickname.value.trim()
}

const onGetPhoneNumber = async (e: any) => {
  if (e.detail.errMsg !== 'getPhoneNumber:ok') {
    if (!e.detail.errMsg?.includes('cancel')) {
      uni.showToast({ title: '获取手机号失败', icon: 'none' })
    }
    return
  }

  loginLoading.value = true
  try {
    const loginRes = await new Promise<UniApp.LoginRes>((resolve, reject) => {
      uni.login({ provider: 'weixin', success: resolve, fail: reject })
    })

    const code = loginRes.code
    if (!code) throw new Error('获取登录凭证失败')

    const result = await login({ code })

    uni.setStorageSync('token', result.token)

    await updateUserInfo({
      nickname: nickname.value,
      avatarUrl: avatarUrl.value
    })

    let finalUserInfo = {
      ...result.userInfo,
      nickname: nickname.value,
      avatarUrl: avatarUrl.value
    }
    const phoneCode = e.detail.code
    if (phoneCode) {
      try {
        const updatedUser = await bindPhone(phoneCode)
        finalUserInfo = { ...finalUserInfo, ...updatedUser }
      } catch (err) {
        console.error('绑定手机号失败:', err)
      }
    }

    uni.removeStorageSync('token')

    userStore.setToken(result.token)
    userStore.setUserInfo(finalUserInfo)

    const redirect = uni.getStorageSync('LOGIN_REDIRECT')
    if (redirect) {
      uni.removeStorageSync('LOGIN_REDIRECT')
      const route = redirect.split('?')[0]
      const tabBarPages = ['pages/index/index', 'pages/category/index', 'pages/cart/index', 'pages/user/index']
      if (tabBarPages.includes(route)) {
        uni.switchTab({ url: '/' + route })
      } else {
        uni.reLaunch({ url: '/' + redirect })
      }
    } else {
      uni.setStorageSync('LOGIN_RESULT', JSON.stringify({
        token: result.token,
        userInfo: finalUserInfo
      }))
      navigateBack()
    }
  } catch (err: any) {
    uni.removeStorageSync('token')
    console.error('Login failed:', err)
    uni.showToast({ title: err.message || '登录失败，请重试', icon: 'none' })
    loginLoading.value = false
  }
}

const navigateBack = () => {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
  } else {
    uni.switchTab({ url: '/pages/user/index' })
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

.avatar-preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 32rpx;
}

.avatar-img {
  width: 160rpx;
  height: 160rpx;
  border-radius: 50%;
  border: 4rpx solid #C9A86C;
}

.avatar-tip {
  font-size: 24rpx;
  color: #999999;
  margin-top: 12rpx;
}

.nickname-input-wrap {
  width: 100%;
  margin-bottom: 32rpx;
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 24rpx;
}

.nickname-input {
  font-size: 30rpx;
  color: #333333;
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
  margin-bottom: 24rpx;

  &::after {
    border: none;
  }

  &:active {
    opacity: 0.9;
    transform: scale(0.98);
  }

  &[disabled] {
    opacity: 0.5;
    box-shadow: none;
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

.main-btn {
  background: linear-gradient(135deg, #C9A86C 0%, #B8956A 100%);
  box-shadow: 0 8rpx 24rpx rgba(201, 168, 108, 0.3);
}

.login-tip {
  margin-top: 16rpx;
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