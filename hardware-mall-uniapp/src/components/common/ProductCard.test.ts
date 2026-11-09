// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProductCard from './ProductCard.vue'

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
