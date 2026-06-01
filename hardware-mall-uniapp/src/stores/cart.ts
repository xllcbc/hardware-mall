import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { CartItem } from '@/types'
import { updateCartQuantity, removeFromCart, clearCart as clearCartApi } from '@/api/cart'

export const useCartStore = defineStore('cart', () => {
  const items = ref<CartItem[]>([])

  const total = computed(() =>
    items.value.reduce((sum, item) => sum + item.subtotal, 0)
  )

  const count = computed(() => items.value.length)

  const selectedItems = computed(() =>
    items.value.filter(item => item.selected)
  )

  const selectedTotal = computed(() =>
    selectedItems.value.reduce((sum, item) => sum + item.subtotal, 0)
  )

  function addItem(item: CartItem) {
    const existIndex = items.value.findIndex(i => i.skuId === item.skuId)
    if (existIndex > -1) {
      items.value[existIndex].quantity += item.quantity
      items.value[existIndex].subtotal = items.value[existIndex].price * items.value[existIndex].quantity
    } else {
      items.value.push({ ...item, selected: true })
    }
  }

  async function updateQuantity(skuId: number, quantity: number) {
    const item = items.value.find(i => i.skuId === skuId)
    if (item) {
      item.quantity = quantity
      item.subtotal = item.price * quantity
      try {
        await updateCartQuantity(item.cartId!, quantity)
      } catch (e) {
        console.error('更新购物车数量失败:', e)
      }
    }
  }

  async function removeItem(skuId: number) {
    const index = items.value.findIndex(i => i.skuId === skuId)
    if (index > -1) {
      const item = items.value[index]
      items.value.splice(index, 1)
      try {
        await removeFromCart(item.cartId!)
      } catch (e) {
        console.error('删除购物车商品失败:', e)
      }
    }
  }

  async function clearSelected() {
    const selectedIds = items.value.filter(item => item.selected).map(i => i.cartId!)
    try {
      for (const cartId of selectedIds) {
        await removeFromCart(cartId)
      }
    } catch (e) {
      console.error('清空购物车失败:', e)
      throw e
    }
  }

  function clearCart() {
    items.value = []
  }

  function setItems(newItems: CartItem[]) {
    items.value = newItems.map(item => ({ ...item, selected: true }))
  }

  return {
    items,
    total,
    count,
    selectedItems,
    selectedTotal,
    addItem,
    updateQuantity,
    removeItem,
    clearSelected,
    clearCart,
    setItems
  }
})
