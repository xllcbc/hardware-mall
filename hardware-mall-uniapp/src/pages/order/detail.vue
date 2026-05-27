<template>
  <view class="order-detail-container">
    <scroll-view class="detail-content" scroll-y>
      <view class="order-status-section" :class="getStatusBgClass(order.status)">
        <view class="status-icon-wrap">
          <text class="status-icon">{{ getStatusIcon(order.status) }}</text>
        </view>
        <view class="status-info">
          <text class="status-text">{{ order.statusText }}</text>
          <text v-if="order.status === 1" class="status-desc">请尽快完成支付</text>
          <text v-if="order.status === 3" class="status-desc">正在配送中，请保持电话畅通</text>
        </view>
      </view>

      <view class="card address-card">
        <view class="address-row">
          <text class="address-icon">📍</text>
          <view class="address-content">
            <view class="address-main">
              <text class="consignee">{{ order.receiverName }}</text>
              <text class="phone">{{ formatPhone(order.receiverPhone) }}</text>
            </view>
            <text class="address-detail">{{ order.receiverAddress }}</text>
          </view>
        </view>
      </view>

      <view class="card info-card">
        <view class="info-row">
          <text class="info-label">订单编号</text>
          <view class="info-value-row">
            <text class="info-value">{{ order.orderNo }}</text>
            <view class="copy-btn" @tap="copyOrderNo">复制</view>
          </view>
        </view>
        <view class="info-row">
          <text class="info-label">下单时间</text>
          <text class="info-value">{{ formatTime(order.createTime) }}</text>
        </view>
        <view v-if="order.payTime" class="info-row">
          <text class="info-label">支付时间</text>
          <text class="info-value">{{ formatTime(order.payTime) }}</text>
        </view>
        <view v-if="order.shipTime" class="info-row">
          <text class="info-label">发货时间</text>
          <text class="info-value">{{ formatTime(order.shipTime) }}</text>
        </view>
      </view>

      <view class="card items-card">
        <view class="items-header">商品清单</view>
        <view class="item" v-for="(item, index) in order.items" :key="item.id">
          <view class="item-inner" :class="{ 'border-bottom': index < order.items.length - 1 }">
            <image class="item-image" :src="item.productImage || '/static/images/default.png'" mode="aspectFill" />
            <view class="item-info">
              <text class="item-name">{{ item.productName }}</text>
              <text class="item-spec" v-if="item.productSpec">{{ item.productSpec }}</text>
            </view>
            <view class="item-right">
              <text class="item-price">¥{{ Number(item.price).toFixed(2) }}</text>
              <text class="item-quantity">× {{ item.quantity }}</text>
            </view>
          </view>
        </view>
      </view>

      <view class="card summary-card">
        <view class="summary-row">
          <text class="summary-label">商品金额</text>
          <text class="summary-value">¥{{ Number(order.goodsAmount || 0).toFixed(2) }}</text>
        </view>
        <view class="summary-row">
          <text class="summary-label">运费</text>
          <text class="summary-value">¥{{ Number(order.freightAmount || 0).toFixed(2) }}</text>
        </view>
        <view class="summary-row total">
          <text class="summary-label">实付款</text>
          <text class="summary-value primary">¥{{ Number(order.payAmount || 0).toFixed(2) }}</text>
        </view>
      </view>

      <view v-if="order.buyerRemark" class="card remark-card">
        <text class="remark-label">订单备注</text>
        <text class="remark-value">{{ order.buyerRemark }}</text>
      </view>
    </scroll-view>

    <view v-if="order.status === 1" class="detail-footer">
      <view class="action-btn secondary" @tap="cancelOrder">取消订单</view>
      <view class="action-btn primary" @tap="payOrder">去支付</view>
    </view>
    <view v-if="order.status === 3" class="detail-footer">
      <view class="action-btn primary" @tap="confirmReceive">确认收货</view>
    </view>
    <view v-if="order.status === 4 || order.status === 5" class="detail-footer">
      <view class="action-btn secondary" @tap="deleteOrder">删除订单</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { Order } from '@/types'
import { cancelOrder as cancelOrderApi, confirmReceive as confirmReceiveApi, deleteOrder as deleteOrderApi, getOrderDetail } from '@/api/order'
import { prepayOrder } from '@/api/pay'

const order = ref<Partial<Order>>({})

