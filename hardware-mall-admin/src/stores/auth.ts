import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { refresh as refreshApi } from '@/api/admin/auth'

export interface AdminUserInfo {
  id: number
  username: string
  role: number
}

const TOKEN_KEY = 'token'
const USER_INFO_KEY = 'userInfo'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const userInfo = ref<AdminUserInfo | null>(
    (() => {
      const raw = localStorage.getItem(USER_INFO_KEY)
      if (!raw) return null
      try { return JSON.parse(raw) as AdminUserInfo } catch { return null }
    })()
  )

  const isLoggedIn = computed(() => !!token.value)

  function setAuth(newToken: string, info: AdminUserInfo) {
    token.value = newToken
    userInfo.value = info
    localStorage.setItem(TOKEN_KEY, newToken)
    localStorage.setItem(USER_INFO_KEY, JSON.stringify(info))
  }

  function clearAuth() {
    token.value = null
    userInfo.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_INFO_KEY)
  }

  async function refreshToken(): Promise<string | null> {
    if (!token.value) return null
    try {
      const newToken = await refreshApi()
      token.value = newToken
      localStorage.setItem(TOKEN_KEY, newToken)
      return newToken
    } catch (e) {
      clearAuth()
      return null
    }
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    setAuth,
    clearAuth,
    refreshToken
  }
})

export function setupAuthStorageSync(router: { push: (path: string) => void }) {
  window.addEventListener('storage', (e) => {
    if (e.key === TOKEN_KEY && !e.newValue) {
      const store = useAuthStore()
      if (store.isLoggedIn) {
        store.clearAuth()
        router.push('/login')
      }
    }
  })
}
