<template>
  <view class="price-display">
    <text v-if="showSymbol" class="price-symbol">¥</text>
    <text class="price-integer">{{ integerPart }}</text>
    <text v-if="showDecimal" class="price-decimal">.{{ decimalPart }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  price: number | string
  showSymbol?: boolean
  showDecimal?: boolean
  size?: 'sm' | 'md' | 'lg' | 'xl'
  color?: string
}

const props = withDefaults(defineProps<Props>(), {
  showSymbol: true,
  showDecimal: true,
  size: 'md',
  color: ''
})

const priceValue = computed(() => {
  const p = typeof props.price === 'string' ? parseFloat(props.price) : props.price
  return isNaN(p) ? 0 : p
})

const integerPart = computed(() => Math.floor(priceValue.value))
const decimalPart = computed(() => {
  const decimals = (priceValue.value % 1).toFixed(2).split('.')[1]
  return decimals || '00'
})
</script>

<style lang="scss" scoped>
.price-display {
  display: inline-flex;
  align-items: baseline;
  color: var(--color-primary);
}

.price-symbol {
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.price-integer {
  font-weight: 700;
}

.price-decimal {
  font-size: 0.7em;
  font-weight: 600;
}
</style>