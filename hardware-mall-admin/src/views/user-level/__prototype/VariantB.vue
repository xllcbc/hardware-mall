<template>
  <section class="variant-b">
    <header class="workbench-header">
      <div class="header-copy">
        <span class="section-code">PRICING / 02</span>
        <h1>分级规则工作台</h1>
        <p>横向比较各等级倍率，在同一个工作台完成检查、调整与发布。</p>
      </div>
      <div class="header-actions">
        <button class="plain-button">
          <el-icon><DocumentCopy /></el-icon>
          复制等级
        </button>
        <button class="solid-button">
          <el-icon><Plus /></el-icon>
          新建等级
        </button>
      </div>
    </header>

    <div class="stage-track" aria-label="用户等级">
      <button
        v-for="(level, index) in filteredLevels"
        :key="level.id"
        class="stage"
        :class="{ active: level.id === selectedId, incomplete: coverageRate(level) < 100 }"
        @click="emit('select', level.id)"
      >
        <span class="stage-number">{{ String(index + 1).padStart(2, '0') }}</span>
        <span class="stage-copy">
          <strong>{{ level.name }}</strong>
          <small>{{ formatNumber(level.userCount) }} 位用户</small>
        </span>
        <span class="stage-status">
          <i></i>
          {{ coverageRate(level) }}%
        </span>
      </button>
    </div>

    <div class="workspace-bar">
      <div class="workspace-caption">
        <span class="live-mark"></span>
        正在查看 <strong>{{ selectedLevel?.name || '未选择' }}</strong>
        <span class="caption-divider"></span>
        {{ filteredCategories.length }} 个分类
      </div>
      <div class="workspace-tools">
        <label class="compact-search">
          <el-icon><Search /></el-icon>
          <input :value="search" placeholder="筛选分类" @input="emitSearch" />
        </label>
        <button
          class="toggle-button"
          :class="{ active: onlyGaps }"
          @click="emit('update-only-gaps', !onlyGaps)"
        >
          <el-icon><Warning /></el-icon>
          只看缺口
        </button>
        <button class="plain-button compact">
          <el-icon><MagicStick /></el-icon>
          批量填充
        </button>
      </div>
    </div>

    <div class="workbench-grid">
      <main class="matrix-panel">
        <div class="matrix-title">
          <div>
            <h2>分类倍率矩阵</h2>
            <p>单元格内可直接调整倍率，橙色表示尚未配置。</p>
          </div>
          <div class="matrix-legend">
            <span><i class="legend-dot complete"></i>已配置</span>
            <span><i class="legend-dot gap"></i>缺口</span>
            <span><i class="legend-line"></i>当前等级</span>
          </div>
        </div>

        <div class="matrix-scroll">
          <table class="rate-matrix">
            <thead>
              <tr>
                <th class="category-column">商品分类</th>
                <th
                  v-for="level in filteredLevels"
                  :key="level.id"
                  :class="{ selected: level.id === selectedId }"
                >
                  <button class="column-label" @click="emit('select', level.id)">
                    <span>{{ level.shortName }}</span>
                    <small>{{ level.status === 1 ? '启用' : '草稿' }}</small>
                  </button>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="category in visibleCategories" :key="category.id">
                <td class="category-column">
                  <div class="category-name">
                    <span>{{ category.code.slice(0, 2) }}</span>
                    <div>
                      <strong>{{ category.name }}</strong>
                      <small>{{ category.code }}</small>
                    </div>
                  </div>
                </td>
                <td
                  v-for="level in filteredLevels"
                  :key="level.id"
                  :class="{ selected: level.id === selectedId }"
                >
                  <label
                    class="matrix-rate"
                    :class="{
                      empty: level.rates[category.id] == null,
                      readonly: level.isRetail
                    }"
                  >
                    <span>×</span>
                    <input
                      type="number"
                      min="0.0001"
                      max="1"
                      step="0.01"
                      :disabled="level.isRetail"
                      :value="level.rates[category.id] ?? ''"
                      :placeholder="level.isRetail ? '基准' : '未配置'"
                      @change="onRateChange(level.id, category.id, $event)"
                    />
                  </label>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="matrix-footer">
          <span>
            <el-icon><InfoFilled /></el-icon>
            倍率范围为 (0, 1]，保存后对新订单生效
          </span>
          <div>
            <button class="plain-button compact">撤销修改</button>
            <button class="solid-button compact">保存矩阵</button>
          </div>
        </div>
      </main>

      <aside v-if="selectedLevel" class="rule-inspector">
        <div class="inspector-kicker">当前规则</div>
        <div class="inspector-heading">
          <div>
            <h2>{{ selectedLevel.name }}</h2>
            <span>{{ selectedLevel.description }}</span>
          </div>
          <span class="mini-status" :class="{ inactive: selectedLevel.status !== 1 }">
            {{ selectedLevel.status === 1 ? '已启用' : '未启用' }}
          </span>
        </div>

        <div class="coverage-block">
          <div
            class="coverage-ring"
            :style="{
              background: `conic-gradient(var(--primary-color) ${selectedCoverage * 3.6}deg, var(--border-light) 0deg)`
            }"
          >
            <div>
              <strong>{{ selectedCoverage }}%</strong>
              <span>覆盖</span>
            </div>
          </div>
          <div class="coverage-copy">
            <span>已配置分类</span>
            <strong>{{ categories.length - selectedMissing.length }} / {{ categories.length }}</strong>
            <small v-if="selectedMissing.length">还缺 {{ selectedMissing.length }} 项</small>
            <small v-else>可以发布</small>
          </div>
        </div>

        <div class="inspector-stats">
          <div class="customer-stat">
            <span>归属用户</span>
            <strong>{{ formatNumber(selectedLevel.userCount) }}</strong>
            <button class="customer-link" @click="openMembers">
              查看客户
              <el-icon><ArrowRight /></el-icon>
            </button>
          </div>
          <div>
            <span>排序权重</span>
            <strong>{{ selectedLevel.sortOrder }}</strong>
          </div>
        </div>

        <div class="publish-checklist">
          <div class="checklist-title">
            <strong>发布检查</strong>
            <span>{{ selectedMissing.length ? '未通过' : '已通过' }}</span>
          </div>
          <div class="check-item">
            <el-icon :class="{ pass: selectedMissing.length === 0 }">
              <CircleCheck v-if="selectedMissing.length === 0" />
              <CircleClose v-else />
            </el-icon>
            分类倍率完整
          </div>
          <div class="check-item">
            <el-icon class="pass"><CircleCheck /></el-icon>
            至少有一名归属用户
          </div>
          <div class="check-item">
            <el-icon class="pass"><CircleCheck /></el-icon>
            倍率处于有效区间
          </div>
        </div>

        <div class="price-example">
          <div class="example-head">
            <span>结算价样例</span>
            <small>不锈钢静音门锁</small>
          </div>
          <div class="example-price">
            <span>零售 ¥268.00</span>
            <strong>{{ examplePrice }}</strong>
          </div>
          <p>按当前分类倍率计算客户实际结算价</p>
        </div>

        <div class="inspector-meta">
          <span>负责人 {{ selectedLevel.owner }}</span>
          <span>更新于 {{ selectedLevel.updatedAt.slice(5) }}</span>
        </div>

        <div class="inspector-actions">
          <button class="plain-button" @click="openPreview">
            <el-icon><View /></el-icon>
            在商品管理中查看
          </button>
          <button class="solid-button" :disabled="selectedMissing.length > 0">发布规则</button>
        </div>
      </aside>
    </div>

    <Teleport to="body">
      <Transition name="proto-overlay">
        <div
          v-if="membersVisible"
          class="proto-overlay member-overlay"
          @click.self="membersVisible = false"
        >
          <aside class="member-drawer" role="dialog" aria-modal="true" aria-label="归属客户">
            <header class="drawer-header">
              <div>
                <span class="drawer-kicker">CUSTOMER ALLOCATION</span>
                <h2>归属客户</h2>
                <p>{{ selectedLevel?.name }} · 共 {{ formatNumber(selectedLevel?.userCount || 0) }} 名客户</p>
              </div>
              <button class="drawer-close" title="关闭" @click="membersVisible = false">
                <el-icon><Close /></el-icon>
              </button>
            </header>

            <div class="drawer-toolbar">
              <label class="drawer-search">
                <el-icon><Search /></el-icon>
                <input
                  v-model="memberQuery"
                  placeholder="搜索客户、企业、城市或标签"
                />
              </label>
              <span>{{ filteredMembers.length }} 条样例</span>
            </div>

            <div class="member-list">
              <article
                v-for="member in filteredMembers"
                :key="member.id"
                class="member-card"
              >
                <span class="member-avatar">{{ member.company.slice(0, 1) }}</span>
                <div class="member-copy">
                  <div class="member-title">
                    <strong>{{ member.company }}</strong>
                    <span>{{ member.contact }}</span>
                  </div>
                  <div class="member-meta">
                    <span><el-icon><Location /></el-icon>{{ member.city }}</span>
                    <span><el-icon><Calendar /></el-icon>{{ member.lastOrder }}</span>
                  </div>
                  <div class="member-tags">
                    <span v-for="tag in member.tags" :key="tag">{{ tag }}</span>
                  </div>
                </div>
                <div class="member-order-count">
                  <strong>{{ member.orders }}</strong>
                  <span>累计订单</span>
                </div>
              </article>

              <div v-if="!filteredMembers.length" class="drawer-empty">
                <el-icon><User /></el-icon>
                <strong>没有匹配的客户</strong>
                <span>当前等级尚未分配客户，或搜索条件过窄。</span>
              </div>
            </div>

            <footer class="drawer-footer">
              <span>原型仅展示前 {{ sampleMembers.length }} 名样例客户</span>
              <button class="solid-button compact" @click="membersVisible = false">完成</button>
            </footer>
          </aside>
        </div>
      </Transition>

      <Transition name="proto-overlay">
        <div
          v-if="previewVisible && selectedLevel"
          class="proto-overlay price-overlay"
          @click.self="previewVisible = false"
        >
          <section class="price-dialog" role="dialog" aria-modal="true" aria-label="结算价预览">
            <header class="price-dialog-header">
              <div>
                <span class="drawer-kicker">PRICE PREVIEW</span>
                <h2>结算价预览</h2>
                <p>{{ selectedLevel.name }}客户在商城看到的代表商品结算价格。</p>
              </div>
              <button class="drawer-close" title="关闭" @click="previewVisible = false">
                <el-icon><Close /></el-icon>
              </button>
            </header>

            <div class="preview-context">
              <span class="preview-level">{{ selectedLevel.shortName }}</span>
              <div>
                <strong>{{ selectedLevel.name }}</strong>
                <span>{{ formatNumber(selectedLevel.userCount) }} 名客户使用此价格规则</span>
              </div>
              <span class="preview-badge" :class="{ warning: selectedMissing.length }">
                {{ selectedMissing.length ? `${selectedMissing.length} 项缺口` : '规则完整' }}
              </span>
            </div>

            <div class="price-preview-grid">
              <article
                v-for="row in previewRows"
                :key="row.product.id"
                class="price-card"
                :class="{ fallback: row.rate == null }"
              >
                <div class="product-visual">
                  <el-icon><component :is="row.product.icon" /></el-icon>
                  <span>{{ row.product.categoryName }}</span>
                </div>
                <div class="product-copy">
                  <span class="product-code">{{ row.product.code }}</span>
                  <strong>{{ row.product.name }}</strong>
                  <small>{{ row.product.spec }}</small>
                </div>
                <div class="price-calculation">
                  <span>零售 {{ formatCurrency(row.product.retailPrice) }}</span>
                  <span>倍率 {{ row.rate == null ? '未配置' : row.rate.toFixed(4) }}</span>
                </div>
                <div class="settlement-price">
                  <span>{{ row.rate == null ? '退回零售价' : '客户结算价' }}</span>
                  <strong>{{ formatCurrency(row.price) }}</strong>
                </div>
              </article>
            </div>

            <div class="preview-note">
              <el-icon><InfoFilled /></el-icon>
              <span>未配置分类会按零售价兜底；正式页面应接入商品分页接口，而不是使用这里固定的样例。</span>
            </div>

            <footer class="price-dialog-footer">
              <span>预览不会保存任何价格调整</span>
              <button class="solid-button compact" @click="previewVisible = false">关闭预览</button>
            </footer>
          </section>
        </div>
      </Transition>
    </Teleport>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  coverageRate,
  draftCategories,
  formatNumber,
  missingCategories,
  type DraftCategory,
  type DraftLevel
} from './prototypeData'

