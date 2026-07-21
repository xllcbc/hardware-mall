<template>
  <view class="logistics-container">
    <view v-if="loading" class="loading-wrap">
      <LoadingState text="加载中..." />
    </view>
    <EmptyState v-else-if="!list.length" text="暂无物流方式" />
    <view v-else class="logistics-list">
      <view v-for="item in list" :key="item.id" class="logistics-item">
        <view class="logistics-header">
          <text class="logistics-name">{{ item.name }}</text>
          <text v-if="item.code" class="logistics-code">代码: {{ item.code }}</text>
        </view>
        <view v-if="item.phones && item.phones.length" class="logistics-phones">
          <text class="phone-label">联系电话:</text>
          <text v-for="phone in item.phones" :key="phone" class="phone-text" @tap="callPhone(phone)">{{ phone }}</text>
        </view>
        <view v-if="item.city" class="logistics-city">
          <text class="city-text">配送区域: {{ item.city }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getLogisticsList } from '@/api/logistics'
import LoadingState from '@/components/common/LoadingState.vue'
import EmptyState from '@/components/common/EmptyState.vue'

interface LogisticsItem {
  id: number
  name: string
  code?: string
  phones?: string[]
  city?: string
  status?: number
}

const list = ref<LogisticsItem[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    const data = await getLogisticsList()
    list.value = (data || []).filter((item: LogisticsItem) => item.status === 1)
  } catch (e: any) {
    uni.showToast({ title: e.message || '加载物流失败', icon: 'none' })
  } finally {
    loading.value = false
  }
})

const callPhone = (phone: string) => {
  uni.makePhoneCall({ phoneNumber: phone, fail: () => {} })
}
</script>

<style scoped>
.logistics-container {
  min-height: 100vh;
  background: #FAFAFA;
  padding: 20rpx;
}
.loading-wrap { padding: 80rpx 0; }
.logistics-list { display: flex; flex-direction: column; gap: 20rpx; }
.logistics-item {
  background: #fff;
  border-radius: 16rpx;
  padding: 32rpx;
  box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.04);
}
.logistics-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16rpx;
}
.logistics-name { font-size: 30rpx; font-weight: 600; color: #333; }
.logistics-code { font-size: 24rpx; color: #999; }
.logistics-phones {
  display: flex; flex-wrap: wrap; align-items: center;
  margin: 12rpx 0;
}
.phone-label { font-size: 26rpx; color: #666; margin-right: 16rpx; }
.phone-text {
  font-size: 26rpx; color: #1890ff; margin-right: 20rpx;
  padding: 4rpx 16rpx; background: #f0f8ff; border-radius: 8rpx;
}
.logistics-city { margin-top: 12rpx; }
.city-text { font-size: 26rpx; color: #666; }
</style>
