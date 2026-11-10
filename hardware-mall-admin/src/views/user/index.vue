<template>
  <div class="user-management">
    <div class="page-header animate-fade-in-down">
      <div class="header-content">
        <h2 class="page-title">用户管理</h2>
        <p class="page-desc">查看和管理平台用户信息</p>
      </div>
    </div>

    <div class="card table-card animate-fade-in-up stagger-1">
      <div class="filter-bar">
        <el-cascader
          v-model="regionFilter"
          :options="provinceCityOptions"
          placeholder="选择省市"
          clearable
          filterable
          class="region-cascader"
        />
        <el-select
          v-model="levelFilter"
          placeholder="按用户等级筛选"
          clearable
          filterable
          class="level-select"
        >
          <el-option
            v-for="level in levelOptions"
            :key="level.id"
            :label="level.name"
            :value="level.id"
          />
        </el-select>
        <el-switch
          v-model="fullFields"
          class="field-switch"
          active-text="完整字段"
          aria-label="切换完整字段"
        />
      </div>
      <el-table 
        :data="tableData" 
        stripe 
        v-loading="loading"
        class="user-table"
      >
        <el-table-column v-if="fullFields" prop="id" label="ID" width="72" />
        <el-table-column label="用户" min-width="180">
          <template #default="{ row }">
            <div class="user-info-cell">
              <el-avatar :size="40" class="user-avatar" :src="row.avatarUrl">
                <el-icon><User /></el-icon>
              </el-avatar>
              <div class="user-detail">
                <span class="user-name">{{ row.nickname || '未设置昵称' }}</span>
                <span class="user-id">ID: {{ row.id }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130">
          <template #default="{ row }">
            <span class="phone">{{ row.phone || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="fullFields" prop="role" label="角色" width="84">
          <template #default="{ row }">
            <el-tag 
              :type="row.role === USER_ROLE.ADMIN ? 'warning' : 'primary'" 
              size="small"
              effect="light"
            >
              {{ USER_ROLE_TEXT[row.role] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="用户等级" width="108">
          <template #default="{ row }">
            <el-tag :type="row.levelId != null && row.levelId === retailLevelId ? 'info' : 'success'" size="small" effect="light">
              {{ row.levelName || '未设置' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="地区" width="96">
          <template #default="{ row }">
            <span class="region">{{ row.city || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="78">
          <template #default="{ row }">
            <el-tag 
              :type="row.status === USER_STATUS.NORMAL ? 'success' : 'danger'" 
              size="small"
              effect="light"
            >
              {{ USER_STATUS_TEXT[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="fullFields" prop="lastLoginTime" label="最后登录" width="168">
          <template #default="{ row }">
            <span class="time">{{ row.lastLoginTime || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="fullFields" prop="createTime" label="注册时间" width="168">
          <template #default="{ row }">
            <span class="time">{{ row.createTime }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <div class="icon-actions">
              <el-tooltip content="查看订单" placement="top">
                <el-button
                  link
                  type="primary"
                  class="icon-action"
                  aria-label="查看用户订单"
                  @click="handleViewOrders(row)"
                >
                  <el-icon><List /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="编辑地区" placement="top">
                <el-button
                  link
                  type="success"
                  class="icon-action"
                  aria-label="编辑用户地区"
                  @click="handleEditRegion(row)"
                >
                  <el-icon><Location /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip :content="row.status === 1 ? '禁用用户' : '启用用户'" placement="top">
                <el-button
                  link
                  :type="row.status === 1 ? 'warning' : 'success'"
                  class="icon-action"
                  :aria-label="row.status === 1 ? '禁用用户' : '启用用户'"
                  @click="handleToggleStatus(row)"
                >
                  <el-icon><Switch /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="调整所属等级" placement="top">
                <el-button
                  link
                  type="warning"
                  class="icon-action"
                  aria-label="调整用户等级"
                  @click="handleChangeLevel(row)"
                >
                  <el-icon><UserFilled /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.limit"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadData"
          @current-change="loadData"
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
        <el-button type="primary" @click="handleRegionSubmit" :loading="regionLoading">确定</el-button>
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
        <el-button type="primary" :loading="levelLoading" @click="handleLevelSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { provinceCityOptions } from '@/utils/regions'
import { getUserList, updateUserStatus, updateUserRegion, updateUserLevel } from '@/api/admin/user'
import { getUserLevelList, type UserLevel } from '@/api/admin/userLevel'
import { USER_ROLE, USER_ROLE_TEXT, USER_STATUS, USER_STATUS_TEXT } from '@/constants/status'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const fullFields = ref(false)
const tableData = ref<any[]>([])
let latestRequestId = 0

const regionFilter = ref<string[]>([])
const levelFilter = ref<number | null>(parseLevelIdQuery(route.query.levelId))

const regionDialogVisible = ref(false)
const regionForm = ref<string[]>([])
const regionLoading = ref(false)
const editingUserId = ref<number | null>(null)
const levelDialogVisible = ref(false)
const levelLoading = ref(false)
const levelOptions = ref<UserLevel[]>([])
const editingLevelUserId = ref<number | null>(null)
const levelForm = reactive({ levelId: null as number | null })

const enabledLevels = computed(() => levelOptions.value.filter(level => level.status === 1))

const retailLevelId = computed(() =>
  levelOptions.value.find(level => level.isRetail === 1)?.id ?? null
)

watch(regionFilter, () => {
  pagination.page = 1
  loadData()
})

watch(levelFilter, (value) => {
  router.replace({
    path: route.path,
    query: {
      ...route.query,
      levelId: value == null ? undefined : String(value)
    }
  })
  pagination.page = 1
  loadData()
})

watch(
  () => route.query.levelId,
  (value) => {
    const nextLevelId = parseLevelIdQuery(value)
    if (nextLevelId !== levelFilter.value) {
      levelFilter.value = nextLevelId
    }
  }
)

const pagination = reactive({
  page: 1,
  limit: 20,
  total: 0
})

const handleViewOrders = (row: any) => {
  router.push({ path: '/order', query: { userId: row.id } })
}

const handleToggleStatus = async (row: any) => {
  try {
    const action = row.status === USER_STATUS.NORMAL ? '禁用' : '启用'
    await ElMessageBox.confirm(`确定要${action}该用户吗？`, '提示')
    const newStatus = row.status === USER_STATUS.NORMAL ? USER_STATUS.DISABLED : USER_STATUS.NORMAL
    await updateUserStatus(row.id, newStatus)
    ElMessage.success('操作成功')
    loadData()
  } catch {
    // error handled by interceptor
  }
}

const handleEditRegion = (row: any) => {
  editingUserId.value = row.id
  regionForm.value = row.province && row.city ? [row.province, row.city] : []
  regionDialogVisible.value = true
}

const handleChangeLevel = (row: any) => {
  editingLevelUserId.value = row.id
  levelForm.levelId = row.levelId
  levelDialogVisible.value = true
}

const handleLevelSubmit = async () => {
  if (!levelForm.levelId) {
    ElMessage.warning('请选择用户等级')
    return
  }
  levelLoading.value = true
  try {
    await updateUserLevel(editingLevelUserId.value!, levelForm.levelId)
    ElMessage.success('已更新用户等级，该用户需重新登录')
    levelDialogVisible.value = false
    loadData()
  } catch {
    // error handled by interceptor
  } finally {
    levelLoading.value = false
  }
}

const handleRegionSubmit = async () => {
  if (!regionForm.value || regionForm.value.length < 2) {
    ElMessage.warning('请选择省市')
    return
  }
  regionLoading.value = true
  try {
    await updateUserRegion(editingUserId.value!, regionForm.value[0], regionForm.value[1])
    ElMessage.success('地区修改成功')
    regionDialogVisible.value = false
    loadData()
  } catch {
    // error handled by interceptor
  } finally {
    regionLoading.value = false
  }
}

const loadData = async () => {
  const requestId = ++latestRequestId
  loading.value = true
  try {
    const [province, city] = regionFilter.value || []
    const res: any = await getUserList({
      page: pagination.page,
      limit: pagination.limit,
      province,
      city,
      levelId: levelFilter.value ?? undefined
    })
    if (requestId !== latestRequestId) return
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch {
    // error handled by interceptor
  } finally {
    if (requestId === latestRequestId) {
      loading.value = false
    }
  }
}

const loadLevels = async () => {
  try {
    levelOptions.value = await getUserLevelList() || []
  } catch {
    levelOptions.value = []
  }
}

onMounted(() => {
  loadLevels()
  loadData()
})

function parseLevelIdQuery(value: unknown): number | null {
  const raw = Array.isArray(value) ? value[0] : value
  if (typeof raw !== 'string' || raw.trim() === '') {
    return null
  }
  const parsed = Number(raw)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}
</script>

<style scoped>
.user-management {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: var(--space-lg);
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
  position: relative;
  overflow: hidden;
}

.card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--primary-color), var(--accent-color));
  opacity: 0;
  transition: opacity var(--transition-base);
}

.card:hover::before {
  opacity: 1;
}

.card:hover {
  box-shadow: var(--shadow-lg);
}

.table-card {
  padding: var(--space-lg);
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  margin-bottom: var(--space-lg);
}

.region-cascader {
  width: 200px;
}

.level-select {
  width: 180px;
}

.field-switch {
  margin-left: auto;
}

.user-table {
  margin: 0 calc(-1 * var(--space-lg));
}

.user-info-cell {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.user-avatar {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  color: white;
  flex-shrink: 0;
}

.user-detail {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-name {
  font-weight: 500;
  color: var(--text-primary);
}

.user-id {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
}

.phone {
  font-family: var(--font-mono);
  color: var(--text-secondary);
}

.time {
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
}

.region {
  color: var(--accent-color);
  font-weight: 500;
}

.icon-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
}

.icon-action {
  width: 24px;
  height: 24px;
  margin-left: 0 !important;
  padding: 0;
  border-radius: var(--radius-sm);
}

.icon-action:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 2px;
}

.pagination-wrapper {
  margin-top: var(--space-lg);
  display: flex;
  justify-content: flex-end;
}

:deep(.el-table) {
  --el-table-border-color: transparent;
}

:deep(.el-table th.el-table__cell) {
  background: var(--bg-page);
  font-weight: 600;
  font-size: var(--font-size-sm);
}

:deep(.el-table tr) {
  transition: background-color var(--transition-fast);
}

:deep(.el-table tr:hover > td.el-table__cell) {
  background: var(--primary-bg) !important;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.action-btn .el-icon {
  font-size: 14px;
}
</style>
