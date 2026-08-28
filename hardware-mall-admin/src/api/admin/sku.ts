import request from '@/utils/request'

export interface Sku {
  id?: number
  spuId: number
  specs: SpecVO[]
  price: number
  stock: number
  image?: string
  status?: number
  createTime?: string
  updateTime?: string
  deleteTime?: number
}

export interface SpecVO {
  templateId: number
  itemId: number
  name: string
  value: string
}

export const getSkuList = (spuId: number, params?: any) => {
  return request.get<any>('/admin/sku/list', { params: { spuId, ...params } })
}

export const getSkuById = (id: number) => {
  return request.get<Sku>(`/admin/sku/${id}`)
}

export const getSkusBySpu = (spuId: number, status?: number) => {
  return request.get<Sku[]>(`/admin/sku/spu/${spuId}`, { params: { status } })
}

export const getSkuCounts = (spuIds: number[]) => {
  return request.get<Record<number, number>>('/admin/sku/counts', {
    params: { spuIds: spuIds.join(',') }
  })
}

export const createSku = (data: Sku) => {
  return request.post<Sku>('/admin/sku', data)
}

export const updateSku = (id: number, data: Sku) => {
  return request.put<Sku>(`/admin/sku/${id}`, data)
}

export const deleteSku = (id: number) => {
  return request.delete(`/admin/sku/${id}`)
}

export const generateSkus = (spuId: number) => {
  return request.post<Sku[]>(`/admin/sku/generate/${spuId}`)
}
