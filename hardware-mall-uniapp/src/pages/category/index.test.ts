// @vitest-environment jsdom

import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CategoryPage from './index.vue'
import { getCategoryList, getProductList } from '@/api/product'
import type { Category, Product } from '@/types'

const { showHandlers } = vi.hoisted(() => ({ showHandlers: [] as Array<() => void> }))

vi.mock('@dcloudio/uni-app', () => ({
  onShow: (cb: () => void) => { showHandlers.push(cb) },
  onPullDownRefresh: vi.fn()
}))

vi.mock('@/api/product', () => ({
  getCategoryList: vi.fn(),
  getProductList: vi.fn()
}))

const uniMock = {
  getStorageSync: vi.fn(() => ''),
  setStorageSync: vi.fn(),
  removeStorageSync: vi.fn(),
  navigateTo: vi.fn(),
  switchTab: vi.fn(),
  stopPullDownRefresh: vi.fn()
}

const category = (): Category => ({ id: 1, name: '分类1', sortOrder: 1, status: 1 })
const product = (id: number, minPrice: number): Product => ({
  id,
  categoryId: 1,
  name: `商品${id}`,
  minPrice,
  maxPrice: minPrice,
  sellable: true,
  status: 1
})

const pageResult = (list: Product[]) => ({
  records: list,
  total: list.length,
  current: 1,
  size: 10
})

describe('category page refreshes prices on show', () => {
  beforeEach(() => {
    showHandlers.length = 0
    vi.clearAllMocks()
    setActivePinia(createPinia())
    vi.stubGlobal('uni', uniMock)
    vi.mocked(getCategoryList).mockResolvedValue([category()])
  })

  it('re-fetches the current category products when shown again', async () => {
    vi.mocked(getProductList)
      .mockResolvedValueOnce(pageResult([product(1, 10)]))
      .mockResolvedValueOnce(pageResult([product(1, 20)]))

    const wrapper = mount(CategoryPage, {
      global: { stubs: { LoadingState: true, EmptyState: true, SearchBar: true } }
    })
    await flushPromises()

    expect(vi.mocked(getProductList)).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('10.00')

    // 模拟从详情/登录页返回分类页(onShow)
    showHandlers.forEach(h => h())
    await flushPromises()

    expect(vi.mocked(getProductList)).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('20.00')
  })
})
