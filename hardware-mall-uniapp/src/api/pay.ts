import request from '@/utils/request'

export const mockPay = (orderId: number) => {
  return request.post('/user/pay/mock', { orderId })
}