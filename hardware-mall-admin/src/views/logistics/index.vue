<template>
  <div class="logistics-management">
    <div class="page-header animate-fade-in-down">
      <div class="header-content">
        <h2 class="page-title">物流管理</h2>
        <p class="page-desc">管理物流公司和配送信息</p>
      </div>
      <el-button type="primary" class="add-btn" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        新增物流
      </el-button>
    </div>

    <div class="card table-card animate-fade-in-up stagger-1">
      <div class="filter-bar">
        <el-form :inline="true" :model="queryForm">
          <el-form-item label="物流名称">
            <el-input 
              v-model="queryForm.name" 
              placeholder="请输入物流名称" 
              clearable 
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="城市">
            <el-select v-model="queryForm.city" placeholder="选择城市" clearable filterable class="city-select">
              <el-option v-for="city in cityOptions" :key="city.value" :label="city.label" :value="city.value">
                <span>{{ city.label }}</span>
                <span class="province-tag">{{ city.province }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="queryForm.status" placeholder="全部" clearable class="status-select">
              <el-option label="启用" :value="LOGISTICS_STATUS.ENABLED" />
              <el-option label="禁用" :value="LOGISTICS_STATUS.DISABLED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch">
              <el-icon><Search /></el-icon>
              搜索
            </el-button>
            <el-button @click="handleReset">
              <el-icon><Refresh /></el-icon>
              重置
            </el-button>
          </el-form-item>
        </el-form>
      </div>
      <el-table 
        :data="tableData" 
        stripe 
        v-loading="loading"
        class="logistics-table"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="物流名称" min-width="200">
          <template #default="{ row }">
            <div class="logistics-name-cell">
              <div class="logistics-icon">
                <el-icon><Van /></el-icon>
              </div>
              <span class="logistics-name">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="物流编码" width="140">
          <template #default="{ row }">
            <code class="code">{{ row.code }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="contact" label="联系人" width="120" />
        <el-table-column label="联系电话" width="160">
          <template #default="{ row }">
            <div class="phone-cell">
              <span class="phone primary-phone">{{ row.phones[0] || '-' }}</span>
              <el-popover
                v-if="row.phones && row.phones.length > 1"
                placement="bottom"
                :width="200"
                trigger="hover"
              >
                <template #reference>
                  <el-button link type="primary" class="more-phones-btn">查看更多({{ row.phones.length - 1 }})</el-button>
                </template>
                <div class="phones-list">
                  <div v-for="(phone, index) in row.phones" :key="index" class="phone-item">
                    {{ phone }}
                  </div>
                </div>
              </el-popover>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="地区" width="120">
          <template #default="{ row }">
            <span class="region">{{ row.city || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="100">
          <template #default="{ row }">
            <span class="sort-order">{{ row.sortOrder }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag 
              :type="row.status === LOGISTICS_STATUS.ENABLED ? 'success' : 'info'" 
              size="small"
              effect="light"
            >
              {{ LOGISTICS_STATUS_TEXT[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
<el-button 
              link 
              :type="row.status === LOGISTICS_STATUS.ENABLED ? 'warning' : 'success'" 
              @click="handleToggleStatus(row)"
            >
              {{ row.status === LOGISTICS_STATUS.ENABLED ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.limit"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @Size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>

    <el-dialog 
      v-model="dialogVisible" 
      :title="isEdit ? '编辑物流' : '新增物流'" 
      width="500px"
      class="logistics-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="物流名称" prop="name">
          <el-input v-model="form.name" placeholder="如：德邦物流" />
        </el-form-item>
        <el-form-item label="物流编码" prop="code">
          <el-input v-model="form.code" placeholder="如：debang" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contact" placeholder="请输入联系人" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-select v-model="form.phones" multiple placeholder="选择或输入联系电话" filterable allow-create default-first-option class="phones-select">
            <el-option v-for="phone in form.phones" :key="phone" :label="phone" :value="phone" />
          </el-select>
        </el-form-item>
        <el-form-item label="地区">
          <el-select v-model="form.city" placeholder="选择城市" filterable clearable class="city-select">
            <el-option v-for="city in cityOptions" :key="city.value" :label="city.label" :value="city.value">
              <span>{{ city.label }}</span>
              <span class="province-tag">{{ city.province }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number 
            v-model="form.sortOrder" 
            :min="0" 
            :controls="false" 
            class="sort-input"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch 
            v-model="form.status" 
            :active-value="LOGISTICS_STATUS.ENABLED" 
            :inactive-value="LOGISTICS_STATUS.DISABLED"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cityOptions } from '@/utils/regions'
import { getLogisticsList, createLogistics, updateLogistics, deleteLogistics, updateLogisticsStatus } from '@/api/admin/logistics'
import { LOGISTICS_STATUS, LOGISTICS_STATUS_TEXT } from '@/constants/status'

const loading = ref(false)
const tableData = ref<any[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()

const queryForm = reactive({
  name: '',
  city: '' as string | null,
  status: null as number | null
})

const pagination = reactive({
  page: 1,
  limit: 10,
  total: 0
})

const form = reactive({
  id: null as number | null,
  name: '',
  code: '',
  contact: '',
  phones: [] as string[],
  city: '',
  sortOrder: 0,
  status: LOGISTICS_STATUS.ENABLED
})

const rules = {
  name: [{ required: true, message: '请输入物流名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入物流编码', trigger: 'blur' }]
}

const handleAdd = () => {
  isEdit.value = false
  Object.assign(form, { id: null, name: '', code: '', contact: '', phones: [], city: '', sortOrder: 0, status: 1 })
  dialogVisible.value = true
}

const handleEdit = (row: any) => {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    name: row.name,
    code: row.code,
    contact: row.contact,
    phones: [...(row.phones || [])],
    city: row.city || '',
    sortOrder: row.sortOrder,
    status: row.status
  })
  dialogVisible.value = true
}

const handleToggleStatus = async (row: any) => {
  try {
    const action = row.status === LOGISTICS_STATUS.ENABLED ? '禁用' : '启用'
    await ElMessageBox.confirm(`确定要${action}该物流方式吗？`, '提示')
    const newStatus = row.status === LOGISTICS_STATUS.ENABLED ? LOGISTICS_STATUS.DISABLED : LOGISTICS_STATUS.ENABLED
    await updateLogisticsStatus(row.id, newStatus)
    ElMessage.success('操作成功')
    loadData()
  } catch {
    // error handled by interceptor
  }
}

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定要删除物流「${row.name}」吗？删除后无法恢复。`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteLogistics(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {
    // error handled by interceptor
  }
}

const confirmSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      try {
        if (isEdit.value) {
          await updateLogistics(form.id as number, form as any)
          ElMessage.success('编辑成功')
        } else {
          await createLogistics(form as any)
          ElMessage.success('新增成功')
        }
        dialogVisible.value = false
        loadData()
      } catch {
        // error handled by interceptor
      }
    }
  })
}

const handleSearch = () => {
  pagination.page = 1
  loadData()
}

const handleReset = () => {
  queryForm.name = ''
  queryForm.city = ''
  queryForm.status = null
  pagination.page = 1
  loadData()
}

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await getLogisticsList({
      page: pagination.page,
      limit: pagination.limit,
      name: queryForm.name || undefined,
      city: queryForm.city || undefined,
      status: queryForm.status || undefined
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
.logistics-management {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
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

.add-btn {
  height: 40px;
  padding: 0 var(--space-lg);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  gap: var(--space-xs);
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
  padding-bottom: var(--space-lg);
  border-bottom: 1px solid var(--border-color);
}

.city-filter,
.city-select {
  width: 180px;
}

.status-select {
  width: 120px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-lg);
  padding-top: var(--space-lg);
  border-top: 1px solid var(--border-color);
}

.logistics-table {
  margin: 0 calc(-1 * var(--space-lg));
}

.logistics-name-cell {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.logistics-icon {
  width: 36px;
  height: 36px;
  background: var(--primary-bg);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary-color);
  font-size: 18px;
}

.logistics-name {
  font-weight: 500;
  color: var(--text-primary);
}

.code {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  background: var(--bg-page);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
}

.phone-cell {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.phone {
  font-family: var(--font-mono);
  color: var(--text-secondary);
}

.primary-phone {
  color: var(--text-primary);
}

.more-phones-btn {
  font-size: var(--font-size-xs);
}

.phones-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.phone-item {
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
  padding: var(--space-xs) 0;
  border-bottom: 1px solid var(--border-light);
}

.phone-item:last-child {
  border-bottom: none;
}

.region {
  color: var(--accent-color);
  font-weight: 500;
}

.sort-order {
  font-family: var(--font-mono);
  color: var(--text-secondary);
}

.sort-input {
  width: 100%;
}

.phones-select {
  width: 100%;
}

.city-select {
  width: 100%;
}

.province-tag {
  font-size: 10px;
  color: var(--text-tertiary);
  margin-left: var(--space-xs);
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
</style>
