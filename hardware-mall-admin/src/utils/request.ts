import axios from 'axios'
import type { AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

const http = axios.create({
  baseURL: '/api',
  timeout: 10000
})

export interface RequestConfig extends AxiosRequestConfig {
  skipAuthRefresh?: boolean
}

interface RetryConfig extends InternalAxiosRequestConfig {
  skipAuthRefresh?: boolean
  _retry?: boolean
}

http.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()
    if (authStore.token) {
      config.headers.Authorization = `Bearer ${authStore.token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

let refreshing: Promise<string | null> | null = null
let logoutPromptShown = false

async function refreshTokenOnce(): Promise<string | null> {
  const authStore = useAuthStore()
  if (!authStore.token) return null
  if (!refreshing) {
    refreshing = authStore.refreshToken().finally(() => {
      refreshing = null
    })
  }
  return refreshing
}

function forceLogout(error: unknown): Promise<never> {
  const authStore = useAuthStore()
  authStore.clearAuth()
  if (!logoutPromptShown) {
    logoutPromptShown = true
    ElMessage.error('登录已过期，请重新登录')
    router.push('/login')
  }
  return Promise.reject(error)
}

http.interceptors.response.use(
  (response) => {
    logoutPromptShown = false
    const res = response.data
    const config = response.config as RetryConfig
    if (res.code !== 200) {
      if (res.code === 401 && !config.skipAuthRefresh) {
        return handle401(config)
          .then((data) => Promise.resolve(data))
          .catch(() => Promise.reject(new Error(res.message || '登录已过期')))
      }
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res.data
  },
  (error) => {
    const config = error.config as RetryConfig
    if (error.response?.status === 401) {
      if (config?.skipAuthRefresh) {
        return Promise.reject(error)
      }
      if (config?._retry) {
        return forceLogout(error)
      }
      return handle401(config).catch(() => forceLogout(error))
    }
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

async function handle401(originalConfig: RetryConfig): Promise<any> {
  const newToken = await refreshTokenOnce()
  if (!newToken) {
    return forceLogout(new Error('refresh failed'))
  }
  originalConfig._retry = true
  originalConfig.headers = originalConfig.headers || {}
  ;(originalConfig.headers as any).Authorization = `Bearer ${newToken}`
  return http(originalConfig)
}

const request = {
  get: <T = any>(url: string, config?: RequestConfig) =>
    http.get(url, config) as unknown as Promise<T>,
  post: <T = any>(url: string, data?: any, config?: RequestConfig) =>
    http.post(url, data, config) as unknown as Promise<T>,
  put: <T = any>(url: string, data?: any, config?: RequestConfig) =>
    http.put(url, data, config) as unknown as Promise<T>,
  delete: <T = any>(url: string, config?: RequestConfig) =>
    http.delete(url, config) as unknown as Promise<T>,
}

export default request