const props = defineProps<{
  levels: DraftLevel[]
  selectedId: number
  search: string
  onlyGaps: boolean
}>()
const router = useRouter()

const emit = defineEmits<{
  select: [id: number]
  'update-search': [value: string]
  'update-only-gaps': [value: boolean]
  'update-rate': [levelId: number, categoryId: number, rate: number | null]
}>()

interface DraftMember {
  id: number
  company: string
  contact: string
  city: string
  lastOrder: string
  orders: number
  tags: string[]
}

interface PreviewProduct {
  id: number
  categoryId: number
  categoryName: string
  code: string
  name: string
  spec: string
  retailPrice: number
  icon: string
}

const categories: DraftCategory[] = draftCategories
const previewVisible = ref(false)
const membersVisible = ref(false)
const memberQuery = ref('')

const membersByLevel: Record<number, DraftMember[]> = {
  1: [
    {
      id: 101,
      company: '林晓雨',
      contact: '个人客户',
      city: '德州市',
      lastOrder: '10-27 18:24',
      orders: 12,
      tags: ['家用五金', '锁具']
    },
    {
      id: 102,
      company: '周明远',
      contact: '个人客户',
      city: '济南市',
      lastOrder: '10-26 09:18',
      orders: 7,
      tags: ['工具', '紧固件']
    },
    {
      id: 103,
      company: '陈师傅',
      contact: '个人客户',
      city: '青岛市',
      lastOrder: '10-24 16:52',
      orders: 5,
      tags: ['维修耗材']
    }
  ],
  2: [
    {
      id: 201,
      company: '宏达建材',
      contact: '王宏 · 采购负责人',
      city: '德州市',
      lastOrder: '10-28 10:32',
      orders: 86,
      tags: ['企业认证', '月采购 18 单']
    },
    {
      id: 202,
      company: '远鑫五金',
      contact: '赵经理',
      city: '聊城市',
      lastOrder: '10-27 14:06',
      orders: 64,
      tags: ['企业认证', '锁具批发']
    },
    {
      id: 203,
      company: '金诚装饰材料',
      contact: '孙女士',
      city: '泰安市',
      lastOrder: '10-25 11:40',
      orders: 41,
      tags: ['企业认证', '拉手合页']
    },
    {
      id: 204,
      company: '鲁北工贸',
      contact: '刘主管',
      city: '滨州市',
      lastOrder: '10-23 17:15',
      orders: 36,
      tags: ['企业认证', '工程辅材']
    }
  ],
  3: [
    {
      id: 301,
      company: '城开建设',
      contact: '李工 · 项目经理',
      city: '济南市',
      lastOrder: '10-28 08:45',
      orders: 29,
      tags: ['项目报备', '工程五金']
    },
    {
      id: 302,
      company: '中建筑装',
      contact: '张经理',
      city: '青岛市',
      lastOrder: '10-26 15:20',
      orders: 23,
      tags: ['项目报备', '门锁合页']
    },
    {
      id: 303,
      company: '齐鲁公装',
      contact: '高工',
      city: '淄博市',
      lastOrder: '10-22 13:36',
      orders: 18,
      tags: ['工程客户', '批量采购']
    },
    {
      id: 304,
      company: '恒基建筑',
      contact: '马经理',
      city: '临沂市',
      lastOrder: '10-19 10:08',
      orders: 14,
      tags: ['工程客户', '待补全资料']
    }
  ],
  4: [
    {
      id: 401,
      company: '中装供应链',
      contact: '赵经理',
      city: '青岛市',
      lastOrder: '10-27 16:18',
      orders: 108,
      tags: ['年度协议', '人工审核']
    },
    {
      id: 402,
      company: '鼎盛工程配套',
      contact: '陈总',
      city: '济南市',
      lastOrder: '10-24 09:50',
      orders: 92,
      tags: ['年度协议', '重点客户']
    },
    {
      id: 403,
      company: '华建物资',
      contact: '杨主管',
      city: '烟台市',
      lastOrder: '10-20 14:26',
      orders: 71,
      tags: ['年度协议', '工程集采']
    }
  ],
  5: []
}

