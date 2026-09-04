<template>
  <view class="login-container">
    <view class="login-header">
      <image class="logo" src="/static/images/logo.jpg" mode="aspectFill" />
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

      <view class="agreement-check" @tap="agreed = !agreed">
        <view class="check-box" :class="{ checked: agreed }">
          <text v-if="agreed" class="check-icon">✓</text>
        </view>
        <view class="check-text-wrap">
          <text class="tip-text">已阅读并同意</text>
          <text class="link" @tap.stop="goTerms">《用户服务协议》</text>
          <text class="tip-text">和</text>
          <text class="link" @tap.stop="goPrivacy">《隐私政策》</text>
        </view>
      </view>
    </view>

    <view class="login-footer">
      <text class="footer-text">如有疑问，请联系客服</text>
    </view>
    <PrivacyPopup @agree="handlePrivacyAgree" />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { login, bindPhone, updateUserInfo } from '@/api/user'
import { useUserStore } from '@/stores/user'
import { uploadAvatar } from '@/utils/upload'
import PrivacyPopup from '@/components/common/PrivacyPopup.vue'
import { checkPrivacyGate, showPrivacy } from '@/composables/usePrivacyGate'

const loginLoading = ref(false)
const avatarUrl = ref('')
const nickname = ref('')
const userStore = useUserStore()

// 协议主动勾选: 隐私弹窗同意过(本地有记录)则默认勾上, 否则必须勾选才能登录
const agreed = ref(!!uni.getStorageSync('PRIVACY_AGREED'))

onMounted(() => {
  checkPrivacyGate()
})

// 隐私弹窗同意后自动同步勾选框
const handlePrivacyAgree = () => {
  agreed.value = true
}

const canLogin = computed(() => {
  return avatarUrl.value && nickname.value.trim().length > 0 && agreed.value
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
    const errMsg = e.detail.errMsg || ''
    if (errMsg.includes('cancel')) {
      return
    }
    if (errMsg.includes('privacy')) {
      // 隐私授权未通过: 弹出隐私弹窗引导用户同意后重试
      uni.showToast({ title: '请先同意隐私政策后再登录', icon: 'none' })
      showPrivacy.value = true
      return
    }
    uni.showToast({ title: '获取手机号失败', icon: 'none' })
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

    if (avatarUrl.value) {
      try {
        avatarUrl.value = await uploadAvatar(avatarUrl.value)
      } catch (err) {
        console.error('头像上传失败:', err)
        uni.showToast({ title: '头像上传失败，已使用默认头像', icon: 'none' })
        avatarUrl.value = ''
      }
    }

    const payload: { nickname: string; avatarUrl?: string } = { nickname: nickname.value }
    if (avatarUrl.value) payload.avatarUrl = avatarUrl.value
    await updateUserInfo(payload)

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

const goTerms = () => {
  uni.navigateTo({ url: '/pages/agreement/terms' })
}

const goPrivacy = () => {
  uni.navigateTo({ url: '/pages/agreement/privacy' })
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

.agreement-check {
  margin-top: 32rpx;
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
  max-width: 100%;
}

.check-box {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 2rpx solid #C0C0C0;
  background: #FFFFFF;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;

  &.checked {
    background: #C9A86C;
    border-color: #C9A86C;
  }

  .check-icon {
    font-size: 22rpx;
    color: #FFFFFF;
    line-height: 1;
  }
}

.check-text-wrap {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
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