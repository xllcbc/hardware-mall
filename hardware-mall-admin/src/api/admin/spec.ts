import request from '@/utils/request'

export interface SpecTemplate {
  id?: number
  categoryId: number
  name: string
  specType?: number
  isRequired?: number
  sortOrder?: number
  createTime?: string
  updateTime?: string
  deleteTime?: number
}

export interface SpecItem {
  id?: number
  templateId: number
  value: string
  sortOrder?: number
  createTime?: string
  updateTime?: string
  deleteTime?: number
}

export const getSpecTemplateList = (params?: any) => {
  return request.get<any>('/admin/spec/template/list', { params })
}

export const getSpecTemplateById = (id: number) => {
  return request.get<SpecTemplate>(`/admin/spec/template/${id}`)
}

export const getSpecTemplatesByCategory = (categoryId: number) => {
  return request.get<SpecTemplate[]>(`/admin/spec/template/category/${categoryId}`)
}

export const createSpecTemplate = (data: SpecTemplate) => {
  return request.post<SpecTemplate>('/admin/spec/template', data)
}

export const updateSpecTemplate = (id: number, data: SpecTemplate) => {
  return request.put<SpecTemplate>(`/admin/spec/template/${id}`, data)
}

export const deleteSpecTemplate = (id: number) => {
  return request.delete(`/admin/spec/template/${id}`)
}

export const getSpecItemList = (templateId: number) => {
  return request.get<SpecItem[]>(`/admin/spec/item/list`, { params: { templateId } })
}

export const getSpecItemsByCategory = (categoryId: number) => {
  return request.get<SpecItem[]>(`/admin/spec/item/category/${categoryId}`)
}

export const createSpecItem = (data: SpecItem) => {
  return request.post<SpecItem>('/admin/spec/item', data)
}

export const updateSpecItem = (id: number, data: SpecItem) => {
  return request.put<SpecItem>(`/admin/spec/item/${id}`, data)
}

export const deleteSpecItem = (id: number) => {
  return request.delete(`/admin/spec/item/${id}`)
}
