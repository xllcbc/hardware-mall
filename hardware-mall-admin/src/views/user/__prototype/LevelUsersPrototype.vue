<template>
  <div class="user-management">
    <div class="prototype-banner">
      <span>PROTOTYPE</span>
      <p>沿用现有用户管理表格与操作，只增加用户等级筛选。</p>
      <code>levelId={{ currentLevel.id }}</code>
      <div class="layout-switch">
        <button :class="{ active: isCompact }" @click="setLayout('compact')">紧凑视图</button>
        <button :class="{ active: !isCompact }" @click="setLayout('full')">完整字段</button>
      </div>
    </div>

    <div class="page-header animate-fade-in-down">
      <div class="header-content">
        <h2 class="page-title">用户管理</h2>
        <p class="page-desc">查看和管理平台用户信息</p>
      </div>
      <el-button type="primary" plain @click="goToPriceView">
        <el-icon><Goods /></el-icon>
        查看该等级商品价格
      </el-button>
    </div>

    <div class="card table-card animate-fade-in-up stagger-1">
      <div class="filter-bar prototype-filter">
        <div class="filter-item">
          <span class="filter-label">用户等级</span>
          <el-select
            :model-value="currentLevel.id"
            class="level-select"
            @change="changeLevel"
          >
            <el-option
              v-for="level in levels"
              :key="level.id"
              :label="level.name"
              :value="level.id"
            />
          </el-select>
        </div>
        <div class="filter-item">
          <span class="filter-label">所在地区</span>
          <el-cascader
            v-model="regionFilter"
            :options="provinceCityOptions"
            placeholder="选择省市"
            clearable
            filterable
            class="region-cascader"
          />
        </div>
        <div class="filter-result">
          <strong>{{ filteredUsers.length }}</strong> 条样例
          <span>· 正式页面继续使用服务端分页</span>
        </div>
      </div>

      <el-table :data="filteredUsers" stripe class="user-table">
        <el-table-column v-if="!isCompact" prop="id" label="ID" width="80" />
        <el-table-column label="用户信息" min-width="200">
          <template #default="{ row }">
            <div class="user-info-cell">
              <el-avatar :size="40" class="user-avatar">
                {{ row.nickname?.slice(0, 1) }}
              </el-avatar>
              <div class="user-detail">
                <span class="user-name">{{ row.nickname }}</span>
                <span class="user-id">ID: {{ row.id }} · {{ row.company }}</span>
                <span v-if="isCompact" class="compact-register">
                  注册 {{ row.createTime.slice(0, 10) }} · 最后登录 {{ row.lastLoginTime.slice(5, 16) }}
                </span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" :width="isCompact ? 150 : 130">
          <template #default="{ row }">
            <span class="phone">{{ row.phone }}</span>
            <span v-if="isCompact" class="compact-role">
              {{ row.role === 2 ? '管理员' : '普通用户' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column v-if="!isCompact" prop="role" label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 2 ? 'warning' : 'primary'" size="small" effect="light">
              {{ row.role === 2 ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="用户等级" width="130">
          <template #default="{ row }">
            <el-tag
              :type="row.levelId === 1 ? 'info' : 'success'"
              size="small"
              effect="light"
            >
              {{ levelName(row.levelId) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="地区" width="120">
          <template #default="{ row }">
            <span class="region">{{ row.city || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" effect="light">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="lastLoginTime"
          label="最后登录"
          :width="isCompact ? 145 : 170"
        />
        <el-table-column v-if="!isCompact" prop="createTime" label="注册时间" width="170" />
        <el-table-column
          label="操作"
          :width="isCompact ? 150 : 360"
          :fixed="isCompact ? false : 'right'"
        >
          <template #default="{ row }">
            <div v-if="isCompact" class="compact-actions">
              <el-tooltip content="查看订单">
                <el-button link type="primary" @click="viewOrders(row)">
                  <el-icon><List /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="编辑地区">
                <el-button link type="success" @click="openRegion(row)">
                  <el-icon><Location /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip :content="row.status === 1 ? '禁用用户' : '启用用户'">
                <el-button
                  link
                  :type="row.status === 1 ? 'warning' : 'success'"
                  @click="toggleStatus(row)"
                >
                  <el-icon><Switch /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="调整所属群体">
                <el-button link type="warning" @click="openLevel(row)">
                  <el-icon><User /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
            <template v-else>
              <el-button link type="primary" @click="viewOrders(row)">
                <el-icon><List /></el-icon>
                订单
              </el-button>
              <el-button link type="success" @click="openRegion(row)">
                <el-icon><Location /></el-icon>
                地区
              </el-button>
              <el-button
                link
                :type="row.status === 1 ? 'warning' : 'success'"
                @click="toggleStatus(row)"
              >
                <el-icon><Switch /></el-icon>
                {{ row.status === 1 ? '禁用' : '启用' }}
              </el-button>
              <el-button link type="warning" @click="openLevel(row)">
                所属群体
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.limit"
          :total="filteredUsers.length"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
        />
      </div>
    </div>

    <el-dialog v-model="regionDialogVisible" title="编辑用户地区" width="400px">
      <el-form label-width="60px">
        <el-form-item label="地区">
          <el-cascader
            v-model="regionForm"
            :options="provinceCityOptions"
            placeholder="请选择省市"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="regionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRegion">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="levelDialogVisible" title="调整用户等级" width="420px">
      <el-form label-width="80px">
        <el-form-item label="用户等级">
          <el-select v-model="levelForm.levelId" placeholder="请选择用户等级" style="width: 100%">
            <el-option
              v-for="level in enabledLevels"
              :key="level.id"
              :label="level.name"
              :value="level.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="dialog-tip">等级变更后该用户需要重新登录才能生效。</div>
      <template #footer>
        <el-button @click="levelDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveLevel">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { provinceCityOptions } from '@/utils/regions'
import {
  draftLevels,
  type DraftLevel
} from '../../user-level/__prototype/prototypeData'

interface DemoUser {
  id: number
  nickname: string
  company: string
  phone: string
  city: string
  levelId: number
  role: number
  status: number
  lastLoginTime: string
  createTime: string
}

const route = useRoute()
const router = useRouter()
const levels = draftLevels
const enabledLevels = draftLevels.filter(level => level.status === 1)
const regionFilter = ref<string[]>([])
const pagination = reactive({ page: 1, limit: 10 })
const regionDialogVisible = ref(false)
const levelDialogVisible = ref(false)
const editingUserId = ref<number | null>(null)
const regionForm = ref<string[]>([])
const levelForm = reactive({ levelId: null as number | null })

const users = ref<DemoUser[]>([
  {
    id: 31001,
    nickname: '李工',
    company: '城开建设',
    phone: '138****4218',
    city: '济南市',
    levelId: 3,
    role: 1,
    status: 1,
    lastLoginTime: '2026-10-28 08:45',
    createTime: '2026-03-16 10:24'
  },
  {
    id: 31002,
    nickname: '张经理',
    company: '中建筑装',
    phone: '186****0972',
    city: '青岛市',
    levelId: 3,
    role: 1,
    status: 1,
    lastLoginTime: '2026-10-26 15:20',
    createTime: '2026-04-02 14:18'
  },
  {
    id: 31003,
    nickname: '高工',
    company: '齐鲁公装',
    phone: '159****6831',
    city: '淄博市',
    levelId: 3,
    role: 1,
    status: 1,
    lastLoginTime: '2026-10-22 13:36',
    createTime: '2026-05-11 09:42'
  },
  {
    id: 31004,
    nickname: '马经理',
    company: '恒基建筑',
    phone: '135****2880',
    city: '临沂市',
    levelId: 3,
    role: 1,
    status: 1,
    lastLoginTime: '2026-10-19 10:08',
    createTime: '2026-06-08 16:05'
  },
  {
    id: 21001,
    nickname: '王宏',
    company: '宏达建材',
    phone: '137****5206',
    city: '德州市',
    levelId: 2,
    role: 1,
    status: 1,
    lastLoginTime: '2026-10-28 10:32',
    createTime: '2025-12-18 11:20'
  },
  {
    id: 21002,
    nickname: '赵经理',
    company: '远鑫五金',
    phone: '151****7344',
    city: '聊城市',
    levelId: 2,
    role: 1,
    status: 1,
    lastLoginTime: '2026-10-27 14:06',
    createTime: '2026-01-06 09:12'
  },
  {
    id: 21003,
    nickname: '孙女士',
    company: '金诚装饰材料',
    phone: '139****0619',
    city: '泰安市',
    levelId: 2,
    role: 1,
    status: 1,
    lastLoginTime: '2026-10-25 11:40',
    createTime: '2026-02-27 15:36'
  },
  {
    id: 41001,
    nickname: '赵经理',
    company: '中装供应链',
    phone: '188****5521',
    city: '青岛市',
    levelId: 4,
    role: 1,
    status: 1,
    lastLoginTime: '2026-10-27 16:18',
    createTime: '2025-08-14 10:08'
  },
  {
    id: 41002,
    nickname: '陈总',
    company: '鼎盛工程配套',
    phone: '158****9906',
    city: '济南市',
    levelId: 4,
    role: 1,
    status: 1,
    lastLoginTime: '2026-10-24 09:50',
    createTime: '2025-09-03 13:42'
  },
  {
    id: 11001,
    nickname: '林晓雨',
    company: '个人客户',
    phone: '182****3145',
    city: '德州市',
    levelId: 1,
    role: 1,
    status: 1,
    lastLoginTime: '2026-10-27 18:24',
    createTime: '2026-07-12 17:56'
  }
])

const currentLevel = computed<DraftLevel>(() => {
  const levelId = Number(route.query.levelId)
  return levels.find(level => level.id === levelId)
    || levels.find(level => level.id === 3)
    || levels[0]
})
const isCompact = computed(() => route.query.layout !== 'full')

const filteredUsers = computed(() => {
  const [province, city] = regionFilter.value || []
  void province
  return users.value.filter(user => {
    const matchesLevel = user.levelId === currentLevel.value.id
    const matchesCity = !city || user.city === city
    return matchesLevel && matchesCity
  })
})

const levelName = (levelId: number) =>
  levels.find(level => level.id === levelId)?.name || '未设置'

const changeLevel = (levelId: number) => {
  regionFilter.value = []
  pagination.page = 1
  router.replace({
    path: route.path,
    query: {
      prototype: 'price-view',
      levelId: String(levelId)
    }
  })
}

const setLayout = (layout: 'compact' | 'full') => {
  router.replace({
    path: route.path,
    query: {
      ...route.query,
      prototype: 'price-view',
      levelId: String(currentLevel.value.id),
      layout
    }
  })
}

const viewOrders = (user: DemoUser) => {
  ElMessage.info(`正式页面继续跳转到用户 ${user.id} 的订单列表`)
}

const toggleStatus = async (user: DemoUser) => {
  await ElMessageBox.confirm(
    `确定要${user.status === 1 ? '禁用' : '启用'}该用户吗？`,
    '提示'
  )
  user.status = user.status === 1 ? 0 : 1
  ElMessage.success('原型状态已更新')
}

const openRegion = (user: DemoUser) => {
  editingUserId.value = user.id
  regionForm.value = ['山东省', user.city]
  regionDialogVisible.value = true
}

const saveRegion = () => {
  const user = users.value.find(item => item.id === editingUserId.value)
  if (user && regionForm.value[1]) {
    user.city = regionForm.value[1]
  }
  regionDialogVisible.value = false
  ElMessage.success('原型地区已更新')
}

const openLevel = (user: DemoUser) => {
  editingUserId.value = user.id
  levelForm.levelId = user.levelId
  levelDialogVisible.value = true
}

const saveLevel = () => {
  const user = users.value.find(item => item.id === editingUserId.value)
  if (user && levelForm.levelId) {
    user.levelId = levelForm.levelId
  }
  levelDialogVisible.value = false
  ElMessage.success('原型等级已更新')
}

const goToPriceView = () => {
  router.push({
    path: '/spu',
    query: {
      prototype: 'price-view',
      levelId: String(currentLevel.value.id)
    }
  })
}
</script>

<style scoped>
.user-management {
  max-width: 1400px;
  margin: 0 auto;
}

.prototype-banner {
  min-height: 38px;
  margin-bottom: 20px;
  padding: 7px 10px;
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px dashed var(--border-accent);
  background: var(--bg-card);
}

.prototype-banner > span {
  padding: 3px 6px;
  border-radius: 3px;
  color: #fff;
  background: #253038;
  font-family: var(--font-mono);
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.prototype-banner p {
  margin: 0;
  color: var(--text-tertiary);
  font-size: 10px;
}

.prototype-banner code {
  margin-left: auto;
  color: var(--text-tertiary);
  font-size: 9px;
}

.layout-switch {
  display: flex;
  padding: 2px;
  border: 1px solid var(--border-color);
  border-radius: 5px;
  background: var(--bg-page);
}

.layout-switch button {
  min-height: 24px;
  padding: 0 8px;
  border: 0;
  border-radius: 3px;
  color: var(--text-tertiary);
  background: transparent;
  font-size: 9px;
}

.layout-switch button.active {
  color: var(--text-primary);
  background: var(--bg-card);
  box-shadow: var(--shadow-xs);
}

.page-header {
  margin-bottom: var(--space-lg);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.header-content {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.page-title {
  font-size: var(--font-size-2xl);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.page-desc {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  margin: 0;
}

.card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-color);
}

.table-card {
  padding: var(--space-lg);
}

.prototype-filter {
  margin-bottom: var(--space-lg);
  padding: 12px;
  display: flex;
  align-items: flex-end;
  gap: 16px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  background: var(--bg-page);
}

.filter-item {
  display: grid;
  gap: 5px;
}

.filter-label {
  color: var(--text-tertiary);
  font-size: 10px;
}

.level-select {
  width: 160px;
}

.level-select :deep(.el-select__wrapper) {
  box-shadow: 0 0 0 1px var(--border-accent) inset;
  background: var(--primary-bg);
}

.region-cascader {
  width: 200px;
}

.filter-result {
  margin-left: auto;
  padding-bottom: 8px;
  color: var(--text-secondary);
  font-size: 11px;
}

.filter-result strong {
  color: var(--primary-color);
  font-family: var(--font-mono);
}

.filter-result span {
  color: var(--text-tertiary);
  font-size: 10px;
}

.user-info-cell {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.user-avatar {
  flex-shrink: 0;
  color: var(--primary-dark);
  background: var(--primary-bg);
  border: 1px solid var(--border-accent);
}

.user-detail {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-name {
  font-weight: 500;
  color: var(--text-primary);
}

.user-id {
  overflow: hidden;
  color: var(--text-tertiary);
  font-size: var(--font-size-xs);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.compact-register {
  overflow: hidden;
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: 8px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.phone {
  font-family: var(--font-mono);
  color: var(--text-secondary);
}

.compact-role {
  margin-top: 2px;
  display: block;
  color: var(--text-tertiary);
  font-size: 8px;
}

.compact-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.compact-actions :deep(.el-button) {
  width: 26px;
  height: 26px;
  margin-left: 0;
  font-size: 14px;
}

.region {
  color: var(--accent-color);
  font-weight: 500;
}

.pagination-wrapper {
  margin-top: var(--space-lg);
  display: flex;
  justify-content: flex-end;
}

.dialog-tip {
  padding-left: 80px;
  color: var(--text-tertiary);
  font-size: var(--font-size-xs);
}

@media (max-width: 760px) {
  .prototype-banner,
  .page-header,
  .prototype-filter {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .prototype-banner code,
  .filter-result {
    width: 100%;
    margin-left: 0;
  }

  .filter-item,
  .level-select,
  .region-cascader {
    width: 100%;
  }
}
</style>
