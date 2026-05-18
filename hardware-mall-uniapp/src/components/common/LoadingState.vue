<template>
  <view class="loading-state">
    <view class="loading-spinner">
      <view v-for="i in 12" :key="i" class="spinner-item" :style="{ animationDelay: `${(i - 1) * 0.1}s` }" />
    </view>
    <text v-if="text" class="loading-text">{{ text }}</text>
  </view>
</template>

<script setup lang="ts">
interface Props {
  text?: string
}

withDefaults(defineProps<Props>(), {
  text: '加载中...'
})
</script>

<style lang="scss" scoped>
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-xxl);
}

.loading-spinner {
  width: 64rpx;
  height: 64rpx;
  position: relative;
}

.spinner-item {
  position: absolute;
  width: 12rpx;
  height: 12rpx;
  background: var(--color-primary);
  border-radius: 50%;
  top: 0;
  left: 50%;
  margin-left: -6rpx;
  transform-origin: 6rpx 32rpx;
  animation: spinner 1.2s linear infinite;
}

@keyframes spinner {
  0% {
    opacity: 1;
    transform: scale(1) rotate(0deg);
  }
  100% {
    opacity: 0.3;
    transform: scale(0.5) rotate(360deg);
  }
}

.loading-text {
  margin-top: var(--spacing-md);
  font-size: var(--font-size-sm);
  color: var(--color-text-placeholder);
}
</style>