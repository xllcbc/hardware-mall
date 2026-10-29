<template>
  <section class="variant-a">
    <header class="page-heading">
      <div>
        <div class="eyebrow">
          <span>用户分级</span>
          <span class="eyebrow-divider"></span>
          <span>运营总览</span>
        </div>
        <h1>用户分级</h1>
        <p>按客户类型维护价格身份，并持续检查分类倍率是否完整。</p>
      </div>
      <div class="heading-actions">
        <button class="button button-ghost">
          <el-icon><Download /></el-icon>
          导出名单
        </button>
        <button class="button button-primary">
          <el-icon><Plus /></el-icon>
          新建等级
        </button>
      </div>
    </header>

    <div class="metric-strip">
      <div class="metric-item">
        <span class="metric-label">分级数量</span>
        <strong>{{ levels.length }}</strong>
        <small>{{ enabledCount }} 个启用</small>
      </div>
      <div class="metric-item">
        <span class="metric-label">覆盖用户</span>
        <strong>{{ formatNumber(totalUsers(levels)) }}</strong>
        <small>占全部注册用户 100%</small>
      </div>
      <div class="metric-item">
        <span class="metric-label">商品分类</span>
        <strong>{{ categories.length }}</strong>
        <small>参与等级定价</small>
      </div>
      <div class="metric-item metric-item-warn">
        <span class="metric-label">待补齐规则</span>
        <strong>{{ totalMissing(levels) }}</strong>
        <small>影响 {{ incompleteCount }} 个等级</small>
      </div>
    </div>

    <div class="overview-shell">
      <aside class="level-rail">
        <div class="rail-header">
          <div>
            <strong>等级列表</strong>
            <span>按展示优先级排序</span>
          </div>
          <button class="icon-button" title="等级设置">
            <el-icon><Setting /></el-icon>
          </button>
        </div>

        <label class="search-field">
          <el-icon><Search /></el-icon>
          <input
            :value="search"
            placeholder="搜索等级或负责人"
            @input="emitSearch"
          />
          <button v-if="search" type="button" class="clear-search" @click="emit('update-search', '')">
            <el-icon><Close /></el-icon>
          </button>
        </label>

        <div class="tier-list">
          <button
            v-for="(level, index) in filteredLevels"
            :key="level.id"
            class="tier-row"
            :class="{ active: level.id === selectedId }"
            @click="emit('select', level.id)"
          >
            <span class="tier-index">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="tier-copy">
              <span class="tier-name">
                {{ level.name }}
                <el-icon v-if="level.isRetail" class="lock-icon"><Lock /></el-icon>
              </span>
              <span class="tier-note">
                {{ level.owner }} · {{ level.status === 1 ? '启用中' : '未启用' }}
              </span>
            </span>
            <span class="tier-users">
              <strong>{{ formatCompact(level.userCount) }}</strong>
              <small>用户</small>
            </span>
          </button>
          <div v-if="!filteredLevels.length" class="empty-list">没有匹配的等级</div>
        </div>

        <div class="rail-footer">
          <span><i class="status-dot"></i>{{ enabledCount }} 个等级正在生效</span>
          <button>成员归属</button>
        </div>
      </aside>

      <main v-if="selectedLevel" class="level-workspace">
        <div class="workspace-heading">
          <div class="workspace-title">
            <span class="level-monogram">{{ selectedLevel.shortName.slice(0, 1) }}</span>
            <div>
              <div class="title-line">
                <h2>{{ selectedLevel.name }}</h2>
                <span class="status-badge" :class="{ inactive: selectedLevel.status !== 1 }">
                  {{ selectedLevel.status === 1 ? '已启用' : '未启用' }}
                </span>
                <span v-if="selectedLevel.isRetail" class="retail-badge">基础等级</span>
              </div>
              <p>{{ selectedLevel.description }}</p>
            </div>
          </div>
          <div class="workspace-actions">
            <button class="button button-ghost">
              <el-icon><View /></el-icon>
              价格预览
            </button>
            <button class="button button-ghost">
              <el-icon><EditPen /></el-icon>
              编辑信息
            </button>
            <el-switch
              :model-value="selectedLevel.status === 1"
              :disabled="selectedLevel.isRetail"
              @change="emitToggle"
            />
          </div>
        </div>

        <div class="readiness-panel" :class="{ warning: selectedMissing.length }">
          <div class="readiness-icon">
            <el-icon v-if="selectedMissing.length"><Warning /></el-icon>
            <el-icon v-else><CircleCheck /></el-icon>
          </div>
          <div class="readiness-copy">
            <strong v-if="selectedMissing.length">
              还有 {{ selectedMissing.length }} 个分类未配置倍率
            </strong>
            <strong v-else>倍率规则已完整覆盖</strong>
            <span v-if="selectedMissing.length">
              {{ selectedMissing.map(item => item.name).join('、') }}将在下单时退回零售价
            </span>
            <span v-else>该等级下的商品将按下列倍率计算</span>
          </div>
          <div class="coverage-score">
            <strong>{{ selectedCoverage }}%</strong>
            <span>分类覆盖</span>
          </div>
          <div class="coverage-track">
            <span :style="{ width: `${selectedCoverage}%` }"></span>
          </div>
        </div>

        <div class="subsection-heading">
          <div>
            <h3>分类倍率</h3>
            <p>倍率越接近 1，等级价越接近零售价。</p>
          </div>
          <div class="table-tools">
            <button class="text-button" @click="emit('update-only-gaps', !onlyGaps)">
              <el-icon><Filter /></el-icon>
              {{ onlyGaps ? '显示全部分类' : '只看缺口' }}
            </button>
            <button class="text-button">
              <el-icon><CopyDocument /></el-icon>
              复制规则
            </button>
          </div>
        </div>

        <div class="rate-table">
          <div class="rate-table-head">
            <span>商品分类</span>
            <span>零售基准</span>
            <span>{{ selectedLevel.name }}倍率</span>
            <span>状态</span>
          </div>
          <div
            v-for="category in visibleCategories"
            :key="category.id"
            class="rate-row"
            :class="{ missing: selectedLevel.rates[category.id] == null }"
          >
            <div class="category-cell">
              <span class="category-mark">{{ category.code.slice(0, 2) }}</span>
              <span>
                <strong>{{ category.name }}</strong>
                <small>{{ category.code }}</small>
              </span>
            </div>
            <span class="retail-rate">1.0000</span>
            <label class="rate-input">
              <span>×</span>
              <input
                type="number"
                min="0.0001"
                max="1"
                step="0.01"
                :value="selectedLevel.rates[category.id] ?? ''"
                placeholder="未配置"
                @change="onRateChange(category.id, $event)"
              />
            </label>
            <span class="rate-state" :class="{ gap: selectedLevel.rates[category.id] == null }">
              {{ selectedLevel.rates[category.id] == null ? '需要补齐' : '规则正常' }}
            </span>
          </div>
        </div>
      </main>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  coverageRate,
  draftCategories,
  formatNumber,
  missingCategories,
  totalMissing,
  totalUsers,
  type DraftCategory,
  type DraftLevel
} from './prototypeData'

