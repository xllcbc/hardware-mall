import request from '@/utils/request'
import type { CartItem } from '@/types'

export const getCartList = () => {
  return request.get<CartItem[]>('/user/cart/list')
}

export const addToCart = (skuId: number, quantity: number) => {
  return request.post('/user/cart/add', { skuId, quantity })
}

export const updateCartQuantity = (cartId: number, quantity: number) => {
  return request.put(`/user/cart/${cartId}/quantity`, { quantity })
}

export const removeFromCart = (cartId: number) => {
  return request.del(`/user/cart/${cartId}`)
}

export const clearCart = () => {
  return request.del('/user/cart/clear')
}
