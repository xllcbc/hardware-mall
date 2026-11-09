import { UNSELLABLE_TEXT } from './pricingView'

export interface MatrixRateItem {
  rate?: number | null
}

export interface MatrixStats {
  total: number
  configured: number
  missing: number
}

export const getMatrixStats = (items: MatrixRateItem[]): MatrixStats => {
  const configured = items.filter(item => item.rate != null).length
  return {
    total: items.length,
    configured,
    missing: items.length - configured
  }
}

export const getMatrixCellStatus = (rate?: number | null) =>
  rate == null
    ? { configured: false, label: `未配置，${UNSELLABLE_TEXT}` }
    : { configured: true, label: '已配置' }

export const canApplyCopiedMatrix = (targetStatus: number | undefined, source: MatrixStats) =>
  targetStatus !== 1 || source.missing === 0

export const runAfterImpactConfirmation = async (
  confirmImpact: () => Promise<boolean>,
  write: () => Promise<void>
) => {
  if (!(await confirmImpact())) {
    return false
  }
  await write()
  return true
}
