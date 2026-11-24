// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import OrderDetailPage from './detail.vue'
import { getOrderDetail } from '@/api/order'
import type { Order } from '@/types'

vi.mock('@dcloudio/uni-app', () => ({
  onPullDownRefresh: vi.fn()
}))

vi.mock('@/api/order', () => ({
  getOrderDetail: vi.fn(),
  cancelOrder: vi.fn(),
  confirmReceiveVerify: vi.fn(),
  deleteOrder: vi.fn(),
  applyRefund: vi.fn()
}))

vi.mock('@/api/pay', () => ({
  prepayOrder: vi.fn()
}))

const uniMock = {
  getStorageSync: vi.fn(() => ''),
  setStorageSync: vi.fn(),
  showToast: vi.fn(),
  showModal: vi.fn(),
  navigateTo: vi.fn(),
  navigateBack: vi.fn(),
  setClipboardData: vi.fn(),
  stopPullDownRefresh: vi.fn(),
  $on: vi.fn(),
  $off: vi.fn()
}

const OSS_IMAGE = 'https://java0251014.oss-cn-beijing.aliyuncs.com/products/order-detail.jpg'

const order = (): Order => ({
  id: 1,
  orderNo: 'NO202611240001',
  status: 2,
  statusText: '待发货',
  totalAmount: 20,
  freightAmount: 0,
  payAmount: 20,
  receiverName: '张三',
  receiverPhone: '13800000000',
  receiverAddress: '上海市某区某路 1 号',
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

describe('order detail image display', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('uni', uniMock)
    vi.stubGlobal('getCurrentPages', () => [{ options: { id: '1' } }])
    vi.mocked(getOrderDetail).mockResolvedValue(order())
  })

  it('renders order item images as 200px thumbnails', async () => {
    const wrapper = mount(OrderDetailPage, {
      global: { stubs: { LoadingState: true } }
    })
    await flushPromises()

    expect(wrapper.find('.item-image').attributes('src')).toBe(
      `${OSS_IMAGE}?x-oss-process=image/resize,w_200/format,webp/quality,q_80`
    )
  })
})