const props = defineProps<{
  levels: DraftLevel[]
  selectedId: number
  search: string
  onlyGaps: boolean
}>()

const emit = defineEmits<{
  select: [id: number]
  toggle: [id: number, enabled: boolean]
  'update-search': [value: string]
  'update-only-gaps': [value: boolean]
  'update-rate': [levelId: number, categoryId: number, rate: number | null]
}>()

const categories: DraftCategory[] = draftCategories
const selectedLevel = computed(() => props.levels.find(level => level.id === props.selectedId))
const selectedMissing = computed(() =>
  selectedLevel.value ? missingCategories(selectedLevel.value) : []
)
const selectedCoverage = computed(() =>
  selectedLevel.value ? coverageRate(selectedLevel.value) : 0
)
const enabledCount = computed(() => props.levels.filter(level => level.status === 1).length)
const incompleteCount = computed(
  () => props.levels.filter(level => missingCategories(level).length > 0).length
)
const filteredLevels = computed(() => {
  const keyword = props.search.trim().toLowerCase()
  if (!keyword) return props.levels
  return props.levels.filter(level =>
    `${level.name} ${level.owner} ${level.shortName}`.toLowerCase().includes(keyword)
  )
})
const visibleCategories = computed(() => {
  if (!props.onlyGaps || !selectedLevel.value) return categories
  return categories.filter(category => selectedLevel.value?.rates[category.id] == null)
})

