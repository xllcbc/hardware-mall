// @vitest-environment jsdom

import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CartPage from './index.vue'
import { getCartList, removeFromCart } from '@/api/cart'
import { useCartStore } from '@/stores/cart'
import { requireLogin } from '@/utils/auth'
import type { CartItem } from '@/types'

vi.mock('@dcloudio/uni-app', () => ({
  onShow: (callback: () => void) => Promise.resolve().then(callback),
  onPullDownRefresh: vi.fn()
}))

vi.mock('@/api/cart', () => ({
  getCartList: vi.fn(),
  updateCartQuantity: vi.fn(),
  removeFromCart: vi.fn(),
  clearCart: vi.fn()
}))

vi.mock('@/utils/auth', () => ({
  requireLogin: vi.fn().mockResolvedValue(true)
}))

const uniMock = {
  getStorageSync: vi.fn((key: string) => key === 'token' ? 'test-token' : ''),
  setStorageSync: vi.fn(),
  removeStorageSync: vi.fn(),
  showModal: vi.fn(),
  showToast: vi.fn(),
  navigateTo: vi.fn(),
  switchTab: vi.fn(),
  stopPullDownRefresh: vi.fn()
}

const normalItem = (): CartItem => ({
  cartId: 1,
  skuId: 9301,
  productId: 9201,
  productName: '正常商品',
  price: 20,
  quantity: 2,
  subtotal: 40,
  stock: 10,
  sellable: true
})

const gapItem = (): CartItem => ({
  cartId: 2,
  skuId: 9302,
  productId: 9202,
  productName: '缺口商品',
  price: null,
  quantity: 1,
  subtotal: null,
  stock: 5,
  sellable: false
})

const OSS_IMAGE = 'https://java0251014.oss-cn-beijing.aliyuncs.com/products/cart.jpg'

const mountCart = () =>
  mount(CartPage, {
    global: {
      stubs: { EmptyState: true, CountStepper: true }
    }
  })

describe('cart page unsellable rows', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    vi.stubGlobal('uni', uniMock)
    vi.mocked(requireLogin).mockResolvedValue(true)
  })

  it('shows 暂不可售 and keeps a gap row unselectable', async () => {
    vi.mocked(getCartList).mockResolvedValue([normalItem(), gapItem()])

    const wrapper = mountCart()
    await flushPromises()

    expect(wrapper.text()).toContain('暂不可售')

    const gapRow = wrapper.findAll('.cart-item')
      .find(row => row.text().includes('缺口商品'))
    expect(gapRow).toBeDefined()
    expect(gapRow!.find('.checkbox').classes()).not.toContain('checked')

    await gapRow!.find('.item-checkbox').trigger('tap')

    expect(gapRow!.find('.checkbox').classes()).not.toContain('checked')
    const cartStore = useCartStore()
    expect(cartStore.selectedItems.map(item => item.skuId)).toEqual([9301])
  })

  it('excludes a gap row from the selected total', async () => {
    vi.mocked(getCartList).mockResolvedValue([normalItem(), gapItem()])

    const wrapper = mountCart()
    await flushPromises()

    expect(wrapper.find('.total-price .price-value').text()).toBe('40.00')
    expect(useCartStore().selectedTotal).toBe(40)
  })

  it('blocks checkout when only a gap row remains', async () => {
    vi.mocked(getCartList).mockResolvedValue([gapItem()])

    const wrapper = mountCart()
    await flushPromises()

    expect(wrapper.find('.total-price .price-value').text()).toBe('0.00')
    const checkoutButton = wrapper.find('.checkout-btn')
    expect(checkoutButton.classes()).toContain('disabled')

    await checkoutButton.trigger('tap')
    await flushPromises()

    expect(requireLogin).not.toHaveBeenCalled()
    expect(uniMock.navigateTo).not.toHaveBeenCalled()
  })

  it('still lets the user select and delete a gap row in manage mode', async () => {
    vi.mocked(getCartList).mockResolvedValue([gapItem()])

    const wrapper = mountCart()
    await flushPromises()

    await wrapper.find('.manage-btn').trigger('tap')
    const gapRow = wrapper.find('.cart-item')
    await gapRow.find('.item-checkbox').trigger('tap')
    expect(gapRow.find('.checkbox').classes()).toContain('checked')

    await wrapper.find('.delete-btn').trigger('tap')
    expect(uniMock.showModal).toHaveBeenCalled()
    const modal = uniMock.showModal.mock.calls[0][0] as {
      success: (res: { confirm: boolean; cancel: boolean }) => void
    }
    modal.success({ confirm: true, cancel: false })
    await flushPromises()

    expect(removeFromCart).toHaveBeenCalledWith(2)
  })

  it('clears a gap selection when leaving manage mode', async () => {
    vi.mocked(getCartList).mockResolvedValue([gapItem()])

    const wrapper = mountCart()
    await flushPromises()

    await wrapper.find('.manage-btn').trigger('tap')
    const gapRow = wrapper.find('.cart-item')
    await gapRow.find('.item-checkbox').trigger('tap')
    expect(gapRow.find('.checkbox').classes()).toContain('checked')

    await wrapper.find('.manage-btn').trigger('tap')
    expect(gapRow.find('.checkbox').classes()).not.toContain('checked')
  })

  it('renders cart images as 200px thumbnails and keeps the placeholder intact', async () => {
    const withImage = normalItem()
    withImage.productImage = OSS_IMAGE
    vi.mocked(getCartList).mockResolvedValue([withImage, gapItem()])

    const wrapper = mountCart()
    await flushPromises()

    const images = wrapper.findAll('.item-image')
    expect(images[0].attributes('src')).toBe(
      `${OSS_IMAGE}?x-oss-process=image/resize,w_200/format,webp/quality,q_80`
    )
    expect(images[1].attributes('src')).toBe('/static/images/default.png')
  })
})
