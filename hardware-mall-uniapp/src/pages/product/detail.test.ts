// @vitest-environment jsdom

import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DetailPage from './detail.vue'
import { getProductDetail } from '@/api/product'
import { addToCart as addToCartApi } from '@/api/cart'
import { useAppStore } from '@/stores/app'
import type { ProductDetail } from '@/types'

vi.mock('@/api/product', () => ({
  getProductDetail: vi.fn()
}))

vi.mock('@/api/cart', () => ({
  addToCart: vi.fn()
}))

vi.mock('@/utils/auth', () => ({
  requireLogin: vi.fn().mockResolvedValue(true)
}))

const { shareHandlers } = vi.hoisted(() => ({ shareHandlers: [] as Array<() => any> }))

vi.mock('@dcloudio/uni-app', () => ({
  onShareAppMessage: (cb: () => any) => { shareHandlers.push(cb) }
}))

const OSS_IMAGE = 'https://java0251014.oss-cn-beijing.aliyuncs.com/products/detail.jpg'

const uniMock = {
  getStorageSync: vi.fn(() => ''),
  setStorageSync: vi.fn(),
  removeStorageSync: vi.fn(),
  showToast: vi.fn(),
  switchTab: vi.fn(),
  navigateTo: vi.fn(),
  navigateBack: vi.fn(),
  showShareMenu: vi.fn()
}

const unsellableDetail = (): ProductDetail => ({
  spu: {
    id: 7,
    categoryId: 3,
    name: '缺口商品',
    images: [],
    originalPrice: null,
    status: 1,
    isRecommend: 0
  },
  skus: [],
  specTemplates: [],
  specItemsMap: {},
  minPrice: null,
  maxPrice: null,
  sellable: false
})

const sellableDetail = (): ProductDetail => ({
  spu: {
    id: 8,
    categoryId: 3,
    name: '正常商品',
    images: [],
    originalPrice: 15,
    status: 1,
    isRecommend: 0
  },
  skus: [],
  specTemplates: [],
  specItemsMap: {},
  minPrice: 12.5,
  maxPrice: 12.5,
  sellable: true
})

const mountPage = () =>
  mount(DetailPage, {
    global: {
      stubs: { LoadingState: true, CountStepper: true }
    }
  })

describe('product detail unsellable handling', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    shareHandlers.length = 0
    vi.stubGlobal('uni', uniMock)
    vi.stubGlobal('getCurrentPages', () => [{ options: { id: '7' } }])
    uniMock.getStorageSync.mockReturnValue('')
  })

  it('shows 暂不可售 without any price for a gap product', async () => {
    vi.mocked(getProductDetail).mockResolvedValue(unsellableDetail())
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('暂不可售')
    expect(wrapper.text()).not.toContain('¥')
    expect(wrapper.text()).not.toContain('0.00')
  })

  it('records the footprint without a price for a gap product', async () => {
    vi.mocked(getProductDetail).mockResolvedValue(unsellableDetail())
    mountPage()
    await flushPromises()

    const appStore = useAppStore()
    expect(appStore.footprint[0]?.price ?? null).toBeNull()
  })

  it('blocks add-to-cart and buy-now with a toast', async () => {
    vi.mocked(getProductDetail).mockResolvedValue(unsellableDetail())
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('.add-cart-btn').trigger('tap')
    expect(uniMock.showToast).toHaveBeenCalledWith(
      expect.objectContaining({ title: '商品暂不可售' })
    )
    expect(addToCartApi).not.toHaveBeenCalled()

    await wrapper.find('.buy-btn').trigger('tap')
    expect(uniMock.navigateTo).not.toHaveBeenCalled()
  })

  it('still shows the final sale price for a normal product', async () => {
    vi.mocked(getProductDetail).mockResolvedValue(sellableDetail())
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('12.50')
    expect(wrapper.text()).not.toContain('暂不可售')

    const appStore = useAppStore()
    expect(appStore.footprint[0]?.price).toBe(12.5)
  })

  it('uses 750px thumbnails in the carousel but shares the original image', async () => {
    const detail = sellableDetail()
    detail.spu.images = [OSS_IMAGE]
    vi.mocked(getProductDetail).mockResolvedValue(detail)

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.product-image').attributes('src')).toBe(
      `${OSS_IMAGE}?x-oss-process=image/resize,w_750/format,webp/quality,q_80`
    )

    expect(shareHandlers).toHaveLength(1)
    expect(shareHandlers[0]().imageUrl).toBe(OSS_IMAGE)
  })
})
