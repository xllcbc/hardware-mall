<template>
  <view class="edit-container">
    <view class="edit-header">
      <view class="header-title">编辑资料</view>
      <view class="close-btn" @tap="handleClose">
        <text class="close-icon">✕</text>
      </view>
    </view>

    <view class="edit-content">
      <view class="avatar-section">
        <view class="section-label">头像</view>
        <view class="avatar-wrapper">
          <image
            class="avatar-preview"
            :src="formData.avatarUrl || '/static/images/face.jpg'"
            mode="aspectFill"
          />
          <view class="avatar-actions">
            <view class="action-btn" @tap="handleChooseWechatAvatar">
              <text class="action-icon">📱</text>
              <text class="action-text">微信头像</text>
            </view>
            <view class="action-btn" @tap="handleChooseImage">
              <text class="action-icon">🖼️</text>
              <text class="action-text">自定义上传</text>
            </view>
          </view>
        </view>
      </view>

      <view class="nickname-section">
        <view class="section-label">昵称</view>
        <input
          class="nickname-input"
          v-model="formData.nickname"
          placeholder="请输入昵称"
          maxlength="20"
        />
      </view>
    </view>

    <view class="save-section">
      <view class="save-btn" :class="{ disabled: saving }" @tap="handleSave">
        <text class="save-text">{{ saving ? '保存中...' : '保存' }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { updateUserInfo } from '@/api/user'

const userStore = useUserStore()
const saving = ref(false)

const formData = ref({
  nickname: '',
  avatarUrl: ''
})

onMounted(() => {
  if (userStore.userInfo) {
    formData.value.nickname = userStore.userInfo.nickname || ''
    formData.value.avatarUrl = userStore.userInfo.avatarUrl || ''
  }
})

const handleClose = () => {
  uni.navigateBack()
}

const handleChooseWechatAvatar = async () => {
  try {
    uni.showLoading({ title: '获取中...' })
    const profileRes = await new Promise<UniApp.GetUserProfileRes>((resolve, reject) => {
      uni.getUserProfile({
        desc: '用于设置头像',
        success: resolve,
        fail: reject
      })
    })
    
    formData.value.avatarUrl = profileRes.userInfo?.avatarUrl || ''
    uni.hideLoading()
    uni.showToast({ title: '已获取微信头像', icon: 'success' })
  } catch (e: any) {
    uni.hideLoading()
    console.error('获取微信头像失败:', e)
    uni.showToast({ title: '获取失败，请重试', icon: 'none' })
  }
}

const handleChooseImage = async () => {
  try {
    const res = await new Promise<UniApp.ChooseImageRes>((resolve, reject) => {
      uni.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        sourceType: ['album'],
        success: resolve,
        fail: reject
      })
    })
    
    if (res.tempFilePaths && res.tempFilePaths.length > 0) {
      uni.showLoading({ title: '上传中...' })
      
      const token = uni.getStorageSync('token')
      
      uni.uploadFile({
        url: 'http://192.168.42.188:8080/api/admin/upload/avatar',
        filePath: res.tempFilePaths[0],
        name: 'file',
        header: {
          'Authorization': token ? `Bearer ${token}` : ''
        },
        success: (uploadRes) => {
          const data = JSON.parse(uploadRes.data)
          if (data.code === 200 && data.data) {
            formData.value.avatarUrl = data.data
            uni.showToast({ title: '上传成功', icon: 'success' })
          } else {
            uni.showToast({ title: data.message || '上传失败', icon: 'none' })
          }
        },
        fail: () => {
          uni.showToast({ title: '上传失败', icon: 'none' })
        },
        complete: () => {
          uni.hideLoading()
        }
      })
    }
  } catch (e: any) {
    console.error('选择图片失败:', e)
  }
}

const handleSave = async () => {
  if (saving.value) return
  saving.value = true
  
  try {
    const result = await updateUserInfo({
      nickname: formData.value.nickname,
      avatarUrl: formData.value.avatarUrl
    })
    
    userStore.setUserInfo({
      ...userStore.userInfo,
      nickname: formData.value.nickname,
      avatarUrl: formData.value.avatarUrl
    } as any)
    
    uni.showToast({ title: '保存成功', icon: 'success' })
    
    setTimeout(() => {
      uni.navigateBack()
    }, 1500)
  } catch (e: any) {
    console.error('保存失败:', e)
    uni.showToast({ title: e.message || '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
.edit-container {
  min-height: 100vh;
  background: #FAFAFA;
  display: flex;
  flex-direction: column;
}

.edit-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: calc(60rpx + env(safe-area-inset-top)) 32rpx 24rpx;
  background: linear-gradient(135deg, #C9A86C 0%, #E5D4B8 100%);

  .header-title {
    font-size: 36rpx;
    font-weight: 600;
    color: #FFFFFF;
  }

  .close-btn {
    width: 56rpx;
    height: 56rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(255, 255, 255, 0.3);
    border-radius: 50%;

    .close-icon {
      font-size: 28rpx;
      color: #FFFFFF;
    }
  }
}

.edit-content {
  flex: 1;
  padding: 24rpx;
}

.avatar-section {
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;

  .section-label {
    font-size: 28rpx;
    font-weight: 500;
    color: #2C2C2C;
    margin-bottom: 16rpx;
  }

  .avatar-wrapper {
    display: flex;
    align-items: center;
    gap: 24rpx;

    .avatar-preview {
      width: 120rpx;
      height: 120rpx;
      border-radius: 60rpx;
      background: #F5F5F5;
      flex-shrink: 0;
    }

    .avatar-actions {
      flex: 1;
      display: flex;
      gap: 16rpx;

      .action-btn {
        flex: 1;
        height: 72rpx;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 8rpx;
        background: #F5F5F5;
        border-radius: 8rpx;

        .action-icon {
          font-size: 32rpx;
        }

        .action-text {
          font-size: 24rpx;
          color: #666666;
        }
      }
    }
  }
}

.nickname-section {
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 24rpx;

  .section-label {
    font-size: 28rpx;
    font-weight: 500;
    color: #2C2C2C;
    margin-bottom: 16rpx;
  }

  .nickname-input {
    height: 88rpx;
    padding: 0 24rpx;
    background: #F5F5F5;
    border-radius: 8rpx;
    font-size: 28rpx;
    color: #2C2C2C;

    &::placeholder {
      color: #999999;
    }
  }
}

.save-section {
  padding: 24rpx;
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
}

.save-btn {
  height: 96rpx;
  background: linear-gradient(135deg, #C9A86C 0%, #D4B88A 100%);
  border-radius: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 24rpx rgba(201, 168, 108, 0.3);

  &.disabled {
    opacity: 0.6;
  }

  .save-text {
    font-size: 32rpx;
    font-weight: 500;
    color: #FFFFFF;
  }
}
</style>
