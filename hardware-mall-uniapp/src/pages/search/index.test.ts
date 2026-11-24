// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SearchPage from './index.vue'
import { getProductList } from '@/api/product'
import type { Product } from '@/types'

vi.mock('@/api/product', () => ({
  getProductList: vi.fn()
}))

const uniMock = {
  getStorageSync: vi.fn(() => ''),
  setStorageSync: vi.fn(),
  removeStorageSync: vi.fn(),
  showModal: vi.fn(),
  showToast: vi.fn(),
  navigateTo: vi.fn(),
  navigateBack: vi.fn()
}

const OSS_IMAGE = 'https://java0251014.oss-cn-beijing.aliyuncs.com/products/search.jpg'

const product = (id: number): Product => ({
  id,
  categoryId: 1,
  name: `商品${id}`,
  images: [OSS_IMAGE],
  minPrice: 10,
  maxPrice: 10,
  sellable: true,
  status: 1
})

describe('search result image display', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('uni', uniMock)
    vi.stubGlobal('getCurrentPages', () => [{ options: { keyword: '螺丝刀' } }])
  })

  it('renders search result cards as 400px thumbnails', async () => {
    vi.mocked(getProductList).mockResolvedValue({
      records: [product(1)],
      total: 1,
      current: 1,
      size: 10
    })

    const wrapper = mount(SearchPage, {
      global: { stubs: { LoadingState: true, EmptyState: true } }
    })
    await flushPromises()

    expect(wrapper.find('.product-image').attributes('src')).toBe(
      `${OSS_IMAGE}?x-oss-process=image/resize,w_400/format,webp/quality,q_80`
    )
  })
})