const previewProducts: PreviewProduct[] = [
  {
    id: 1,
    categoryId: 1,
    categoryName: '锁具',
    code: 'LOCK-3048',
    name: '304不锈钢静音锁体',
    spec: '5850 双舌 / 拉丝银',
    retailPrice: 268,
    icon: 'Lock'
  },
  {
    id: 2,
    categoryId: 2,
    categoryName: '拉手',
    code: 'HANDLE-160',
    name: '铝合金极简拉手',
    spec: '160mm / 哑光黑',
    retailPrice: 89,
    icon: 'Link'
  },
  {
    id: 3,
    categoryId: 3,
    categoryName: '合页',
    code: 'HINGE-4IN',
    name: '液压缓冲合页',
    spec: '4寸 / 不锈钢',
    retailPrice: 56,
    icon: 'Connection'
  },
  {
    id: 4,
    categoryId: 4,
    categoryName: '门吸闭门器',
    code: 'CLOSER-908',
    name: '强磁静音门吸',
    spec: '加厚款 / 不锈钢',
    retailPrice: 42,
    icon: 'Box'
  }
]

const selectedLevel = computed(() => props.levels.find(level => level.id === props.selectedId))
const selectedMissing = computed(() =>
  selectedLevel.value ? missingCategories(selectedLevel.value) : []
)
const selectedCoverage = computed(() =>
  selectedLevel.value ? coverageRate(selectedLevel.value) : 0
)
const filteredLevels = computed(() => props.levels)
const visibleCategories = computed(() => {
  const keyword = props.search.trim().toLowerCase()
  return categories.filter(category => {
    const matchesKeyword =
      !keyword || `${category.name} ${category.code}`.toLowerCase().includes(keyword)
    const missesForSelected =
      !props.onlyGaps || selectedLevel.value?.rates[category.id] == null
    return matchesKeyword && missesForSelected
  })
})
const filteredCategories = visibleCategories
const examplePrice = computed(() => {
  const rate = selectedLevel.value?.rates[1] ?? 1
  return `¥${(268 * rate).toFixed(2)}`
})
const sampleMembers = computed(() => membersByLevel[props.selectedId] || [])
const filteredMembers = computed(() => {
  const keyword = memberQuery.value.trim().toLowerCase()
  if (!keyword) return sampleMembers.value
  return sampleMembers.value.filter(member =>
    [
      member.company,
      member.contact,
      member.city,
      member.tags.join(' ')
    ]
      .join(' ')
      .toLowerCase()
      .includes(keyword)
  )
})
const previewRows = computed(() =>
  previewProducts.map(product => {
    const rate = selectedLevel.value?.rates[product.categoryId]
    return {
      product,
      rate,
      price: product.retailPrice * (rate ?? 1)
    }
  })
)

