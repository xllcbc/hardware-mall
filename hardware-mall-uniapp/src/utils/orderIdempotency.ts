import type { CreateOrderData } from '@/api/order'

const normalizeAmount = (amount: number) => {
  const cents = Math.round(amount * 100)
  return Number.isFinite(cents) ? String(cents) : String(amount)
}

/**
 * 覆盖后端请求指纹的同一组字段：商品项（SKU + 数量，按 SKU 排序）、地址、
 * 物流、买家备注、期望支付金额。载荷变化即视为新请求，必须换用新的幂等键。
 */
export const buildOrderPayloadFingerprint = (payload: CreateOrderData) => {
  const items = payload.items
    .map(item => ({ skuId: Number(item.skuId), quantity: Number(item.quantity) }))
    .sort((left, right) => left.skuId - right.skuId || left.quantity - right.quantity)
    .map(item => `${item.skuId}:${item.quantity}`)
    .join(',')

  return [
    'v1',
    `items=${items}`,
    `addressId=${payload.addressId}`,
    `logisticsId=${payload.logisticsId}`,
    `buyerRemark=${payload.buyerRemark ?? ''}`,
    `expectedPayAmount=${normalizeAmount(payload.expectedPayAmount)}`
  ].join('\u001f')
}

export interface IdempotencyKeyState {
  key: string
  fingerprint: string
}

export const shouldRefreshIdempotencyKey = (
  current: IdempotencyKeyState,
  fingerprint: string
) => !current.key || current.fingerprint !== fingerprint
