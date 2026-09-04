<template>
  <view class="cart-container">
    <view v-if="!cartStore.items.length" class="cart-empty">
      <EmptyState text="购物车是空的" icon="🛒">
        <template #action>
          <view class="empty-btn" @tap="goShopping">去逛逛</view>
          <view v-if="!userStore.isLoggedIn" class="empty-btn" @tap="goLogin">去登录</view>
        </template>
      </EmptyState>
    </view>

    <template v-else>
      <view class="cart-header">
        <view class="header-placeholder"></view>
        <text class="manage-btn" @tap="toggleManageMode">
          {{ manageMode ? '退出管理' : '管理' }}
        </text>
      </view>

      <scroll-view class="cart-list" scroll-y>
        <view class="cart-item" v-for="item in cartStore.items" :key="item.skuId">
          <view class="item-checkbox" @tap="toggleSelect(item)">
            <view class="checkbox" :class="{ checked: item.selected }">
              <text v-if="item.selected" class="check-icon">✓</text>
            </view>
          </view>

          <view class="item-main" @tap="goProductDetail(item.productId)">
            <image class="item-image" :src="item.productImage || '/static/images/default.png'" mode="aspectFill" lazy-load />

            <view class="item-content">
              <text class="item-name">{{ item.productName }}</text>
              <text v-if="item.spec" class="item-spec">{{ item.spec }}</text>
              <text v-if="item.stock === 0" class="soldout-badge">已售罄</text>
              <view class="item-bottom">
                <view class="item-price">
                  <text class="price-symbol">¥</text>
                  <text class="price-value">{{ item.price.toFixed(2) }}</text>
                </view>
                <CountStepper
                  v-if="!manageMode"
                  :model-value="item.quantity"
                  :min="1"
                  :max="item.stock ?? 99"
                  :disabled="item.stock === 0"
                  @update:model-value="updateQuantity(item, $event)"
                />
              </view>
            </view>
          </view>
        </view>
      </scroll-view>

      <view class="cart-footer">
        <view class="footer-left">
          <view class="select-all" @tap="toggleSelectAll">
            <view class="checkbox" :class="{ checked: isSelectAll }">
              <text v-if="isSelectAll" class="check-icon">✓</text>
            </view>
            <text class="select-text">全选</text>
          </view>

          <template v-if="!manageMode">
            <view class="total-wrap">
              <text class="total-label">合计:</text>
              <view class="total-price">
                <text class="price-symbol">¥</text>
                <text class="price-value">{{ cartStore.selectedTotal.toFixed(2) }}</text>
              </view>
            </view>
          </template>
        </view>

        <view class="footer-right">
          <template v-if="!manageMode">
            <view class="checkout-btn" :class="{ disabled: !cartStore.selectedItems.length }" @tap="goCheckout">
              去结算({{ cartStore.selectedItems.length }})
            </view>
          </template>
          <template v-else>
            <view class="favorite-btn" @tap="moveToFavorites">添加收藏</view>
            <view class="delete-btn" :class="{ disabled: !cartStore.selectedItems.length }" @tap="deleteSelected">
              删除
            </view>
          </template>
        </view>
      </view>
    </template>
    <PrivacyPopup v-model="showPrivacy" @agree="onAgree" />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import EmptyState from '@/components/common/EmptyState.vue'
import CountStepper from '@/components/common/CountStepper.vue'
import PrivacyPopup from '@/components/common/PrivacyPopup.vue'
import { useCartStore } from '@/stores/cart'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { usePrivacyGate } from '@/composables/usePrivacyGate'
import { getCartList } from '@/api/cart'
import type { CartItem } from '@/types'

const cartStore = useCartStore()
const appStore = useAppStore()
const userStore = useUserStore()
const { showPrivacy, onAgree, check: checkPrivacy } = usePrivacyGate()
const manageMode = ref(false)

onShow(() => {
  // 未登录不请求购物车接口: 展示空车 + 去登录入口, 不强制跳转登录页(审核合规)
  if (!userStore.isLoggedIn) {
    cartStore.setItems([])
    return
  }
  loadCart()
})

onPullDownRefresh(() => {
  if (userStore.isLoggedIn) loadCart()
  uni.stopPullDownRefresh()
})

const loadCart = async () => {
  try {
    const data = await getCartList()
    cartStore.setItems(data || [])
  } catch (e) {
    console.error('加载购物车失败:', e)
  }
}

const goLogin = () => {
  uni.navigateTo({ url: '/pages/login/index' })
}

const isSelectAll = computed(() =>
  cartStore.items.length > 0 && cartStore.items.every(item => item.selected)
)

const goShopping = () => {
  uni.switchTab({ url: '/pages/index/index' })
}

const goProductDetail = (id: number) => {
  if (manageMode.value) return
  uni.navigateTo({ url: `/pages/product/detail?id=${id}` })
}

const toggleManageMode = () => {
  manageMode.value = !manageMode.value
}

const toggleSelect = (item: CartItem) => {
  item.selected = !item.selected
}

const toggleSelectAll = () => {
  const allSelected = isSelectAll.value
  cartStore.items.forEach(item => {
    item.selected = !allSelected
  })
}

