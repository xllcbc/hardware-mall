import { BASE_URL } from './request'

export const uploadAvatar = (filePath: string): Promise<string> => {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('token')
    uni.uploadFile({
      url: `${BASE_URL}/user/upload/avatar`,
      filePath,
      name: 'file',
      header: {
        Authorization: token ? `Bearer ${token}` : ''
      },
      success: (uploadRes) => {
        const data = JSON.parse(uploadRes.data)
        if (data.code === 200 && data.data && data.data.url) {
          resolve(data.data.url)
        } else {
          reject(new Error(data.message || '头像上传失败'))
        }
      },
      fail: () => {
        reject(new Error('头像上传失败'))
      }
    })
  })
}
