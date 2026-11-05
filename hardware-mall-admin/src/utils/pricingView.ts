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
  const minText = formatCurrency(min)
  if (max == null || Number(max) === Number(min)) return minText
  return `${minText} - ${formatCurrency(max)}`
}

export function formatRate(rate?: number | null, missing = false): string {
  if (missing) return '未配置 / 零售兜底'
  if (rate == null) return '-'
  return Number(rate).toFixed(4)
}

export function resolveUnitPrice(
  retailPrice?: number | null,
  rate?: number | null
): number | null {
  if (retailPrice == null || rate == null) return null
  const retail = toDecimal(retailPrice)
  const multiplier = toDecimal(rate)
  if (!retail || !multiplier) return null

  const coefficient = retail.coefficient * multiplier.coefficient
  const scale = retail.scale + multiplier.scale
  const sign = coefficient < 0n ? -1n : 1n
  const absolute = coefficient < 0n ? -coefficient : coefficient
  const roundedCents = scale <= 2
    ? absolute * 10n ** BigInt(2 - scale)
    : (absolute + 10n ** BigInt(scale - 2) / 2n) / 10n ** BigInt(scale - 2)
  const cents = roundedCents * sign
  return Number(cents) / 100
}

export function formatCurrency(value?: number | null): string {
  if (value == null) return '-'
  return `¥${Number(value).toFixed(2)}`
}

function toDecimal(value: number): { coefficient: bigint; scale: number } | null {
  if (!Number.isFinite(value)) return null

  const match = value.toString().toLowerCase().match(/^(-?)(\d+)(?:\.(\d+))?(?:e([+-]?\d+))?$/)
  if (!match) return null

  const [, sign, integer, fraction = '', exponentText = '0'] = match
  const exponent = Number(exponentText)
  let digits = `${integer}${fraction}`.replace(/^0+(?=\d)/, '') || '0'
  let scale = fraction.length - exponent
  if (scale < 0) {
    digits += '0'.repeat(-scale)
    scale = 0
  }

  return {
    coefficient: BigInt(`${sign}${digits}`),
    scale
  }
}
