import { MOCK_ENABLED, MOCK_CATEGORIES, getMockProductImages, MOCK_ADDRESSES, MOCK_CART, MOCK_ORDERS, MOCK_USER_INFO } from './mock'
import { requireLogin } from './auth'

export const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

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

function getMockData<T>(url: string, params?: any): T {
  if (url.includes('/user/category/list')) {
    return MOCK_CATEGORIES as T
  }
  if (url.includes('/user/product/list')) {
    const records = getMockProductImages()
    return { records, total: records.length, current: 1, size: 10 } as T
  }
  if (url.includes('/user/product/recommend')) {
    return getMockProductImages() as T
  }
  if (url.includes('/user/product/')) {
    const id = parseInt(url.split('/user/product/')[1])
    const product = getMockProductImages().find(p => p.id === id)
    return product as T
  }
  if (url.includes('/user/cart/list')) {
    return MOCK_CART as T
  }
  if (url.includes('/user/address/list')) {
    return MOCK_ADDRESSES as T
  }
  if (url.includes('/user/order/list')) {
    return { records: MOCK_ORDERS, total: MOCK_ORDERS.length, current: 1, size: 10 } as T
  }
  if (url.includes('/user/order/preview')) {
    const items = MOCK_CART.map(item => ({
      skuId: item.skuId,
      productId: item.productId,
      productName: item.productName,
      productImage: item.productImage,
      spec: item.spec,
      price: item.price,
      quantity: item.quantity,
      subtotal: item.subtotal
    }))
    const goodsAmount = items.reduce((sum, item) => sum + item.subtotal, 0)
    return {
      items,
      goodsAmount,
      freightAmount: 0,
      payAmount: goodsAmount
    } as T
  }
  if (url.includes('/user/order/')) {
    const id = parseInt(url.split('/user/order/')[1])
    const order = MOCK_ORDERS.find(o => o.id === id)
    return order as T
  }
  if (url.includes('/user/info')) {
    return MOCK_USER_INFO as T
  }
  return {} as T
}

async function request<T = any>(options: RequestOptions): Promise<T> {
  const { url, method = 'GET', data, header = {} } = options

  if (MOCK_ENABLED) {
    await new Promise(resolve => setTimeout(resolve, 300))
    return getMockData<T>(url, data)
  }

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
        } else if (response.code === API_CODE_PRICE_CHANGED) {
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
