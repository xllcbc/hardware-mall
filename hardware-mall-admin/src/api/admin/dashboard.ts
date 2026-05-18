import request from '@/utils/request'

export interface DashboardStats {
  todayOrders: number
  todaySales: number
  pendingShip: number
  totalProducts: number
  trend?: {
    ordersTrend: number
    salesTrend: number
    shipTrend: number
    productTrend: number
  }
}

export interface RecentOrder {
  id: number
  orderNo: string
  userId: number
  totalAmount: number
  status: number
  statusText: string
  createTime: string
}

export const getDashboardStats = () => {
  return request.get<DashboardStats>('/admin/dashboard/stats')
}

export const getRecentOrders = (limit?: number) => {
  return request.get<RecentOrder[]>('/admin/dashboard/recent-orders', { params: { limit } })
}
