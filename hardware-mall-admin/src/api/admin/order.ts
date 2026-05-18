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

export const shipOrder = (id: number, logisticsId: number, logisticsNo: string) => {
  return request.put(`/admin/order/${id}/ship`, { logisticsId, logisticsNo })
}

export const refundOrder = (id: number, reason: string) => {
  return request.put(`/admin/order/${id}/refund`, { reason })
}
