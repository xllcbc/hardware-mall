<template>
  <div class="spec-management">
    <div class="page-header animate-fade-in-down">
      <div class="header-content">
        <h2 class="page-title">规格模板管理</h2>
        <p class="page-desc">为分类配置商品规格类型（如颜色、尺寸）</p>
      </div>
      <el-button type="primary" class="add-btn" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        新增规格
      </el-button>
    </div>

    <div class="card search-card animate-fade-in-up stagger-1">
      <el-form :inline="true" :model="queryForm" class="search-form">
        <el-form-item label="商品分类">
          <el-select
            v-model="queryForm.categoryId"
            placeholder="请选择分类"
            clearable
            class="search-select"
            @change="handleCategoryChange"
          >
            <el-option
              v-for="item in categoryList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模板名称">
          <el-input
            v-model="queryForm.name"
            placeholder="请输入模板名称"
            clearable
            class="search-input"
          />
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

    <div class="card table-card animate-fade-in-up stagger-2">
      <el-table
        :data="tableData"
        stripe
        v-loading="loading"
        class="spec-table"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="categoryName" label="所属分类" width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="light">{{ row.categoryName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="规格名称" min-width="150">
          <template #default="{ row }">
            <span class="spec-name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="specType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.specType === 1 ? 'primary' : 'warning'" size="small">
              {{ row.specType === 1 ? '选择型' : '输入型' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isRequired" label="是否必选" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isRequired === 1 ? 'danger' : 'info'" size="small">
              {{ row.isRequired === 1 ? '必选' : '可选' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column label="规格值" min-width="200">
          <template #default="{ row }">
            <div class="spec-items">
              <el-tag
                v-for="item in getSpecItems(row.id)"
                :key="item.id"
                size="small"
                class="spec-item-tag"
                closable
                @close="handleDeleteSpecItem(item)"
              >
                {{ item.value }}
              </el-tag>
              <el-button
                size="small"
                text
                type="primary"
                class="add-item-btn"
                @click="handleAddSpecItem(row)"
              >
                <el-icon><Plus /></el-icon>
                添加
              </el-button>
            </div>
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
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑规格模板' : '新增规格模板'"
      width="500px"
      class="spec-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="所属分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择分类" class="form-select">
            <el-option
              v-for="item in categoryList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="规格名称" prop="name">
          <el-input v-model="form.name" placeholder="如：颜色、尺寸、锁体大小" />
        </el-form-item>
        <el-form-item label="规格类型">
          <el-radio-group v-model="form.specType">
            <el-radio :label="1">选择型</el-radio>
            <el-radio :label="2">输入型</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="是否必选">
          <el-radio-group v-model="form.isRequired">
            <el-radio :label="1">必选</el-radio>
            <el-radio :label="0">可选</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :controls="false" />
          <span class="sort-tip">数值越大排序越靠前</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="itemDialogVisible"
      title="添加规格值"
      width="400px"
      class="item-dialog"
    >
      <el-form :model="itemForm" label-width="80px">
        <el-form-item label="规格值">
          <el-input v-model="itemForm.value" placeholder="请输入规格值" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmAddItem">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCategoryList } from '@/api/admin/category'
import {
  getSpecTemplateList,
  createSpecTemplate,
  updateSpecTemplate,
  deleteSpecTemplate,
  getSpecItemList,
  createSpecItem,
  deleteSpecItem,
  SpecTemplate,
  SpecItem
} from '@/api/admin/spec'

const loading = ref(false)
const tableData = ref<any[]>([])
const categoryList = ref<any[]>([])
const specItemsMap = ref<Record<number, SpecItem[]>>({})
const dialogVisible = ref(false)
const itemDialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const currentTemplateId = ref<number | null>(null)

const queryForm = reactive({
  categoryId: null as number | null,
  name: ''
})

const pagination = reactive({
  page: 1,
  limit: 20,
  total: 0
})

const form = reactive({
  id: null as number | null,
  categoryId: null as number | null,
  name: '',
  specType: 1,
  isRequired: 1,
  sortOrder: 0
})

const itemForm = reactive({
  value: ''
})

const rules = {
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  name: [{ required: true, message: '请输入规格名称', trigger: 'blur' }]
}

const loadCategoryList = async () => {
  try {
    const res: any = await getCategoryList({ page: 1, limit: 100 })
    categoryList.value = res.records || []
  } catch {
    // error handled by interceptor
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await getSpecTemplateList({
      page: pagination.page,
      limit: pagination.limit,
      categoryId: queryForm.categoryId || undefined,
      name: queryForm.name || undefined
    })
    tableData.value = (res.records || []).map((item: any) => {
      const category = categoryList.value.find(c => c.id === item.categoryId)
      return {
        ...item,
        categoryName: category?.name || ''
      }
    })
    pagination.total = res.total || 0

    for (const item of tableData.value) {
      await loadSpecItems(item.id)
    }
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

const loadSpecItems = async (templateId: number) => {
  try {
    const res = await getSpecItemList(templateId)
    specItemsMap.value[templateId] = res || []
  } catch {
    specItemsMap.value[templateId] = []
  }
}

const getSpecItems = (templateId: number) => {
  return specItemsMap.value[templateId] || []
}

const handleCategoryChange = () => {
  pagination.page = 1
  loadData()
}

const handleSearch = () => {
  pagination.page = 1
  loadData()
}

const handleReset = () => {
  queryForm.categoryId = null
  queryForm.name = ''
  pagination.page = 1
  loadData()
}

const handleAdd = () => {
  isEdit.value = false
  Object.assign(form, {
    id: null,
    categoryId: queryForm.categoryId,
    name: '',
    specType: 1,
    isRequired: 1,
    sortOrder: 0
  })
  dialogVisible.value = true
}

const handleEdit = (row: any) => {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    categoryId: row.categoryId,
    name: row.name,
    specType: row.specType,
    isRequired: row.isRequired,
    sortOrder: row.sortOrder
  })
  dialogVisible.value = true
}

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定要删除该规格模板吗？', '提示')
    await deleteSpecTemplate(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {
    // error handled by interceptor
  }
}

const handleAddSpecItem = (row: any) => {
  currentTemplateId.value = row.id
  itemForm.value = ''
  itemDialogVisible.value = true
}

const handleDeleteSpecItem = async (item: SpecItem) => {
  try {
    await ElMessageBox.confirm('确定要删除该规格值吗？', '提示')
    await deleteSpecItem(item.id!)
    ElMessage.success('删除成功')
    if (currentTemplateId.value) {
      await loadSpecItems(currentTemplateId.value)
    }
  } catch {
    // error handled by interceptor
  }
}

const confirmAddItem = async () => {
  if (!itemForm.value.trim()) {
    ElMessage.warning('请输入规格值')
    return
  }
  try {
    await createSpecItem({
      templateId: currentTemplateId.value!,
      value: itemForm.value
    })
    ElMessage.success('添加成功')
    itemDialogVisible.value = false
    await loadSpecItems(currentTemplateId.value!)
    await loadData()
  } catch {
    // error handled by interceptor
  }
}

const confirmSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (isEdit.value) {
          await updateSpecTemplate(form.id!, form)
          ElMessage.success('编辑成功')
        } else {
          await createSpecTemplate(form)
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

onMounted(() => {
  loadCategoryList().then(() => {
    loadData()
  })
})
</script>

<style scoped>
.spec-management {
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
}

.search-card {
  padding: var(--space-lg);
  margin-bottom: var(--space-lg);
}

.search-form {
  margin: 0;
}

.search-select,
.search-input {
  width: 180px;
}

.table-card {
  padding: var(--space-lg);
}

.spec-table {
  margin: 0 calc(-1 * var(--space-lg));
}

.spec-name {
  font-weight: 500;
}

.spec-items {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.spec-item-tag {
  margin-right: 4px;
}

.add-item-btn {
  font-size: 12px;
}

.form-select {
  width: 100%;
}

.sort-tip {
  display: block;
  margin-top: var(--space-xs);
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
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

:deep(.el-table tr:hover > td.el-table__cell) {
  background: var(--primary-bg) !important;
}
</style>
