import request from '@/utils/request'

export interface Logistics {
  id?: number
  name: string
  code: string
  description?: string
  contact?: string
  phone?: string
  phones?: string[]
  city?: string
  address?: string
  sortOrder?: number
  status?: number
  createTime?: string
  updateTime?: string
  deleteTime?: number
}

export interface LogisticsQuery {
  page?: number
  limit?: number
  name?: string
  city?: string
  status?: number
}

export const getLogisticsList = (params?: LogisticsQuery) => {
  return request.get<any>('/admin/logistics/list', { params })
}

export const getLogisticsById = (id: number) => {
  return request.get<Logistics>(`/admin/logistics/${id}`)
}

export const createLogistics = (data: Logistics) => {
  return request.post<Logistics>('/admin/logistics', data)
}

export const updateLogistics = (id: number, data: Logistics) => {
  return request.put<Logistics>(`/admin/logistics/${id}`, data)
}

export const deleteLogistics = (id: number) => {
  return request.delete(`/admin/logistics/${id}`)
}

export const updateLogisticsStatus = (id: number, status: number) => {
  return request.put(`/admin/logistics/${id}/status`, { status })
}
