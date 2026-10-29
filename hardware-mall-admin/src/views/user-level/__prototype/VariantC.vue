<template>
  <section class="variant-c">
    <header class="ledger-masthead">
      <div class="masthead-copy">
        <span class="masthead-index">03 / OPERATIONS LEDGER</span>
        <h1>用户分级台账</h1>
        <p>先看全局归属，再展开单个等级处理规则和成员。</p>
      </div>
      <div class="masthead-summary">
        <div>
          <span>等级</span>
          <strong>{{ levels.length }}</strong>
        </div>
        <div>
          <span>覆盖用户</span>
          <strong>{{ formatNumber(totalUsers(levels)) }}</strong>
        </div>
        <div class="has-alert">
          <span>待补齐</span>
          <strong>{{ totalMissing(levels) }}</strong>
        </div>
      </div>
      <div class="masthead-actions">
        <button class="outline-button">
          <el-icon><Upload /></el-icon>
          导入归属
        </button>
        <button class="dark-button">
          <el-icon><Plus /></el-icon>
          新建分级
        </button>
      </div>
    </header>

    <div class="ledger-tabs">
      <div class="tab-list" role="tablist">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          :class="{ active: activeTab === tab.key }"
          role="tab"
          :aria-selected="activeTab === tab.key"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
          <span>{{ tab.count }}</span>
        </button>
      </div>
      <div class="tab-tools">
        <label class="ledger-search">
          <el-icon><Search /></el-icon>
          <input :value="search" placeholder="搜索分级、负责人" @input="emitSearch" />
        </label>
        <button
          class="filter-button"
          :class="{ active: onlyGaps }"
          @click="emit('update-only-gaps', !onlyGaps)"
        >
          <el-icon><Filter /></el-icon>
          仅看异常
        </button>
      </div>
    </div>

    <div v-if="activeTab === 'rules'" class="ledger">
      <div class="ledger-head">
        <span>分级 / 用途</span>
        <span>准入标签</span>
        <span>规则覆盖</span>
        <span>用户</span>
        <span>排序</span>
        <span>最近更新</span>
        <span>状态</span>
        <span></span>
      </div>

      <template v-for="(level, index) in visibleLevels" :key="level.id">
        <div
          class="ledger-row"
          :class="{
            expanded: level.id === selectedId,
            alert: missingCategories(level).length > 0
          }"
          @click="emit('select', level.id)"
        >
          <div class="identity-cell">
            <span class="ledger-index">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="identity-mark">{{ level.shortName.slice(0, 1) }}</span>
            <span class="identity-copy">
              <strong>{{ level.name }}</strong>
              <small>{{ level.description }}</small>
            </span>
          </div>
          <div class="tag-cell">
            <span v-for="tag in levelTags[level.id]" :key="tag">{{ tag }}</span>
          </div>
          <div class="coverage-cell">
            <span class="coverage-bar"><i :style="{ width: `${coverageRate(level)}%` }"></i></span>
            <strong>{{ coverageRate(level) }}%</strong>
          </div>
          <div class="number-cell">{{ formatNumber(level.userCount) }}</div>
          <div class="number-cell muted">{{ level.sortOrder }}</div>
          <div class="time-cell">
            <strong>{{ level.updatedAt.slice(5, 10) }}</strong>
            <span>{{ level.updatedAt.slice(11) }}</span>
          </div>
          <div class="state-cell">
            <span :class="{ inactive: level.status !== 1 }">
              <i></i>{{ level.status === 1 ? '启用' : '停用' }}
            </span>
          </div>
          <div class="expand-cell">
            <button
              :title="level.id === selectedId ? '收起规则' : '展开规则'"
              @click.stop="emit('select', level.id)"
            >
              <el-icon :class="{ rotated: level.id === selectedId }"><ArrowDown /></el-icon>
            </button>
          </div>
        </div>

        <div v-if="level.id === selectedId" class="ledger-expansion">
          <div class="expansion-heading">
            <div>
              <span class="expansion-code">{{ level.shortName }} / RATE MATRIX</span>
              <h2>{{ level.name }} · 分类倍率</h2>
              <p>直接在倍率块内修改；未配置项会回退到零售价。</p>
            </div>
            <div class="expansion-actions">
              <button class="text-action">
                <el-icon><CopyDocument /></el-icon>
                复制其他等级
              </button>
              <button class="outline-button compact">价格预览</button>
              <button class="dark-button compact">保存规则</button>
            </div>
          </div>

          <div class="expansion-body">
            <div class="rate-block-grid">
              <label
                v-for="category in categories"
                :key="category.id"
                class="rate-block"
                :class="{ missing: level.rates[category.id] == null }"
              >
                <span class="rate-block-head">
                  <span>{{ category.name }}</span>
                  <small>{{ category.code }}</small>
                </span>
                <span class="rate-block-input">
                  <span>×</span>
                  <input
                    type="number"
                    min="0.0001"
                    max="1"
                    step="0.01"
                    :value="level.rates[category.id] ?? ''"
                    placeholder="未配置"
                    @click.stop
                    @change="onRateChange(level.id, category.id, $event)"
                  />
                </span>
                <span class="rate-block-foot">
                  <span>零售价基准</span>
                  <strong>{{ level.rates[category.id] == null ? '需补齐' : '规则正常' }}</strong>
                </span>
              </label>
            </div>

            <aside class="expansion-side">
              <div class="side-section">
                <span class="side-label">归属概览</span>
                <strong class="side-number">{{ formatNumber(level.userCount) }}</strong>
                <span class="side-unit">名用户</span>
                <div class="side-mini-grid">
                  <div>
                    <span>负责人</span>
                    <strong>{{ level.owner }}</strong>
                  </div>
                  <div>
                    <span>缺口</span>
                    <strong :class="{ warning: missingCategories(level).length }">
                      {{ missingCategories(level).length }} 项
                    </strong>
                  </div>
                </div>
              </div>
              <div class="side-section">
                <span class="side-label">最近操作</span>
                <div class="side-timeline">
                  <span><i></i>调整门吸闭门器倍率</span>
                  <span><i></i>批量分配 18 名用户</span>
                  <span><i></i>更新分级说明</span>
                </div>
              </div>
              <button class="member-link">
                查看该分级用户
                <el-icon><ArrowRight /></el-icon>
              </button>
            </aside>
          </div>
        </div>
      </template>

      <div v-if="!visibleLevels.length" class="ledger-empty">
        <el-icon><Search /></el-icon>
        没有符合条件的分级
      </div>
    </div>

    <div v-else class="tab-surface">
      <div class="surface-heading">
        <div>
          <span class="expansion-code">MEMBER ALLOCATION</span>
          <h2>成员归属</h2>
          <p>按分级查看当前归属用户，调整等级后用户需重新登录生效。</p>
        </div>
        <button class="outline-button compact">批量调整归属</button>
      </div>
      <div class="member-ledger">
        <div v-for="level in visibleLevels" :key="level.id" class="member-row">
          <div class="member-level">
            <span>{{ level.shortName }}</span>
            <strong>{{ level.name }}</strong>
          </div>
          <div class="member-avatars">
            <span v-for="avatar in sampleMembers(level.id)" :key="avatar">{{ avatar }}</span>
            <span v-if="level.userCount > 3" class="more-avatar">+{{ level.userCount - 3 }}</span>
          </div>
          <div class="member-count">{{ formatNumber(level.userCount) }} 人</div>
          <button class="text-action">查看名单</button>
        </div>
      </div>
    </div>

  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
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
  'update-search': [value: string]
  'update-only-gaps': [value: boolean]
  'update-rate': [levelId: number, categoryId: number, rate: number | null]
}>()

