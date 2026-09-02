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
      </div>
      <el-table 
        :data="tableData" 
        stripe 
        v-loading="loading"
        class="user-table"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="用户信息" min-width="200">
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
        <el-table-column prop="role" label="角色" width="100">
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
        <el-table-column label="地区" width="140">
          <template #default="{ row }">
            <span class="region">{{ row.city || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
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
        <el-table-column prop="lastLoginTime" label="最后登录" width="180">
          <template #default="{ row }">
            <span class="time">{{ row.lastLoginTime || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间" width="180">
          <template #default="{ row }">
            <span class="time">{{ row.createTime }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewOrders(row)">
              <el-icon><List /></el-icon>
              订单
            </el-button>
            <el-button link type="success" @click="handleEditRegion(row)">
              <el-icon><Location /></el-icon>
              地区
            </el-button>
            <el-button
              link
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="handleToggleStatus(row)"
            >
              <el-icon><Switch /></el-icon>
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { provinceCityOptions } from '@/utils/regions'
import { getUserList, updateUserStatus, updateUserRegion } from '@/api/admin/user'
import { USER_ROLE, USER_ROLE_TEXT, USER_STATUS, USER_STATUS_TEXT } from '@/constants/status'

const router = useRouter()
const loading = ref(false)
const tableData = ref<any[]>([])

const regionFilter = ref<string[]>([])

const regionDialogVisible = ref(false)
const regionForm = ref<string[]>([])
const regionLoading = ref(false)
const editingUserId = ref<number | null>(null)

watch(regionFilter, () => {
  pagination.page = 1
  loadData()
})



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
  loading.value = true
  try {
    const [province, city] = regionFilter.value || []
    const res: any = await getUserList({
      page: pagination.page,
      limit: pagination.limit,
      province,
      city
    })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})
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
  margin-bottom: var(--space-lg);
}

.region-cascader {
  width: 200px;
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