const emitSearch = (event: Event) => {
  emit('update-search', (event.target as HTMLInputElement).value)
}

const emitToggle = (value: string | number | boolean) => {
  if (!selectedLevel.value) return
  emit('toggle', selectedLevel.value.id, Boolean(value))
}

const onRateChange = (categoryId: number, event: Event) => {
  if (!selectedLevel.value) return
  const raw = (event.target as HTMLInputElement).value
  const rate = raw === '' ? null : Number(raw)
  emit(
    'update-rate',
    selectedLevel.value.id,
    categoryId,
    rate != null && Number.isFinite(rate) ? rate : null
  )
}

const formatCompact = (value: number) =>
  value >= 10000 ? `${(value / 10000).toFixed(1)}万` : formatNumber(value)
</script>

<style scoped>
.variant-a {
  --a-ink: var(--text-primary);
  --a-muted: var(--text-secondary);
  --a-line: var(--border-color);
  --a-soft: var(--bg-page);
  --a-copper: var(--primary-color);
  color: var(--a-ink);
}

.page-heading,
.workspace-heading,
.subsection-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.eyebrow {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  color: var(--a-copper);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.eyebrow-divider {
  width: 24px;
  height: 1px;
  background: currentColor;
  opacity: 0.6;
}

.page-heading h1 {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(30px, 3vw, 42px);
  line-height: 1.06;
  letter-spacing: -0.025em;
}

.page-heading p,
.subsection-heading p {
  margin: 8px 0 0;
  color: var(--a-muted);
  font-size: 14px;
}

.heading-actions,
.workspace-actions,
.table-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.button {
  min-height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 0 14px;
  border: 1px solid transparent;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  transition: 160ms ease;
}

.button:hover {
  transform: translateY(-1px);
}

.button-primary {
  color: #fff;
  background: var(--a-ink);
  box-shadow: 0 6px 16px rgba(26, 26, 26, 0.14);
}

.button-ghost {
  color: var(--a-ink);
  background: var(--bg-card);
  border-color: var(--a-line);
}

.button-ghost:hover {
  border-color: var(--a-copper);
}

.metric-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: 30px;
  border-top: 1px solid var(--a-line);
  border-bottom: 1px solid var(--a-line);
}

.metric-item {
  position: relative;
  min-height: 118px;
  padding: 20px 22px;
  display: grid;
  align-content: center;
  border-right: 1px solid var(--a-line);
}

.metric-item:last-child {
  border-right: 0;
}

.metric-label {
  color: var(--a-muted);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.metric-item strong {
  margin: 2px 0;
  font-family: var(--font-display);
  font-size: 32px;
  font-weight: 600;
  line-height: 1.1;
}

.metric-item small {
  color: var(--text-tertiary);
  font-size: 12px;
}

.metric-item-warn::before {
  content: '';
  position: absolute;
  top: -1px;
  left: 22px;
  width: 42px;
  height: 2px;
  background: var(--warning);
}

.metric-item-warn strong {
  color: var(--warning);
}

.overview-shell {
  min-height: 560px;
  margin-top: 24px;
  display: grid;
  grid-template-columns: 322px minmax(0, 1fr);
  border: 1px solid var(--a-line);
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
}

.level-rail {
  display: flex;
  flex-direction: column;
  min-width: 0;
  border-right: 1px solid var(--a-line);
  background:
    linear-gradient(180deg, var(--bg-card), transparent 36%),
    var(--a-soft);
}

.rail-header {
  padding: 21px 20px 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.rail-header > div {
  display: grid;
  gap: 2px;
}

.rail-header strong {
  font-size: 14px;
}

.rail-header span {
  color: var(--text-tertiary);
  font-size: 12px;
}

.icon-button,
.clear-search {
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 6px;
  display: grid;
  place-items: center;
  color: var(--a-muted);
  background: transparent;
}

.icon-button:hover,
.clear-search:hover {
  color: var(--a-ink);
  background: var(--primary-bg);
}

.search-field {
  height: 38px;
  margin: 0 16px 14px;
  padding: 0 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--a-line);
  border-radius: 6px;
  background: var(--bg-card);
  color: var(--text-tertiary);
}

.search-field:focus-within {
  border-color: var(--a-copper);
  box-shadow: 0 0 0 3px var(--primary-bg);
}

.search-field input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  color: var(--a-ink);
  background: transparent;
  font-size: 13px;
}

