<template>
  <view class="detail-container">
    <swiper v-if="currentImages?.length" class="product-swiper" indicator-dots indicator-active-color="#C9A86C">
      <swiper-item v-for="(img, index) in currentImages" :key="index">
        <image class="product-image" :src="img" mode="aspectFill" />
      </swiper-item>
    </swiper>
    <view v-else class="product-image-placeholder">
      <image class="placeholder-img" src="/static/images/placeholder.svg" mode="aspectFill" />
    </view>

    <view class="product-content">
      <view class="price-section">
        <view class="price-wrap">
          <text class="price-symbol">¥</text>
          <text class="price-value">{{ formatPrice(currentSkuPrice) }}</text>
          <text v-if="currentSku && product.originalPrice" class="original-price">¥{{ formatPrice(product.originalPrice) }}</text>
        </view>
      </view>

      <view class="product-meta">
        <view class="meta-item">
          <text class="meta-value" :class="{ 'out-of-stock': !currentSkuStock }">{{ stockText }}</text>
        </view>
        <view class="meta-divider">|</view>
        <view class="meta-item">
          <text class="meta-label">销量</text>
          <text class="meta-value">{{ product.salesCount || 0 }}</text>
        </view>
      </view>

      <view class="product-info">
        <text class="product-name">{{ product.name }}</text>
        <text v-if="product.subtitle" class="product-subtitle">{{ product.subtitle }}</text>
      </view>

      <view class="spec-section" v-if="specTemplates.length">
        <view class="spec-block" v-for="template in specTemplates" :key="template.id">
          <view class="spec-name">{{ template.name }}</view>
          <view class="spec-values">
            <view
              v-for="item in getSpecItems(template.id)"
              :key="item.id"
              class="spec-value"
              :class="{ selected: isSpecSelected(template.id, item.id), disabled: !isSpecAvailable(template.id, item.id) }"
              @tap="selectSpec(template.id, item.id, item.value)"
            >
              {{ item.value }}
            </view>
          </view>
        </view>
      </view>

      <view class="quantity-section">
        <text class="quantity-label">数量</text>
        <CountStepper v-model="quantity" :min="1" :max="99999" />
      </view>

      <view class="product-desc" v-if="product.description">
        <view class="desc-header">
          <text class="desc-title">商品详情</text>
        </view>
        <rich-text class="desc-content" :nodes="product.description"></rich-text>
      </view>
    </view>

    <view class="detail-footer">
      <view class="footer-left">
        <view class="action-item" @tap="toggleFavorite">
          <text class="action-icon" :class="{ active: isFavorite }">{{ isFavorite ? '❤️' : '🤍' }}</text>
          <text class="action-text">收藏</text>
        </view>
        <view class="action-item" @tap="goCart">
          <text class="action-icon">🛒</text>
          <text class="action-text">购物车</text>
        </view>
      </view>
      <view class="footer-right">
        <view class="add-cart-btn" @tap="addToCart">加入购物车</view>
        <view class="buy-btn" @tap="buyNow">立即购买</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getProductDetail } from '@/api/product'
import { addToCart as addToCartApi } from '@/api/cart'
import { useAppStore } from '@/stores/app'
import { usePreOrderStore } from '@/stores/preOrder'
import CountStepper from '@/components/common/CountStepper.vue'
import type { ProductDetail, SpecTemplate, SpecItem, Sku, SpecVO } from '@/types'

const appStore = useAppStore()
const preOrderStore = usePreOrderStore()
const product = ref<any>({})
const detailData = ref<ProductDetail | null>(null)
const loading = ref(true)
const selectedSpecs = ref<Record<number, { itemId: number; value: string }>>({})
const quantity = ref(1)

const specTemplates = computed(() => detailData.value?.specTemplates || [])
const skus = computed(() => detailData.value?.skus || [])

const isFavorite = computed(() => appStore.isFavorite(product.value.id!))

const currentSku = computed(() => {
  if (Object.keys(selectedSpecs.value).length !== specTemplates.value.length) {
    return null
  }
  const selectedSpecList: SpecVO[] = Object.entries(selectedSpecs.value).map(([templateId, spec]) => ({
    templateId: Number(templateId),
    itemId: spec.itemId,
    name: specTemplates.value.find(t => t.id === Number(templateId))?.name || '',
    value: spec.value
  }))

  return skus.value.find(sku => {
    if (sku.specs.length !== selectedSpecList.length) return false
    return selectedSpecList.every(selected =>
      sku.specs.some(skuSpec =>
        skuSpec.templateId === selected.templateId && skuSpec.itemId === selected.itemId
      )
    )
  }) || null
})

const currentSkuPrice = computed(() => {
  return currentSku.value?.price || product.value.originalPrice || 0
})