onMounted(async () => {
  const pages = getCurrentPages()
  const currentPage = pages[pages.length - 1] as any
  const orderId = currentPage?.options?.id
  if (orderId) {
    try {
      const data = await getOrderDetail(Number(orderId))
      order.value = data || {}
    } catch (e) {
      console.error('加载订单详情失败:', e)
      uni.showToast({ title: '加载失败', icon: 'none' })
    }
  }
})

const getStatusClass = (status: number) => {
  const map: Record<number, string> = {
    1: 'warning',
    2: 'info',
    3: 'primary',
    4: 'success'
  }
  return map[status] || ''
}

const getStatusBgClass = (status: number) => {
  const map: Record<number, string> = {
    1: 'bg-warning',
    2: 'bg-info',
    3: 'bg-primary',
    4: 'bg-success'
  }
  return map[status] || ''
}

const getStatusIcon = (status: number) => {
  const map: Record<number, string> = {
    1: '⏱',
    2: '📦',
    3: '🚚',
    4: '✓'
  }
  return map[status] || '📋'
}

const formatPhone = (phone: string) => {
  if (!phone) return ''
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}

const formatTime = (time: string) => {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 19)
}

const copyOrderNo = () => {
  uni.setClipboardData({
    data: order.value.orderNo!,
    success: () => {
      uni.showToast({ title: '已复制', icon: 'success' })
    }
  })
}

const cancelOrder = async () => {
  uni.showModal({
    title: '提示',
    content: '确定取消该订单?',
    success: async (res) => {
      if (res.confirm) {
        try {
          await cancelOrderApi(order.value.id!, '用户取消')
          uni.showToast({ title: '订单已取消', icon: 'success' })
          setTimeout(() => {
            uni.navigateBack()
          }, 1500)
        } catch (e) {
          uni.showToast({ title: e.message || '操作失败', icon: 'none' })
        }
      }
    }
  })
}

const payOrder = async () => {
  try {
    uni.showLoading({ title: '支付中...' })
    const res = await prepayOrder(order.value.id!)
    uni.hideLoading()
    uni.requestPayment({
      timeStamp: res.timeStamp,
      nonceStr: res.nonceStr,
      package: res.packageValue,
      signType: 'RSA',
      paySign: res.paySign,
      success: async () => {
        uni.showToast({ title: '支付成功', icon: 'success' })
        try {
          const data = await getOrderDetail(Number(order.value.id))
          order.value = data || {}
        } catch (e) {
          console.error('刷新订单失败:', e)
        }
      },
      fail: (err: any) => {
        if (err.errMsg?.includes('cancel')) {
          uni.showToast({ title: '已取消支付', icon: 'none' })
          return
        }
        uni.showToast({ title: '支付失败', icon: 'none' })
      }
    })
  } catch (e: any) {
    uni.hideLoading()
    uni.showToast({ title: e.message || '支付失败', icon: 'none' })
  }
}

const confirmReceive = async () => {
  uni.showModal({
    title: '提示',
    content: '确认已收到货物?',
    success: async (res) => {
      if (res.confirm) {
        try {
          await confirmReceiveApi(order.value.id!)
          uni.showToast({ title: '已确认收货', icon: 'success' })
          setTimeout(() => {
            uni.navigateBack()
          }, 1500)
        } catch (e) {
          uni.showToast({ title: e.message || '操作失败', icon: 'none' })
        }
      }
    }
  })
}

