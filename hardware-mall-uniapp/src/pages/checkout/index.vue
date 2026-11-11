<template>
  <view v-if="!submitted" class="checkout-container">
    <scroll-view class="checkout-content" scroll-y>
      <view class="address-section" @tap="selectAddress">
        <view v-if="selectedAddress" class="address-info">
          <view class="address-header">
            <text class="consignee">{{ selectedAddress.consignee }}</text>
            <text class="phone">{{ formatPhone(selectedAddress.phone) }}</text>
            <view v-if="selectedAddress.isDefault" class="default-tag">默认</view>
          </view>
          <text class="address-detail">
            {{ selectedAddress.province }}{{ selectedAddress.city }}{{ selectedAddress.district }}{{ selectedAddress.detail }}
          </text>
        </view>
        <view v-else class="address-empty" @tap.stop="addAddress">
          <text class="empty-icon">📍</text>
          <text class="empty-text">请添加收货地址</text>
          <view class="add-btn">添加</view>
        </view>
        <view v-if="selectedAddress" class="address-arrow">›</view>
      </view>

      <view class="order-items">
        <view class="items-header">
          <text class="items-title">商品清单</text>
          <text class="items-count">共{{ orderItems.length }}件</text>
        </view>
        <view v-if="previewLoading" class="items-loading">正在计算结算金额...</view>
        <view class="item" v-for="item in orderItems" :key="item.skuId || item.productId">
          <image class="item-image" :src="item.productImage || '/static/images/placeholder.svg'" mode="aspectFill" lazy-load />
          <view class="item-info">
            <text class="item-name">{{ item.productName }}</text>
            <text v-if="item.spec" class="item-spec">{{ formatSpec(item.spec) }}</text>
            <view class="item-bottom">
              <text class="item-price">¥{{ formatPrice(item.price) }}</text>
              <text class="item-quantity">×{{ item.quantity }}</text>
            </view>
          </view>
        </view>
      </view>

      <view class="order-remark">
        <text class="remark-label">订单备注</text>
        <input
          class="remark-input"
          v-model="buyerRemark"
          placeholder="选填，可备注特殊需求"
          maxlength="200"
        />
      </view>

      <view class="order-summary">
        <view class="summary-row">
          <text class="summary-label">商品金额</text>
          <text class="summary-value">¥{{ formatPrice(goodsAmount) }}</text>
        </view>
        <view class="summary-row">
          <text class="summary-label">运费</text>
          <text class="summary-value">{{ freightAmount > 0 ? '¥' + formatPrice(freightAmount) : '免运费' }}</text>
        </view>
        <view class="summary-row total">
          <text class="summary-label">合计</text>
          <text class="summary-value primary">¥{{ formatPrice(payAmount) }}</text>
        </view>
      </view>
    </scroll-view>

    <view class="checkout-footer">
      <view class="footer-left">
        <text class="pay-label">实付款:</text>
        <view class="pay-price">
          <text class="price-symbol">¥</text>
          <text class="price-value">{{ formatPrice(payAmount) }}</text>
        </view>
      </view>
      <view class="footer-right">
        <view class="pay-btn" :class="{ disabled: previewLoading || !preview }" @tap="submitOrder">
          {{ previewLoading ? '计算中...' : '提交订单' }}
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useCartStore } from '@/stores/cart'
import { usePreOrderStore } from '@/stores/preOrder'
import { useUserStore } from '@/stores/user'
import { getAddressList } from '@/api/address'
import { getLogisticsList } from '@/api/logistics'
import { createOrder, getCheckoutPreview, getOrderDetail, getIdempotencyToken } from '@/api/order'
import { prepayOrder } from '@/api/pay'
import { ApiError, API_CODE_IDEMPOTENCY_CONFLICT, API_CODE_PRICE_CHANGED } from '@/utils/request'
import { decidePriceChange, shouldBlockPayment } from '@/utils/checkoutPricing'
import { buildOrderPayloadFingerprint, shouldRefreshIdempotencyKey } from '@/utils/orderIdempotency'
import { requireLogin } from '@/utils/auth'
import type { Address, CheckoutPreview, Logistics } from '@/types'

