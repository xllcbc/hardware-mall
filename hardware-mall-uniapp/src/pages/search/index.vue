<template>
  <view class="search-container">
    <view class="search-header">
      <view class="search-input-wrap">
        <icon class="search-icon" type="search" :size="18" color="#999999" />
        <input
          class="search-input"
          v-model="keyword"
          placeholder="搜索商品"
          placeholder-class="input-placeholder"
          @confirm="onSearch(keyword)"
        />
      </view>
      <text class="cancel-btn" @tap="cancel">取消</text>
    </view>

    <view v-if="!loaded" class="skeleton-search-content">
      <view class="skeleton-grid">
        <view class="skeleton-item" v-for="i in 4" :key="i">
          <view class="skeleton-image"></view>
          <view class="skeleton-line"></view>
          <view class="skeleton-line short"></view>
        </view>
      </view>
    </view>

    <template v-if="loaded">
    <scroll-view v-if="!hasSearched" class="search-content" scroll-y>
      <view v-if="historyKeywords.length" class="search-section">
        <view class="section-header">
          <view class="section-title-wrap">
            <text class="section-icon">⏱</text>
            <text class="section-title">搜索历史</text>
          </view>
          <text class="section-action" @tap="clearHistory">清空</text>
        </view>
        <view class="tag-list">
          <text
            v-for="kw in historyKeywords"
            :key="kw"
            class="tag-item"
            @tap="onHistoryClick(kw)"
          >{{ kw }}</text>
        </view>
      </view>

      <view class="divider"></view>

      <view class="search-section">
        <view class="section-header">
          <view class="section-title-wrap">
            <text class="section-icon">🔥</text>
            <text class="section-title">热门搜索</text>
          </view>
        </view>
        <view class="tag-list">
          <text
            v-for="kw in hotKeywords"
            :key="kw"
            class="tag-item"
            @tap="onHistoryClick(kw)"
          >{{ kw }}</text>
        </view>
      </view>
    </scroll-view>

    <scroll-view v-else class="result-content" scroll-y>
      <view v-if="loading" class="loading-wrap">
        <LoadingState text="搜索中..." />
      </view>
      <EmptyState v-else-if="!products.length" text="未找到相关商品" />
      <view v-else class="product-grid">
        <ProductCard
          v-for="product in products"
          :key="product.id"
          :id="product.id"
          :image="product.images?.[0]"
          :name="product.name"
          :subtitle="product.subtitle"
          :price="product.price"
          :original-price="product.originalPrice"
          :min-price="product.minPrice"
          @click="goProductDetail"
        />
      </view>
    </scroll-view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import ProductCard from '@/components/common/ProductCard.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { getProductList } from '@/api/product'
import type { Product } from '@/types'

const HISTORY_KEY = 'mall_search_history'
const loaded = ref(false)
const keyword = ref('')
const hasSearched = ref(false)
const loading = ref(false)
const historyKeywords = ref<string[]>(uni.getStorageSync(HISTORY_KEY) || [])
const hotKeywords = ref(['门锁', '工具箱', '螺丝刀', '水管', '灯具', '胶带', '插座', '开关'])
const products = ref<Product[]>([])

onMounted(() => {
  nextTick(() => {
    loaded.value = true
  })
  const pages = getCurrentPages()
  const currentPage = pages[pages.length - 1] as any
  const kw = currentPage?.options?.keyword
  if (kw) {
    keyword.value = kw
    onSearch(kw)
  }
})

const onSearch = async (kw: string) => {
  if (!kw) return
  keyword.value = kw
  hasSearched.value = true
  saveHistory(kw)
  loading.value = true
  try {
    const data = await getProductList({ keyword: kw })
    products.value = data?.records || []
  } catch (e) {
    console.error('Search failed:', e)
    products.value = []
  } finally {
    loading.value = false
  }
}

const onHistoryClick = (kw: string) => {
  keyword.value = kw
  onSearch(kw)
}

const saveHistory = (kw: string) => {
  const history = historyKeywords.value.filter(h => h !== kw)
  history.unshift(kw)
  historyKeywords.value = history.slice(0, 20)
  uni.setStorageSync(HISTORY_KEY, historyKeywords.value)
}

const clearHistory = () => {
  uni.showModal({
    title: '提示',
    content: '确定清空搜索历史?',
    success: (res) => {
      if (res.confirm) {
        historyKeywords.value = []
        uni.removeStorageSync(HISTORY_KEY)
      }
    }
  })
}

const cancel = () => {
  uni.navigateBack()
}

const goProductDetail = (id: number | string) => {
  uni.navigateTo({ url: `/pages/product/detail?id=${id}` })
}
</script>

<style lang="scss" scoped>
.search-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #F7F8FA;
}

.skeleton-search-content {
  flex: 1;
  padding: 20rpx;
}

.skeleton-grid {
  display: flex;
  flex-wrap: wrap;
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

.search-header {
  display: flex;
  align-items: center;
  padding: 24rpx;
  background: #FFFFFF;
  border-bottom: 1rpx solid #E5E5E5;
}

.search-input-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  height: 72rpx;
  padding: 0 24rpx;
  background: #F7F8FA;
  border-radius: 36rpx;
}

.search-icon {
  margin-right: 12rpx;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  font-size: 28rpx;
  color: #1A1A1A;
}

.input-placeholder {
  color: #999999;
}

.cancel-btn {
  margin-left: 24rpx;
  font-size: 28rpx;
  color: #666666;
  padding: 8rpx;
}

.search-content {
  flex: 1;
  padding: 32rpx 24rpx;
}

.search-section {
  margin-bottom: 32rpx;
  padding: 0 8rpx;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
  overflow: hidden;
}

.section-title-wrap {
  display: flex;
  align-items: center;
  gap: 8rpx;
  flex-shrink: 0;
  max-width: calc(100% - 80rpx);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.section-icon {
  font-size: 28rpx;
}

.section-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #1A1A1A;
}

.section-action {
  font-size: 24rpx;
  color: #999999;
  flex-shrink: 0;
  padding: 4rpx 12rpx;
  min-width: 60rpx;
  text-align: center;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.tag-item {
  padding: 16rpx 28rpx;
  background: #FFFFFF;
  border-radius: 8rpx;
  font-size: 26rpx;
  color: #666666;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.divider {
  height: 1rpx;
  background: #E5E5E5;
  margin-bottom: 32rpx;
}

.result-content {
  flex: 1;
  padding: 24rpx;
  box-sizing: border-box;
}

.loading-wrap {
  padding: 100rpx 0;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16rpx;
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}
</style>