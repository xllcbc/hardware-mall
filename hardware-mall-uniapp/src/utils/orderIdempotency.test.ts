import { describe, expect, it } from 'vitest'
import type { CreateOrderData } from '@/api/order'
import {
  buildOrderPayloadFingerprint,
  shouldRefreshIdempotencyKey
} from './orderIdempotency'

const payload = (overrides: Partial<CreateOrderData> = {}): CreateOrderData => ({
  items: [
    { skuId: 9301, quantity: 1 },
    { skuId: 9201, quantity: 2 }
  ],
  addressId: 1,
  logisticsId: 2,
  expectedPayAmount: 40,
  buyerRemark: '尽快发货',
  ...overrides
})

describe('order payload fingerprint', () => {
  it('is stable regardless of item order and amount formatting', () => {
    const fingerprint = buildOrderPayloadFingerprint(payload())
    const reordered = buildOrderPayloadFingerprint(payload({
      items: [
        { skuId: 9201, quantity: 2 },
        { skuId: 9301, quantity: 1 }
      ],
      expectedPayAmount: 40.0
    }))

    expect(reordered).toBe(fingerprint)
  })

  it('changes when any fingerprinted field changes', () => {
    const fingerprint = buildOrderPayloadFingerprint(payload())
    const changed = [
      buildOrderPayloadFingerprint(payload({ items: [{ skuId: 9301, quantity: 2 }] })),
      buildOrderPayloadFingerprint(payload({ addressId: 9 })),
      buildOrderPayloadFingerprint(payload({ logisticsId: 9 })),
      buildOrderPayloadFingerprint(payload({ buyerRemark: '放门口' })),
      buildOrderPayloadFingerprint(payload({ buyerRemark: '' })),
      buildOrderPayloadFingerprint(payload({ expectedPayAmount: 41 }))
    ]

    changed.forEach(candidate => expect(candidate).not.toBe(fingerprint))
  })

  it('treats a missing remark as empty', () => {
    expect(buildOrderPayloadFingerprint(payload({ buyerRemark: undefined })))
      .toBe(buildOrderPayloadFingerprint(payload({ buyerRemark: '' })))
  })
})

describe('idempotency key refresh decision', () => {
  it('requests a new key when none is held', () => {
    expect(shouldRefreshIdempotencyKey({ key: '', fingerprint: '' }, 'fp')).toBe(true)
  })

  it('keeps the key for the same payload and refreshes for a new payload', () => {
    expect(shouldRefreshIdempotencyKey({ key: 'key-1', fingerprint: 'fp' }, 'fp')).toBe(false)
    expect(shouldRefreshIdempotencyKey({ key: 'key-1', fingerprint: 'fp' }, 'other')).toBe(true)
  })
})
