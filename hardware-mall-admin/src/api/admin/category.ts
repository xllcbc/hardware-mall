import request from '@/utils/request'

export interface Category {
  id?: number
  parentId?: number
  name: string
  icon?: string
  sortOrder?: number
  status?: number
  createTime?: string
  updateTime?: string
  deleteTime?: number
}

export interface CategoryQuery {
  page?: number
  limit?: number
  name?: string
  status?: number
}

export const getCategoryList = (params?: CategoryQuery) => {
  return request.get<any>('/admin/category/list', { params })
}

export const getCategoryById = (id: number) => {
  return request.get<Category>(`/admin/category/${id}`)
}

export const createCategory = (data: Category) => {
  return request.post<Category>('/admin/category', data)
}

export const updateCategory = (id: number, data: Category) => {
  return request.put<Category>(`/admin/category/${id}`, data)
}

export const deleteCategory = (id: number) => {
  return request.delete(`/admin/category/${id}`)
}
