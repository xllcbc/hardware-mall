import request from '@/utils/request'

export interface User {
  id: number
  openid?: string
  unionid?: string
  nickname?: string
  avatarUrl?: string
  phone?: string
  province?: string
  city?: string
  role: number
  status: number
  lastLoginTime?: string
  lastLoginIp?: string
  createTime?: string
}

export interface UserQuery {
  page?: number
  limit?: number
  province?: string
  city?: string
  status?: number
}

export const getUserList = (params?: UserQuery) => {
  return request.get<any>('/admin/user/list', { params })
}

export const updateUserStatus = (id: number, status: number) => {
  return request.put(`/admin/user/${id}/status`, { status })
}

export const updateUserRegion = (id: number, province: string, city: string) => {
  return request.put(`/admin/user/${id}/region`, { province, city })
}