type LedgerTab = 'rules' | 'members'

const categories: DraftCategory[] = draftCategories
const activeTab = ref<LedgerTab>('rules')
const tabs = computed<Array<{ key: LedgerTab; label: string; count: string }>>(() => [
  { key: 'rules', label: '分级规则', count: String(props.levels.length) },
  { key: 'members', label: '成员归属', count: formatNumber(totalUsers(props.levels)) }
])

const levelTags: Record<number, string[]> = {
  1: ['默认注册'],
  2: ['企业认证', '批发采购'],
  3: ['项目报备', '工程客户'],
  4: ['年度协议', '人工审核'],
  5: ['预留', '待分配']
}

const visibleLevels = computed(() => {
  const keyword = props.search.trim().toLowerCase()
  return props.levels.filter(level => {
    const matches =
      !keyword ||
      `${level.name} ${level.shortName} ${level.owner} ${level.description}`
        .toLowerCase()
        .includes(keyword)
    const hasGap = missingCategories(level).length > 0
    return matches && (!props.onlyGaps || hasGap)
  })
})

const emitSearch = (event: Event) => {
  emit('update-search', (event.target as HTMLInputElement).value)
}

const onRateChange = (levelId: number, categoryId: number, event: Event) => {
  const raw = (event.target as HTMLInputElement).value
  const rate = raw === '' ? null : Number(raw)
  emit('update-rate', levelId, categoryId, rate != null && Number.isFinite(rate) ? rate : null)
}

