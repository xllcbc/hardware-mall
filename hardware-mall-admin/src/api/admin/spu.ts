import request from '@/utils/request'

export interface Spu {
  id?: number
  categoryId: number
  name: string
  subtitle?: string
  description?: string
  images?: string[]
  originalPrice?: number
  weight?: number
  salesCount?: number
  status?: number
  isRecommend?: number
  excludeDiscount?: number
  createTime?: string
  updateTime?: string
  deleteTime?: number
}

export interface SpuQuery {
  page?: number
  limit?: number
  categoryId?: number
  keyword?: string
  status?: number
  levelId?: number
}

export interface AdminProductPricing {
  id: number
  categoryId: number
  name: string
  subtitle?: string
  description?: string
  images?: string[]
  originalPrice?: number
  weight?: number
  retailMinPrice?: number
  retailMaxPrice?: number
  discountRate: number
  effectiveRate: number
  settlementMinPrice?: number
  settlementMaxPrice?: number
  rateMissing: boolean
  excludeDiscount: number
  status: number
  isRecommend: number
  salesCount: number
}

export const getSpuList = (params?: SpuQuery) => {
  return request.get<any>('/admin/spu/list', { params })
}

export const getAdminProductPricingList = (params?: SpuQuery) => {
  return request.get<{ records: AdminProductPricing[]; total: number }>(
    '/admin/spu/pricing-list',
    { params }
  )
}

export const getSpuById = (id: number) => {
  return request.get<Spu>(`/admin/spu/${id}`)
}

export const createSpu = (data: Spu) => {
  return request.post<Spu>('/admin/spu', data)
}

export const updateSpu = (id: number, data: Spu) => {
  return request.put<Spu>(`/admin/spu/${id}`, data)
}

export const deleteSpu = (id: number) => {
  return request.delete(`/admin/spu/${id}`)
}

export const updateSpuStatus = (id: number, status: number) => {
  return request.put(`/admin/spu/${id}/status`, { status })
}
