<template>
  <view class="search-bar" :class="{ 'search-bar--active': isFocus }">
    <icon class="search-icon" type="search" :size="16" color="#999999" />
    <input
      class="search-input"
      :placeholder="placeholder"
      :value="modelValue"
      :disabled="disabled"
      confirm-type="search"
      @focus="onFocus"
      @blur="onBlur"
      @input="onInput"
      @confirm="onSearch"
    />
    <icon
      v-if="modelValue"
      class="clear-icon"
      type="clear"
      :size="14"
      color="#CCCCCC"
      @tap="onClear"
    />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'

interface Props {
  modelValue?: string
  placeholder?: string
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  placeholder: '搜索商品',
  disabled: false
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  search: [value: string]
  focus: []
  blur: []
}>()

const isFocus = ref(false)

const onFocus = () => {
  isFocus.value = true
  emit('focus')
}

const onBlur = () => {
  isFocus.value = false
  emit('blur')
}

const onInput = (e: any) => {
  emit('update:modelValue', e.detail.value)
}

const onClear = () => {
  emit('update:modelValue', '')
}

const onSearch = () => {
  emit('search', props.modelValue)
}
</script>

<style lang="scss" scoped>
.search-bar {
  display: flex;
  align-items: center;
  height: 72rpx;
  background: #F5F5F5;
  border-radius: var(--radius-full);
  padding: 0 var(--spacing-md);
  transition: all 0.3s ease;
}

.search-bar--active {
  background: #FFFFFF;
  box-shadow: 0 0 0 2rpx var(--color-primary-light);
}

.search-icon {
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  height: 100%;
  padding: 0 var(--spacing-sm);
  font-size: var(--font-size-md);
  color: var(--color-text-primary);
}

.clear-icon {
  flex-shrink: 0;
  padding: var(--spacing-xs);
}
</style>