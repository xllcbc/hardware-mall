<template>
  <div class="user-level-prototype">
    <div class="prototype-banner">
      <div class="prototype-badge">
        <span></span>
        PROTOTYPE
      </div>
      <p>交互草稿 · 不会保存到数据库 · 只用于比较页面结构</p>
      <div class="state-line">
        <strong>{{ activeVariant.key }} · {{ activeVariant.name }}</strong>
        <span>{{ notice || stateSummary }}</span>
      </div>
    </div>

    <VariantA
      v-if="variantKey === 'A'"
      :levels="levels"
      :selected-id="selectedId"
      :search="search"
      :only-gaps="onlyGaps"
      @select="selectedId = $event"
      @toggle="toggleLevel"
      @update-search="search = $event"
      @update-only-gaps="onlyGaps = $event"
      @update-rate="updateRate"
    />
    <VariantB
      v-else-if="variantKey === 'B'"
      :levels="levels"
      :selected-id="selectedId"
      :search="search"
      :only-gaps="onlyGaps"
      @select="selectedId = $event"
      @update-search="search = $event"
      @update-only-gaps="onlyGaps = $event"
      @update-rate="updateRate"
    />
    <VariantC
      v-else
      :levels="levels"
      :selected-id="selectedId"
      :search="search"
      :only-gaps="onlyGaps"
      @select="selectedId = $event"
      @update-search="search = $event"
      @update-only-gaps="onlyGaps = $event"
      @update-rate="updateRate"
    />

    <div class="prototype-switcher" role="navigation" aria-label="切换界面草稿">
      <button class="switch-arrow" title="上一个方案" @click="cycleVariant(-1)">
        <el-icon><ArrowLeft /></el-icon>
      </button>
      <div class="switch-copy">
        <span>UI 方案 {{ activeVariant.key }}</span>
        <strong>{{ activeVariant.name }}</strong>
      </div>
      <div class="switch-dots" aria-hidden="true">
        <i
          v-for="item in variantList"
          :key="item.key"
          :class="{ active: item.key === variantKey }"
        ></i>
      </div>
      <button class="switch-arrow" title="下一个方案" @click="cycleVariant(1)">
        <el-icon><ArrowRight /></el-icon>
      </button>
      <span class="keyboard-hint">← → 切换</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import VariantA from './VariantA.vue'
import VariantB from './VariantB.vue'
import VariantC from './VariantC.vue'
import {
  draftLevels,
  formatNumber,
  missingCategories,
  type DraftLevel
} from './prototypeData'

type VariantKey = 'A' | 'B' | 'C'

const route = useRoute()
const router = useRouter()

const variantList: Array<{ key: VariantKey; name: string }> = [
  { key: 'A', name: '运营总览' },
  { key: 'B', name: '规则工作台' },
  { key: 'C', name: '高密度台账' }
]

const levels = ref<DraftLevel[]>(
  draftLevels.map(level => ({
    ...level,
    rates: { ...level.rates }
  }))
)
const selectedId = ref(3)
const search = ref('')
const onlyGaps = ref(false)
const notice = ref('')
let noticeTimer: number | undefined

const variantKey = computed<VariantKey>(() => {
  const candidate = String(route.query.variant || 'A').toUpperCase()
  return candidate === 'B' || candidate === 'C' ? candidate : 'A'
})

const activeVariant = computed(
  () => variantList.find(item => item.key === variantKey.value) || variantList[0]
)

const selectedLevel = computed(() =>
  levels.value.find(level => level.id === selectedId.value)
)

const stateSummary = computed(() => {
  const current = selectedLevel.value
  if (!current) return `共 ${levels.value.length} 个分级`
  const gaps = missingCategories(current).length
  const filterText = onlyGaps.value ? '仅看异常' : '全部分类'
  const searchText = search.value ? `搜索“${search.value}”` : '无搜索'
  return `${current.name} · ${formatNumber(current.userCount)} 用户 · ${gaps} 项缺口 · ${filterText} · ${searchText}`
})

const showNotice = (message: string) => {
  notice.value = message
  window.clearTimeout(noticeTimer)
  noticeTimer = window.setTimeout(() => {
    notice.value = ''
  }, 3200)
}

const setVariant = (key: VariantKey) => {
  if (key === variantKey.value) return
  router.replace({
    path: route.path,
    query: {
      ...route.query,
      prototype: '1',
      variant: key
    }
  })
}

const cycleVariant = (direction: number) => {
  const currentIndex = variantList.findIndex(item => item.key === variantKey.value)
  const nextIndex = (currentIndex + direction + variantList.length) % variantList.length
  setVariant(variantList[nextIndex].key)
}

