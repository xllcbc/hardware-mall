import request from '@/utils/request'
import type { Logistics } from '@/types'

export const getLogisticsList = () => {
  return request.get<Logistics[]>('/user/logistics/list')
}
