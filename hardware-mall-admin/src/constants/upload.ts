import axios from 'axios'

export const IMAGE_UPLOAD_MAX_SIZE_MB = 5
export const IMAGE_UPLOAD_MAX_SIZE_BYTES = IMAGE_UPLOAD_MAX_SIZE_MB * 1024 * 1024
export const IMAGE_UPLOAD_TIMEOUT_MS = 150_000
export const IMAGE_UPLOAD_ACCEPT = 'image/jpeg,image/png,image/gif,image/jpg'

const IMAGE_TYPES = new Set(IMAGE_UPLOAD_ACCEPT.split(','))

export function validateImageFile(file: File): string | null {
  if (!IMAGE_TYPES.has(file.type)) {
    return '只能上传 JPG、PNG、GIF 格式的图片'
  }
  if (file.size > IMAGE_UPLOAD_MAX_SIZE_BYTES) {
    return `图片大小不能超过 ${IMAGE_UPLOAD_MAX_SIZE_MB}MB`
  }
  return null
}

export function getUploadErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
      return '图片上传超时，请压缩图片或检查网络后重试'
    }
    if (error.response?.status === 413) {
      return `图片大小不能超过 ${IMAGE_UPLOAD_MAX_SIZE_MB}MB`
    }
    const responseMessage = (error.response?.data as { message?: unknown } | undefined)?.message
    if (typeof responseMessage === 'string' && responseMessage.trim()) {
      return responseMessage
    }
  }
  if (error instanceof Error && error.message) {
    return error.message
  }
  return '图片上传失败，请稍后重试'
}
