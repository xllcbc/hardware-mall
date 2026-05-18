<template>
  <view class="empty-state">
    <image v-if="image" class="empty-image" :src="image" mode="aspectFill" />
    <view v-else class="empty-icon">
      <text>{{ icon }}</text>
    </view>
    <text class="empty-text">{{ text }}</text>
    <slot name="action" />
  </view>
</template>

<script setup lang="ts">
interface Props {
  image?: string
  icon?: string
  text?: string
}

withDefaults(defineProps<Props>(), {
  icon: '📦',
  text: '暂无数据'
})
</script>

<style lang="scss" scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-xxl) var(--spacing-lg);
}

.empty-image {
  width: 240rpx;
  height: 240rpx;
  opacity: 0.6;
}

.empty-icon {
  font-size: 120rpx;
  line-height: 1;
  opacity: 0.5;
}

.empty-text {
  margin-top: var(--spacing-md);
  font-size: var(--font-size-md);
  color: var(--color-text-placeholder);
}
</style>