const cartStore = useCartStore()
const preOrderStore = usePreOrderStore()
const userStore = useUserStore()
const addresses = ref<Address[]>([])
const selectedAddress = ref<Address | null>(null)
const logisticsList = ref<Logistics[]>([])
const selectedLogistics = ref<Logistics | null>(null)
const buyerRemark = ref('')
const submitted = ref(false)
const idempotency = ref({ key: '', fingerprint: '' })
const preview = ref<CheckoutPreview | null>(null)
const previewLoading = ref(false)

const isDirectBuy = computed(() => preOrderStore.item !== null)

const requestItems = computed(() => {
  if (isDirectBuy.value) {
    const item = preOrderStore.item
    if (!item) return []
    return [{
      skuId: item.skuId,
      quantity: item.quantity
    }]
  }
  return cartStore.selectedItems.map(item => ({
    skuId: item.skuId,
    quantity: item.quantity
  }))
})

const orderItems = computed(() => preview.value?.items ?? [])
const goodsAmount = computed(() => preview.value?.goodsAmount ?? 0)
const freightAmount = computed(() => preview.value?.freightAmount ?? 0)
const payAmount = computed(() => preview.value?.payAmount ?? 0)

const formatPrice = (price: number) => {
  return price.toFixed(2)
}

const formatPhone = (phone: string) => {
  if (!phone) return ''
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}

const loadAddresses = async () => {
  try {
    const data = await getAddressList()
    addresses.value = data || []
    const picked = uni.getStorageSync('selectedAddress') as Address | null
    if (picked?.id) {
      const matched = addresses.value.find(a => a.id === picked.id)
      selectedAddress.value = matched || picked
      uni.removeStorageSync('selectedAddress')
      return
    }
    const defaultAddr = addresses.value.find(a => a.isDefault === 1)
    selectedAddress.value = defaultAddr || addresses.value[0] || null
  } catch (e) {
    console.error('Failed to load addresses:', e)
  }
}

const loadLogistics = async () => {
  try {
    const data = await getLogisticsList()
    logisticsList.value = data || []
    // M10: 自动选第一个启用的物流, 不再硬编码 logisticsId: 1
    selectedLogistics.value = logisticsList.value[0] || null
  } catch (e) {
    console.error('Failed to load logistics:', e)
  }
}

const refreshPreview = async () => {
  const items = requestItems.value
  if (!items.length) {
    preview.value = null
    return
  }
  previewLoading.value = true
  try {
    preview.value = await getCheckoutPreview({ items })
  } catch (e) {
    preview.value = null
    console.error('加载结算预览失败:', e)
  } finally {
    previewLoading.value = false
  }
}

onShow(() => {
  // 未登录不请求结算接口, 不强制跳转(审核合规); 需要结算时再走 requireLogin
  if (!userStore.isLoggedIn) return
  loadAddresses()
  loadLogistics()
  refreshPreview()
})

const selectAddress = () => {
  uni.navigateTo({ url: '/pages/address/list?mode=select' })
}

const addAddress = () => {
  uni.navigateTo({ url: '/pages/address/edit' })
}

const clearIdempotencyKey = () => {
  idempotency.value = { key: '', fingerprint: '' }
}

const ensureIdempotencyKey = async (fingerprint: string) => {
  if (!shouldRefreshIdempotencyKey(idempotency.value, fingerprint)) {
    return idempotency.value.key
  }
  const token = await getIdempotencyToken()
  idempotency.value = { key: token, fingerprint }
  return token
}

