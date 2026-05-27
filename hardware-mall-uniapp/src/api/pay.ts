import request from '@/utils/request'

export const prepayOrder = (orderId: number) => {
  return request.post<any>('/user/pay/prepay', { orderId })
}

export const queryPayStatus = (orderId: number) => {
  return request.get<any>(`/user/pay/query/${orderId}`)
}