// @vitest-environment jsdom

import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CheckoutPage from './index.vue'
import { getAddressList } from '@/api/address'
import { getLogisticsList } from '@/api/logistics'
import {
  createOrder,
  getCheckoutPreview,
  getIdempotencyToken
} from '@/api/order'
import { prepayOrder } from '@/api/pay'
import { useCartStore } from '@/stores/cart'
import { usePreOrderStore } from '@/stores/preOrder'
import { ApiError, API_CODE_IDEMPOTENCY_CONFLICT, API_CODE_PRICE_CHANGED } from '@/utils/request'
import type { CartItem, CheckoutPreview, Order } from '@/types'

vi.mock('@dcloudio/uni-app', () => ({
  onShow: (callback: () => void) => callback()
}))

vi.mock('@/api/address', () => ({
  getAddressList: vi.fn()
}))

vi.mock('@/api/logistics', () => ({
  getLogisticsList: vi.fn()
}))

vi.mock('@/api/order', () => ({
  createOrder: vi.fn(),
  getCheckoutPreview: vi.fn(),
  getOrderDetail: vi.fn(),
  getIdempotencyToken: vi.fn()
}))

vi.mock('@/api/pay', () => ({
  prepayOrder: vi.fn()
}))

vi.mock('@/api/cart', () => ({
  updateCartQuantity: vi.fn(),
  removeFromCart: vi.fn(),
  clearCart: vi.fn()
}))

vi.mock('@/utils/auth', () => ({
  requireLogin: vi.fn().mockResolvedValue(true)
}))

const preview = (payAmount: number): CheckoutPreview => ({
  items: [{
    skuId: 9301,
    productId: 9201,
    productName: '价格链路测试商品',
    spec: '',
    price: payAmount,
    quantity: 1,
    subtotal: payAmount
  }],
  goodsAmount: payAmount,
  freightAmount: 0,
  payAmount
})

const cartItem = (): CartItem => ({
  cartId: 1,
  skuId: 9301,
  productId: 9201,
  productName: '价格链路测试商品',
  price: 40,
  quantity: 1,
  subtotal: 40,
  stock: 10,
  selected: true
})

const order = (payAmount: number): Order => ({
  id: 100,
  orderNo: 'SO-PRICE-GUARD',
  status: 1,
  statusText: '待付款',
  totalAmount: payAmount,
  freightAmount: 0,
  payAmount,
  createTime: '2026-11-04 12:00:00',
  items: []
})

const uniMock = {
  getStorageSync: vi.fn(() => ''),
  setStorageSync: vi.fn(),
  removeStorageSync: vi.fn(),
  showModal: vi.fn(),
  showToast: vi.fn(),
  showLoading: vi.fn(),
  hideLoading: vi.fn(),
  redirectTo: vi.fn(),
  navigateTo: vi.fn(),
  requestPayment: vi.fn()
}

