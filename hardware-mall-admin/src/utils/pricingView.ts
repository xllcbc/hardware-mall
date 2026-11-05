export const RETAIL_PRICE_VIEW = 'retail' as const

export type PriceView = typeof RETAIL_PRICE_VIEW | number

export function parsePriceView(value: unknown): PriceView {
  const raw = Array.isArray(value) ? value[0] : value
  if (typeof raw !== 'string' || raw.trim() === '') return RETAIL_PRICE_VIEW
  const parsed = Number(raw)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : RETAIL_PRICE_VIEW
}

export function toPriceViewLevelId(view: PriceView): number | undefined {
  return view === RETAIL_PRICE_VIEW ? undefined : view
}

export function formatPriceRange(min?: number | null, max?: number | null): string {
  if (min == null) return '-'
  const minText = `¥${Number(min).toFixed(2)}`
  if (max == null || Number(max) === Number(min)) return minText
  return `${minText} - ¥${Number(max).toFixed(2)}`
}

export function formatRate(rate?: number | null, missing = false): string {
  if (missing) return '未配置 / 零售兜底'
  if (rate == null) return '-'
  return Number(rate).toFixed(4)
}
