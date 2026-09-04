<template>
  <view class="category-container">
    <view class="category-search">
      <SearchBar v-model="searchKeyword" placeholder="搜索商品" @search="onSearch" />
    </view>

    <view class="category-content">
      <scroll-view class="category-left" scroll-y>
        <view class="category-list">
          <view
            v-for="cat in categories"
            :key="cat.id"
            class="category-item"
            :class="{ active: currentCategory?.id === cat.id }"
            @tap="selectCategory(cat)"
          >
            <view class="category-item-inner">
              <text class="category-name">{{ cat.name }}</text>
            </view>
          </view>
        </view>
      </scroll-view>

      <scroll-view class="category-right" scroll-y @scrolltolower="loadMore">
        <view class="category-header" v-if="currentCategory">
          <text class="header-title">{{ currentCategory.name }}</text>
        </view>

        <view class="product-grid" v-if="products.length">
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

        <LoadingState v-if="loading" text="加载中..." />
        <EmptyState v-else-if="!products.length && !loading" text="该分类暂无商品" />
        <view v-else-if="noMore && products.length" class="no-more">— 没有更多了 —</view>
      </scroll-view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import SearchBar from '@/components/common/SearchBar.vue'
import ProductCard from '@/components/common/ProductCard.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { getCategoryList, getProductList } from '@/api/product'
import { useAppStore } from '@/stores/app'
import type { Category, Product } from '@/types'

const searchKeyword = ref('')
const categories = ref<Category[]>([])
const currentCategory = ref<Category | null>(null)
const products = ref<Product[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = 10
const noMore = ref(false)

onMounted(async () => {
  await loadCategories()
})

onShow(() => {
  if (!categories.value.length) return
  const store = useAppStore()
  const targetId = store.preselectedCategoryId
  if (!targetId) return
  const target = categories.value.find(c => c.id === targetId)
  if (target) {
    selectCategory(target)
    store.selectCategory(null)
  }
})

onPullDownRefresh(async () => {
  try {
    await loadCategories()
  } finally {
    uni.stopPullDownRefresh()
  }
})

const loadCategories = async () => {
  try {
    const data = await getCategoryList()
    categories.value = data || []
    if (categories.value.length) {
      const store = useAppStore()
      const targetId = store.preselectedCategoryId
      const target = targetId ? categories.value.find(c => c.id === targetId) : null
      selectCategory(target || categories.value[0])
      store.selectCategory(null)
    }
  } catch (e) {
    console.error('Failed to load categories:', e)
  }
}

const selectCategory = async (cat: Category) => {
  currentCategory.value = cat
  page.value = 1
  noMore.value = false
  products.value = []
  await loadProducts()
}

const loadProducts = async () => {
  if (loading.value || noMore.value) return
  loading.value = true
  try {
    const data = await getProductList({
      categoryId: currentCategory.value?.id,
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
  uni.navigateTo({ url: `/pages/search/index?keyword=${keyword}` })
}

const goProductDetail = (id: number | string) => {
  uni.navigateTo({ url: `/pages/product/detail?id=${id}` })
}
</script>

<style lang="scss" scoped>
.category-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #F5F5F5;
  overflow: hidden;
}

.category-search {
  flex-shrink: 0;
  padding: 16rpx 24rpx;
  background: #FFFFFF;
  border-bottom: 1rpx solid #EEEEEE;
}

.category-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.category-left {
  width: 180rpx;
  min-width: 180rpx;
  height: 100%;
  background: #FFFFFF;
  border-right: 1rpx solid #EEEEEE;
}

.category-list {
  padding: 16rpx 0;
}

.category-item {
  padding: 0 16rpx;
  margin: 0 12rpx 8rpx;
  border-radius: 12rpx;
  transition: all 0.2s ease;

  &.active {
    background: linear-gradient(135deg, rgba(201, 168, 108, 0.12) 0%, rgba(201, 168, 108, 0.06) 100%);

    .category-item-inner {
      border-left: 6rpx solid #C9A86C;
      padding-left: 16rpx;
    }

    .category-name {
      color: #C9A86C;
      font-weight: 600;
    }
  }
}

.category-item-inner {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24rpx 0;
  border-left: 6rpx solid transparent;
  transition: all 0.2s ease;
}

.category-name {
  font-size: 28rpx;
  color: #666666;
  line-height: 1;
  text-align: center;
}

.category-right {
  flex: 1;
  height: 100%;
  padding: 24rpx;
  background: #F5F5F5;
}

.category-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 180rpx;
  margin-bottom: 24rpx;
  border-radius: 16rpx;
  background: linear-gradient(135deg, #C9A86C 0%, #B8956A 100%);
  box-shadow: 0 4rpx 16rpx rgba(201, 168, 108, 0.3);
}

.header-title {
  font-size: 36rpx;
  font-weight: 600;
  color: #FFFFFF;
  letter-spacing: 2rpx;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20rpx;
  width: 100%;
  box-sizing: border-box;
}

.no-more {
  text-align: center;
  padding: 32rpx;
  font-size: 24rpx;
  color: #999999;
}
</style>
