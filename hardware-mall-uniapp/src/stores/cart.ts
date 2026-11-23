import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { CartItem } from '@/types'
import { updateCartQuantity, removeFromCart, clearCart as clearCartApi } from '@/api/cart'

const subtotalOf = (item: CartItem) => item.subtotal ?? 0

export const useCartStore = defineStore('cart', () => {
  const items = ref<CartItem[]>([])

  const total = computed(() =>
    items.value.reduce((sum, item) => sum + subtotalOf(item), 0)
  )

  const count = computed(() => items.value.length)

  const sellableItems = computed(() =>
    items.value.filter(item => item.sellable !== false)
  )

  const selectedItems = computed(() =>
    sellableItems.value.filter(item => item.selected)
  )

  const selectedTotal = computed(() =>
    selectedItems.value.reduce((sum, item) => sum + subtotalOf(item), 0)
  )

  function addItem(item: CartItem) {
    const existIndex = items.value.findIndex(i => i.skuId === item.skuId)
    if (existIndex > -1) {
      const existing = items.value[existIndex]
      existing.quantity += item.quantity
      existing.subtotal = existing.price == null ? null : existing.price * existing.quantity
    } else {
      items.value.push({ ...item, selected: item.sellable !== false })
    }
  }

  async function updateQuantity(skuId: number, quantity: number) {
    const item = items.value.find(i => i.skuId === skuId)
    if (!item) return
    const oldQuantity = item.quantity
    const oldSubtotal = item.subtotal
    item.quantity = quantity
    item.subtotal = item.price == null ? null : item.price * quantity
    try {
      await updateCartQuantity(item.cartId!, quantity)
    } catch (e) {
      item.quantity = oldQuantity
      item.subtotal = oldSubtotal
      uni.showToast({ title: '更新数量失败, 请稍后重试', icon: 'none' })
      console.error('更新购物车数量失败:', e)
    }
  }

  async function removeItem(skuId: number) {
    const index = items.value.findIndex(i => i.skuId === skuId)
    if (index === -1) return
    const item = items.value[index]
    items.value.splice(index, 1)
    try {
      await removeFromCart(item.cartId!)
    } catch (e) {
      items.value.splice(index, 0, item)
      uni.showToast({ title: '删除失败, 请稍后重试', icon: 'none' })
      console.error('删除购物车商品失败:', e)
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
    items.value = newItems.map(item => ({ ...item, selected: item.sellable !== false }))
  }

  return {
    items,
    total,
    count,
    sellableItems,
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