const sampleMembers = (levelId: number) => {
  const samples: Record<number, string[]> = {
    1: ['林', '周', '陈'],
    2: ['宏', '远', '鑫'],
    3: ['建', '城', '筑'],
    4: ['合', '协'],
    5: []
  }
  return samples[levelId] || []
}
</script>

<style scoped>
.variant-c {
  --ink: var(--text-primary);
  --muted: var(--text-secondary);
  --line: var(--border-color);
  --soft: var(--bg-page);
  --copper: var(--primary-color);
  color: var(--ink);
}

.ledger-masthead {
  display: grid;
  grid-template-columns: minmax(320px, 1.4fr) auto auto;
  align-items: end;
  gap: 32px;
  padding-bottom: 22px;
  border-bottom: 2px solid var(--ink);
}

.masthead-index,
.expansion-code {
  color: var(--copper);
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.masthead-copy h1 {
  margin: 8px 0 4px;
  font-family: var(--font-display);
  font-size: clamp(30px, 3vw, 44px);
  line-height: 1.05;
}

.masthead-copy p,
.surface-heading p,
.expansion-heading p {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
}

.masthead-summary {
  display: flex;
  align-items: stretch;
}

.masthead-summary > div {
  min-width: 84px;
  padding: 0 16px;
  display: grid;
  gap: 1px;
  border-left: 1px solid var(--line);
}

.masthead-summary span {
  color: var(--text-tertiary);
  font-size: 9px;
}

.masthead-summary strong {
  font-family: var(--font-mono);
  font-size: 21px;
  font-weight: 600;
}

.masthead-summary .has-alert strong {
  color: var(--warning);
}

.masthead-actions,
.expansion-actions,
.tab-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.outline-button,
.dark-button,
.filter-button {
  min-height: 36px;
  padding: 0 13px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px solid var(--line);
  border-radius: 3px;
  color: var(--ink);
  background: var(--bg-card);
  font-size: 11px;
  font-weight: 700;
}

.dark-button {
  color: #fff;
  border-color: var(--ink);
  background: var(--ink);
}

.outline-button:hover,
.filter-button:hover {
  border-color: var(--copper);
}

.compact {
  min-height: 30px;
  padding: 0 10px;
  font-size: 10px;
}

.ledger-tabs {
  min-height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid var(--line);
}

.tab-list {
  align-self: stretch;
  display: flex;
  gap: 4px;
}

.tab-list button {
  position: relative;
  padding: 0 14px;
  display: flex;
  align-items: center;
  gap: 7px;
  border: 0;
  color: var(--muted);
  background: transparent;
  font-size: 12px;
  font-weight: 600;
}

.tab-list button::after {
  content: '';
  position: absolute;
  right: 14px;
  bottom: -1px;
  left: 14px;
  height: 2px;
  background: var(--ink);
  transform: scaleX(0);
  transition: transform 160ms ease;
}

.tab-list button.active {
  color: var(--ink);
}

.tab-list button.active::after {
  transform: scaleX(1);
}

.tab-list button span {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  color: var(--text-tertiary);
  background: var(--soft);
  font-family: var(--font-mono);
  font-size: 8px;
}

.ledger-search {
  height: 32px;
  padding: 0 9px;
  display: flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--line);
  border-radius: 3px;
  color: var(--text-tertiary);
  background: var(--bg-card);
}

