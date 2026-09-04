import { ref } from 'vue'

/**
 * 隐私合规闸门（模块级单例状态）
 *
 * 弹窗时机: 仅在登录页弹出 —— 隐私 API(chooseImage/getPhoneNumber) 全部出现在登录流程内,
 * 收集信息前征得明示同意即可合规, 浏览类页面不打扰用户。
 *
 * 两层合规机制:
 * 1. 登录时弹窗: 未同意过(本地无 PRIVACY_AGREED)时, 登录页 checkPrivacyGate() 弹出全屏弹窗
 * 2. 微信原生: 注册 wx.onNeedPrivacyAuthorization, 用户触发隐私 API 而微信侧未登记同意时,
 *    微信暂停该 API 并唤起同一弹窗; 弹窗内使用原生 <button open-type="agreePrivacyAuthorization">
 *    (微信标准做法, 保证授权登记有效), 点击后 resolve 放行被暂停的 API
 *    (需配合 manifest.json 的 "__usePrivacyCheck__": true)
 *
 * 状态必须模块级单例: wx.onNeedPrivacyAuthorization 是全局回调, 无法访问组件实例内部的 ref;
 * 弹窗组件直接读取这里的导出状态, 页面无需在组件间传递状态。
 */

const STORAGE_KEY = 'PRIVACY_AGREED'

/** 隐私弹窗显隐(全局唯一弹窗) */
export const showPrivacy = ref(false)

/** 微信隐私授权流程是否挂起中(挂起时弹窗使用原生同意按钮) */
export const isWxPrivacyPending = ref(false)

let wxPendingResolve: ((result: { event: string }) => void) | null = null
let handlerRegistered = false

/**
 * 页面级检查: 未同意过则弹出隐私弹窗(仅登录页调用)
 */
export function checkPrivacyGate() {
  // #ifdef MP-WEIXIN
  registerWxHandler()
  // #endif
  if (!uni.getStorageSync(STORAGE_KEY)) {
    showPrivacy.value = true
  }
}

/**
 * 原生同意按钮 agreeprivacyauthorization 事件回调:
 * 微信已登记用户同意, 此处 resolve 放行被暂停的隐私 API
 */
export function agreeWxPrivacy() {
  // #ifdef MP-WEIXIN
  if (wxPendingResolve) {
    wxPendingResolve({ event: 'agree' })
    wxPendingResolve = null
  }
  // #endif
  isWxPrivacyPending.value = false
}

function registerWxHandler() {
  if (handlerRegistered) return
  handlerRegistered = true
  if (typeof wx !== 'undefined' && wx.onNeedPrivacyAuthorization) {
    wx.onNeedPrivacyAuthorization((resolve) => {
      // 微信暂停隐私 API, 等待用户在弹窗(原生同意按钮)中做出选择
      wxPendingResolve = resolve
      isWxPrivacyPending.value = true
      showPrivacy.value = true
    })
  }
}
