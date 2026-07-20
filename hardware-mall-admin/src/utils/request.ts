import axios from 'axios'
import type { AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

const http = axios.create({
  baseURL: '/api',
  timeout: 10000
})

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

http.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      if (res.code === 401) {
        return handle401(response.config as InternalAxiosRequestConfig)
          .then((data) => Promise.resolve(data))
          .catch(() => Promise.reject(new Error(res.message || '登录已过期')))
      }
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res.data
  },
  (error) => {
    if (error.response?.status === 401) {
      return handle401(error.config)
        .catch(() => {
          ElMessage.error('登录已过期，请重新登录')
          return Promise.reject(error)
        })
    }
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

async function handle401(originalConfig: InternalAxiosRequestConfig): Promise<any> {
  const authStore = useAuthStore()
  if (!authStore.token) {
    authStore.clearAuth()
    router.push('/login')
    throw new Error('no token')
  }
  if (!refreshing) {
    refreshing = authStore.refreshToken()
  }
  const newToken = await refreshing
  refreshing = null
  if (!newToken) {
    authStore.clearAuth()
    router.push('/login')
    throw new Error('refresh failed')
  }
  originalConfig.headers = originalConfig.headers || {}
  ;(originalConfig.headers as any).Authorization = `Bearer ${newToken}`
  return http(originalConfig)
}

const request = {
  get: <T = any>(url: string, config?: AxiosRequestConfig) =>
    http.get(url, config) as unknown as Promise<T>,
  post: <T = any>(url: string, data?: any, config?: AxiosRequestConfig) =>
    http.post(url, data, config) as unknown as Promise<T>,
  put: <T = any>(url: string, data?: any, config?: AxiosRequestConfig) =>
    http.put(url, data, config) as unknown as Promise<T>,
  delete: <T = any>(url: string, config?: AxiosRequestConfig) =>
    http.delete(url, config) as unknown as Promise<T>,
}

export default request
