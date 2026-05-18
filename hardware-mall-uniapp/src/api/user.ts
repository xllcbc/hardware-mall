import request from '@/utils/request'
import type { UserInfo } from '@/types'

interface LoginResult {
  token: string
  userInfo: UserInfo
}

interface LoginParams {
  code: string
  nickname?: string
  avatarUrl?: string
}

export const login = (params: LoginParams) => {
  return request.post<LoginResult>('/user/login', params)
}

export const getUserInfo = () => {
  return request.get<UserInfo>('/user/info')
}

export const updateUserInfo = (data: Partial<UserInfo>) => {
  return request.put<UserInfo>('/user/info', data)
}
