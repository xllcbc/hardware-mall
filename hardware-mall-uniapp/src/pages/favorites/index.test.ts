// @vitest-environment jsdom

import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import FavoritesPage from './index.vue'
import { getProductPrices } from '@/api/product'
import { useAppStore } from '@/stores/app'
import type { ProductListVO } from '@/types'

const { showHandlers } = vi.hoisted(() => ({ showHandlers: [] as Array<() => void> }))

vi.mock('@dcloudio/uni-app', () => ({
  onShow: (cb: () => void) => { showHandlers.push(cb) }
}))

vi.mock('@/api/product', () => ({
  getProductPrices: vi.fn()
}))

const uniMock = {
  getStorageSync: vi.fn(() => ''),
  setStorageSync: vi.fn(),
  removeStorageSync: vi.fn(),
  navigateTo: vi.fn(),
  switchTab: vi.fn(),
  showToast: vi.fn(),
  showModal: vi.fn()
}

const freshPrice = (id: number, minPrice: number): ProductListVO => ({
  id,
  categoryId: 1,
  name: `商品${id}`,
  minPrice,
  maxPrice: minPrice,
  sellable: true,
  status: 1
})

describe('favorites page refreshes prices on show', () => {
  beforeEach(() => {
    showHandlers.length = 0
    vi.clearAllMocks()
    setActivePinia(createPinia())
    vi.stubGlobal('uni', uniMock)
  })

  it('re-fetches and updates favorited prices when shown', async () => {
    const appStore = useAppStore()
    appStore.favorites.push({ id: 1, name: '商品1', image: '', minPrice: 10, addTime: 0 })
    vi.mocked(getProductPrices).mockResolvedValue([freshPrice(1, 20)])

    mount(FavoritesPage, { global: { stubs: { EmptyState: true } } })
    await flushPromises()

    // 尚未触发 onShow 时保持旧价
    expect(appStore.favorites[0].minPrice).toBe(10)

    showHandlers.forEach(h => h())
    await flushPromises()

    expect(vi.mocked(getProductPrices)).toHaveBeenCalledWith([1])
    expect(appStore.favorites[0].minPrice).toBe(20)
  })
})
