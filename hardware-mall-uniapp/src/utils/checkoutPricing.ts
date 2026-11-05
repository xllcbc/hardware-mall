import type { CheckoutPreview } from '@/types'

type MoneyValue = number | string

export interface PriceChangeDecision {
  preview: CheckoutPreview
  retrySubmit: boolean
  preservePurchaseData: true
}

export const sameMoney = (left: MoneyValue, right: MoneyValue) => {
  const leftCents = Math.round(Number(left) * 100)
  const rightCents = Math.round(Number(right) * 100)
  return Number.isFinite(leftCents)
    && Number.isFinite(rightCents)
    && leftCents === rightCents
}

export const shouldBlockPayment = (orderPayAmount: MoneyValue, confirmedPayAmount: MoneyValue) => {
  return !sameMoney(orderPayAmount, confirmedPayAmount)
}

export const decidePriceChange = (
  confirmed: boolean,
  latestPreview: CheckoutPreview
): PriceChangeDecision => ({
  preview: latestPreview,
  retrySubmit: confirmed,
  preservePurchaseData: true
})
