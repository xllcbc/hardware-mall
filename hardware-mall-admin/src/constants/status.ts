/**
 * 商城系统状态常量
 * 集中管理所有状态值，避免魔数散落各处
 */

// ==================== 订单状态 ====================
export const ORDER_STATUS = {
  PENDING_PAYMENT: 1,    // 待付款
  PENDING_SHIPMENT: 2,   // 待发货
  SHIPPED: 3,           // 已发货
  COMPLETED: 4,          // 已完成
  CANCELLED: 5,          // 已取消
  REFUNDING: 6,          // 退款中
  REFUNDED: 7,           // 已退款
  REFUND_REQUESTED: 8,   // 退款申请中(等待管理员审核)
  REFUND_FAILED: 9,      // 退款失败(待管理员处理/重试)
} as const

export const ORDER_STATUS_TEXT: Record<number, string> = {
  [ORDER_STATUS.PENDING_PAYMENT]: '待付款',
  [ORDER_STATUS.PENDING_SHIPMENT]: '待发货',
  [ORDER_STATUS.SHIPPED]: '已发货',
  [ORDER_STATUS.COMPLETED]: '已完成',
  [ORDER_STATUS.CANCELLED]: '已取消',
  [ORDER_STATUS.REFUNDING]: '退款中',
  [ORDER_STATUS.REFUNDED]: '已退款',
  [ORDER_STATUS.REFUND_REQUESTED]: '退款申请中',
  [ORDER_STATUS.REFUND_FAILED]: '退款失败',
}

export const ORDER_STATUS_TYPE: Record<number, string> = {
  [ORDER_STATUS.PENDING_PAYMENT]: 'warning',   // 橙色
  [ORDER_STATUS.PENDING_SHIPMENT]: 'primary',  // 蓝色
  [ORDER_STATUS.SHIPPED]: 'success',           // 绿色
  [ORDER_STATUS.COMPLETED]: 'info',            // 灰色
  [ORDER_STATUS.CANCELLED]: 'info',            // 灰色
  [ORDER_STATUS.REFUNDING]: 'danger',          // 红色
  [ORDER_STATUS.REFUNDED]: 'danger',           // 红色
  [ORDER_STATUS.REFUND_REQUESTED]: 'warning',  // 橙色
  [ORDER_STATUS.REFUND_FAILED]: 'danger',      // 红色
}

// ==================== 发货方式(对齐微信 logistics_type) ====================
export const DELIVERY_TYPE = {
  LOCAL: 2,    // 同城配送
  PICKUP: 4,   // 用户自提
} as const

export const DELIVERY_TYPE_TEXT: Record<number, string> = {
  [DELIVERY_TYPE.LOCAL]: '同城配送',
  [DELIVERY_TYPE.PICKUP]: '用户自提',
}

// ==================== 用户角色 ====================
export const USER_ROLE = {
  REGULAR: 1,  // 普通用户
  ADMIN: 2,    // 管理员
} as const

export const USER_ROLE_TEXT: Record<number, string> = {
  [USER_ROLE.REGULAR]: '普通用户',
  [USER_ROLE.ADMIN]: '管理员',
}

// ==================== 用户状态 ====================
export const USER_STATUS = {
  NORMAL: 1,    // 正常
  DISABLED: 0,  // 禁用
} as const

export const USER_STATUS_TEXT: Record<number, string> = {
  [USER_STATUS.NORMAL]: '正常',
  [USER_STATUS.DISABLED]: '禁用',
}

// ==================== 商品状态 ====================
export const PRODUCT_STATUS = {
  ON_SHELF: 1,    // 上架
  OFF_SHELF: 0,   // 下架
} as const

export const PRODUCT_STATUS_TEXT: Record<number, string> = {
  [PRODUCT_STATUS.ON_SHELF]: '上架',
  [PRODUCT_STATUS.OFF_SHELF]: '下架',
}

// ==================== 分类状态 ====================
export const CATEGORY_STATUS = {
  ENABLED: 1,    // 启用
  DISABLED: 0,   // 禁用
} as const

export const CATEGORY_STATUS_TEXT: Record<number, string> = {
  [CATEGORY_STATUS.ENABLED]: '启用',
  [CATEGORY_STATUS.DISABLED]: '禁用',
}

// ==================== 物流状态 ====================
export const LOGISTICS_STATUS = {
  ENABLED: 1,    // 启用
  DISABLED: 0,   // 禁用
} as const

export const LOGISTICS_STATUS_TEXT: Record<number, string> = {
  [LOGISTICS_STATUS.ENABLED]: '启用',
  [LOGISTICS_STATUS.DISABLED]: '禁用',
}
