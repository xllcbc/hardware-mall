import { describe, expect, it, vi } from 'vitest'
import {
  canApplyCopiedMatrix,
  getMatrixCellStatus,
  getMatrixStats,
  runAfterImpactConfirmation
} from './pricingImpact'

describe('pricing matrix impact', () => {
  it('distinguishes an explicit 1.0000 from a missing rate', () => {
    expect(getMatrixStats([{ rate: 1 }, { rate: null }])).toEqual({
      total: 2,
      configured: 1,
      missing: 1
    })
    expect(getMatrixCellStatus(1).label).toBe('已配置')
    expect(getMatrixCellStatus(null).label).toBe('未配置，零售兜底')
  })

  it('only allows an incomplete source matrix to be copied to a disabled target', () => {
    const incomplete = getMatrixStats([{ rate: 0.8 }, { rate: null }])
    const complete = getMatrixStats([{ rate: 0.8 }, { rate: 1 }])

    expect(canApplyCopiedMatrix(0, incomplete)).toBe(true)
    expect(canApplyCopiedMatrix(1, incomplete)).toBe(false)
    expect(canApplyCopiedMatrix(1, complete)).toBe(true)
  })

  it('does not run the write when impact confirmation is cancelled', async () => {
    const write = vi.fn(async () => undefined)

    const executed = await runAfterImpactConfirmation(async () => false, write)

    expect(executed).toBe(false)
    expect(write).not.toHaveBeenCalled()
  })
})
