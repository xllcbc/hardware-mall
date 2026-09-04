<template>
  <view v-if="modelValue" class="privacy-mask" catchtouchmove="return">
    <view class="privacy-card">
      <text class="privacy-title">隐私保护提示</text>
      <scroll-view class="privacy-body" scroll-y>
        <text class="privacy-text">欢迎使用五金商城！我们非常重视您的个人信息与隐私保护。</text>
        <text class="privacy-text">为了向您提供服务，我们会在征得您明示同意后，收集以下必要信息：</text>
        <text class="privacy-text">1. 微信昵称与头像，用于展示和完善个人资料；</text>
        <text class="privacy-text">2. 手机号，用于账号登录注册及订单配送联系；</text>
        <text class="privacy-text">3. 收货地址，用于商品配送发货；</text>
        <text class="privacy-text">4. 订单信息，用于处理发货、退款及售后；</text>
        <text class="privacy-text">5. 选中的相册照片，仅用于您自定义头像上传。</text>
        <text class="privacy-text">您可以阅读
          <text class="link" @tap.stop="goPrivacy">《隐私政策》</text>
          和
          <text class="link" @tap.stop="goTerms">《用户服务协议》</text>
          全文了解详情。
        </text>
        <text class="privacy-text">若您同意以上内容，请点击"同意"开始使用。</text>
      </scroll-view>
      <view class="privacy-actions">
        <view class="privacy-btn secondary" @tap="onDisagree">不同意</view>
        <view class="privacy-btn primary" @tap="onAgree">同意</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'agree'): void
}>()

const onAgree = () => {
  emit('agree')
  emit('update:modelValue', false)
}

const onDisagree = () => {
  uni.showModal({
    title: '提示',
    content: '未同意隐私政策将无法使用本小程序，确定拒绝?',
    confirmText: '仍不同意',
    cancelText: '返回同意',
    success: (res) => {
      if (res.confirm) {
        uni.showToast({ title: '您已拒绝隐私政策，部分功能将不可用', icon: 'none' })
      }
    }
  })
}

const goPrivacy = () => {
  uni.navigateTo({ url: '/pages/agreement/privacy' })
}

const goTerms = () => {
  uni.navigateTo({ url: '/pages/agreement/terms' })
}
</script>

<style lang="scss" scoped>
.privacy-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.55);
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 64rpx;
}

.privacy-card {
  width: 100%;
  max-width: 600rpx;
  background: #FFFFFF;
  border-radius: 24rpx;
  padding: 40rpx 32rpx 32rpx;
  display: flex;
  flex-direction: column;
  box-shadow: 0 16rpx 48rpx rgba(0, 0, 0, 0.2);
}

.privacy-title {
  font-size: 36rpx;
  font-weight: 700;
  color: #333333;
  text-align: center;
  margin-bottom: 24rpx;
}

.privacy-body {
  max-height: 520rpx;
  margin-bottom: 32rpx;
}

.privacy-text {
  display: block;
  font-size: 26rpx;
  line-height: 1.7;
  color: #666666;
  margin-bottom: 12rpx;
}

.link {
  color: #C9A86C;
}

.privacy-actions {
  display: flex;
  gap: 24rpx;
}

.privacy-btn {
  flex: 1;
  height: 80rpx;
  border-radius: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;

  &.primary {
    background: linear-gradient(135deg, #C9A86C 0%, #B8956A 100%);
    color: #FFFFFF;
    font-weight: 500;
  }

  &.secondary {
    background: #FFFFFF;
    color: #999999;
    border: 1rpx solid #E0E0E0;
  }

  &:active {
    opacity: 0.9;
  }
}
</style>