const submitOrder = async () => {
  if (submitted.value) return
  if (!(await requireLogin('登录后才能提交订单'))) return
  if (!selectedAddress.value) {
    uni.showToast({ title: '请选择收货地址', icon: 'none' })
    return
  }
  if (!isDirectBuy.value && requestItems.value.length === 0) {
    uni.showToast({ title: '请先在购物车选择商品', icon: 'none' })
    setTimeout(() => {
      uni.redirectTo({ url: '/pages/cart/index' })
    }, 1500)
    return
  }
  if (!selectedLogistics.value) {
    uni.showToast({ title: '暂无可用配送方式', icon: 'none' })
    return
  }
  if (previewLoading.value) return
  if (!preview.value) {
    await refreshPreview()
    if (!preview.value) {
      uni.showToast({ title: '结算金额加载失败，请稍后重试', icon: 'none' })
      return
    }
  }

  submitted.value = true
  const expectedPayAmount = preview.value.payAmount
  let fingerprint = ''
  try {
    const items = requestItems.value

    const orderData = {
      items,
      addressId: selectedAddress.value.id,
      logisticsId: selectedLogistics.value.id,
      expectedPayAmount,
      buyerRemark: buyerRemark.value
    }

    fingerprint = buildOrderPayloadFingerprint(orderData)
    const idempotencyKey = await ensureIdempotencyKey(fingerprint)
    const order = await createOrder(orderData, idempotencyKey)
    clearIdempotencyKey()

    if (isDirectBuy.value) {
      preOrderStore.clearItem()
    } else {
      cartStore.clearSelected()
    }

    if (shouldBlockPayment(order.payAmount, expectedPayAmount)) {
      submitted.value = false
      uni.showModal({
        title: '订单金额异常',
        content: `订单应付 ¥${formatPrice(order.payAmount)}，确认金额 ¥${formatPrice(expectedPayAmount)}，已停止支付。`,
        showCancel: false,
        success: () => {
          uni.redirectTo({ url: `/pages/order/detail?id=${order.id}` })
        }
      })
      return
    }

    try {
      uni.showLoading({ title: '支付中...' })
      const payParams = await prepayOrder(order.id)
      uni.hideLoading()

      uni.requestPayment({
        timeStamp: payParams.timeStamp,
        nonceStr: payParams.nonceStr,
        package: payParams.packageValue,
        signType: 'RSA',
        paySign: payParams.paySign,
        success: () => {
          uni.showToast({ title: '支付成功', icon: 'success' })
          setTimeout(async () => {
            await getOrderDetail(order.id)
            uni.redirectTo({ url: `/pages/order/detail?id=${order.id}` })
          }, 1500)
        },
        fail: (err: any) => {
          if (err.errMsg?.includes('cancel')) {
            uni.showToast({ title: '已取消支付', icon: 'none' })
          } else {
            uni.showToast({ title: '支付失败', icon: 'none' })
          }
          submitted.value = false
          setTimeout(() => {
            uni.redirectTo({ url: `/pages/order/detail?id=${order.id}` })
          }, 1500)
        }
      })
    } catch (payErr: any) {
      uni.hideLoading()
      uni.showToast({ title: payErr.message || '调起支付失败', icon: 'none' })
      submitted.value = false
      setTimeout(() => {
        uni.redirectTo({ url: `/pages/order/detail?id=${order.id}` })
      }, 1500)
    }
  } catch (e: any) {
    if (e instanceof ApiError && e.code === API_CODE_IDEMPOTENCY_CONFLICT) {
      clearIdempotencyKey()
      try {
        await ensureIdempotencyKey(fingerprint)
      } catch {
        // 刷新失败时保持空键, 下次提交会再次获取
      }
      submitted.value = false
      uni.showModal({
        title: '订单信息已变化',
        content: '请重新提交订单。',
        showCancel: false
      })
      return
    }
    if (e instanceof ApiError
      && e.code === API_CODE_PRICE_CHANGED
      && e.data?.preview) {
      const latestPreview = e.data.preview as CheckoutPreview
      const latestAmount = Number(latestPreview.payAmount)
      const previousAmount = Number(e.data.expectedPayAmount ?? expectedPayAmount)
      submitted.value = false
      clearIdempotencyKey()
      preview.value = latestPreview
      uni.showModal({
        title: '商品价格已更新',
        content: `原应付 ¥${formatPrice(previousAmount)}，最新应付 ¥${formatPrice(latestAmount)}。是否按新金额创建订单？`,
        confirmText: '确认下单',
        cancelText: '暂不',
        success: (res) => {
          const decision = decidePriceChange(res.confirm, latestPreview)
          preview.value = decision.preview
          if (decision.retrySubmit) {
            void submitOrder()
          }
        }
      })
      return
    }
    uni.showToast({ title: e.message || '提交失败', icon: 'none' })
    submitted.value = false
  }
}

const formatSpec = (spec: string) => {
  try {
    const specArr = JSON.parse(spec)
    return specArr.map((s: any) => s.value).join(', ')
  } catch {
    return spec
  }
}
</script>