describe('checkout page pricing guards', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    vi.stubGlobal('uni', uniMock)
    uniMock.getStorageSync.mockImplementation((key: string) => key === 'token' ? 'test-token' : '')
    vi.mocked(getAddressList).mockResolvedValue([{
      id: 1,
      userId: 2,
      consignee: '测试用户',
      phone: '13800000000',
      province: '广东省',
      city: '深圳市',
      district: '南山区',
      detail: '测试路1号',
      isDefault: 1
    }])
    vi.mocked(getLogisticsList).mockResolvedValue([{
      id: 1,
      name: '测试物流',
      code: 'test',
      sortOrder: 1,
      status: 1
    }])
    vi.mocked(getIdempotencyToken).mockResolvedValue('idem-key')
    vi.mocked(getCheckoutPreview).mockResolvedValue(preview(40))
  })

  it('keeps cart and direct-buy data when the user cancels price-change confirmation', async () => {
    const cartStore = useCartStore()
    const preOrderStore = usePreOrderStore()
    cartStore.setItems([cartItem()])
    preOrderStore.setItem({
      skuId: 9301,
      productId: 9201,
      productName: '价格链路测试商品',
      productImage: '',
      spec: '',
      price: 40,
      quantity: 1
    })
    vi.mocked(createOrder).mockRejectedValue(new ApiError(
      API_CODE_PRICE_CHANGED,
      '商品价格已更新',
      { expectedPayAmount: 40, preview: preview(35) }
    ))

    const wrapper = mount(CheckoutPage)
    await flushPromises()
    await wrapper.find('.pay-btn').trigger('tap')
    await flushPromises()

    expect(uniMock.showModal).toHaveBeenCalledWith(expect.objectContaining({
      title: '商品价格已更新'
    }))
    const modal = uniMock.showModal.mock.calls[0][0]
    modal.success({ confirm: false, cancel: true })
    await flushPromises()

    expect(cartStore.items).toHaveLength(1)
    expect(preOrderStore.item?.skuId).toBe(9301)
    expect(wrapper.text()).toContain('35.00')
    expect(prepayOrder).not.toHaveBeenCalled()
  })

  it('does not call prepay when the created order amount differs from the confirmed amount', async () => {
    const preOrderStore = usePreOrderStore()
    preOrderStore.setItem({
      skuId: 9301,
      productId: 9201,
      productName: '价格链路测试商品',
      productImage: '',
      spec: '',
      price: 35,
      quantity: 1
    })
    vi.mocked(getCheckoutPreview).mockResolvedValue(preview(35))
    vi.mocked(createOrder).mockResolvedValue(order(40))

    const wrapper = mount(CheckoutPage)
    await flushPromises()
    await wrapper.find('.pay-btn').trigger('tap')
    await flushPromises()

    expect(prepayOrder).not.toHaveBeenCalled()
    expect(uniMock.showModal).toHaveBeenCalledWith(expect.objectContaining({
      title: '订单金额异常'
    }))
  })

  it('keeps the idempotency key after a failed submit and reuses it for an unchanged retry', async () => {
    const preOrderStore = usePreOrderStore()
    preOrderStore.setItem({
      skuId: 9301,
      productId: 9201,
      productName: '价格链路测试商品',
      productImage: '',
      spec: '',
      price: 40,
      quantity: 1
    })
    vi.mocked(createOrder)
      .mockRejectedValueOnce(new Error('网络中断'))
      .mockRejectedValueOnce(new Error('网络中断'))

    const wrapper = mount(CheckoutPage)
    await flushPromises()
    await wrapper.find('.pay-btn').trigger('tap')
    await flushPromises()
    await wrapper.find('.pay-btn').trigger('tap')
    await flushPromises()

    expect(getIdempotencyToken).toHaveBeenCalledTimes(1)
    expect(createOrder).toHaveBeenCalledTimes(2)
    expect(vi.mocked(createOrder).mock.calls[0][1]).toBe('idem-key')
    expect(vi.mocked(createOrder).mock.calls[1][1]).toBe('idem-key')
  })

  it('fetches a new idempotency key when the payload changes after a failure', async () => {
    const preOrderStore = usePreOrderStore()
    preOrderStore.setItem({
      skuId: 9301,
      productId: 9201,
      productName: '价格链路测试商品',
      productImage: '',
      spec: '',
      price: 40,
      quantity: 1
    })
    vi.mocked(getIdempotencyToken)
      .mockResolvedValueOnce('key-1')
      .mockResolvedValueOnce('key-2')
    vi.mocked(createOrder).mockRejectedValue(new Error('网络中断'))

    const wrapper = mount(CheckoutPage)
    await flushPromises()
    await wrapper.find('.pay-btn').trigger('tap')
    await flushPromises()
    await wrapper.find('.remark-input').setValue('放门口')
    await wrapper.find('.pay-btn').trigger('tap')
    await flushPromises()

    expect(getIdempotencyToken).toHaveBeenCalledTimes(2)
    expect(vi.mocked(createOrder).mock.calls[0][1]).toBe('key-1')
    expect(vi.mocked(createOrder).mock.calls[1][1]).toBe('key-2')
  })

  it('refreshes the idempotency key on conflict and asks the user to retry', async () => {
    const preOrderStore = usePreOrderStore()
    preOrderStore.setItem({
      skuId: 9301,
      productId: 9201,
      productName: '价格链路测试商品',
      productImage: '',
      spec: '',
      price: 40,
      quantity: 1
    })
    vi.mocked(getIdempotencyToken)
      .mockResolvedValueOnce('key-1')
      .mockResolvedValueOnce('key-2')
    vi.mocked(createOrder)
      .mockRejectedValueOnce(new ApiError(
        API_CODE_IDEMPOTENCY_CONFLICT,
        '幂等键已用于其他请求，请刷新后重试'
      ))
      .mockRejectedValueOnce(new Error('网络中断'))

    const wrapper = mount(CheckoutPage)
    await flushPromises()
    await wrapper.find('.pay-btn').trigger('tap')
    await flushPromises()

    expect(getIdempotencyToken).toHaveBeenCalledTimes(2)
    expect(uniMock.showModal).toHaveBeenCalledWith(expect.objectContaining({
      title: '订单信息已变化'
    }))

    await wrapper.find('.pay-btn').trigger('tap')
    await flushPromises()

    expect(getIdempotencyToken).toHaveBeenCalledTimes(2)
    expect(vi.mocked(createOrder).mock.calls[0][1]).toBe('key-1')
    expect(vi.mocked(createOrder).mock.calls[1][1]).toBe('key-2')
  })

  it('clears the idempotency key once the order is created', async () => {
    const cartStore = useCartStore()
    cartStore.setItems([cartItem()])
    vi.mocked(getIdempotencyToken)
      .mockResolvedValueOnce('key-1')
      .mockResolvedValueOnce('key-2')
    vi.mocked(createOrder)
      .mockResolvedValueOnce(order(45))
      .mockRejectedValueOnce(new Error('网络中断'))

    const wrapper = mount(CheckoutPage)
    await flushPromises()
    await wrapper.find('.pay-btn').trigger('tap')
    await flushPromises()

    expect(uniMock.showModal).toHaveBeenCalledWith(expect.objectContaining({
      title: '订单金额异常'
    }))

    await wrapper.find('.pay-btn').trigger('tap')
    await flushPromises()

    expect(getIdempotencyToken).toHaveBeenCalledTimes(2)
    expect(vi.mocked(createOrder).mock.calls[0][1]).toBe('key-1')
    expect(vi.mocked(createOrder).mock.calls[1][1]).toBe('key-2')
  })
})
