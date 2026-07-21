<template>
  <view class="edit-container">
    <view v-if="loading" class="loading-wrap">
      <LoadingState text="加载中..." />
    </view>
    <template v-else>
    <view class="form-section">
      <view class="form-item">
        <text class="form-label">收货人</text>
        <input 
          class="form-input" 
          v-model="form.consignee" 
          placeholder="请输入收货人姓名"
          placeholder-class="input-placeholder"
        />
      </view>
      <view class="form-divider"></view>
      <view class="form-item">
        <text class="form-label">手机号码</text>
        <input 
          class="form-input" 
          v-model="form.phone" 
          type="number" 
          placeholder="请输入手机号码" 
          maxlength="11"
          placeholder-class="input-placeholder"
        />
      </view>
      <view class="form-divider"></view>
      <picker class="region-picker" mode="region" @change="onRegionChange">
        <view class="form-item">
          <text class="form-label">所在地区</text>
          <view class="form-value">
            <text v-if="form.province" class="region-text">{{ form.province }} {{ form.city }} {{ form.district }}</text>
            <text v-else class="input-placeholder">请选择省市区</text>
          </view>
          <text class="form-arrow">›</text>
        </view>
      </picker>
      <view class="form-divider"></view>
      <view class="form-item">
        <text class="form-label">详细地址</text>
        <input 
          class="form-input" 
          v-model="form.detail" 
          placeholder="请输入详细地址"
          placeholder-class="input-placeholder"
        />
      </view>
      <view class="form-divider"></view>
      <view class="form-item">
        <text class="form-label">邮政编码</text>
        <input 
          class="form-input" 
          v-model="form.postalCode" 
          type="number" 
          placeholder="选填" 
          maxlength="6"
          placeholder-class="input-placeholder"
        />
      </view>
      <view class="form-divider"></view>
      <view class="form-item switch-item">
        <text class="form-label">设为默认地址</text>
        <switch
          :checked="form.isDefault === 1"
          color="#C9A86C"
          @change="onDefaultChange"
        />
      </view>
    </view>

    <view class="save-btn" @tap="saveAddress">保存地址</view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import type { Address } from '@/types'
import { createAddress, updateAddress, getAddressDetail } from '@/api/address'
import LoadingState from '@/components/common/LoadingState.vue'

const form = reactive({
  id: 0,
  consignee: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  postalCode: '',
  isDefault: 0
})

const loading = ref(false)

onMounted(async () => {
  const pages = getCurrentPages()
  const currentPage = pages[pages.length - 1] as any
  const addressId = currentPage?.options?.id
  if (addressId) {
    form.id = Number(addressId)
    loading.value = true
    try {
      const data = await getAddressDetail(form.id)
      Object.assign(form, data)
    } catch (e) {
      console.error('Failed to load address:', e)
    } finally {
      loading.value = false
    }
  }
})

const onRegionChange = (e: any) => {
  const [province, city, district] = e.detail.value
  form.province = province
  form.city = city
  form.district = district
}

const onDefaultChange = (e: any) => {
  form.isDefault = e.detail.value ? 1 : 0
}

const saveAddress = async () => {
  if (!form.consignee) {
    uni.showToast({ title: '请输入收货人', icon: 'none' })
    return
  }
  if (!form.phone || form.phone.length !== 11) {
    uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
    return
  }
  if (!form.province) {
    uni.showToast({ title: '请选择所在地区', icon: 'none' })
    return
  }
  if (!form.detail) {
    uni.showToast({ title: '请输入详细地址', icon: 'none' })
    return
  }
  
  try {
    if (form.id) {
      await updateAddress(form.id, form)
    } else {
      await createAddress(form)
    }
    uni.showToast({ title: '保存成功', icon: 'success' })
    setTimeout(() => {
      uni.navigateBack()
    }, 1500)
  } catch (e) {
    uni.showToast({ title: e.message || '保存失败', icon: 'none' })
  }
}
</script>

<style lang="scss" scoped>
.edit-container {
  min-height: 100vh;
  background: #FAFAFA;
  padding-bottom: 200rpx;
}

.form-section {
  margin: 24rpx;
  background: #FFFFFF;
  border-radius: 16rpx;
  border: 1rpx solid #F0F0F0;
  overflow: hidden;
}

.form-item {
  display: flex;
  align-items: center;
  padding: 28rpx 24rpx;
}

.form-divider {
  height: 1rpx;
  background: #F0F0F0;
  margin: 0 24rpx;
}

.form-label {
  width: 180rpx;
  font-size: 28rpx;
  color: #2C2C2C;
  flex-shrink: 0;
}

.form-input {
  flex: 1;
  font-size: 28rpx;
  color: #2C2C2C;
  border: 1rpx solid transparent;
  border-radius: 8rpx;
  padding: 8rpx 12rpx;
  transition: border-color 0.2s;

  &:focus {
    border-color: #C9A86C;
  }
}

.input-placeholder {
  color: #999999;
}

.form-value {
  flex: 1;
  font-size: 28rpx;
  color: #2C2C2C;
}

.region-text {
  color: #2C2C2C;
}

.form-arrow {
  font-size: 32rpx;
  color: #999999;
  margin-left: 16rpx;
}

.switch-item {
  justify-content: space-between;
}

.region-picker {
  width: 100%;
}

.save-btn {
  margin: 48rpx 24rpx 0;
  height: 96rpx;
  background: linear-gradient(135deg, #C9A86C 0%, #B8956A 100%);
  color: #FFFFFF;
  border-radius: 9999rpx;
  font-size: 32rpx;
  font-weight: 500;
  text-align: center;
  line-height: 96rpx;
}
</style>