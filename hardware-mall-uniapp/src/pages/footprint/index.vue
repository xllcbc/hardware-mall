<template>
  <view class="footprint-container">
    <view v-if="!loaded" class="skeleton-divider"></view>
    <view v-if="!loaded" class="skeleton-grid">
      <view class="skeleton-item" v-for="i in 4" :key="i">
        <view class="skeleton-image"></view>
        <view class="skeleton-line"></view>
        <view class="skeleton-line short"></view>
      </view>
    </view>

    <template v-if="loaded">
    <view class="footprint-header">
      <text class="footprint-tip">最近30天的浏览记录</text>
      <text class="manage-btn" @tap="toggleManageMode">
        {{ manageMode ? '退出管理' : '管理' }}
      </text>
    </view>

    <scroll-view v-if="appStore.footprint.length" class="footprint-list" scroll-y>
      <view class="footprint-grid">
        <view
          v-for="item in appStore.footprint"
          :key="item.id"
          class="footprint-item"
        >
          <view v-if="manageMode" class="item-checkbox" @tap="toggleSelect(item.id)">
            <view class="checkbox" :class="{ checked: isSelected(item.id) }">
              <text v-if="isSelected(item.id)" class="check-icon">✓</text>
            </view>
          </view>

          <view class="item-image-wrap" @tap="goProductDetail(item.id)">
            <image class="item-image" :src="item.image || '/static/images/placeholder.svg'" mode="aspectFill" lazy-load />
          </view>

          <view class="item-info">
            <text class="item-name">{{ item.name }}</text>
            <view class="item-meta">
              <text class="item-price">¥{{ formatPrice(item.price) }}</text>
              <text class="view-time">{{ formatTime(item.viewTime) }}</text>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>

    <EmptyState v-else text="暂无浏览足迹" icon="👀">
      <template #action>
        <view class="go-shopping" @tap="goShopping">去逛逛</view>
      </template>
    </EmptyState>

    <view v-if="manageMode && selectedCount > 0" class="action-bar">
      <view class="action-btn favorite-btn" @tap="addToFavorites">
        <text>添加收藏</text>
      </view>
      <view class="action-btn delete-btn" @tap="deleteSelected">
        <text>删除</text>
      </view>
    </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted } from 'vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { useAppStore, type FootprintItem } from '@/stores/app'

const appStore = useAppStore()
const loaded = ref(false)
const manageMode = ref(false)
const selectedIds = ref<number[]>([])

onMounted(() => {
  nextTick(() => {
    loaded.value = true
  })
})

const selectedCount = computed(() => selectedIds.value.length)

const isSelected = (id: number) => selectedIds.value.includes(id)

const toggleManageMode = () => {
  manageMode.value = !manageMode.value
  if (!manageMode.value) {
    selectedIds.value = []
  }
}

const toggleSelect = (id: number) => {
  const index = selectedIds.value.indexOf(id)
  if (index > -1) {
    selectedIds.value.splice(index, 1)
  } else {
    selectedIds.value.push(id)
  }
}

const formatPrice = (price: number) => {
  return price.toFixed(2)
}

const formatTime = (timestamp: number) => {
  const now = Date.now()
  const diff = now - timestamp
  const day = 24 * 60 * 60 * 1000

  if (diff < day) {
    return '今天'
  } else if (diff < 2 * day) {
    return '昨天'
  } else if (diff < 7 * day) {
    return `${Math.floor(diff / day)}天前`
  } else {
    const date = new Date(timestamp)
    return `${date.getMonth() + 1}/${date.getDate()}`
  }
}

const goProductDetail = (id: number) => {
  if (manageMode.value) return
  uni.navigateTo({ url: `/pages/product/detail?id=${id}` })
}

const addToFavorites = () => {
  const selectedItems = appStore.footprint.filter(item => selectedIds.value.includes(item.id))
  selectedItems.forEach(item => {
    appStore.addFavorite({
      id: item.id,
      name: item.name,
      image: item.image,
      minPrice: item.price
    })
  })
  uni.showToast({ title: '已添加收藏', icon: 'success' })
  selectedIds.value = []
  manageMode.value = false
}

