export const DISCOUNT_PARTICIPATION = {
  ENABLED: 0,
  DISABLED: 1,
} as const

export const DISCOUNT_PARTICIPATION_TEXT = {
  [DISCOUNT_PARTICIPATION.ENABLED]: '参与折扣',
  [DISCOUNT_PARTICIPATION.DISABLED]: '不参与折扣',
} as const

export const DISCOUNT_PARTICIPATION_TAG_TYPE = {
  [DISCOUNT_PARTICIPATION.ENABLED]: 'success',
  [DISCOUNT_PARTICIPATION.DISABLED]: 'info',
} as const

export const isDiscountExcluded = (value?: number | null) =>
  value === DISCOUNT_PARTICIPATION.DISABLED

const resolveDiscountParticipation = (value?: number | null) =>
  isDiscountExcluded(value)
    ? DISCOUNT_PARTICIPATION.DISABLED
    : DISCOUNT_PARTICIPATION.ENABLED

export const getExcludeDiscountText = (value?: number | null) =>
  DISCOUNT_PARTICIPATION_TEXT[resolveDiscountParticipation(value)]

export const getExcludeDiscountTagType = (value?: number | null) =>
  DISCOUNT_PARTICIPATION_TAG_TYPE[resolveDiscountParticipation(value)]
