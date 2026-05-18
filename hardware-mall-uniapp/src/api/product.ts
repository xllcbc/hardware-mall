import request from '@/utils/request'
import type { Category, ProductListVO, ProductDetail, Sku } from '@/types'

interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export const getCategoryList = () => {
  return request.get<Category[]>('/user/category/list')
}

export const getProductList = (params: {
  categoryId?: number
  keyword?: string
  page?: number
  limit?: number
}) => {
  return request.get<PageResult<ProductListVO>>('/user/product/list', params)
}

export const getRecommendProducts = () => {
  return request.get<ProductListVO[]>('/user/product/recommend')
}

export const getProductDetail = (id: number) => {
  return request.get<ProductDetail>(`/user/product/${id}`)
}

export const getProductSkus = (id: number) => {
  return request.get<Sku[]>(`/user/product/${id}/skus`)
}
