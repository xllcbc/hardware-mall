/**
 * 登录守卫（模块级单例状态）
 *
 * 审核合规要求: 登录必须可取消/可拒绝, 不得强制跳转登录页, 不得反复弹窗。
 * 所有"需要登录"的入口统一走 requireLogin —— 弹可取消提示, 用户确认后才跳登录页。
 */

const LOGIN_PAGE = 'pages/login/index'

let prompting = false

const hasToken = () => !!uni.getStorageSync('token')

const currentRoute = () => {
  const pages = getCurrentPages()
  if (!pages.length) return ''
  return (pages[pages.length - 1] as any).route || ''
}

export const isOnLoginPage = () => currentRoute() === LOGIN_PAGE

/** 跳转登录页(已在登录页时不重复跳转) */
export const goLogin = () => {
  if (isOnLoginPage()) return
  uni.navigateTo({ url: '/pages/login/index' })
}

/**
 * 需要登录时才继续: 未登录时弹可取消提示, 确认后跳登录页。
 * @returns 当前已登录 → true; 未登录(无论取消, 还是确认去登录) → false
 *          (确认时已跳转登录页, 调用方直接 return 即可)
 */
export const requireLogin = (message = '该功能需要登录后使用'): Promise<boolean> => {
  if (hasToken()) return Promise.resolve(true)
  if (prompting || isOnLoginPage()) return Promise.resolve(false)

  prompting = true
  return new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '提示',
      content: message,
      confirmText: '去登录',
      cancelText: '取消',
      success: (res) => {
        if (res.confirm) {
          goLogin()
        }
        resolve(false)
      },
      fail: () => resolve(false),
      complete: () => {
        prompting = false
      }
    })
  })
}
