import request from '@/utils/request'
import {
  getUploadErrorMessage,
  IMAGE_UPLOAD_TIMEOUT_MS
} from '@/constants/upload'

export interface UploadResult {
  url: string
}

const uploadFile = async (url: string, file: File, categoryId?: number) => {
  const formData = new FormData()
  formData.append('file', file)
  if (categoryId != null) {
    formData.append('categoryId', String(categoryId))
  }
  try {
    return await request.post<UploadResult>(url, formData, {
      timeout: IMAGE_UPLOAD_TIMEOUT_MS,
      skipErrorMessage: true
    })
  } catch (error) {
    throw new Error(getUploadErrorMessage(error))
  }
}

export const uploadProductImage = (file: File, categoryId?: number) => {
  return uploadFile('/admin/upload/product', file, categoryId)
}

export const uploadAvatar = (file: File) => {
  return uploadFile('/admin/upload/avatar', file)
}

export const uploadBanner = (file: File) => {
  return uploadFile('/admin/upload/banner', file)
}