.ledger-search input {
  width: 120px;
  border: 0;
  outline: 0;
  color: var(--ink);
  background: transparent;
  font-size: 10px;
}

.filter-button {
  min-height: 32px;
  color: var(--muted);
  font-size: 10px;
}

.filter-button.active {
  color: var(--warning);
  border-color: #ead9b7;
  background: var(--warning-light);
}

.ledger {
  border-right: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  border-left: 1px solid var(--line);
  background: var(--bg-card);
}

.ledger-head,
.ledger-row {
  display: grid;
  grid-template-columns: minmax(260px, 1.6fr) minmax(130px, 0.9fr) 110px 72px 56px 94px 72px 38px;
  gap: 10px;
  align-items: center;
}

.ledger-head {
  min-height: 38px;
  padding: 0 14px;
  color: var(--text-tertiary);
  background: var(--soft);
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.ledger-row {
  min-height: 68px;
  padding: 0 14px;
  border-top: 1px solid var(--border-light);
  cursor: pointer;
  transition: background-color 150ms ease;
}

.ledger-row:hover,
.ledger-row.expanded {
  background: var(--primary-bg);
}

.ledger-row.expanded {
  border-bottom: 1px solid var(--border-accent);
}

.ledger-row.alert .coverage-bar i {
  background: var(--warning);
}

.identity-cell {
  min-width: 0;
  display: grid;
  grid-template-columns: 22px 34px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
}

.ledger-index {
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: 9px;
}

.identity-mark {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: 1px solid var(--border-accent);
  border-radius: 2px;
  color: var(--primary-dark);
  background: var(--bg-card);
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 600;
}

.identity-copy {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.identity-copy strong {
  font-size: 12px;
}

.identity-copy small {
  max-width: 100%;
  color: var(--text-tertiary);
  font-size: 9px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tag-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.tag-cell span {
  padding: 2px 5px;
  border: 1px solid var(--line);
  border-radius: 2px;
  color: var(--muted);
  font-size: 8px;
}

.coverage-cell {
  display: flex;
  align-items: center;
  gap: 7px;
}

.coverage-bar {
  width: 48px;
  height: 4px;
  overflow: hidden;
  border-radius: 99px;
  background: var(--border-light);
}

.coverage-bar i {
  display: block;
  height: 100%;
  background: var(--success);
}

.coverage-cell strong {
  font-family: var(--font-mono);
  font-size: 9px;
}

.number-cell {
  font-family: var(--font-mono);
  font-size: 10px;
}

.number-cell.muted,
.time-cell span {
  color: var(--text-tertiary);
}

.time-cell {
  display: grid;
  gap: 1px;
}

.time-cell strong {
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 500;
}

.time-cell span {
  font-family: var(--font-mono);
  font-size: 8px;
}

.state-cell span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--success);
  font-size: 9px;
  font-weight: 600;
}

.state-cell span i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.state-cell span.inactive {
  color: var(--text-tertiary);
}

.expand-cell {
  display: flex;
  justify-content: flex-end;
}

.expand-cell button {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: 1px solid transparent;
  border-radius: 2px;
  color: var(--muted);
  background: transparent;
}

.expand-cell button:hover {
  border-color: var(--line);
  background: var(--bg-card);
}

.expand-cell .el-icon {
  transition: transform 160ms ease;
}

.expand-cell .el-icon.rotated {
  transform: rotate(180deg);
}

.ledger-expansion {
  padding: 20px 24px 24px;
  border-top: 1px dashed var(--border-accent);
  border-bottom: 1px solid var(--border-light);
  background:
    linear-gradient(120deg, var(--primary-bg), transparent 46%),
    var(--bg-card);
  animation: revealExpansion 180ms ease-out;
}

@keyframes revealExpansion {
  from {
    opacity: 0;
    transform: translateY(-6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.expansion-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.expansion-heading h2,
.surface-heading h2 {
  margin: 5px 0 3px;
  font-family: var(--font-body);
  font-size: 17px;
}

.expansion-body {
  margin-top: 18px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 246px;
  border-top: 1px solid var(--line);
  border-left: 1px solid var(--line);
}

.rate-block-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.rate-block {
  min-height: 112px;
  padding: 12px;
  display: grid;
  align-content: space-between;
  gap: 8px;
  border-right: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  background: var(--bg-card);
}

.rate-block.missing {
  background: var(--warning-light);
}

.rate-block-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.rate-block-head > span {
  font-size: 10px;
  font-weight: 700;
}

.rate-block-head small {
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: 8px;
}

.rate-block-input {
  display: flex;
  align-items: baseline;
  gap: 4px;
  border-bottom: 1px solid var(--line);
}

.rate-block-input > span {
  color: var(--text-tertiary);
  font-size: 11px;
}

.rate-block-input input {
  width: 100%;
  min-width: 0;
  padding: 1px 0 5px;
  border: 0;
  outline: 0;
  color: var(--ink);
  background: transparent;
  font-family: var(--font-mono);
  font-size: 20px;
}

.rate-block-input input::placeholder {
  color: var(--warning);
  font-size: 13px;
}

.rate-block-input input::-webkit-inner-spin-button {
  display: none;
}

.rate-block-foot {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  color: var(--text-tertiary);
  font-size: 8px;
}

.rate-block-foot strong {
  color: var(--success);
  font-size: 8px;
}

.rate-block.missing .rate-block-foot strong {
  color: var(--warning);
}

.expansion-side {
  grid-row: 1 / span 4;
  border-right: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  background: var(--bg-card);
}

.side-section {
  padding: 16px;
  border-bottom: 1px solid var(--line);
}

.side-label {
  display: block;
  color: var(--text-tertiary);
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.side-number {
  margin-top: 4px;
  display: inline-block;
  font-family: var(--font-mono);
  font-size: 24px;
  font-weight: 500;
}

.side-unit {
  margin-left: 4px;
  color: var(--text-tertiary);
  font-size: 8px;
}

.side-mini-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: 1fr 1fr;
}

.side-mini-grid > div {
  display: grid;
  gap: 2px;
}

.side-mini-grid span {
  color: var(--text-tertiary);
  font-size: 8px;
}

.side-mini-grid strong {
  font-size: 10px;
}

.side-mini-grid .warning {
  color: var(--warning);
}

.side-timeline {
  margin-top: 11px;
  display: grid;
  gap: 8px;
}

.side-timeline span {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--muted);
  font-size: 9px;
}

.side-timeline i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--copper);
}

.member-link {
  width: 100%;
  min-height: 42px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 0;
  color: var(--copper);
  background: transparent;
  font-size: 9px;
  font-weight: 700;
}

.ledger-empty {
  min-height: 150px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.tab-surface {
  padding: 22px;
  border: 1px solid var(--line);
  border-top: 0;
  background: var(--bg-card);
}

.surface-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 18px;
}

.member-ledger,
.audit-table {
  border-top: 1px solid var(--line);
}

.member-row {
  min-height: 64px;
  padding: 0 14px;
  display: grid;
  grid-template-columns: 180px minmax(220px, 1fr) 90px 90px;
  align-items: center;
  gap: 14px;
  border-bottom: 1px solid var(--border-light);
}

.member-level {
  display: flex;
  align-items: center;
  gap: 10px;
}

.member-level span {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: 1px solid var(--border-accent);
  color: var(--primary-dark);
  font-family: var(--font-display);
  font-size: 14px;
}

.member-level strong {
  font-size: 11px;
}

.member-avatars {
  display: flex;
}

.member-avatars > span {
  width: 28px;
  height: 28px;
  margin-left: -4px;
  display: grid;
  place-items: center;
  border: 2px solid var(--bg-card);
  border-radius: 50%;
  color: var(--text-secondary);
  background: var(--soft);
  font-size: 9px;
}

.member-avatars > span:first-child {
  margin-left: 0;
}

.member-avatars .more-avatar {
  font-family: var(--font-mono);
}

.member-count {
  font-family: var(--font-mono);
  font-size: 10px;
}

.text-action {
  min-height: 30px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0;
  border: 0;
  color: var(--copper);
  background: transparent;
  font-size: 9px;
  font-weight: 700;
}

.audit-head,
.audit-row {
  display: grid;
  grid-template-columns: 110px 90px 120px minmax(0, 1fr) 60px;
  align-items: center;
  gap: 14px;
}

.audit-head {
  min-height: 36px;
  padding: 0 14px;
  color: var(--text-tertiary);
  background: var(--soft);
  font-size: 8px;
  font-weight: 700;
}

.audit-row {
  min-height: 48px;
  padding: 0 14px;
  border-bottom: 1px solid var(--border-light);
  color: var(--muted);
  font-size: 10px;
}

.audit-row strong {
  color: var(--ink);
}

.audit-row .mono {
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: 9px;
}

.audit-result {
  color: var(--success);
  font-size: 9px;
}

@media (max-width: 1180px) {
  .ledger-masthead {
    grid-template-columns: minmax(300px, 1fr) auto;
  }

  .masthead-actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
  }

  .ledger-head,
  .ledger-row {
    grid-template-columns: minmax(230px, 1.5fr) minmax(120px, 0.8fr) 95px 66px 55px 76px 38px;
  }

  .ledger-head span:nth-child(6),
  .ledger-row .time-cell {
    display: none;
  }

  .ledger-head span:nth-child(8) {
    grid-column: 6;
  }
}

