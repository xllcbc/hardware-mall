// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import OrderListPage from './list.vue'
import { getOrderList } from '@/api/order'
import type { Order } from '@/types'

const { showHandlers } = vi.hoisted(() => ({ showHandlers: [] as Array<() => void> }))

vi.mock('@dcloudio/uni-app', () => ({
  onShow: (cb: () => void) => { showHandlers.push(cb) }
}))

vi.mock('@/api/order', () => ({
  getOrderList: vi.fn(),
  cancelOrder: vi.fn(),
  confirmReceiveVerify: vi.fn(),
  deleteOrder: vi.fn(),
  applyRefund: vi.fn()
}))

const uniMock = {
  getStorageSync: vi.fn(() => ''),
  setStorageSync: vi.fn(),
  showToast: vi.fn(),
  showModal: vi.fn(),
  navigateTo: vi.fn(),
  setClipboardData: vi.fn(),
  stopPullDownRefresh: vi.fn()
}

const OSS_IMAGE = 'https://java0251014.oss-cn-beijing.aliyuncs.com/products/order-list.jpg'

const order = (): Order => ({
  id: 1,
  orderNo: 'NO202611240001',
  status: 2,
  statusText: '待发货',
  totalAmount: 20,
  freightAmount: 0,
  payAmount: 20,
  createTime: '2026-11-24T10:00:00',
  items: [
    {
      id: 11,
      orderId: 1,
      skuId: 101,
      spuId: 1001,
      productName: '螺丝刀',
      productImage: OSS_IMAGE,
      price: 20,
      quantity: 1,
      subtotal: 20
    }
  ]
})

describe('order list image display', () => {
  beforeEach(() => {
    showHandlers.length = 0
    vi.clearAllMocks()
    vi.stubGlobal('uni', uniMock)
    vi.mocked(getOrderList).mockResolvedValue({
      records: [order()],
      total: 1,
      current: 1,
      size: 10
    })
  })

  it('renders order item images as 200px thumbnails', async () => {
    const wrapper = mount(OrderListPage, {
      global: { stubs: { LoadingState: true, EmptyState: true } }
    })

    showHandlers.forEach(h => h())
    await flushPromises()

    expect(wrapper.find('.product-image').attributes('src')).toBe(
      `${OSS_IMAGE}?x-oss-process=image/resize,w_200/format,webp/quality,q_80`
    )
  })
})
