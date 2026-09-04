import request from '@/utils/request'
import type { Order } from '@/types'

interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export interface CreateOrderData {
  items: { skuId: number; quantity: number }[]
  addressId: number
  logisticsId: number
  buyerRemark?: string
}

export const createOrder = (data: CreateOrderData, idemKey: string) => {
  return request.post<Order>('/user/order/create', data, { 'X-Idempotency-Key': idemKey })
}

export const getIdempotencyToken = () => {
  return request.get<string>('/user/order/token')
}

export const getOrderList = (params: {
  status?: number
  page?: number
  limit?: number
}) => {
  return request.get<PageResult<Order>>('/user/order/list', params)
}

export const getOrderDetail = (id: number) => {
  return request.get<Order>(`/user/order/${id}`)
}

export const cancelOrder = (id: number, reason: string) => {
  return request.put(`/user/order/${id}/cancel`, { reason })
}

export const applyRefund = (id: number, reason: string) => {
  return request.put(`/user/order/${id}/apply-refund`, { reason })
}

export const confirmReceive = (id: number) => {
  return request.put(`/user/order/${id}/receive`, {})
}

export const deleteOrder = (id: number) => {
  return request.del(`/user/order/${id}`)
}
