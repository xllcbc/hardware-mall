import request from '@/utils/request'

export interface UserLevel {
  id: number
  name: string
  isRetail: number
  sortOrder: number
  status: number
  userCount?: number
}

export interface LevelRate {
  categoryId: number
  categoryName: string
  rate?: number
  missing: boolean
}

export const getUserLevelList = () => {
  return request.get<UserLevel[]>('/admin/user-level/list')
}

export const createUserLevel = (data: { name: string; sortOrder?: number }) => {
  return request.post<UserLevel>('/admin/user-level', data)
}

export const updateUserLevelInfo = (id: number, data: { name?: string; sortOrder?: number }) => {
  return request.put<UserLevel>(`/admin/user-level/${id}`, data)
}

export const updateUserLevelStatus = (id: number, status: number) => {
  return request.put(`/admin/user-level/${id}/status`, { status })
}

export const deleteUserLevel = (id: number) => {
  return request.delete(`/admin/user-level/${id}`)
}

export const getUserLevelRates = (id: number) => {
  return request.get<LevelRate[]>(`/admin/user-level/${id}/rates`)
}

export const saveUserLevelRates = (
  id: number,
  items: Array<{ categoryId: number; rate: number }>
) => {
  return request.put(`/admin/user-level/${id}/rates`, { items })
}

export const fillUserLevelRates = (id: number, rate: number) => {
  return request.post(`/admin/user-level/${id}/rates/fill`, { rate })
}

export const copyUserLevelRates = (id: number, sourceLevelId: number) => {
  return request.post(`/admin/user-level/${id}/rates/copy`, { sourceLevelId })
}