const deleteOrder = async () => {
  uni.showModal({
    title: '提示',
    content: '确定删除该订单?',
    success: async (res) => {
      if (res.confirm) {
        try {
          await deleteOrderApi(order.value.id!)
          uni.showToast({ title: '已删除', icon: 'success' })
          setTimeout(() => {
            uni.navigateBack()
          }, 1500)
        } catch (e) {
          uni.showToast({ title: e.message || '操作失败', icon: 'none' })
        }
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.order-detail-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
}

.detail-content {
  flex: 1;
}

.order-status-section {
  display: flex;
  align-items: center;
  gap: 32rpx;
  padding: 40rpx 32rpx;
  color: #FFFFFF;

  &.bg-warning {
    background: linear-gradient(135deg, #E53935 0%, #C62828 100%);
  }
  &.bg-info {
    background: linear-gradient(135deg, #1976D2 0%, #1565C0 100%);
  }
  &.bg-primary {
    background: linear-gradient(135deg, #C9A86C 0%, #B8956A 100%);
  }
  &.bg-success {
    background: linear-gradient(135deg, #388E3C 0%, #2E7D32 100%);
  }
}

.status-icon-wrap {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
}

.status-icon {
  font-size: 48rpx;
}

.status-info {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.status-text {
  font-size: 36rpx;
  font-weight: 700;
}

.status-desc {
  font-size: 26rpx;
  opacity: 0.9;
}

.card {
  margin: 24rpx;
  padding: 24rpx;
  background: #FFFFFF;
  border-radius: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}

.address-card {
  padding: 0;
}

.address-row {
  display: flex;
  align-items: flex-start;
  padding: 24rpx;
}

.address-icon {
  font-size: 36rpx;
  margin-right: 16rpx;
  margin-top: 4rpx;
}

.address-content {
  flex: 1;
}

.address-main {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 8rpx;
}

.consignee {
  font-size: 30rpx;
  font-weight: 600;
  color: #2C2C2C;
}

.phone {
  font-size: 26rpx;
  color: #666666;
}

.address-detail {
  font-size: 26rpx;
  color: #999999;
  line-height: 1.5;
}

.info-card {
  padding: 0;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 24rpx;
  border-bottom: 1rpx solid #F5F5F5;

  &:last-child {
    border-bottom: none;
  }
}

.info-label {
  font-size: 26rpx;
  color: #999999;
  flex-shrink: 0;
}

.info-value-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.info-value {
  font-size: 26rpx;
  color: #2C2C2C;
}

.copy-btn {
  padding: 8rpx 20rpx;
  background: #FFF8E1;
  color: #C9A86C;
  font-size: 22rpx;
  border-radius: 8rpx;
}

.items-card {
  padding: 0;
}

.items-header {
  padding: 20rpx 24rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #2C2C2C;
  border-bottom: 1rpx solid #F5F5F5;
}

.item-inner {
  display: flex;
  align-items: center;
  padding: 20rpx 24rpx;

  &.border-bottom {
    border-bottom: 1rpx solid #F5F5F5;
  }
}

.item-image {
  width: 140rpx;
  height: 140rpx;
  border-radius: 8rpx;
  flex-shrink: 0;
}

.item-info {
  flex: 1;
  min-width: 0;
  margin-left: 16rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.item-name {
  font-size: 28rpx;
  font-weight: 500;
  color: #2C2C2C;
  lines: 2;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-spec {
  font-size: 22rpx;
  color: #999999;
  margin-top: 6rpx;
}

.item-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  margin-left: 16rpx;
  min-width: 120rpx;
}

.item-price {
  font-size: 28rpx;
  font-weight: 600;
  color: #C9A86C;
}

.item-quantity {
  font-size: 24rpx;
  color: #999999;
  margin-top: 8rpx;
}

.summary-card {
  padding: 0;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 24rpx;
  border-bottom: 1rpx solid #F5F5F5;

  &:last-child {
    border-bottom: none;
  }

  &.total {
    background: #FFF8E1;
    margin: 0;
    padding: 24rpx;
    border-bottom: none;
  }
}

.summary-label {
  font-size: 26rpx;
  color: #666666;
}

.summary-value {
  font-size: 28rpx;
  color: #2C2C2C;

  &.primary {
    font-size: 40rpx;
    font-weight: 700;
    color: #E53935;
  }
}

.remark-card {
  padding: 0;
}

.remark-label {
  display: block;
  padding: 20rpx 24rpx 8rpx;
  font-size: 24rpx;
  color: #999999;
}

.remark-value {
  display: block;
  padding: 0 24rpx 20rpx;
  font-size: 26rpx;
  color: #2C2C2C;
  line-height: 1.5;
}

.detail-footer {
  display: flex;
  justify-content: flex-end;
  gap: 24rpx;
  padding: 24rpx 32rpx;
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
  background: #FFFFFF;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.05);
}

.action-btn {
  min-width: 160rpx;
  height: 72rpx;
  padding: 0 32rpx;
  border-radius: 36rpx;
  font-size: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;

  &.primary {
    background: linear-gradient(135deg, #E53935 0%, #C62828 100%);
    color: #FFFFFF;
    font-weight: 600;
  }

  &.secondary {
    background: #FFFFFF;
    color: #666666;
    border: 1rpx solid #E0E0E0;
  }
}
</style>