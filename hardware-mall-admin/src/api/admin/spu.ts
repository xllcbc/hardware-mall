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
  createTime?: string
  updateTime?: string
  deleteTime?: number
}

export interface SpuQuery {
  page?: number
  limit?: number
  categoryId?: number
  keyword?: string
}

export const getSpuList = (params?: SpuQuery) => {
  return request.get<any>('/admin/spu/list', { params })
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
