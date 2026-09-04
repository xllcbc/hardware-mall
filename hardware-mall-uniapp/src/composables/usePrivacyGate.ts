import { ref } from 'vue'

/**
 * 隐私合规闸门
 *
 * 两层合规机制:
 * 1. 首次启动: 未在本地存储 PRIVACY_AGREED 时, 注入页面弹出全屏隐私弹窗, 用户主动同意后放行
 * 2. 微信原生: 注册 wx.onNeedPrivacyAuthorization, 当用户触发隐私 API(相册/摄像头等)而尚未
 *    同意「用户隐私保护指引」时, 微信会暂停该 API 并唤起同一弹窗, 同意后 resolve 放行
 *    (需配合 manifest.json 的 "__usePrivacyCheck__": true)
 *
 * 注意: uni-app 小程序端 App.vue 模板不渲染, 需在各入口页面(tabBar 4 页 + 登录页)调用 usePrivacyGate()
 */

const STORAGE_KEY = 'PRIVACY_AGREED'

// 防抖: 每笔申请/检查互不影响, 同一会话只注册一次微信原生回调
let wxPrivacyHandlerRegistered = false
let pendingWxResolve: ((result: { event: string; buttonId?: string }) => void) | null = null

export function usePrivacyGate() {
  const showPrivacy = ref(false)

  const onAgree = () => {
    uni.setStorageSync(STORAGE_KEY, true)
    // #ifdef MP-WEIXIN
    if (pendingWxResolve) {
      pendingWxResolve({ event: 'agree' })
      pendingWxResolve = null
    }
    // #endif
  }

  // #ifdef MP-WEIXIN
  const registerWxPrivacyHandler = () => {
    if (wxPrivacyHandlerRegistered) return
    wxPrivacyHandlerRegistered = true
    if (typeof wx !== 'undefined' && wx.onNeedPrivacyAuthorization) {
      wx.onNeedPrivacyAuthorization((resolve) => {
        // 微信暂停隐私 API, 等待用户在弹窗中做出选择
        pendingWxResolve = resolve
        if (!uni.getStorageSync(STORAGE_KEY)) {
          showPrivacy.value = true
        } else {
          resolve({ event: 'agree' })
        }
      })
    }
  }
  registerWxPrivacyHandler()
  // #endif

  const check = () => {
    if (!uni.getStorageSync(STORAGE_KEY)) {
      showPrivacy.value = true
    }
  }

  return { showPrivacy, onAgree, check }
}
