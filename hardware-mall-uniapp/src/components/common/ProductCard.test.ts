// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProductCard from './ProductCard.vue'

const OSS_IMAGE = 'https://java0251014.oss-cn-beijing.aliyuncs.com/products/card.jpg'

describe('ProductCard pricing display', () => {
  it('shows the final sale price for a sellable product', () => {
    const wrapper = mount(ProductCard, {
      props: { id: 1, name: '螺丝刀', minPrice: 12.5, maxPrice: 12.5, sellable: true }
    })
    expect(wrapper.text()).toContain('12.50')
    expect(wrapper.text()).not.toContain('暂不可售')
  })

  it('shows a price range starting hint when min and max differ', () => {
    const wrapper = mount(ProductCard, {
      props: { id: 1, name: '螺丝刀', minPrice: 12.5, maxPrice: 20, sellable: true }
    })
    expect(wrapper.text()).toContain('12.50起')
  })

  it('shows 暂不可售 without any price when unsellable', () => {
    const wrapper = mount(ProductCard, {
      props: {
        id: 2,
        name: '扳手',
        minPrice: null,
        maxPrice: null,
        originalPrice: null,
        sellable: false
      }
    })
    expect(wrapper.text()).toContain('暂不可售')
    expect(wrapper.text()).not.toContain('¥')
    expect(wrapper.text()).not.toContain('0.00')
  })

  it('fails closed to 暂不可售 when no final price is available, even if sellable', () => {
    const wrapper = mount(ProductCard, {
      props: { id: 3, name: '锤子', minPrice: null, sellable: true }
    })
    expect(wrapper.text()).toContain('暂不可售')
    expect(wrapper.text()).not.toContain('¥')
    expect(wrapper.text()).not.toContain('0.00')
  })
})

describe('ProductCard image display', () => {
  it('renders OSS card images as 400px thumbnails', () => {
    const wrapper = mount(ProductCard, {
      props: { id: 1, name: '螺丝刀', image: OSS_IMAGE, minPrice: 12.5, sellable: true }
    })

    expect(wrapper.find('.product-image').attributes('src')).toBe(
      `${OSS_IMAGE}?x-oss-process=image/resize,w_400/format,webp/quality,q_80`
    )
  })

  it('keeps the local placeholder and non-OSS images unchanged', () => {
    const placeholder = mount(ProductCard, {
      props: { id: 1, name: '螺丝刀', minPrice: 12.5, sellable: true }
    })
    expect(placeholder.find('.product-image').attributes('src')).toBe(
      '/static/images/placeholder.svg'
    )

    const external = mount(ProductCard, {
      props: {
        id: 2,
        name: '扳手',
        image: 'https://example.com/products/a.jpg',
        minPrice: 12.5,
        sellable: true
      }
    })
    expect(external.find('.product-image').attributes('src')).toBe(
      'https://example.com/products/a.jpg'
    )
  })
})
