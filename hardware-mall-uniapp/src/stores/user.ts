import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo } from '@/types'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(uni.getStorageSync('token') || '')
  const savedUserInfo = uni.getStorageSync('userInfo')
const userInfo = ref<UserInfo | null>(savedUserInfo ? JSON.parse(savedUserInfo) : null)
  
  const isLoggedIn = computed(() => !!token.value)

  const loginResult = ref<{ token: string; userInfo: UserInfo } | null>(null)

  function setLoginResult(t: string, info: UserInfo) {
    setToken(t)
    setUserInfo(info)
    loginResult.value = { token: t, userInfo: info }
  }

  function consumeLoginResult() {
    const result = loginResult.value
    loginResult.value = null
    return result
  }

  function setToken(newToken: string) {
    token.value = newToken
    uni.setStorageSync('token', newToken)
  }
  
  function setUserInfo(info: UserInfo) {
    userInfo.value = info
    uni.setStorageSync('userInfo', JSON.stringify(info))
  }
  
  function logout() {
    token.value = ''
    userInfo.value = null
    uni.removeStorageSync('token')
    uni.removeStorageSync('userInfo')
  }
  
  return {
    token,
    userInfo,
    isLoggedIn,
    loginResult,
    setLoginResult,
    consumeLoginResult,
    setToken,
    setUserInfo,
    logout
  }
})
