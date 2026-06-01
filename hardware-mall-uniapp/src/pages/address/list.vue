<template>
  <view class="address-container">
    <view v-if="!loaded" class="skeleton-address-list">
      <view class="skeleton-card" v-for="i in 3" :key="i">
        <view class="skeleton-contact">
          <view class="skeleton-circle"></view>
          <view class="skeleton-line short"></view>
          <view class="skeleton-line short"></view>
        </view>
        <view class="skeleton-line"></view>
        <view class="skeleton-line"></view>
      </view>
    </view>

    <template v-if="loaded">
    <scroll-view class="address-list" scroll-y>
      <view class="list-header">
        <text class="list-title">我的收货地址</text>
        <text class="manage-btn" @tap="toggleManage">{{ isManaging ? '完成' : '管理' }}</text>
      </view>

      <view v-if="!addresses.length" class="empty-wrap">
        <EmptyState text="暂无收货地址" icon="📍">
          <template #action>
            <view class="add-btn" @tap="addAddress">添加地址</view>
          </template>
        </EmptyState>
      </view>
      <view v-else>
        <view
          class="address-item"
          v-for="addr in addresses"
          :key="addr.id"
          @tap="selectAddress(addr)"
        >
          <view class="address-top">
            <view class="contact-row">
              <text class="contact-icon">👤</text>
              <text class="consignee">{{ addr.consignee }}</text>
              <text class="phone">{{ addr.phone }}</text>
              <view v-if="addr.isDefault" class="default-tag">默认</view>
            </view>
          </view>
          
          <view class="address-divider"></view>
          
          <view class="address-middle">
            <text class="address-icon">📍</text>
            <text class="address-detail">
              {{ addr.province }}{{ addr.city }}{{ addr.district }}{{ addr.detail }}
            </text>
          </view>

          <view class="address-bottom" v-if="isManaging">
            <view class="action-buttons">
              <view class="action-btn edit-btn" @tap.stop="editAddress(addr)">
                <text class="btn-icon">✏️</text>
                <text class="btn-text">编辑</text>
              </view>
              <view class="action-btn delete-btn" @tap.stop="deleteAddress(addr)">
                <text class="btn-icon">🗑️</text>
                <text class="btn-text">删除</text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>

    <view class="address-footer">
      <view class="add-btn-full" @tap="addAddress">
        <text class="add-icon">＋</text>
        <text>添加新地址</text>
      </view>
    </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import EmptyState from '@/components/common/EmptyState.vue'
import { getAddressList, deleteAddress as deleteAddressApi } from '@/api/address'
import type { Address } from '@/types'

const addresses = ref<Address[]>([])
const loaded = ref(false)
const selectMode = ref(false)
const isManaging = ref(false)

onMounted(() => {
  nextTick(() => {
    loaded.value = true
  })
  const pages = getCurrentPages()
  const currentPage = pages[pages.length - 1] as any
  if (currentPage?.options?.mode === 'select') {
    selectMode.value = true
  }
})

onShow(async () => {
  await loadAddresses()
})

const loadAddresses = async () => {
  try {
    const data = await getAddressList()
    addresses.value = data || []
  } catch (e) {
    console.error('Failed to load addresses:', e)
  }
}

const toggleManage = () => {
  isManaging.value = !isManaging.value
}

const selectAddress = (addr: Address) => {
  if (selectMode.value) {
    uni.setStorageSync('selectedAddress', addr)
    uni.navigateBack()
  }
}

const addAddress = () => {
  uni.navigateTo({ url: '/pages/address/edit' })
}

const editAddress = (addr: Address) => {
  uni.navigateTo({ url: `/pages/address/edit?id=${addr.id}` })
}

const deleteAddress = (addr: Address) => {
  console.log('deleteAddress called, addr:', addr)
  uni.showModal({
    title: '提示',
    content: '确定删除该地址?',
    success: async (res) => {
      console.log('modal confirm, res:', res, 'addr.id:', addr.id)
      if (res.confirm) {
        try {
          console.log('calling deleteAddressApi with id:', addr.id)
          await deleteAddressApi(addr.id)
          console.log('deleteAddressApi success')
          addresses.value = addresses.value.filter(a => a.id !== addr.id)
          uni.showToast({ title: '已删除', icon: 'success' })
        } catch (e) {
          console.log('deleteAddressApi error:', e)
          uni.showToast({ title: '删除失败', icon: 'none' })
        }
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.address-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #FAFAFA;
}

.skeleton-address-list {
  padding: 24rpx;
}

.skeleton-card {
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
}

.skeleton-contact {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.skeleton-circle {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  background: #EEEEEE;
}

.skeleton-line {
  height: 28rpx;
  margin-bottom: 16rpx;
  background: #EEEEEE;
  border-radius: 4rpx;
}

.skeleton-line.short {
  width: 120rpx;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx;
}

.list-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #2C2C2C;
}

.manage-btn {
  font-size: 28rpx;
  color: #C9A86C;
}

.address-list {
  flex: 1;
}

.empty-wrap {
  padding: 100rpx 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.add-btn {
  margin-top: 32rpx;
  padding: 16rpx 48rpx;
  background: linear-gradient(135deg, #C9A86C 0%, #B8956A 100%);
  color: #FFFFFF;
  border-radius: 9999rpx;
  font-size: 28rpx;
}

.address-item {
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 24rpx;
  margin: 0 24rpx 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.address-top {
  margin-bottom: 16rpx;
}

.contact-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.contact-icon {
  font-size: 24rpx;
}

.consignee {
  font-size: 32rpx;
  font-weight: 600;
  color: #2C2C2C;
}

.phone {
  font-size: 28rpx;
  color: #666666;
}

.default-tag {
  padding: 4rpx 16rpx;
  background: #E5D4B8;
  color: #C9A86C;
  font-size: 20rpx;
  border-radius: 9999rpx;
}

.address-divider {
  height: 1rpx;
  border-top: 1rpx dashed #E8E8E8;
  margin: 16rpx 0;
}

.address-middle {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
}

.address-icon {
  font-size: 24rpx;
  margin-top: 4rpx;
}

.address-detail {
  flex: 1;
  font-size: 24rpx;
  color: #666666;
  line-height: 1.5;
}

.address-bottom {
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid #F0F0F0;
  display: flex;
  justify-content: flex-end;
}

.action-buttons {
  display: flex;
  gap: 24rpx;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 24rpx;
  border-radius: 9999rpx;
  font-size: 24rpx;
}

.edit-btn {
  border: 1rpx solid #C9A86C;
  color: #C9A86C;
}

.delete-btn {
  border: 1rpx solid #E53935;
  color: #E53935;
}

.btn-icon {
  font-size: 20rpx;
}

.btn-text {
  font-size: 24rpx;
}

.address-footer {
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: #FFFFFF;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.05);
}

.add-btn-full {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  height: 96rpx;
  background: linear-gradient(135deg, #C9A86C 0%, #B8956A 100%);
  color: #FFFFFF;
  border-radius: 9999rpx;
  font-size: 32rpx;
  font-weight: 500;
}

.add-icon {
  font-size: 40rpx;
}
</style>