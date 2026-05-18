<template>
  <view class="favorites-container">
    <view class="favorites-header">
      <view class="header-left">
        <text v-if="!manageMode && selectedCount > 0" class="selected-tip">已选择{{ selectedCount }}件</text>
      </view>
      <text class="manage-btn" @tap="toggleManageMode">
        {{ manageMode ? '退出管理' : '管理' }}
      </text>
    </view>

    <scroll-view v-if="appStore.favorites.length" class="favorites-list" scroll-y>
      <view class="favorites-grid">
        <view
          v-for="item in appStore.favorites"
          :key="item.id"
          class="favorite-item"
        >
          <view v-if="manageMode" class="item-checkbox" @tap="toggleSelect(item.id)">
            <view class="checkbox" :class="{ checked: isSelected(item.id) }">
              <text v-if="isSelected(item.id)" class="check-icon">✓</text>
            </view>
          </view>

          <view class="item-image-wrap" @tap="goProductDetail(item.id)">
            <image class="item-image" :src="item.image || '/static/images/placeholder.svg'" mode="aspectFill" />
          </view>

          <view class="item-info">
            <text class="item-name">{{ item.name }}</text>
            <view class="item-bottom">
<text class="item-price">¥{{ formatPrice(item.minPrice) }}起</text>
             </view>
          </view>
        </view>
      </view>
    </scroll-view>

    <EmptyState v-else text="暂无收藏商品" icon="♡">
      <template #action>
        <view class="go-shopping" @tap="goShopping">去逛逛</view>
      </template>
    </EmptyState>

    <view v-if="manageMode && selectedCount > 0" class="action-bar">
      <view class="delete-btn" @tap="deleteSelected">
        <text>删除</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
const manageMode = ref(false)
const selectedIds = ref<number[]>([])

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

const formatPrice = (price: number | undefined) => {
  if (!price) return '0.00'
  return price.toFixed(2)
}

const goProductDetail = (id: number) => {
  if (manageMode.value) return
  uni.navigateTo({ url: `/pages/product/detail?id=${id}` })
}

const deleteSelected = () => {
  if (selectedIds.value.length === 0) return
  uni.showModal({
    title: '提示',
    content: `确定删除选中的 ${selectedIds.value.length} 件商品?`,
    success: (res) => {
      if (res.confirm) {
        selectedIds.value.forEach(id => {
          appStore.removeFavorite(id)
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
.favorites-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #F5F5F5;
  position: relative;
}

.favorites-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 24rpx;
  background: #FFFFFF;
  border-bottom: 1rpx solid #EEEEEE;
}

.header-left {
  flex: 1;
}

.selected-tip {
  font-size: 26rpx;
  color: #666666;
}

.manage-btn {
  font-size: 28rpx;
  color: #C9A86C;
  font-weight: 500;
}

.favorites-list {
  flex: 1;
  padding: 20rpx;
  padding-bottom: calc(20rpx + 140rpx);
}

.favorites-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20rpx;
}

.favorite-item {
  position: relative;
  background: #FFFFFF;
  border-radius: 16rpx;
  overflow: hidden;
  box-shadow: 0 4rpx 20rpx rgba(201, 168, 108, 0.08);
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

.item-bottom {
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
  justify-content: center;
  align-items: center;
  padding: 20rpx 24rpx;
  background: #FFFFFF;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.05);
  z-index: 100;
}

.delete-btn {
  flex: 1;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #E53935 0%, #C62828 100%);
  color: #FFFFFF;
  border-radius: 44rpx;
  font-size: 30rpx;
  font-weight: 600;
}
</style>