const deleteSelected = () => {
  if (selectedIds.value.length === 0) return
  uni.showModal({
    title: '提示',
    content: `确定删除选中的 ${selectedIds.value.length} 条浏览记录?`,
    success: (res) => {
      if (res.confirm) {
        selectedIds.value.forEach(id => {
          appStore.removeFootprint(id)
        })
        selectedIds.value = []
        uni.showToast({ title: '已删除', icon: 'success' })
      }
    }
  })
}

const goShopping = () => {
  uni.switchTab({ url: '/pages/index/index' })
}
</script>

<style lang="scss" scoped>
.footprint-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #F5F5F5;
  position: relative;
}

.skeleton-divider {
  height: 80rpx;
}

.skeleton-grid {
  display: flex;
  flex-wrap: wrap;
  padding: 20rpx;
}

.skeleton-item {
  width: calc((100% - 20rpx) / 2);
  margin-right: 20rpx;
  margin-bottom: 20rpx;
  background: #FFFFFF;
  border-radius: 16rpx;
  overflow: hidden;
}

.skeleton-item:nth-child(2n) {
  margin-right: 0;
}

.skeleton-image {
  width: 100%;
  height: 0;
  padding-bottom: 100%;
  background: #EEEEEE;
}

.skeleton-line {
  height: 28rpx;
  margin: 16rpx;
  background: #EEEEEE;
  border-radius: 4rpx;
}

.skeleton-line.short {
  width: 60%;
}

.footprint-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 24rpx;
  background: #FFFFFF;
  border-bottom: 1rpx solid #EEEEEE;
}

.footprint-tip {
  font-size: 26rpx;
  color: #999999;
}

.manage-btn {
  font-size: 28rpx;
  color: #C9A86C;
  font-weight: 500;
}

.footprint-list {
  flex: 1;
  padding: 20rpx;
  padding-bottom: calc(20rpx + 140rpx);
}

.footprint-grid {
  display: flex;
  flex-wrap: wrap;
}

.footprint-item {
  position: relative;
  background: #FFFFFF;
  border-radius: 16rpx;
  overflow: hidden;
  box-shadow: 0 4rpx 20rpx rgba(201, 168, 108, 0.08);
  width: calc((100% - 20rpx) / 2);
  margin-right: 20rpx;
  margin-bottom: 20rpx;
}

.footprint-item:nth-child(2n) {
  margin-right: 0;
}

.item-checkbox {
  position: absolute;
  top: 16rpx;
  left: 16rpx;
  z-index: 10;
  padding: 8rpx;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 50%;
}

.checkbox {
  width: 40rpx;
  height: 40rpx;
  border: 2rpx solid #CCCCCC;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #FFFFFF;

  &.checked {
    background: #C9A86C;
    border-color: #C9A86C;
  }
}

.check-icon {
  color: #FFFFFF;
  font-size: 22rpx;
  font-weight: bold;
}

.item-image-wrap {
  width: 100%;
  height: 0;
  padding-bottom: 100%;
  position: relative;
  background: #F8F8F8;
  overflow: hidden;
}

.item-image {
  width: 100%;
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
}

.item-info {
  padding: 16rpx;
}

.item-name {
  font-size: 28rpx;
  color: #2C2C2C;
  font-weight: 500;
  line-height: 1.4;
  height: 78rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.item-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12rpx;
}

.item-price {
  font-size: 28rpx;
  font-weight: 700;
  color: #C9A86C;
}

.view-time {
  font-size: 22rpx;
  color: #999999;
}

.go-shopping {
  margin-top: 32rpx;
  padding: 20rpx 48rpx;
  background: linear-gradient(135deg, #C9A86C 0%, #B8956A 100%);
  color: #FFFFFF;
  border-radius: 40rpx;
  font-size: 28rpx;
  font-weight: 500;
}

.action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  gap: 20rpx;
  padding: 20rpx 24rpx;
  background: #FFFFFF;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.05);
  z-index: 100;
}

.action-btn {
  flex: 1;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 44rpx;
  font-size: 30rpx;
  font-weight: 600;
}

.favorite-btn {
  background: #FFF3E0;
  color: #E65100;
}

.delete-btn {
  background: linear-gradient(135deg, #E53935 0%, #C62828 100%);
  color: #FFFFFF;
}
</style>