const emitSearch = (event: Event) => {
  emit('update-search', (event.target as HTMLInputElement).value)
}

const openMembers = () => {
  router.push({
    path: '/user',
    query: {
      prototype: 'price-view',
      levelId: String(props.selectedId)
    }
  })
}

const openPreview = () => {
  router.push({
    path: '/spu',
    query: {
      prototype: 'price-view',
      levelId: String(props.selectedId)
    }
  })
}

const formatCurrency = (value: number) => `¥${value.toFixed(2)}`

const onRateChange = (levelId: number, categoryId: number, event: Event) => {
  const raw = (event.target as HTMLInputElement).value
  const rate = raw === '' ? null : Number(raw)
  emit('update-rate', levelId, categoryId, rate != null && Number.isFinite(rate) ? rate : null)
}

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    membersVisible.value = false
    previewVisible.value = false
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.variant-b {
  --ink: var(--text-primary);
  --muted: var(--text-secondary);
  --line: var(--border-color);
  --soft: var(--bg-page);
  --copper: var(--primary-color);
  color: var(--ink);
}

.workbench-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
}

.section-code {
  color: var(--copper);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.header-copy h1 {
  margin: 8px 0 4px;
  font-family: var(--font-display);
  font-size: clamp(30px, 3vw, 42px);
  line-height: 1.08;
}

.header-copy p,
.matrix-title p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}

