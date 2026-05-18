import request from '@/utils/request'
import type { Address } from '@/types'

export const getAddressList = () => {
  return request.get<Address[]>('/user/address/list')
}

export const getAddressDetail = (id: number) => {
  return request.get<Address>(`/user/address/${id}`)
}

export const createAddress = (data: Partial<Address>) => {
  return request.post<Address>('/user/address', data)
}

export const updateAddress = (id: number, data: Partial<Address>) => {
  return request.put<Address>(`/user/address/${id}`, data)
}

export const deleteAddress = (id: number) => {
  return request.del<void>(`/user/address/${id}`)
}

export const setDefaultAddress = (id: number) => {
  return request.put(`/user/address/${id}/default`)
}
