import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface FavoriteItem {
  id: number
  name: string
  image: string
  minPrice: number
  originalPrice?: number
  addTime: number
}

export interface FootprintItem {
  id: number
  name: string
  image: string
  price: number
  viewTime: number
}

const FAVORITES_KEY = 'mall_favorites'
const FOOTPRINT_KEY = 'mall_footprint'

export const useAppStore = defineStore('app', () => {
  const preselectedCategoryId = ref<number | null>(null)
  const favorites = ref<FavoriteItem[]>(uni.getStorageSync(FAVORITES_KEY) || [])
  const footprint = ref<FootprintItem[]>(uni.getStorageSync(FOOTPRINT_KEY) || [])

  const favoriteCount = computed(() => favorites.value.length)
  const footprintCount = computed(() => footprint.value.length)

  const isFavorite = (id: number) => favorites.value.some(item => item.id === id)

  const addFavorite = (item: Omit<FavoriteItem, 'addTime'>) => {
    if (!isFavorite(item.id)) {
      favorites.value.unshift({
        ...item,
        addTime: Date.now()
      })
      saveFavorites()
    }
  }

  const removeFavorite = (id: number) => {
    const index = favorites.value.findIndex(item => item.id === id)
    if (index > -1) {
      favorites.value.splice(index, 1)
      saveFavorites()
    }
  }

  const toggleFavorite = (item: Omit<FavoriteItem, 'addTime'>) => {
    if (isFavorite(item.id)) {
      removeFavorite(item.id)
    } else {
      addFavorite(item)
    }
  }

  let _saveFavoritesTimer: ReturnType<typeof setTimeout> | null = null
  const saveFavorites = () => {
    if (_saveFavoritesTimer) clearTimeout(_saveFavoritesTimer)
    _saveFavoritesTimer = setTimeout(() => {
      uni.setStorageSync(FAVORITES_KEY, favorites.value)
    }, 300)
  }

  const addFootprint = (item: Omit<FootprintItem, 'viewTime'>) => {
    const existing = footprint.value.findIndex(f => f.id === item.id)
    if (existing > -1) {
      footprint.value.splice(existing, 1)
    }
    footprint.value.unshift({
      ...item,
      viewTime: Date.now()
    })
    if (footprint.value.length > 100) {
      footprint.value = footprint.value.slice(0, 100)
    }
    saveFootprint()
  }

  const clearFootprint = () => {
    footprint.value = []
    saveFootprint()
  }

  const removeFootprint = (id: number) => {
    const index = footprint.value.findIndex(f => f.id === id)
    if (index > -1) {
      footprint.value.splice(index, 1)
      saveFootprint()
    }
  }

  let _saveFootprintTimer: ReturnType<typeof setTimeout> | null = null
  const saveFootprint = () => {
    if (_saveFootprintTimer) clearTimeout(_saveFootprintTimer)
    _saveFootprintTimer = setTimeout(() => {
      uni.setStorageSync(FOOTPRINT_KEY, footprint.value)
    }, 300)
  }

  const selectCategory = (id: number | null) => {
    preselectedCategoryId.value = id
  }

  return {
    preselectedCategoryId,
    selectCategory,
    favorites,
    footprint,
    favoriteCount,
    footprintCount,
    isFavorite,
    addFavorite,
    removeFavorite,
    toggleFavorite,
    addFootprint,
    clearFootprint,
    removeFootprint
  }
})