.header-actions,
.workspace-tools,
.matrix-footer > div,
.inspector-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.plain-button,
.solid-button,
.toggle-button {
  min-height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 13px;
  border: 1px solid var(--line);
  border-radius: 5px;
  color: var(--ink);
  background: var(--bg-card);
  font-size: 12px;
  font-weight: 600;
  transition: 160ms ease;
}

.plain-button:hover,
.toggle-button:hover {
  border-color: var(--copper);
}

.solid-button {
  color: #fff;
  border-color: var(--ink);
  background: var(--ink);
}

.solid-button:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 5px 14px rgba(0, 0, 0, 0.14);
}

.solid-button:disabled {
  cursor: not-allowed;
  opacity: 0.42;
}

.compact {
  min-height: 30px;
  padding: 0 10px;
  font-size: 11px;
}

.stage-track {
  position: relative;
  margin-top: 28px;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  border: 1px solid var(--line);
  background: var(--bg-card);
}

.stage {
  position: relative;
  min-width: 0;
  min-height: 88px;
  padding: 14px 16px;
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr);
  align-content: center;
  gap: 4px 10px;
  border: 0;
  border-right: 1px solid var(--line);
  color: var(--ink);
  text-align: left;
  background: transparent;
  transition: 180ms ease;
}

.stage:last-child {
  border-right: 0;
}

.stage::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 3px;
  background: var(--copper);
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 180ms ease;
}

.stage:hover {
  background: var(--primary-bg);
}

.stage.active {
  background: var(--primary-bg);
}

.stage.active::after {
  transform: scaleX(1);
}

.stage-number {
  grid-row: span 2;
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: 10px;
}

.stage-copy {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.stage-copy strong {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
}

.stage-copy small,
.stage-status {
  color: var(--text-tertiary);
  font-size: 10px;
}

.stage-status {
  display: flex;
  align-items: center;
  gap: 5px;
}

.stage-status i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--success);
}

.stage.incomplete .stage-status i {
  background: var(--warning);
}

.workspace-bar {
  min-height: 54px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-right: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  border-left: 1px solid var(--line);
  background: var(--bg-card);
}

.workspace-caption {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--muted);
  font-size: 12px;
}

.workspace-caption strong {
  color: var(--ink);
}

.live-mark {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--copper);
  box-shadow: 0 0 0 4px var(--primary-bg);
}

.caption-divider {
  width: 1px;
  height: 14px;
  background: var(--line);
}

.compact-search {
  height: 32px;
  padding: 0 9px;
  display: flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--line);
  border-radius: 5px;
  color: var(--text-tertiary);
  background: var(--bg-card);
}

.compact-search input {
  width: 110px;
  border: 0;
  outline: 0;
  color: var(--ink);
  background: transparent;
  font-size: 11px;
}

.toggle-button {
  min-height: 32px;
  color: var(--muted);
}

.toggle-button.active {
  color: var(--warning);
  border-color: #ead9b7;
  background: var(--warning-light);
}

.workbench-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 292px;
  border-right: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  border-left: 1px solid var(--line);
  background: var(--bg-card);
}

.matrix-panel {
  min-width: 0;
  padding: 20px 20px 14px;
  border-right: 1px solid var(--line);
}

.matrix-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.matrix-title h2 {
  margin: 0 0 4px;
  font-family: var(--font-body);
  font-size: 16px;
}

.matrix-legend {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--text-tertiary);
  font-size: 9px;
  white-space: nowrap;
}

.matrix-legend span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.legend-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.legend-dot.complete {
  background: var(--success);
}

.legend-dot.gap {
  background: var(--warning);
}

.legend-line {
  width: 13px;
  height: 2px;
  background: var(--copper);
}

.matrix-scroll {
  overflow-x: auto;
  border: 1px solid var(--line);
}

.rate-matrix {
  width: 100%;
  min-width: 720px;
  border-collapse: collapse;
  table-layout: fixed;
}

.rate-matrix th,
.rate-matrix td {
  border-right: 1px solid var(--border-light);
  border-bottom: 1px solid var(--border-light);
}

.rate-matrix th:last-child,
.rate-matrix td:last-child {
  border-right: 0;
}

.rate-matrix tbody tr:last-child td {
  border-bottom: 0;
}

.rate-matrix th {
  height: 46px;
  background: var(--soft);
  color: var(--text-tertiary);
  font-size: 10px;
  font-weight: 700;
}

.rate-matrix th.selected,
.rate-matrix td.selected {
  background: var(--primary-bg);
  box-shadow: inset 1px 0 0 var(--primary-color), inset -1px 0 0 var(--primary-color);
}

.category-column {
  width: 170px;
  text-align: left;
}

.column-label {
  width: 100%;
  border: 0;
  display: grid;
  justify-items: center;
  gap: 2px;
  color: var(--ink);
  background: transparent;
}

.column-label span {
  font-size: 11px;
  font-weight: 700;
}

.column-label small {
  color: var(--text-tertiary);
  font-size: 9px;
}

.rate-matrix td {
  height: 54px;
  padding: 7px;
}

.category-name {
  height: 100%;
  padding-left: 9px;
  display: flex;
  align-items: center;
  gap: 9px;
}

