<template>
  <div class="category-management">
    <div class="page-header animate-fade-in-down">
      <div class="header-content">
        <h2 class="page-title">分类管理</h2>
        <p class="page-desc">管理商品分类信息</p>
      </div>
      <el-button type="primary" class="add-btn" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        新增分类
      </el-button>
    </div>

    <div class="card table-card animate-fade-in-up stagger-1">
      <div class="filter-bar">
        <el-form :inline="true" :model="queryForm">
          <el-form-item label="分类名称">
            <el-input 
              v-model="queryForm.name" 
              placeholder="请输入分类名称" 
              clearable 
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="状态">
            <el-select 
              v-model="queryForm.status" 
              placeholder="全部" 
              clearable
              class="status-select"
            >
              <el-option label="启用" :value="CATEGORY_STATUS.ENABLED" />
              <el-option label="禁用" :value="CATEGORY_STATUS.DISABLED" />
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
        class="category-table"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="分类名称" min-width="200">
          <template #default="{ row }">
            <div class="category-name-cell">
              <div class="category-icon" :style="{ background: row.iconBg }">
                <el-icon :style="{ color: row.iconColor }">
                  <Folder />
                </el-icon>
              </div>
              <span class="category-name">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="120">
          <template #default="{ row }">
            <span class="sort-order">{{ row.sortOrder }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag 
              :type="row.status === CATEGORY_STATUS.ENABLED ? 'success' : 'info'" 
              size="small"
              effect="light"
            >
              {{ CATEGORY_STATUS_TEXT[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180">
          <template #default="{ row }">
            <span class="time">{{ row.createTime }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>

    <el-dialog 
      v-model="dialogVisible" 
      :title="isEdit ? '编辑分类' : '新增分类'" 
      width="450px"
      class="category-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number 
            v-model="form.sortOrder" 
            :min="0" 
            :controls="false" 
            class="sort-input"
          />
          <span class="sort-tip">数值越大排序越靠前</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch 
            v-model="form.status" 
            :active-value="1" 
            :inactive-value="0"
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
import { getCategoryList, createCategory, updateCategory, deleteCategory } from '@/api/admin/category'
import { CATEGORY_STATUS, CATEGORY_STATUS_TEXT } from '@/constants/status'

const loading = ref(false)
const tableData = ref<any[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()

const queryForm = reactive({
  name: '',
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
  sortOrder: 0,
  status: CATEGORY_STATUS.ENABLED
})

const rules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

const handleAdd = () => {
  isEdit.value = false
  Object.assign(form, { id: null, name: '', sortOrder: 0, status: CATEGORY_STATUS.ENABLED })
  dialogVisible.value = true
}

const handleEdit = (row: any) => {
  isEdit.value = true
  Object.assign(form, { id: row.id, name: row.name, sortOrder: row.sortOrder, status: row.status })
  dialogVisible.value = true
}

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定要删除该分类吗？', '提示')
    await deleteCategory(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {
    // error handled by interceptor
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadData()
}

const handleReset = () => {
  queryForm.name = ''
  queryForm.status = null
  pagination.page = 1
  loadData()
}

const confirmSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (isEdit.value) {
          await updateCategory(form.id!, { name: form.name, sortOrder: form.sortOrder, status: form.status })
          ElMessage.success('编辑成功')
        } else {
          await createCategory({ name: form.name, sortOrder: form.sortOrder, status: form.status })
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

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await getCategoryList({
      page: pagination.page,
      limit: pagination.limit,
      name: queryForm.name || undefined,
      status: queryForm.status
    })
    tableData.value = (res.records || []).map((item: any) => ({
      ...item,
      iconBg: 'linear-gradient(135deg, #3B82F6, #60A5FA)',
      iconColor: '#fff'
    }))
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
.category-management {
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

.category-table {
  margin: 0 calc(-1 * var(--space-lg));
}

.filter-bar {
  margin-bottom: var(--space-lg);
  padding-bottom: var(--space-lg);
  border-bottom: 1px solid var(--border-color);
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

.category-name-cell {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.category-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.category-name {
  font-weight: 500;
  color: var(--text-primary);
}

.sort-order {
  font-family: var(--font-mono);
  color: var(--text-secondary);
}

.time {
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
}

.sort-input {
  width: 100%;
}

.sort-tip {
  display: block;
  margin-top: var(--space-xs);
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
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