const currentSkuStock = computed(() => {
  return currentSku.value?.stock || 0
})

const currentImages = computed(() => {
  if (currentSku.value?.image) {
    return [currentSku.value.image, ...(product.value.images || [])]
  }
  return product.value.images || []
})

const stockText = computed(() => {
  if (!specTemplates.value.length) {
    return product.value.stock && product.value.stock > 0 ? '有货' : '无货'
  }
  if (!currentSku.value) {
    return '请选择规格'
  }
  return currentSkuStock.value > 0 ? '有货' : '无货'
})

const getSpecItems = (templateId: number) => {
  if (!detailData.value?.specItemsMap) return []
  const key = String(templateId)
  const items = detailData.value.specItemsMap[key]
  if (items) return items
  return detailData.value.specItemsMap[templateId] || []
}

const isSpecSelected = (templateId: number, itemId: number) => {
  return selectedSpecs.value[templateId]?.itemId == itemId
}

const isSpecAvailable = (templateId: number, itemId: number) => {
  const tempSelected = { ...selectedSpecs.value }
  tempSelected[templateId] = { itemId, value: '' }

  const currentSpecs = Object.entries(tempSelected).map(([tid, spec]) => ({
    templateId: Number(tid),
    itemId: spec.itemId
  }))

  return skus.value.some(sku =>
    sku.status === 1 &&
    sku.stock > 0 &&
    currentSpecs.every(cs =>
      sku.specs.some(s => s.templateId === cs.templateId && s.itemId === cs.itemId)
    )
  )
}

const selectSpec = (templateId: number, itemId: number, value: string) => {
  if (!isSpecAvailable(templateId, itemId)) return
  selectedSpecs.value[templateId] = { itemId, value }
}

const formatPrice = (price: number | string | undefined) => {
  if (!price) return '0.00'
  const num = typeof price === 'string' ? parseFloat(price) : price
  return isNaN(num) ? '0.00' : num.toFixed(2)
}

