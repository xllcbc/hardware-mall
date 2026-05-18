<template>
  <view class="index-container">
    <view class="search-bar" @tap="goSearch">
      <text class="search-icon">🔍</text>
      <text class="search-text">搜索商品</text>
    </view>

    <!-- <swiper class="banner" indicator-dots autoplay circular indicator-active-color="#C9A86C">
      <swiper-item v-for="(banner, index) in banners" :key="index" @tap="onBannerClick(banner)">
        <image v-if="banner.image" class="banner-image" :src="banner.image" mode="aspectFill" />
        <view v-else class="banner-slide" :class="`banner-${index}`">
          <text class="banner-title">{{ banner.title }}</text>
        </view>
      </swiper-item>
    </swiper> -->

    <view class="category-grid">
      <view
        v-for="cat in displayCategories"
        :key="cat.id"
        class="category-item"
        :class="{ 'category-more': cat.isMore }"
        @tap="onCategoryClick(cat)"
      >
        <view class="category-icon-wrap">
          <text class="category-icon">{{ cat.isMore ? '→' : cat.name.charAt(0) }}</text>
        </view>
        <text class="category-name">{{ cat.name }}</text>
      </view>
    </view>

    <view class="section">
      <view class="section-header">
        <view class="section-title-wrap">
          <text class="section-title">精品推荐</text>
          <text class="section-subtitle">精选好物 品质保障</text>
        </view>
        <view class="section-more" @tap="goProductList">
          <text>查看更多</text>
          <text class="arrow">›</text>
        </view>
      </view>

      <view v-if="loading" class="loading-wrap">
        <LoadingState text="加载中..." />
      </view>
      <view v-else-if="!products.length" class="empty-wrap">
        <EmptyState text="暂无推荐商品" />
      </view>
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
          :sales-count="product.salesCount"
          @click="() => goProductDetail(product.id)"
        />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import ProductCard from '@/components/common/ProductCard.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { getCategoryList, getRecommendProducts } from '@/api/product'
import { useAppStore } from '@/stores/app'
// import { MOCK_BANNERS } from '@/utils/mock'
import type { Category, Product } from '@/types'

interface CategoryItem extends Category {
  isMore?: boolean
}

// const banners = ref(MOCK_BANNERS)
const categories = ref<Category[]>([])
const products = ref<Product[]>([])
const loading = ref(true)

const displayCategories = computed<CategoryItem[]>(() => {
  const cats = categories.value.slice(0, 7).map(c => ({ ...c, isMore: false }))
  if (categories.value.length > 7) {
    cats.push({ id: -1, name: '查看更多', icon: '', isMore: true })
  }
  return cats
})

onMounted(async () => {
  await Promise.all([
    loadCategories(),
    loadProducts()
  ])
  loading.value = false
})

const loadCategories = async () => {
  try {
    const data = await getCategoryList()
    categories.value = data || []
  } catch (e) {
    console.error('Failed to load categories:', e)
    categories.value = []
  }
}

const loadProducts = async () => {
  try {
    const data = await getRecommendProducts()
    products.value = data || []
  } catch (e) {
    console.error('Failed to load products:', e)
    products.value = []
  }
}

const onCategoryClick = (cat: CategoryItem) => {
  const store = useAppStore()
  if (cat.isMore) {
    store.selectCategory(null)
  } else {
    store.selectCategory(cat.id)
  }
  uni.switchTab({ url: '/pages/category/index' })
}

const goProductList = () => {
  uni.switchTab({ url: '/pages/category/index' })
}

const goSearch = () => {
  uni.navigateTo({ url: '/pages/search/index' })
}

const goProductDetail = (id: number | string) => {
  uni.navigateTo({ url: `/pages/product/detail?id=${id}` })
}

// const onBannerClick = (banner: { url?: string }) => {
//   if (banner.url) {
//     uni.navigateTo({ url: banner.url })
//   }
// }
</script>

<style lang="scss" scoped>
.index-container {
  min-height: 100vh;
  background: #FAFAFA;
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
  overflow-x: hidden;
}

.search-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 80rpx;
  margin: 16rpx 24rpx;
  background: #FFFFFF;
  border-radius: 40rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.06);
}

.search-icon {
  font-size: 28rpx;
  margin-right: 12rpx;
}

.search-text {
  font-size: 28rpx;
  color: #999999;
}

.banner {
  height: 320rpx;
  margin: 24rpx;
  border-radius: 16rpx;
  overflow: hidden;
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.08);
}

.banner-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.banner-slide {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gradient-primary);
}

.banner-0 { background: var(--gradient-primary-dark); }
.banner-1 { background: var(--gradient-accent); }
.banner-2 { background: var(--gradient-light); }

.banner-title {
  font-size: 48rpx;
  font-weight: 700;
  color: #FFFFFF;
  text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.2);
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16rpx;
  padding: 0 24rpx;
  margin-bottom: 32rpx;
}

.category-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;

  &.category-more {
    .category-icon-wrap {
      border: 2rpx dashed #C9A86C;
      background: transparent;
      box-shadow: none;
    }

    .category-name {
      color: #C9A86C;
      font-weight: 600;
    }
  }
}

.category-icon-wrap {
  width: 100rpx;
  height: 100rpx;
  background: #FFFFFF;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.category-icon {
  font-size: 48rpx;
}

.category-name {
  font-size: 24rpx;
  color: #666666;
}

.section {
  padding: 0 24rpx;
  margin-bottom: 32rpx;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 24rpx;
}

.section-title-wrap {
  display: flex;
  flex-direction: column;
}

.section-title {
  font-size: 40rpx;
  font-weight: 600;
  color: #2C2C2C;
  font-family: 'Georgia', serif;
}

.section-subtitle {
  font-size: 20rpx;
  color: #999999;
  margin-top: 4rpx;
}

.section-more {
  display: flex;
  align-items: center;
  font-size: 24rpx;
  color: #666666;

  .arrow {
    font-size: 28rpx;
    margin-left: 4rpx;
  }
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16rpx;
  width: 100%;
  box-sizing: border-box;
}

.loading-wrap,
.empty-wrap {
  padding: 48rpx 0;
}
</style>