.category-name > span {
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  display: grid;
  place-items: center;
  border: 1px solid var(--line);
  border-radius: 4px;
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: 8px;
}

.category-name div {
  display: grid;
}

.category-name strong {
  font-size: 11px;
}

.category-name small {
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: 8px;
}

.matrix-rate {
  height: 34px;
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 0 8px;
  border: 1px solid transparent;
  border-radius: 4px;
  color: var(--text-tertiary);
  background: var(--bg-card);
}

.matrix-rate:hover {
  border-color: var(--line);
}

.matrix-rate:focus-within {
  border-color: var(--copper);
  box-shadow: 0 0 0 2px var(--primary-bg);
}

.matrix-rate.empty {
  color: var(--warning);
  border-color: #ead9b7;
  background: var(--warning-light);
}

.matrix-rate.readonly {
  opacity: 0.66;
}

.matrix-rate input {
  width: 100%;
  min-width: 0;
  border: 0;
  outline: 0;
  color: var(--ink);
  background: transparent;
  font-family: var(--font-mono);
  font-size: 10px;
}

.matrix-rate input::-webkit-inner-spin-button {
  display: none;
}

.matrix-footer {
  min-height: 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  color: var(--text-tertiary);
  font-size: 10px;
}

.matrix-footer > span {
  display: flex;
  align-items: center;
  gap: 5px;
}

.rule-inspector {
  min-width: 0;
  padding: 20px;
  background:
    linear-gradient(160deg, var(--primary-bg), transparent 30%),
    var(--bg-card);
}

.inspector-kicker {
  color: var(--copper);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.inspector-heading {
  margin-top: 8px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.inspector-heading h2 {
  margin: 0;
  font-size: 20px;
}

.inspector-heading span {
  display: block;
  margin-top: 5px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.55;
}

.mini-status {
  flex: 0 0 auto;
  padding: 3px 6px;
  border-radius: 4px;
  color: var(--success);
  background: var(--success-light);
  font-size: 9px;
  font-weight: 700;
}

.mini-status.inactive {
  color: var(--muted);
  background: var(--soft);
}

.coverage-block {
  margin-top: 20px;
  padding-bottom: 18px;
  display: flex;
  align-items: center;
  gap: 16px;
  border-bottom: 1px solid var(--line);
}

.coverage-ring {
  width: 78px;
  height: 78px;
  flex: 0 0 78px;
  padding: 6px;
  border-radius: 50%;
}

.coverage-ring > div {
  width: 100%;
  height: 100%;
  display: grid;
  place-content: center;
  justify-items: center;
  border-radius: 50%;
  background: var(--bg-card);
}

.coverage-ring strong {
  font-family: var(--font-mono);
  font-size: 17px;
}

.coverage-ring span,
.coverage-copy span,
.coverage-copy small {
  color: var(--text-tertiary);
  font-size: 9px;
}

.coverage-copy {
  display: grid;
  gap: 3px;
}

.coverage-copy strong {
  font-family: var(--font-mono);
  font-size: 16px;
}

.inspector-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  border-bottom: 1px solid var(--line);
}

.inspector-stats > div {
  padding: 14px 0;
  display: grid;
  gap: 2px;
}

.inspector-stats > div + div {
  padding-left: 16px;
  border-left: 1px solid var(--line);
}

.inspector-stats span {
  color: var(--text-tertiary);
  font-size: 9px;
}

.inspector-stats strong {
  font-family: var(--font-mono);
  font-size: 14px;
}

.customer-stat {
  align-content: start;
}

.customer-link {
  width: max-content;
  margin-top: 4px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: 0;
  color: var(--copper);
  background: transparent;
  font-size: 9px;
  font-weight: 700;
}

.customer-link:hover {
  color: var(--primary-dark);
}

.publish-checklist {
  padding: 16px 0;
  border-bottom: 1px solid var(--line);
}

.checklist-title {
  margin-bottom: 10px;
  display: flex;
  justify-content: space-between;
  font-size: 11px;
}

.checklist-title span {
  color: var(--warning);
}

.check-item {
  min-height: 25px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--muted);
  font-size: 10px;
}

.check-item .el-icon {
  color: var(--danger);
}

.check-item .el-icon.pass {
  color: var(--success);
}

.price-example {
  margin-top: 16px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--bg-card);
}

.example-head,
.example-price {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.example-head span {
  font-size: 10px;
  font-weight: 700;
}

.example-head small {
  font-size: 9px;
}

.example-price {
  margin-top: 8px;
}

.example-price span {
  color: var(--text-tertiary);
  font-size: 9px;
}

.example-price strong {
  color: var(--danger);
  font-family: var(--font-mono);
  font-size: 16px;
}

.price-example p {
  margin: 7px 0 0;
  padding-top: 7px;
  border-top: 1px dashed var(--border-light);
  color: var(--text-tertiary);
  font-size: 8px;
}

.inspector-meta {
  padding: 12px 0;
  display: flex;
  justify-content: space-between;
  gap: 8px;
  color: var(--text-tertiary);
  font-size: 9px;
}

.inspector-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
}

.change-ledger {
  margin-top: 20px;
  border: 1px solid var(--line);
  background: var(--bg-card);
}

