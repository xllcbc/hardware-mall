// @vitest-environment jsdom

import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import IndexPage from './index.vue'
import { getCategoryList, getRecommendProducts } from '@/api/product'
import type { Product } from '@/types'

const { showHandlers } = vi.hoisted(() => ({ showHandlers: [] as Array<() => void> }))

vi.mock('@dcloudio/uni-app', () => ({
  onShow: (cb: () => void) => { showHandlers.push(cb) }
}))

vi.mock('@/api/product', () => ({
  getCategoryList: vi.fn(),
  getRecommendProducts: vi.fn()
}))

const uniMock = {
  getStorageSync: vi.fn(() => ''),
  setStorageSync: vi.fn(),
  removeStorageSync: vi.fn(),
  switchTab: vi.fn(),
  navigateTo: vi.fn()
}

const product = (id: number, minPrice: number): Product => ({
  id,
  categoryId: 1,
  name: `商品${id}`,
  minPrice,
  maxPrice: minPrice,
  sellable: true,
  status: 1
})

const OSS_IMAGE = 'https://java0251014.oss-cn-beijing.aliyuncs.com/products/home.jpg'

describe('index page refreshes prices on show', () => {
  beforeEach(() => {
    showHandlers.length = 0
    vi.clearAllMocks()
    setActivePinia(createPinia())
    vi.stubGlobal('uni', uniMock)
    vi.mocked(getCategoryList).mockResolvedValue([])
  })

  it('re-fetches recommend products when the page is shown again', async () => {
    vi.mocked(getRecommendProducts)
      .mockResolvedValueOnce([product(1, 10)])
      .mockResolvedValueOnce([product(1, 20)])

    const wrapper = mount(IndexPage, {
      global: { stubs: { LoadingState: true, EmptyState: true } }
    })
    await flushPromises()

    expect(vi.mocked(getRecommendProducts)).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('10.00')

    // 模拟从详情/登录页返回首页(onShow)
    showHandlers.forEach(h => h())
    await flushPromises()

    expect(vi.mocked(getRecommendProducts)).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('20.00')
  })

  it('renders recommend cards as 400px thumbnails', async () => {
    const withImage = product(1, 10)
    withImage.images = [OSS_IMAGE]
    vi.mocked(getRecommendProducts).mockResolvedValue([withImage])

    const wrapper = mount(IndexPage, {
      global: { stubs: { LoadingState: true, EmptyState: true } }
    })
    await flushPromises()

    expect(wrapper.find('.product-image').attributes('src')).toBe(
      `${OSS_IMAGE}?x-oss-process=image/resize,w_400/format,webp/quality,q_80`
    )
  })
})
