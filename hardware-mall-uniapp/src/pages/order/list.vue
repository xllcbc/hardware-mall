<template>
  <view class="order-container">
    <view class="order-tabs">
      <view
        v-for="(tab, index) in tabs"
        :key="index"
        class="tab-item"
        :class="{ active: currentTab === index }"
        @tap="switchTab(index)"
      >
        <text>{{ tab.label }}</text>
        <view v-if="tab.count" class="tab-badge">{{ tab.count }}</view>
      </view>
    </view>

    <scroll-view class="order-list" scroll-y @scrolltolower="loadMore">
      <view v-if="loading && !orders.length" class="loading-wrap">
        <LoadingState text="加载中..." />
      </view>
      <EmptyState v-else-if="!orders.length" text="暂无订单" />
      <view v-else>
        <view class="order-item" v-for="order in orders" :key="order.id">
          <view class="order-header">
            <view class="order-no-wrap" @tap.stop="copyOrderNo(order.orderNo)">
              <text class="order-no">订单号: {{ order.orderNo }}</text>
              <text class="copy-hint">点击复制</text>
            </view>
            <text class="order-status" :class="getStatusClass(order.status)">{{ order.statusText }}</text>
          </view>

          <view class="order-products">
            <view class="product-item" v-for="item in order.items" :key="item.id" @tap.stop="goProductDetail(item.productId)">
              <image class="product-image" :src="item.productImage || '/static/images/default.png'" mode="aspectFill" />
              <view class="product-info">
                <text class="product-name">{{ item.productName }}</text>
                <text class="product-spec" v-if="item.productSpec">{{ item.productSpec }}</text>
              </view>
              <view class="product-right">
                <text class="product-price">¥{{ Number(item.price).toFixed(2) }}</text>
                <text class="product-quantity">× {{ item.quantity }}</text>
              </view>
            </view>
          </view>

          <view class="order-summary">
            <text class="summary-text">共{{ getTotalQuantity(order) }}件商品，实付款</text>
            <text class="summary-price">¥{{ Number(order.payAmount).toFixed(2) }}</text>
          </view>

          <view class="order-actions">
            <view class="action-btn secondary" @tap.stop="goOrderDetail(order.id)">查看详情</view>
            <view v-if="order.status === 1" class="action-btn secondary" @tap.stop="cancelOrder(order)">取消订单</view>
            <view v-if="order.status === 2" class="action-btn secondary" @tap.stop="viewLogistics(order)">查看物流</view>
            <view v-if="order.status === 3" class="action-btn primary" @tap.stop="confirmReceive(order)">确认收货</view>
            <view v-if="order.status === 4 || order.status === 5" class="action-btn secondary" @tap.stop="deleteOrder(order)">删除订单</view>
          </view>
        </view>
      </view>
      <view v-if="noMore && orders.length" class="no-more">— 没有更多了 —</view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import LoadingState from '@/components/common/LoadingState.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import type { Order } from '@/types'
import { cancelOrder as cancelOrderApi, confirmReceive as confirmReceiveApi, deleteOrder as deleteOrderApi, getOrderList } from '@/api/order'

const tabs = reactive([
  { label: '全部', status: 0, count: 0 },
  { label: '待付款', status: 1, count: 0 },
  { label: '待发货', status: 2, count: 0 },
  { label: '已发货', status: 3, count: 0 },
  { label: '已完成', status: 4, count: 0 },
  { label: '已取消', status: 5, count: 0 }
])