<style lang="scss" scoped>
.checkout-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #FAFAFA;
}

.checkout-content {
  flex: 1;
}

.address-section {
  display: flex;
  align-items: center;
  padding: 32rpx 24rpx;
  background: #FFFFFF;
  margin-bottom: 16rpx;
}

.address-info {
  flex: 1;
}

.address-header {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 8rpx;
}

.consignee {
  font-size: 32rpx;
  font-weight: 600;
  color: #2C2C2C;
}

.phone {
  font-size: 28rpx;
  color: #666666;
}

.default-tag {
  padding: 4rpx 16rpx;
  background: #E5D4B8;
  color: #C9A86C;
  font-size: 20rpx;
  border-radius: 9999rpx;
}

.address-detail {
  font-size: 24rpx;
  color: #666666;
  line-height: 1.5;
}

.address-empty {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.empty-icon {
  font-size: 48rpx;
}

.empty-text {
  flex: 1;
  font-size: 28rpx;
  color: #999999;
}

.add-btn {
  padding: 8rpx 24rpx;
  background: linear-gradient(135deg, #C9A86C 0%, #B8956A 100%);
  color: #FFFFFF;
  font-size: 24rpx;
  border-radius: 24rpx;
}

.address-arrow {
  font-size: 40rpx;
  color: #CCCCCC;
  margin-left: 16rpx;
}

.order-items {
  background: #FFFFFF;
  margin-bottom: 16rpx;
}

.items-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx;
  border-bottom: 1rpx solid #F0F0F0;
}

.items-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #2C2C2C;
}

.items-count {
  font-size: 24rpx;
  color: #999999;
}

.items-loading {
  padding: 32rpx 24rpx;
  font-size: 26rpx;
  color: #999999;
}

.item {
  display: flex;
  padding: 24rpx;
  border-bottom: 1rpx solid #F0F0F0;

  &:last-child {
    border-bottom: none;
  }
}

.item-image {
  width: 160rpx;
  height: 160rpx;
  border-radius: 8rpx;
  flex-shrink: 0;
  background: #F5F5F5;
}

.item-info {
  flex: 1;
  margin-left: 16rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.item-name {
  font-size: 28rpx;
  color: #2C2C2C;
  line-height: 1.4;
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
}

.item-price {
  font-size: 28rpx;
  color: #C9A86C;
  font-weight: 600;
}

.item-quantity {
  font-size: 24rpx;
  color: #999999;
}

.order-remark {
  display: flex;
  align-items: center;
  padding: 24rpx;
  background: #FFFFFF;
  margin-bottom: 16rpx;
}

.remark-label {
  font-size: 28rpx;
  color: #2C2C2C;
  flex-shrink: 0;
}

.remark-input {
  flex: 1;
  margin-left: 24rpx;
  font-size: 28rpx;
  color: #666666;
}

.order-summary {
  background: #FFFFFF;
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8rpx 0;

  &.total {
    margin-top: 16rpx;
    padding-top: 16rpx;
    border-top: 1rpx solid #F0F0F0;
  }
}

.summary-label {
  font-size: 28rpx;
  color: #666666;
}

.summary-value {
  font-size: 28rpx;
  color: #2C2C2C;

  &.primary {
    font-size: 40rpx;
    font-weight: 700;
    color: #C9A86C;
  }
}

.checkout-footer {
  display: flex;
  align-items: center;
  padding: 24rpx;
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
  background: #FFFFFF;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.05);
}

.footer-left {
  flex: 1;
  display: flex;
  align-items: baseline;
  gap: 8rpx;
}

.pay-label {
  font-size: 28rpx;
  color: #666666;
}

.pay-price {
  display: flex;
  align-items: baseline;
  color: #C9A86C;
}

.price-symbol {
  font-size: 28rpx;
  font-weight: 600;
}

.price-value {
  font-size: 40rpx;
  font-weight: 700;
}

.footer-right {
  display: flex;
  align-items: center;
}

.pay-btn {
  padding: 16rpx 48rpx;
  background: linear-gradient(135deg, #C9A86C 0%, #B8956A 100%);
  color: #FFFFFF;
  border-radius: 40rpx;
  font-size: 28rpx;
  font-weight: 500;

  &.disabled {
    opacity: 0.55;
  }
}
</style>
