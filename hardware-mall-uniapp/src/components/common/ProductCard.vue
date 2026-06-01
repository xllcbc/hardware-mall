<template>
  <view class="product-card" @tap="onClick">
    <view class="image-wrapper">
      <image class="product-image" :src="image || '/static/images/placeholder.svg'" mode="aspectFill" lazy-load />
    </view>
    <view class="product-info">
      <text class="product-name">{{ name }}</text>
      <text v-if="subtitle" class="product-subtitle">{{ subtitle }}</text>
      <view class="product-bottom">
        <view class="price-wrap">
          <text class="price-symbol">¥</text>
          <text class="price-value">{{ displayPrice }}</text>
        </view>
        <text v-if="salesCount" class="sales-count">已售{{ salesCount }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  id?: number | string
  image?: string
  name: string
  subtitle?: string
  price?: number | string
  originalPrice?: number | string
  minPrice?: number | string
  maxPrice?: number | string
  salesCount?: number | string
}

const props = defineProps<Props>()
const emit = defineEmits<{
  click: [id: number | string]
}>()

const formatPrice = (price: number | string | undefined) => {
  if (!price) return '0.00'
  const num = typeof price === 'string' ? parseFloat(price) : price
  return isNaN(num) ? '0.00' : num.toFixed(2)
}

const displayPrice = computed(() => {
  if (props.minPrice != null && props.minPrice !== props.originalPrice) {
    return formatPrice(props.minPrice) + '起'
  }
  return formatPrice(props.price ?? props.originalPrice)
})

const onClick = () => {
  const id = props.id
  if (id == null || Number(id) <= 0) return
  emit('click', props.id!)
}
</script>

<style lang="scss" scoped>
.product-card {
  background: #FFFFFF;
  border-radius: 16rpx;
  box-shadow: 0 4rpx 20rpx rgba(201, 168, 108, 0.08);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  width: 100%;
  box-sizing: border-box;
  min-width: 0;
}

.image-wrapper {
  width: 100%;
  height: 0;
  padding-bottom: 100%;
  position: relative;
  background: #F5F5F5;
  overflow: hidden;
}

.product-image {
  width: 100%;
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
}

.product-info {
  padding: 16rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  min-width: 0;
  overflow: hidden;
  box-sizing: border-box;
}

.product-name {
  font-size: 28rpx;
  color: #2C2C2C;
  font-weight: 500;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  word-break: break-all;
}

.product-subtitle {
  font-size: 24rpx;
  color: #999999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  word-break: break-all;
}

.product-bottom {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-top: 8rpx;
  min-width: 0;
  overflow: hidden;
}

.price-wrap {
  display: flex;
  align-items: baseline;
  min-width: 0;
  overflow: hidden;
}

.price-symbol {
  font-size: 24rpx;
  color: #C9A86C;
  font-weight: 600;
  flex-shrink: 0;
}

.price-value {
  font-size: 28rpx;
  color: #C9A86C;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

.sales-count {
  font-size: 20rpx;
  color: #999999;
  flex-shrink: 0;
}
</style>