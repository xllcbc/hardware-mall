import { describe, expect, it } from 'vitest'
import {
  RETAIL_PRICE_VIEW,
  formatCurrency,
  formatPriceRange,
  formatRate,
  parsePriceView,
  resolveUnitPrice,
  toPriceViewLevelId
} from './pricingView'

describe('pricingView', () => {
  it('defaults invalid route values to retail view', () => {
    expect(parsePriceView(undefined)).toBe(RETAIL_PRICE_VIEW)
    expect(parsePriceView('')).toBe(RETAIL_PRICE_VIEW)
    expect(parsePriceView('0')).toBe(RETAIL_PRICE_VIEW)
    expect(parsePriceView('abc')).toBe(RETAIL_PRICE_VIEW)
  })

  it('parses and serializes enabled level ids', () => {
    expect(parsePriceView(['12'])).toBe(12)
    expect(toPriceViewLevelId(12)).toBe(12)
    expect(toPriceViewLevelId(RETAIL_PRICE_VIEW)).toBeUndefined()
  })

  it('formats ranges and distinguishes configured one from a gap', () => {
    expect(formatPriceRange(10, 20)).toBe('¥10.00 - ¥20.00')
    expect(formatPriceRange(10, 10)).toBe('¥10.00')
    expect(formatPriceRange()).toBe('-')
    expect(formatRate(1)).toBe('1.0000')
    expect(formatRate(1, true)).toBe('未配置 / 零售兜底')
  })

  it('resolves and formats the settlement price with two-decimal rounding', () => {
    expect(resolveUnitPrice(218, 0.7)).toBe(152.6)
    expect(resolveUnitPrice(2.3, 0.95)).toBe(2.19)
    expect(resolveUnitPrice(10.005, 1)).toBe(10.01)
    expect(resolveUnitPrice(10.075, 1)).toBe(10.08)
    expect(resolveUnitPrice(undefined, 0.7)).toBeNull()
    expect(formatCurrency(152.6)).toBe('¥152.60')
    expect(formatCurrency(null)).toBe('-')
  })
})