.ledger-heading {
  min-height: 46px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--line);
}

.ledger-heading strong {
  font-size: 12px;
}

.ledger-heading button {
  border: 0;
  color: var(--copper);
  background: transparent;
  font-size: 10px;
  font-weight: 600;
}

.change-item {
  min-height: 40px;
  padding: 0 16px;
  display: grid;
  grid-template-columns: 88px 80px minmax(0, 1fr) 160px;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid var(--border-light);
  color: var(--muted);
  font-size: 10px;
}

.change-item:last-child {
  border-bottom: 0;
}

.change-time {
  color: var(--text-tertiary);
  font-family: var(--font-mono);
}

.change-user {
  color: var(--accent-color);
  font-weight: 600;
}

.change-item code {
  color: var(--text-tertiary);
  font-size: 9px;
  text-align: right;
}

.proto-overlay {
  position: fixed;
  inset: 0;
  z-index: 400;
  padding: 24px;
  display: flex;
  background: rgba(15, 18, 20, 0.46);
  backdrop-filter: blur(3px);
}

.member-overlay {
  justify-content: flex-end;
  padding: 0;
}

.member-drawer {
  width: min(470px, 100%);
  height: 100%;
  display: flex;
  flex-direction: column;
  border-left: 1px solid rgba(255, 255, 255, 0.18);
  background: var(--bg-card);
  box-shadow: -24px 0 64px rgba(15, 18, 20, 0.22);
}

.drawer-header,
.price-dialog-header {
  padding: 22px 22px 18px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  border-bottom: 1px solid var(--line);
}

.drawer-kicker {
  color: var(--copper);
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.1em;
}

.drawer-header h2,
.price-dialog-header h2 {
  margin: 6px 0 3px;
  font-family: var(--font-display);
  font-size: 28px;
}

.drawer-header p,
.price-dialog-header p {
  margin: 0;
  color: var(--muted);
  font-size: 11px;
}

.drawer-close {
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  display: grid;
  place-items: center;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--muted);
  background: var(--bg-card);
  font-size: 15px;
}

.drawer-close:hover {
  color: var(--ink);
  border-color: var(--copper);
}

.drawer-toolbar {
  padding: 12px 22px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--line);
  background: var(--soft);
}

.drawer-search {
  height: 36px;
  flex: 1;
  padding: 0 10px;
  display: flex;
  align-items: center;
  gap: 7px;
  border: 1px solid var(--line);
  border-radius: 5px;
  color: var(--text-tertiary);
  background: var(--bg-card);
}

.drawer-search:focus-within {
  border-color: var(--copper);
  box-shadow: 0 0 0 3px var(--primary-bg);
}

.drawer-search input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  color: var(--ink);
  background: transparent;
  font-size: 11px;
}

.drawer-toolbar > span {
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: 9px;
  white-space: nowrap;
}

.member-list {
  flex: 1;
  min-height: 0;
  padding: 6px 22px;
  overflow-y: auto;
}

.member-card {
  min-height: 96px;
  padding: 15px 0;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  align-items: start;
  gap: 12px;
  border-bottom: 1px solid var(--border-light);
}

.member-avatar {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border: 1px solid var(--border-accent);
  border-radius: 50%;
  color: var(--primary-dark);
  background: var(--primary-bg);
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 600;
}

.member-copy {
  min-width: 0;
  display: grid;
  gap: 7px;
}

.member-title {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 7px;
}

.member-title strong {
  font-size: 12px;
}

.member-title span {
  color: var(--text-tertiary);
  font-size: 9px;
}

.member-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.member-meta span {
  display: flex;
  align-items: center;
  gap: 3px;
  color: var(--muted);
  font-size: 9px;
}

.member-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.member-tags span {
  padding: 2px 6px;
  border-radius: 3px;
  color: var(--accent-color);
  background: var(--info-light);
  font-size: 8px;
}

.member-order-count {
  padding-left: 12px;
  display: grid;
  justify-items: end;
  gap: 1px;
  border-left: 1px solid var(--border-light);
}

.member-order-count strong {
  font-family: var(--font-mono);
  font-size: 14px;
}

.member-order-count span {
  color: var(--text-tertiary);
  font-size: 8px;
}

.drawer-empty {
  min-height: 260px;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 7px;
  color: var(--text-tertiary);
  text-align: center;
}

.drawer-empty .el-icon {
  margin-bottom: 4px;
  font-size: 28px;
}

.drawer-empty strong {
  color: var(--muted);
  font-size: 12px;
}

.drawer-empty span {
  max-width: 240px;
  font-size: 9px;
  line-height: 1.55;
}

.drawer-footer,
.price-dialog-footer {
  min-height: 64px;
  padding: 0 22px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-top: 1px solid var(--line);
}

.drawer-footer > span,
.price-dialog-footer > span {
  color: var(--text-tertiary);
  font-size: 9px;
}

.price-overlay {
  align-items: center;
  justify-content: center;
}

.price-dialog {
  width: min(780px, 100%);
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  border: 1px solid var(--line);
  background: var(--bg-card);
  box-shadow: 0 24px 72px rgba(15, 18, 20, 0.28);
}

.preview-context {
  margin: 18px 22px 0;
  padding: 12px 14px;
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--border-accent);
  background: var(--primary-bg);
}