const handleKeydown = (event: KeyboardEvent) => {
  const target = event.target as HTMLElement | null
  if (
    target?.matches('input, textarea, select, [contenteditable="true"]') ||
    target?.closest('[contenteditable="true"]')
  ) {
    return
  }
  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    cycleVariant(-1)
  }
  if (event.key === 'ArrowRight') {
    event.preventDefault()
    cycleVariant(1)
  }
}

const updateRate = (levelId: number, categoryId: number, rate: number | null) => {
  if (rate != null && (rate <= 0 || rate > 1)) {
    showNotice('倍率需要落在 (0, 1] 区间，本次输入未应用')
    return
  }
  const level = levels.value.find(item => item.id === levelId)
  if (!level) return
  level.rates[categoryId] = rate
  showNotice(
    rate == null
      ? '已清空倍率，该分类会显示为待补齐'
      : `已临时调整倍率至 ${rate.toFixed(4)}，刷新页面会重置`
  )
}

const toggleLevel = (levelId: number, enabled: boolean) => {
  const level = levels.value.find(item => item.id === levelId)
  if (!level) return
  if (enabled && missingCategories(level).length > 0) {
    showNotice(`“${level.name}”仍有未配置分类，补齐后才能启用`)
    return
  }
  level.status = enabled ? 1 : 0
  showNotice(enabled ? `已模拟启用“${level.name}”` : `已模拟停用“${level.name}”`)
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  window.clearTimeout(noticeTimer)
})
</script>

<style scoped>
.user-level-prototype {
  max-width: 1440px;
  min-height: 100%;
  margin: 0 auto;
  padding-bottom: 102px;
}

.prototype-banner {
  min-height: 42px;
  margin-bottom: 24px;
  padding: 8px 12px;
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px dashed var(--border-accent);
  border-radius: 5px;
  color: var(--text-secondary);
  background: color-mix(in srgb, var(--bg-card) 82%, transparent);
}

.prototype-badge {
  height: 24px;
  padding: 0 8px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 3px;
  color: #fff;
  background: #253038;
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.08em;
  white-space: nowrap;
}

.prototype-badge span {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #e4b56e;
}

.prototype-banner p {
  margin: 0;
  color: var(--text-tertiary);
  font-size: 10px;
}

.state-line {
  margin-left: auto;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  font-size: 10px;
}

.state-line strong {
  color: var(--primary-dark);
  white-space: nowrap;
}

.state-line span {
  max-width: 540px;
  overflow: hidden;
  color: var(--text-secondary);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.prototype-switcher {
  position: fixed;
  left: 50%;
  bottom: 20px;
  z-index: 260;
  min-height: 56px;
  padding: 6px;
  display: flex;
  align-items: center;
  gap: 6px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 9px;
  color: #fff;
  background: #20282d;
  box-shadow: 0 18px 45px rgba(10, 15, 18, 0.34);
  transform: translateX(-50%);
}

.switch-arrow {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 6px;
  color: #f4eee7;
  background: rgba(255, 255, 255, 0.04);
  font-size: 17px;
  transition: 150ms ease;
}

.switch-arrow:hover {
  border-color: rgba(221, 184, 146, 0.65);
  background: rgba(221, 184, 146, 0.13);
}

.switch-copy {
  min-width: 138px;
  padding: 0 8px;
  display: grid;
  gap: 1px;
}

.switch-copy span {
  color: rgba(255, 255, 255, 0.52);
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.09em;
}

.switch-copy strong {
  color: #fff;
  font-size: 12px;
}

.switch-dots {
  padding: 0 8px;
  display: flex;
  gap: 5px;
}

.switch-dots i {
  width: 14px;
  height: 3px;
  border-radius: 99px;
  background: rgba(255, 255, 255, 0.2);
  transition: width 160ms ease, background-color 160ms ease;
}

.switch-dots i.active {
  width: 24px;
  background: #d9ad7e;
}

.keyboard-hint {
  padding: 0 8px 0 2px;
  color: rgba(255, 255, 255, 0.42);
  font-family: var(--font-mono);
  font-size: 8px;
  white-space: nowrap;
}

@media (max-width: 980px) {
  .prototype-banner {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .state-line {
    width: 100%;
    margin-left: 0;
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .user-level-prototype {
    padding-bottom: 92px;
  }

  .prototype-banner p {
    max-width: calc(100% - 104px);
    line-height: 1.55;
  }

  .state-line {
    align-items: flex-start;
    flex-direction: column;
    gap: 2px;
  }

  .state-line span {
    max-width: 100%;
    white-space: normal;
  }

  .prototype-switcher {
    bottom: 12px;
  }

  .switch-copy {
    min-width: 112px;
  }

  .switch-dots,
  .keyboard-hint {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .switch-arrow,
  .switch-dots i {
    transition: none;
  }
}
</style>
