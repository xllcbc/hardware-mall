import { computed } from 'vue'
import { useUserStore } from '@/stores/user'

export const USER_GROUP_WHOLESALE = 2

export function useIsWholesaleUser() {
  const userStore = useUserStore()
  return computed(() => userStore.userInfo?.userGroup === USER_GROUP_WHOLESALE)
}