const updateQuantity = (item: CartItem, quantity: number) => {
  cartStore.updateQuantity(item.skuId, quantity)
}

const deleteSelected = () => {
  if (!cartStore.selectedItems.length) return
  uni.showModal({
    title: '提示',
    content: `确定删除选中的 ${cartStore.selectedItems.length} 件商品?`,
    success: (res) => {
      if (res.confirm) {
        const itemsToDelete = [...cartStore.selectedItems]
        itemsToDelete.forEach(item => {
          cartStore.removeItem(item.skuId)
        })
      }
    }
  })
}

const moveToFavorites = () => {
  const selectedItems = cartStore.selectedItems
  if (selectedItems.length === 0) {
    uni.showToast({ title: '请先选择商品', icon: 'none' })
    return
  }
  selectedItems.forEach(item => {
    appStore.addFavorite({
      id: item.productId,
      name: item.productName,
      image: item.productImage,
      minPrice: item.minPrice || item.price,
      originalPrice: item.maxPrice
    })
  })
  uni.showToast({ title: '已移入收藏夹', icon: 'success' })
  manageMode.value = false
}

const goCheckout = () => {
  if (!cartStore.selectedItems.length) return
  uni.navigateTo({ url: '/pages/checkout/index' })
}
</script>

<style lang="scss" scoped>
.cart-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
  position: relative;
}

.cart-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-btn {
  margin-top: var(--spacing-lg);
  padding: var(--spacing-sm) var(--spacing-xl);
  background: var(--gradient-primary-dark);
  color: #FFFFFF;
  border-radius: var(--radius-full);
  font-size: var(--font-size-md);
}

.cart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 24rpx;
  background: #FFFFFF;
  border-bottom: 1rpx solid #EEEEEE;
}

.header-placeholder {
  width: 100rpx;
}

.manage-btn {
  font-size: 28rpx;
  color: #666666;
}

.item-main {
  flex: 1;
  display: flex;
  min-width: 0;
}

.cart-list {
  flex: 1;
  padding: 24rpx;
  padding-bottom: calc(24rpx + 120rpx);
}

.cart-item {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
  padding: 24rpx;
  background: var(--color-bg-card);
  border-radius: 16rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);
}

.item-checkbox {
  display: flex;
  align-items: center;
  padding-top: 50rpx;
  margin-right: 16rpx;
  flex-shrink: 0;
}

.checkbox {
  width: 44rpx;
  height: 44rpx;
  border: 2rpx solid #CCCCCC;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;

  &.checked {
    background: #C9A86C;
    border-color: #C9A86C;
  }
}

.check-icon {
  color: #FFFFFF;
  font-size: 24rpx;
  font-weight: bold;
}

.item-image {
  width: 180rpx;
  height: 180rpx;
  border-radius: 12rpx;
  flex-shrink: 0;
}

.item-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.item-name {
  font-size: 28rpx;
  color: #2C2C2C;
  lines: 2;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-spec {
  font-size: 20rpx;
  color: #999999;
  margin-top: 4rpx;
}

.soldout-badge {
  display: inline-block;
  align-self: flex-start;
  margin-top: 8rpx;
  padding: 2rpx 12rpx;
  font-size: 20rpx;
  color: #E53935;
  background: #FFEBEE;
  border-radius: 6rpx;
}

.item-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.item-price {
  display: flex;
  align-items: baseline;

  .price-symbol {
    font-size: 24rpx;
    font-weight: 500;
    color: #C9A86C;
  }

  .price-value {
    font-size: 32rpx;
    font-weight: 700;
    color: #C9A86C;
  }
}

.cart-footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 24rpx;
  background: #FFFFFF;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.05);
  flex-shrink: 0;
  z-index: 100;
}

.footer-left {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.select-all {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.select-text {
  font-size: 26rpx;
  color: #666666;
}

.total-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0;
}

.total-label {
  font-size: 22rpx;
  color: #999999;
}

.total-price {
  display: flex;
  align-items: baseline;

  .price-symbol {
    font-size: 28rpx;
    font-weight: 600;
    color: #E53935;
  }

  .price-value {
    font-size: 48rpx;
    font-weight: 800;
    color: #E53935;
  }
}

.footer-right {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.checkout-btn {
  min-width: 200rpx;
  height: 80rpx;
  padding: 0 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #E53935 0%, #C62828 100%);
  color: #FFFFFF;
  border-radius: 40rpx;
  font-size: 30rpx;
  font-weight: 600;

  &.disabled {
    background: #CCCCCC;
    color: #999999;
  }
}

.favorite-btn {
  min-width: 160rpx;
  height: 72rpx;
  padding: 0 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #FFF3E0;
  color: #E65100;
  border-radius: 36rpx;
  font-size: 26rpx;
  font-weight: 500;
}

.delete-btn {
  min-width: 140rpx;
  height: 72rpx;
  padding: 0 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #E53935;
  color: #FFFFFF;
  border-radius: 36rpx;
  font-size: 26rpx;
  font-weight: 500;

  &.disabled {
    background: #CCCCCC;
    color: #999999;
  }
}
</style>
