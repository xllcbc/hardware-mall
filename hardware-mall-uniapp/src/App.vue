<script setup lang="ts">
import '@/styles/global.scss'
import { onShow } from '@dcloudio/uni-app'
import { confirmReceiveVerify } from '@/api/order'

// 微信确认收货组件回调: 组件通过 wx.navigateBackMiniProgram 返回, 回调参数在 referrerInfo 中
// AppID 固定 wx1183b055aeec94d1; 仅 success 时调后端二次校验(get_order)并推进本地 3→4
onShow((options: any) => {
  const referrer = options?.referrerInfo
  if (!referrer || referrer.appId !== 'wx1183b055aeec94d1') return
  const status = referrer.extraData?.status
  const orderId = uni.getStorageSync('pendingConfirmOrderId')
  if (orderId) {
    uni.removeStorageSync('pendingConfirmOrderId')
  }
  if (status !== 'success' || !orderId) return
  confirmReceiveVerify(Number(orderId))
    .then(() => uni.$emit('wechat-confirm-done', Number(orderId)))
    .catch(() => uni.showToast({ title: '确认收货失败，请稍后重试', icon: 'none' }))
})
</script>

<template>
  <view>
    <slot />
  </view>
</template>
