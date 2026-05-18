<template>
  <view class="list-container">
    <view class="list-header">
      <SearchBar v-model="searchKeyword" placeholder="搜索商品" @search="onSearch" />
    </view>

    <view class="list-toolbar">
      <view class="toolbar-left">
        <text
          v-for="sort in sortOptions"
          :key="sort.value"
          class="sort-item"
          :class="{ active: currentSort === sort.value }"
          @tap="onSort(sort.value)"
        >
          {{ sort.label }}
        </text>
      </view>
      <view class="toolbar-right">
        <text class="view-mode" :class="{ active: viewMode === 'grid' }" @tap="viewMode = 'grid'">▦</text>
        <text class="view-mode" :class="{ active: viewMode === 'list' }" @tap="viewMode = 'list'">☰</text>
      </view>
    </view>

    <scroll-view class="list-content" scroll-y @scrolltolower="loadMore">
      <view v-if="loading && !products.length" class="loading-wrap">
        <LoadingState text="加载中..." />
      </view>
      <EmptyState v-else-if="!products.length" text="暂无商品" />
      <template v-else>
        <view v-if="viewMode === 'grid'" class="product-grid">
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
            :sales-count="product.salesCount"
            @click="goProductDetail"
          />
        </view>
        <view v-else class="product-list">
          <view
            v-for="product in products"
            :key="product.id"
            class="product-list-item"
            @tap="goProductDetail(product.id)"
          >
            <image class="list-item-image" :src="product.images?.[0]" mode="aspectFill" />
            <view class="list-item-info">
              <text class="list-item-name">{{ product.name }}</text>
              <text class="list-item-sub" v-if="product.subtitle">{{ product.subtitle }}</text>
              <view class="list-item-bottom">
                <view class="list-item-price">
                  <text class="price-symbol">¥</text>
                  <text class="price-value">{{ product.minPrice || product.price || product.originalPrice }}</text>
                  <text v-if="product.originalPrice" class="original-price">¥{{ product.originalPrice }}</text>
                </view>
                <text class="list-item-sales">已售{{ product.salesCount || 0 }}</text>
              </view>
            </view>
          </view>
        </view>
      </template>

      <LoadingState v-if="loading && products.length" text="加载中..." />
      <view v-if="noMore && products.length" class="no-more">— 没有更多了 —</view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import SearchBar from '@/components/common/SearchBar.vue'
import ProductCard from '@/components/common/ProductCard.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { getProductList } from '@/api/product'
import type { Product } from '@/types'

const searchKeyword = ref('')
const products = ref<Product[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = 10
const noMore = ref(false)
const currentSort = ref('default')
const viewMode = ref<'grid' | 'list'>('grid')

const sortOptions = [
  { label: '综合', value: 'default' },
  { label: '销量', value: 'sales' },
  { label: '价格', value: 'price_asc' }
]

onMounted(() => {
  const pages = getCurrentPages()
  const currentPage = pages[pages.length - 1] as any
  if (currentPage?.options) {
    const { categoryId, keyword } = currentPage.options
    if (keyword) searchKeyword.value = keyword
  }
  loadProducts()
})

const loadProducts = async () => {
  if (loading.value || noMore.value) return
  loading.value = true
  try {
    const data = await getProductList({
      keyword: searchKeyword.value,
      page: page.value,
      limit: pageSize
    })
    const newProducts = data?.records || []
    if (page.value === 1) {
      products.value = newProducts
    } else {
      products.value.push(...newProducts)
    }
    noMore.value = newProducts.length < pageSize
    page.value++
  } catch (e) {
    console.error('Failed to load products:', e)
  } finally {
    loading.value = false
  }
}

const loadMore = () => {
  if (!noMore.value) {
    loadProducts()
  }
}

const onSearch = (keyword: string) => {
  searchKeyword.value = keyword
  page.value = 1
  noMore.value = false
  products.value = []
  loadProducts()
}

const onSort = (sort: string) => {
  currentSort.value = sort
}

const goProductDetail = (id: number) => {
  uni.navigateTo({ url: `/pages/product/detail?id=${id}` })
}
</script>

<style lang="scss" scoped>
.list-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
}

.list-header {
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--color-bg-card);
  border-bottom: 1rpx solid var(--color-border-light);
}

.list-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--color-bg-card);
  border-bottom: 1rpx solid var(--color-border-light);
}

.toolbar-left {
  display: flex;
  gap: var(--spacing-lg);
}

.sort-item {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  &.active {
    color: var(--color-primary);
    font-weight: 600;
  }
}

.toolbar-right {
  display: flex;
  gap: var(--spacing-sm);
}

.view-mode {
  font-size: 32rpx;
  color: var(--color-text-placeholder);
  &.active {
    color: var(--color-primary);
  }
}

.list-content {
  flex: 1;
  padding: var(--spacing-md);
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--spacing-md);
}

.product-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.product-list-item {
  display: flex;
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.list-item-image {
  width: 200rpx;
  height: 200rpx;
  flex-shrink: 0;
}

.list-item-info {
  flex: 1;
  padding: var(--spacing-sm);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.list-item-name {
  font-size: var(--font-size-md);
  color: var(--color-text-primary);
  lines: 2;
  overflow: hidden;
  text-overflow: ellipsis;
}

.list-item-sub {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin-top: var(--spacing-xs);
}

.list-item-bottom {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}

.list-item-price {
  display: flex;
  align-items: baseline;
  color: var(--color-primary);
  .price-symbol {
    font-size: var(--font-size-sm);
    font-weight: 600;
  }
  .price-value {
    font-size: var(--font-size-lg);
    font-weight: 700;
  }
  .original-price {
    font-size: var(--font-size-xs);
    color: var(--color-text-placeholder);
    text-decoration: line-through;
    margin-left: var(--spacing-xs);
  }
}

.list-item-sales {
  font-size: var(--font-size-xs);
  color: var(--color-text-placeholder);
}

.loading-wrap {
  padding: var(--spacing-xxl);
}

.no-more {
  text-align: center;
  padding: var(--spacing-lg);
  font-size: var(--font-size-sm);
  color: var(--color-text-placeholder);
}
</style>