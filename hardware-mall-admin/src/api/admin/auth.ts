import request from '@/utils/request'

export interface LoginData {
  username: string
  password: string
}

export interface LoginResult {
  token: string
  userInfo: {
    id: number
    username: string
    role: number
  }
}

export const login = (data: LoginData) => {
  return request.post<LoginResult>('/admin/login', data)
}

export const refresh = () => {
  return request.post<string>('/admin/refresh')
}

export const logout = () => {
  return request.post<void>('/admin/logout')
}