onMounted(async () => {
  const pages = getCurrentPages()
  const currentPage = pages[pages.length - 1] as any
  const productId = currentPage?.options?.id

  const numericId = Number(productId)
  if (productId && numericId > 0) {
    try {
      const data = await getProductDetail(numericId)
      detailData.value = data
      product.value = data?.spu || {}
      appStore.addFootprint({
        id: product.value.id,
        name: product.value.name,
        image: product.value.images?.[0] || '/static/images/face.jpg',
        price: data?.minPrice || product.value.originalPrice || 0
      })
    } catch (e) {
      console.error('Failed to load product:', e)
    }
  } else {
    uni.showToast({ title: '商品不存在', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 1500)
    return
  }
  loading.value = false
})

const toggleFavorite = () => {
  if (!product.value.id) return
  appStore.toggleFavorite({
    id: product.value.id,
    name: product.value.name || '',
    image: product.value.images?.[0] || '/static/images/face.jpg',
    minPrice: detailData.value?.minPrice || currentSkuPrice.value,
    originalPrice: product.value.originalPrice
  })
}

const goCart = () => {
  uni.switchTab({ url: '/pages/cart/index' })
}

const addToCart = async () => {
  if (specTemplates.value.length && !currentSku.value) {
    uni.showToast({ title: '请选择商品规格', icon: 'none' })
    return
  }
  if (!specTemplates.value.length && !currentSku.value) {
    uni.showToast({ title: '暂无可用规格，请稍后再试', icon: 'none' })
    return
  }
  if (currentSkuStock.value <= 0) {
    uni.showToast({ title: '商品已售罄', icon: 'none' })
    return
  }
  try {
    const skuId = currentSku.value?.id || product.value.id
    if (!skuId) throw new Error('未找到可用规格')
    await addToCartApi(skuId, quantity.value)
    uni.showToast({ title: '已加入购物车', icon: 'success' })
  } catch (e: any) {
    console.error('加入购物车失败:', e)
    uni.showToast({ title: e.message || '加入购物车失败', icon: 'none' })
  }
}

const buyNow = async () => {
  if (specTemplates.value.length && !currentSku.value) {
    uni.showToast({ title: '请选择商品规格', icon: 'none' })
    return
  }
  if (!specTemplates.value.length && !currentSku.value) {
    uni.showToast({ title: '暂无可用规格，请稍后再试', icon: 'none' })
    return
  }
  if (currentSkuStock.value <= 0) {
    uni.showToast({ title: '商品已售罄', icon: 'none' })
    return
  }
  if (!currentSku.value) {
    uni.showToast({ title: '暂无可用规格，请稍后再试', icon: 'none' })
    return
  }

  const specStr = Object.values(selectedSpecs.value)
    .map(spec => spec.value)
    .join(' ')

  preOrderStore.setItem({
    skuId: currentSku.value.id,
    productId: product.value.id,
    productName: product.value.name,
    productImage: currentSku.value.image || product.value.images?.[0] || '/static/images/face.jpg',
    spec: specStr,
    price: currentSkuPrice.value,
    quantity: quantity.value
  })

  uni.navigateTo({ url: '/pages/checkout/index' })
}

const onShare = () => {
  uni.showShareMenu({ withShareTicket: true })
}
</script>

<style lang="scss" scoped>
.detail-container {
  min-height: 100vh;
  background: #FAFAFA;
  padding-bottom: calc(140rpx + env(safe-area-inset-bottom));
}

.product-swiper {
  height: 750rpx;
  background: #FFFFFF;
}

.product-image {
  width: 100%;
  height: 100%;
}

.product-image-placeholder {
  height: 750rpx;
  background: #F5F5F5;
  display: flex;
  align-items: center;
  justify-content: center;
}

.placeholder-img {
  width: 100%;
  height: 100%;
}

.product-content {
  padding: 32rpx;
}

.price-section {
  margin-bottom: 24rpx;
}

.price-wrap {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
}

.price-symbol {
  font-size: 32rpx;
  font-weight: 600;
  color: #C9A86C;
}

.price-value {
  font-size: 56rpx;
  font-weight: 700;
  color: #C9A86C;
}

.original-price {
  font-size: 24rpx;
  color: #999999;
  text-decoration: line-through;
  margin-left: 16rpx;
}

.product-meta {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
  border-top: 1rpx solid #F0F0F0;
  border-bottom: 1rpx solid #F0F0F0;
  margin-bottom: 24rpx;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.meta-label {
  font-size: 24rpx;
  color: #999999;
}

.meta-value {
  font-size: 24rpx;
  color: #2C2C2C;

  &.out-of-stock {
    color: #E53935;
  }
}

.meta-divider {
  font-size: 24rpx;
  color: #E8E8E8;
  margin: 0 24rpx;
}

.product-info {
  margin-bottom: 24rpx;
}

.product-name {
  font-size: 36rpx;
  font-weight: 600;
  color: #2C2C2C;
  line-height: 1.4;
  margin-bottom: 12rpx;
}

.product-subtitle {
  font-size: 26rpx;
  color: #666666;
  line-height: 1.5;
}

.spec-section {
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
}

.spec-block {
  margin-bottom: 24rpx;

  &:last-child {
    margin-bottom: 0;
  }
}

.spec-name {
  font-size: 28rpx;
  color: #333333;
  font-weight: 500;
  margin-bottom: 16rpx;
}

.spec-values {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.spec-value {
  padding: 12rpx 24rpx;
  background: #F5F5F5;
  border-radius: 8rpx;
  font-size: 26rpx;
  color: #333333;
  border: 2rpx solid transparent;

  &.selected {
    background: #FDF6EC;
    border-color: #C9A86C;
    color: #C9A86C;
  }

  &.disabled {
    background: #F0F0F0;
    color: #CCCCCC;
    text-decoration: line-through;
  }
}

.quantity-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx;
  background: #FFFFFF;
  border-radius: 16rpx;
  margin-bottom: 24rpx;
}

.quantity-label {
  font-size: 28rpx;
  color: #333333;
  font-weight: 500;
}

.product-desc {
  margin-top: 32rpx;
}

.desc-header {
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid #F0F0F0;
}

.desc-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #2C2C2C;
}

.desc-content {
  padding: 24rpx 0;
  font-size: 28rpx;
  color: #666666;
  line-height: 1.8;
}

.detail-footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: #FFFFFF;
  box-shadow: 0 -2rpx 16rpx rgba(0, 0, 0, 0.05);
}

.footer-left {
  display: flex;
  align-items: center;
  gap: 32rpx;
}

.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
}

.action-icon {
  font-size: 44rpx;
  color: #666666;

  &.active {
    color: #E53935;
  }
}

.action-text {
  font-size: 20rpx;
  color: #666666;
}

.footer-right {
  flex: 1;
  display: flex;
  gap: 16rpx;
  justify-content: flex-end;
  margin-left: 32rpx;
}

.add-cart-btn,
.buy-btn {
  height: 80rpx;
  padding: 0 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 40rpx;
  font-size: 28rpx;
  font-weight: 500;
}

.add-cart-btn {
  background: linear-gradient(135deg, #F5E6D3 0%, #E5D4B8 100%);
  color: #8B6914;
}

.buy-btn {
  background: linear-gradient(135deg, #C9A86C 0%, #B8956A 100%);
  color: #FFFFFF;
}
</style>