.tier-list {
  flex: 1;
  padding: 0 10px;
}

.tier-row {
  position: relative;
  width: 100%;
  min-height: 72px;
  padding: 12px 10px;
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  border: 0;
  border-bottom: 1px solid var(--border-light);
  color: var(--a-ink);
  text-align: left;
  background: transparent;
  transition: 160ms ease;
}

.tier-row::before {
  content: '';
  position: absolute;
  top: 12px;
  bottom: 12px;
  left: 0;
  width: 3px;
  border-radius: 2px;
  background: var(--a-copper);
  opacity: 0;
}

.tier-row:hover {
  background: var(--primary-bg);
}

.tier-row.active {
  background: var(--bg-card);
  box-shadow: 0 6px 16px rgba(26, 26, 26, 0.06);
}

.tier-row.active::before {
  opacity: 1;
}

.tier-index {
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: 11px;
}

.tier-copy,
.tier-users {
  display: grid;
  gap: 4px;
}

.tier-name {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 14px;
  font-weight: 600;
}

.lock-icon {
  color: var(--text-tertiary);
  font-size: 12px;
}

.tier-note {
  color: var(--text-tertiary);
  font-size: 11px;
}

.tier-users {
  justify-items: end;
}

.tier-users strong {
  font-family: var(--font-mono);
  font-size: 13px;
}

.tier-users small {
  font-size: 10px;
}

.empty-list {
  padding: 32px 12px;
  color: var(--text-tertiary);
  text-align: center;
  font-size: 13px;
}

.rail-footer {
  min-height: 54px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-top: 1px solid var(--a-line);
  color: var(--a-muted);
  font-size: 11px;
}

.rail-footer span {
  display: flex;
  align-items: center;
  gap: 6px;
}

.rail-footer button {
  border: 0;
  color: var(--a-copper);
  background: transparent;
  font-size: 12px;
  font-weight: 600;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--success);
  box-shadow: 0 0 0 3px var(--success-light);
}

.level-workspace {
  min-width: 0;
  padding: 24px;
}

.workspace-title {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.level-monogram {
  width: 44px;
  height: 44px;
  flex: 0 0 44px;
  display: grid;
  place-items: center;
  border: 1px solid var(--border-accent);
  border-radius: 7px;
  color: var(--primary-dark);
  background: var(--primary-bg);
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 600;
}

.title-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.title-line h2 {
  margin: 0;
  font-size: 22px;
  line-height: 1.2;
}

.workspace-title p {
  margin: 5px 0 0;
  color: var(--a-muted);
  font-size: 13px;
}

.status-badge,
.retail-badge {
  padding: 3px 7px;
  border-radius: 4px;
  color: var(--success);
  background: var(--success-light);
  font-size: 10px;
  font-weight: 700;
}

.status-badge.inactive {
  color: var(--text-secondary);
  background: var(--bg-page);
}

.retail-badge {
  color: var(--accent-color);
  background: var(--info-light);
}

.readiness-panel {
  position: relative;
  min-height: 72px;
  margin-top: 22px;
  padding: 14px 104px 14px 14px;
  display: flex;
  align-items: center;
  gap: 12px;
  overflow: hidden;
  border: 1px solid #d8e8dc;
  border-radius: 7px;
  background: var(--success-light);
}

.readiness-panel.warning {
  border-color: #ead9b7;
  background: var(--warning-light);
}

.readiness-icon {
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  color: var(--success);
  background: var(--bg-card);
  font-size: 17px;
}

.warning .readiness-icon {
  color: var(--warning);
}

.readiness-copy {
  display: grid;
  gap: 3px;
  font-size: 12px;
}

.readiness-copy strong {
  font-size: 13px;
}

.readiness-copy span {
  color: var(--text-secondary);
}

.coverage-score {
  position: absolute;
  top: 14px;
  right: 16px;
  display: grid;
  justify-items: end;
}

.coverage-score strong {
  font-family: var(--font-mono);
  font-size: 16px;
}

.coverage-score span {
  color: var(--text-tertiary);
  font-size: 9px;
}

.coverage-track {
  position: absolute;
  right: 16px;
  bottom: 13px;
  width: 74px;
  height: 3px;
  overflow: hidden;
  border-radius: 99px;
  background: rgba(0, 0, 0, 0.08);
}

.coverage-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--success);
}

