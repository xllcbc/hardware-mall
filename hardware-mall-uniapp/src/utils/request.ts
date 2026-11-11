import { requireLogin } from './auth'

export const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: any
  header?: Record<string, string>
}

interface ResponseData<T = any> {
  code: number
  message: string
  data: T
}

export const API_CODE_PRICE_CHANGED = 40901
export const API_CODE_IDEMPOTENCY_CONFLICT = 40902

const CLIENT_HANDLED_CODES = new Set([API_CODE_PRICE_CHANGED, API_CODE_IDEMPOTENCY_CONFLICT])

export class ApiError extends Error {
  code: number
  data: any

  constructor(code: number, message: string, data?: any) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.data = data
  }
}

async function request<T = any>(options: RequestOptions): Promise<T> {
  const { url, method = 'GET', data, header = {} } = options

  const token = uni.getStorageSync('token')
  if (token) {
    header['Authorization'] = `Bearer ${token}`
  }

  header['Content-Type'] = header['Content-Type'] || 'application/json'

  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + url,
      method,
      data,
      header,
      success: (res) => {
        const response = res.data as ResponseData<T>
        console.log('请求响应:', { url, code: response.code, message: response.message, data: response.data })
        if (response.code === 200) {
          resolve(response.data)
        } else if (response.code === 401) {
          uni.removeStorageSync('token')
          uni.removeStorageSync('userInfo')

          const pages = getCurrentPages()
          if (pages.length > 0) {
            const page = pages[pages.length - 1] as any
            const route = page.route || ''
            const options = page.$page?.options || page.options || {}
            const query = Object.keys(options).length > 0
              ? '?' + Object.entries(options).map(([k, v]) =>
                  `${k}=${encodeURIComponent(String(v))}`
                ).join('&')
              : ''
            if (route) {
              uni.setStorageSync('LOGIN_REDIRECT', query ? route + query : route)
            }
          }

          // 审核合规: 不强制跳转登录页, 弹可取消提示, 用户确认后才去登录
          requireLogin(response.message || '登录已过期，请重新登录')
          reject(new ApiError(response.code, response.message, response.data))
        } else if (CLIENT_HANDLED_CODES.has(response.code)) {
          // 由调用方按业务场景处理, 不重复弹出通用 toast
          reject(new ApiError(response.code, response.message, response.data))
        } else {
          uni.showToast({ title: response.message || '请求失败', icon: 'none' })
          console.error('请求失败:', { url, code: response.code, message: response.message })
          reject(new ApiError(response.code, response.message, response.data))
        }
      },
      fail: (err) => {
        uni.showToast({ title: '网络错误', icon: 'none' })
        reject(err)
      }
    })
  })
}

export const get = <T = any>(url: string, params?: any) =>
  request<T>({ url, method: 'GET', data: params })

export const post = <T = any>(url: string, data?: any, header?: Record<string, string>) =>
  request<T>({ url, method: 'POST', data, header })

export const put = <T = any>(url: string, data?: any) =>
  request<T>({ url, method: 'PUT', data })

export const del = <T = any>(url: string, data?: any) =>
  request<T>({ url, method: 'DELETE', data })

export default { get, post, put, del }
