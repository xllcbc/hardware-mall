import { describe, expect, it } from 'vitest'
import type { CheckoutPreview } from '@/types'
import { decidePriceChange, shouldBlockPayment } from './checkoutPricing'

const latestPreview: CheckoutPreview = {
  items: [],
  goodsAmount: 35,
  freightAmount: 0,
  payAmount: 35
}

describe('checkout pricing guard', () => {
  it('blocks prepay when order payAmount differs from the confirmed amount', () => {
    expect(shouldBlockPayment(35, 40)).toBe(true)
    expect(shouldBlockPayment(40, 35)).toBe(true)
  })

  it('allows prepay when amounts match at cent precision', () => {
    expect(shouldBlockPayment(40, 40.0)).toBe(false)
    expect(shouldBlockPayment('40.00' as unknown as number, 40)).toBe(false)
  })

  it('keeps purchase data and replaces the preview when price change is cancelled', () => {
    expect(decidePriceChange(false, latestPreview)).toEqual({
      preview: latestPreview,
      retrySubmit: false,
      preservePurchaseData: true
    })
  })

  it('retries only after explicit confirmation and still preserves purchase data', () => {
    expect(decidePriceChange(true, latestPreview)).toEqual({
      preview: latestPreview,
      retrySubmit: true,
      preservePurchaseData: true
    })
  })
})
