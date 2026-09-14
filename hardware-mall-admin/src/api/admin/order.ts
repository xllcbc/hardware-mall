import request from '@/utils/request'

export interface OrderItem {
  id: number
  orderId: number
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
  userId: number
  receiverName?: string
  receiverPhone?: string
  receiverAddress?: string
  totalAmount: number
  freightAmount: number
  payAmount: number
  status: number
  statusText?: string
  logisticsName?: string
  logisticsNo?: string
  deliveryType?: number
  deliveryTypeText?: string
  payTime?: string
  shipTime?: string
  receiveTime?: string
  cancelTime?: string
  cancelReason?: string
  buyerRemark?: string
  createTime?: string
  items?: OrderItem[]
}

export interface OrderQuery {
  page?: number
  limit?: number
  userId?: number
  status?: number
  orderNo?: string
}

export const getOrderList = (params?: OrderQuery) => {
  return request.get<any>('/admin/order/list', { params })
}

export const getOrderById = (id: number) => {
  return request.get<Order>(`/admin/order/${id}`)
}

export const getOrderStats = () => {
  return request.get<any>('/admin/order/stats')
}

export interface ShipOrderPayload {
  deliveryType: number
  logisticsId?: number
}

export const shipOrder = (id: number, payload: ShipOrderPayload) => {
  return request.put(`/admin/order/${id}/ship`, payload)
}

export const refundOrder = (id: number, reason: string) => {
  return request.put(`/admin/order/${id}/refund`, { reason })
}

export const rejectRefund = (id: number, reason: string) => {
  return request.put(`/admin/order/${id}/reject-refund`, { reason })
}