.warning .coverage-track span {
  background: var(--warning);
}

.subsection-heading {
  margin-top: 26px;
  align-items: center;
}

.subsection-heading h3 {
  margin: 0;
  font-size: 16px;
  font-family: var(--font-body);
}

.subsection-heading p {
  margin-top: 3px;
  font-size: 12px;
}

.text-button {
  min-height: 32px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0 8px;
  border: 0;
  color: var(--a-muted);
  background: transparent;
  font-size: 12px;
  font-weight: 600;
}

.text-button:hover {
  color: var(--a-copper);
}

.rate-table {
  margin-top: 12px;
  border-top: 1px solid var(--a-line);
}

.rate-table-head,
.rate-row {
  display: grid;
  grid-template-columns: minmax(180px, 1.4fr) 100px 150px 100px;
  align-items: center;
  gap: 12px;
}

.rate-table-head {
  min-height: 38px;
  color: var(--text-tertiary);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.rate-row {
  min-height: 58px;
  border-top: 1px solid var(--border-light);
  font-size: 12px;
}

.rate-row.missing {
  background: linear-gradient(90deg, transparent, var(--warning-light));
}

.category-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.category-cell > span:last-child {
  display: grid;
  gap: 1px;
}

.category-cell strong {
  font-size: 13px;
}

.category-cell small {
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: 9px;
}

.category-mark {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border: 1px solid var(--a-line);
  border-radius: 5px;
  color: var(--a-muted);
  font-family: var(--font-mono);
  font-size: 9px;
}

.retail-rate {
  color: var(--text-tertiary);
  font-family: var(--font-mono);
}

.rate-input {
  height: 32px;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 0 9px;
  border: 1px solid var(--a-line);
  border-radius: 5px;
  background: var(--bg-card);
  color: var(--text-tertiary);
}

.rate-input:focus-within {
  border-color: var(--a-copper);
  box-shadow: 0 0 0 3px var(--primary-bg);
}

.rate-input input {
  width: 100%;
  border: 0;
  outline: 0;
  color: var(--a-ink);
  background: transparent;
  font-family: var(--font-mono);
  font-size: 12px;
}

.rate-input input::-webkit-inner-spin-button {
  display: none;
}

.rate-state {
  color: var(--success);
  font-size: 11px;
}

.rate-state.gap {
  color: var(--warning);
}

@media (max-width: 1080px) {
  .overview-shell {
    grid-template-columns: 280px minmax(0, 1fr);
  }

  .level-workspace {
    padding: 20px;
  }

  .workspace-heading {
    flex-direction: column;
  }

  .workspace-actions {
    width: 100%;
    justify-content: flex-end;
  }
}

@media (max-width: 860px) {
  .page-heading {
    flex-direction: column;
  }

  .metric-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metric-item:nth-child(2) {
    border-right: 0;
  }

  .metric-item:nth-child(-n + 2) {
    border-bottom: 1px solid var(--a-line);
  }

  .overview-shell {
    grid-template-columns: 1fr;
  }

  .level-rail {
    border-right: 0;
    border-bottom: 1px solid var(--a-line);
  }

  .tier-list {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 4px;
    padding-bottom: 12px;
  }

  .tier-row {
    border: 1px solid var(--border-light);
    border-radius: 6px;
  }

  .rate-table-head {
    display: none;
  }

  .rate-row {
    grid-template-columns: minmax(150px, 1fr) 120px;
    padding: 10px 0;
  }

  .retail-rate,
  .rate-state {
    display: none;
  }
}

@media (max-width: 560px) {
  .heading-actions,
  .workspace-actions {
    flex-wrap: wrap;
  }

  .tier-list {
    grid-template-columns: 1fr;
  }

  .readiness-panel {
    padding-right: 88px;
  }

  .rate-row {
    grid-template-columns: 1fr;
  }
}
</style>
