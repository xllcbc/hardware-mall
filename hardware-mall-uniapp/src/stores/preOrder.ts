import { defineStore } from 'pinia'
import { ref } from 'vue'

interface PreOrderItem {
  skuId: number
  productId: number
  productName: string
  productImage: string
  spec: string
  price: number
  quantity: number
}

export const usePreOrderStore = defineStore('preOrder', () => {
  const item = ref<PreOrderItem | null>(null)

  function setItem(data: PreOrderItem) {
    item.value = data
  }

  function clearItem() {
    item.value = null
  }

  return { item, setItem, clearItem }
})