const currentTab = ref(0)
const orders = ref<Order[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = 10
const noMore = ref(false)

onShow(() => {
  page.value = 1
  noMore.value = false
  loadOrders()
})

const switchTab = (index: number) => {
  currentTab.value = index
  page.value = 1
  noMore.value = false
  orders.value = []
  loadOrders()
}

const loadOrders = async () => {
  if (loading.value) return
  loading.value = true
  try {
    const currentTabData = tabs[currentTab.value]
    const params: any = {
      page: page.value,
      limit: pageSize
    }
    if (currentTabData.status !== 0) {
      params.status = currentTabData.status
    }
    const data = await getOrderList(params)
    if (page.value === 1) {
      orders.value = data.records || []
    } else {
      orders.value.push(...(data.records || []))
    }
    noMore.value = orders.value.length >= data.total
  } catch (e) {
    console.error('Failed to load orders:', e)
  } finally {
    loading.value = false
  }
}

const loadMore = () => {
  if (!noMore.value && !loading.value) {
    page.value++
    loadOrders()
  }
}

const getStatusClass = (status: number) => {
  const map: Record<number, string> = {
    1: 'warning',
    2: 'info',
    3: 'primary',
    4: 'success'
  }
  return map[status] || ''
}

const getTotalQuantity = (order: Order) => {
  return order.items.reduce((sum, item) => sum + item.quantity, 0)
}

const goOrderDetail = (id: number) => {
  uni.navigateTo({ url: `/pages/order/detail?id=${id}` })
}

const goProductDetail = (productId: number) => {
  uni.navigateTo({ url: `/pages/product/detail?id=${productId}` })
}

const copyOrderNo = (orderNo: string) => {
  uni.setClipboardData({
    data: orderNo,
    success: () => {
      uni.showToast({ title: '订单号已复制', icon: 'success' })
    }
  })
}

const cancelOrder = async (order: Order) => {
  uni.showModal({
    title: '提示',
    content: '确定取消该订单?',
    success: async (res) => {
      if (res.confirm) {
        try {
          await cancelOrderApi(order.id, '用户取消')
          uni.showToast({ title: '订单已取消', icon: 'success' })
          loadOrders()
        } catch (e) {
          uni.showToast({ title: e.message || '操作失败', icon: 'none' })
        }
      }
    }
  })
}

const viewLogistics = (order: Order) => {
  uni.navigateTo({ url: `/pages/logistics/index?orderId=${order.id}` })
}

const confirmReceive = async (order: Order) => {
  uni.showModal({
    title: '提示',
    content: '确认已收到货物?',
    success: async (res) => {
      if (res.confirm) {
        try {
          await confirmReceiveApi(order.id)
          uni.showToast({ title: '已确认收货', icon: 'success' })
          loadOrders()
        } catch (e) {
          uni.showToast({ title: e.message || '操作失败', icon: 'none' })
        }
      }
    }
  })
}

const deleteOrder = async (order: Order) => {
  uni.showModal({
    title: '提示',
    content: '确定删除该订单?',
    success: async (res) => {
      if (res.confirm) {
        try {
          await deleteOrderApi(order.id)
          orders.value = orders.value.filter(o => o.id !== order.id)
          uni.showToast({ title: '已删除', icon: 'success' })
        } catch (e) {
          uni.showToast({ title: e.message || '操作失败', icon: 'none' })
        }
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.order-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
}

.order-tabs {
  display: flex;
  background: var(--color-bg-card);
  padding: var(--spacing-sm) 0;
  border-bottom: 1rpx solid var(--color-border-light);
}

.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
  font-size: var(--font-size-md);
  color: var(--color-text-secondary);
  position: relative;

  &.active {
    color: var(--color-primary);
    font-weight: 600;
  }
}

.tab-badge {
  position: absolute;
  top: -8rpx;
  right: 50%;
  margin-right: -24rpx;
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 8rpx;
  background: var(--color-error);
  color: #FFFFFF;
  font-size: 20rpx;
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
}

.order-list {
  flex: 1;
  padding: var(--spacing-md);
}

.loading-wrap {
  padding: var(--spacing-xxl);
}

.order-item {
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  margin-bottom: var(--spacing-md);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx;
  border-bottom: 1rpx solid var(--color-border-light);
}

.order-no-wrap {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.order-no {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.copy-hint {
  font-size: 18rpx;
  color: #C9A86C;
}

.order-status {
  font-size: 28rpx;
  font-weight: 600;
  padding: 6rpx 16rpx;
  border-radius: 20rpx;

  &.warning {
    color: #E53935;
    background: #FFEBEE;
  }
  &.info {
    color: #1976D2;
    background: #E3F2FD;
  }
  &.primary {
    color: #C9A86C;
    background: #FFF8E1;
  }
  &.success {
    color: #388E3C;
    background: #E8F5E9;
  }
}

.order-products {
  padding: 16rpx 24rpx;
}

.product-item {
  display: flex;
  align-items: center;
  padding: 12rpx 0;
}

.product-image {
  width: 140rpx;
  height: 140rpx;
  border-radius: 8rpx;
  flex-shrink: 0;
}

.product-info {
  flex: 1;
  min-width: 0;
  margin-left: 16rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.product-name {
  font-size: 28rpx;
  font-weight: 500;
  color: #2C2C2C;
  lines: 2;
  overflow: hidden;
  text-overflow: ellipsis;
}

.product-spec {
  font-size: 22rpx;
  color: #999999;
  margin-top: 6rpx;
}

.product-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  margin-left: 16rpx;
  min-width: 120rpx;
}

.product-price {
  font-size: 28rpx;
  font-weight: 600;
  color: #C9A86C;
}

.product-quantity {
  font-size: 24rpx;
  color: #999999;
  margin-top: 8rpx;
}

.order-summary {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8rpx;
  padding: 16rpx 24rpx;
  border-top: 1rpx solid var(--color-border-light);
}

.summary-text {
  font-size: 24rpx;
  color: #666666;
}

.summary-price {
  font-size: 36rpx;
  font-weight: 700;
  color: #E53935;
}

.order-actions {
  display: flex;
  justify-content: flex-end;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  border-top: 1rpx solid var(--color-border-light);
}

.action-btn {
  min-width: 140rpx;
  height: 60rpx;
  padding: 0 24rpx;
  border-radius: 30rpx;
  font-size: 26rpx;
  display: flex;
  align-items: center;
  justify-content: center;

  &.primary {
    background: linear-gradient(135deg, #E53935 0%, #C62828 100%);
    color: #FFFFFF;
  }

  &.secondary {
    background: #FFFFFF;
    color: #666666;
    border: 1rpx solid #E0E0E0;
  }
}

.no-more {
  text-align: center;
  padding: var(--spacing-lg);
  font-size: var(--font-size-sm);
  color: var(--color-text-placeholder);
}
</style>