@media (max-width: 900px) {
  .ledger-masthead {
    grid-template-columns: 1fr;
    align-items: start;
  }

  .masthead-summary > div:first-child {
    padding-left: 0;
    border-left: 0;
  }

  .masthead-actions {
    grid-column: auto;
    justify-content: flex-start;
  }

  .ledger-tabs {
    padding: 10px 0;
    align-items: flex-start;
    flex-direction: column;
  }

  .tab-list {
    min-height: 42px;
    align-self: auto;
  }

  .ledger-head {
    display: none;
  }

  .ledger-row {
    min-height: 78px;
    grid-template-columns: minmax(230px, 1fr) 100px 70px 38px;
  }

  .ledger-row .tag-cell,
  .ledger-row .number-cell.muted {
    display: none;
  }

  .ledger-row .time-cell {
    display: grid;
    grid-column: 2;
  }

  .ledger-row .state-cell {
    grid-column: 3;
  }

  .ledger-row .expand-cell {
    grid-column: 4;
  }

  .expansion-heading {
    flex-direction: column;
  }

  .expansion-body {
    grid-template-columns: 1fr;
  }

  .expansion-side {
    grid-row: auto;
  }
}

@media (max-width: 640px) {
  .masthead-summary {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
  }

  .masthead-summary > div {
    min-width: 0;
  }

  .tab-tools {
    width: 100%;
    flex-wrap: wrap;
  }

  .ledger-row {
    grid-template-columns: minmax(190px, 1fr) 38px;
    padding: 10px 12px;
  }

  .ledger-row .coverage-cell,
  .ledger-row .number-cell,
  .ledger-row .time-cell,
  .ledger-row .state-cell {
    display: none;
  }

  .ledger-row .expand-cell {
    grid-column: 2;
  }

  .ledger-expansion {
    padding: 16px 12px;
  }

  .expansion-actions {
    flex-wrap: wrap;
  }

  .rate-block-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .member-row {
    grid-template-columns: 1fr auto;
  }

  .member-avatars,
  .member-count {
    display: none;
  }

  .audit-head {
    display: none;
  }

  .audit-row {
    padding: 10px 12px;
    grid-template-columns: 80px 1fr auto;
  }

  .audit-row span:nth-child(2),
  .audit-row span:nth-child(4) {
    display: none;
  }
}
</style>
