export interface Category {
  id: number
  name: string
  icon?: string
  sortOrder: number
  status: number
}

export interface SpecVO {
  templateId: number
  itemId: number
  name: string
  value: string
}

export interface Sku {
  id: number
  spuId: number
  specs: SpecVO[]
  price: number
  stock: number
  image?: string
  status: number
}

export interface SpecTemplate {
  id: number
  categoryId: number
  name: string
  specType: number
  isRequired: number
  sortOrder: number
}

export interface SpecItem {
  id: number
  templateId: number
  value: string
  sortOrder: number
}

export interface Spu {
  id: number
  categoryId: number
  name: string
  subtitle?: string
  description?: string
  images?: string[]
  originalPrice?: number
  weight?: number
  salesCount: number
  status: number
  isRecommend: number
}

export interface ProductListVO {
  id: number
  categoryId: number
  name: string
  subtitle?: string
  images?: string[]
  originalPrice?: number
  minPrice?: number
  maxPrice?: number
  salesCount: number
  status: number
}

export type Product = ProductListVO

export interface ProductDetail {
  spu: Spu
  skus: Sku[]
  specTemplates: SpecTemplate[]
  specItemsMap: Record<number, SpecItem[]>
  minPrice?: number
  maxPrice?: number
}

export interface Address {
  id: number
  userId: number
  consignee: string
  phone: string
  province: string
  city: string
  district: string
  detail: string
  postalCode?: string
  isDefault: number
}

export interface CartItem {
  cartId?: number
  skuId: number
  productId: number
  productName: string
  productImage?: string
  spec?: string
  price: number
  minPrice?: number
  maxPrice?: number
  quantity: number
  subtotal: number
  selected?: boolean
}

export interface Logistics {
  id: number
  name: string
  code: string
  description?: string
  sortOrder: number
  status: number
}

export interface OrderItem {
  id: number
  orderId: number
  skuId: number
  spuId: number
  productId: number
  productName: string
  productSpec?: string
  productImage?: string
  price: number
  quantity: number
  subtotal: number
}

export interface Order {
  id: number
  orderNo: string
  status: number
  statusText: string
  totalAmount: number
  freightAmount: number
  payAmount: number
  logisticsName?: string
  logisticsNo?: string
  receiverName?: string
  receiverPhone?: string
  receiverAddress?: string
  buyerRemark?: string
  payTime?: string
  shipTime?: string
  receiveTime?: string
  createTime: string
  items: OrderItem[]
}

export interface UserInfo {
  id: number
  nickname?: string
  avatarUrl?: string
  phone?: string
  role: number
}
