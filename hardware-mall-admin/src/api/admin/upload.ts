import request from '@/utils/request'

export interface UploadResult {
  url: string
}

export const uploadProductImage = (file: File, categoryId?: number) => {
  const formData = new FormData()
  formData.append('file', file)
  if (categoryId != null) {
    formData.append('categoryId', String(categoryId))
  }
  return request.post<UploadResult>('/admin/upload/product', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export const uploadAvatar = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<UploadResult>('/admin/upload/avatar', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export const uploadBanner = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<UploadResult>('/admin/upload/banner', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