.preview-level {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  background: var(--ink);
  font-family: var(--font-display);
  font-size: 16px;
}

.preview-context > div {
  display: grid;
  gap: 1px;
}

.preview-context strong {
  font-size: 12px;
}

.preview-context > div span {
  color: var(--muted);
  font-size: 9px;
}

.preview-badge {
  margin-left: auto;
  padding: 4px 7px;
  border-radius: 3px;
  color: var(--success);
  background: var(--success-light);
  font-size: 9px;
  font-weight: 700;
}

.preview-badge.warning {
  color: var(--warning);
  background: var(--warning-light);
}

.price-preview-grid {
  padding: 18px 22px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.price-card {
  min-height: 142px;
  padding: 14px;
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr);
  align-content: start;
  gap: 9px 12px;
  border: 1px solid var(--line);
  background: var(--bg-card);
}

.price-card.fallback {
  border-color: #ead9b7;
  background: linear-gradient(135deg, var(--warning-light), var(--bg-card) 60%);
}

.product-visual {
  width: 54px;
  height: 54px;
  grid-row: span 2;
  display: grid;
  place-items: center;
  border: 1px solid var(--border-accent);
  color: var(--primary-dark);
  background: var(--primary-bg);
  font-size: 21px;
}

.product-visual span {
  display: none;
}

.product-copy {
  min-width: 0;
  display: grid;
  gap: 1px;
}

.product-code {
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: 8px;
}

.product-copy strong {
  font-size: 11px;
}

.product-copy small {
  color: var(--muted);
  font-size: 9px;
}

.price-calculation {
  grid-column: 1 / -1;
  padding-top: 9px;
  display: flex;
  justify-content: space-between;
  gap: 8px;
  border-top: 1px dashed var(--border-light);
  color: var(--text-tertiary);
  font-size: 8px;
}

.settlement-price {
  grid-column: 1 / -1;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.settlement-price span {
  color: var(--muted);
  font-size: 9px;
}

.settlement-price strong {
  color: var(--danger);
  font-family: var(--font-mono);
  font-size: 20px;
}

.price-card.fallback .settlement-price strong {
  color: var(--warning);
}

.preview-note {
  margin: 0 22px 18px;
  padding: 10px 12px;
  display: flex;
  align-items: flex-start;
  gap: 7px;
  color: var(--text-secondary);
  background: var(--soft);
  font-size: 9px;
  line-height: 1.55;
}

.preview-note .el-icon {
  margin-top: 1px;
  color: var(--copper);
}

.proto-overlay-enter-active,
.proto-overlay-leave-active {
  transition: opacity 160ms ease;
}

.proto-overlay-enter-active .member-drawer,
.proto-overlay-leave-active .member-drawer {
  transition: transform 200ms ease;
}

.proto-overlay-enter-active .price-dialog,
.proto-overlay-leave-active .price-dialog {
  transition: transform 180ms ease, opacity 160ms ease;
}

.proto-overlay-enter-from,
.proto-overlay-leave-to {
  opacity: 0;
}

.proto-overlay-enter-from .member-drawer,
.proto-overlay-leave-to .member-drawer {
  transform: translateX(100%);
}

.proto-overlay-enter-from .price-dialog,
.proto-overlay-leave-to .price-dialog {
  opacity: 0;
  transform: translateY(10px) scale(0.985);
}

@media (max-width: 1180px) {
  .workbench-grid {
    grid-template-columns: minmax(0, 1fr) 260px;
  }

  .matrix-legend {
    display: none;
  }

  .stage-track {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .stage:nth-child(3) {
    border-right: 0;
  }

  .stage:nth-child(-n + 3) {
    border-bottom: 1px solid var(--line);
  }
}

@media (max-width: 920px) {
  .workbench-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .workspace-bar {
    padding: 12px 16px;
    align-items: flex-start;
    flex-direction: column;
  }

  .workspace-tools {
    width: 100%;
    flex-wrap: wrap;
  }

  .workbench-grid {
    grid-template-columns: 1fr;
  }

  .matrix-panel {
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }
}

@media (max-width: 640px) {
  .proto-overlay {
    padding: 10px;
  }

  .member-overlay {
    padding: 0;
  }

  .member-drawer {
    width: 100%;
  }

  .drawer-header,
  .price-dialog-header {
    padding: 18px 16px 14px;
  }

  .drawer-toolbar,
  .member-list,
  .price-preview-grid {
    padding-right: 16px;
    padding-left: 16px;
  }

  .price-preview-grid {
    grid-template-columns: 1fr;
  }

  .preview-context {
    margin-right: 16px;
    margin-left: 16px;
  }

  .preview-note {
    margin-right: 16px;
    margin-left: 16px;
  }

  .drawer-footer,
  .price-dialog-footer {
    padding: 0 16px;
  }

  .stage-track {
    grid-template-columns: 1fr;
  }

  .stage {
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }

  .matrix-title {
    flex-direction: column;
  }

  .matrix-footer {
    padding-top: 10px;
    align-items: flex-start;
    flex-direction: column;
  }

  .change-item {
    padding: 10px 16px;
    grid-template-columns: 90px 1fr;
  }

  .change-item code {
    text-align: left;
  }
}
</style>
