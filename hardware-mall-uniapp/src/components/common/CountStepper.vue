<template>
  <view class="count-stepper" :class="{ disabled }">
    <view class="stepper-btn minus" :class="{ disabled: modelValue <= min }" @tap.stop="decrease">
      <text>-</text>
    </view>
    <input
      class="stepper-input"
      type="number"
      :value="modelValue"
      :min="min"
      :max="max"
      @blur="onBlur"
    />
    <view class="stepper-btn plus" :class="{ disabled: modelValue >= max }" @tap.stop="increase">
      <text>+</text>
    </view>
  </view>
</template>

<script setup lang="ts">
interface Props {
  modelValue: number
  min?: number
  max?: number
  step?: number
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  min: 1,
  max: 999,
  step: 1,
  disabled: false
})

const emit = defineEmits<{
  'update:modelValue': [value: number]
}>()

const decrease = () => {
  if (props.disabled) return
  if (props.modelValue > props.min) {
    emit('update:modelValue', props.modelValue - props.step)
  }
}

const increase = () => {
  if (props.disabled) return
  if (props.modelValue < props.max) {
    emit('update:modelValue', props.modelValue + props.step)
  }
}

const onBlur = (e: any) => {
  if (props.disabled) return
  let value = parseInt(e.detail.value) || props.min
  value = Math.max(props.min, Math.min(props.max, value))
  emit('update:modelValue', value)
}
</script>

<style lang="scss" scoped>
.count-stepper {
  display: inline-flex;
  align-items: center;
  height: 56rpx;
  background: var(--color-bg-dark);
  border-radius: var(--radius-sm);
  overflow: hidden;

  &.disabled {
    opacity: 0.5;
  }
}

.stepper-btn {
  width: 56rpx;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  color: var(--color-text-primary);
  transition: all 0.2s;

  &.disabled {
    color: var(--color-text-placeholder);
  }

  &:active:not(.disabled) {
    background: var(--color-primary-light);
  }
}

.stepper-input {
  width: 80rpx;
  height: 100%;
  text-align: center;
  font-size: var(--font-size-md);
  color: var(--color-text-primary);
  background: transparent;
}
</style>