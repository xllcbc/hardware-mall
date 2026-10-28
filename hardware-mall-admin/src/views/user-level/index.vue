<template>
  <UserLevelPrototype v-if="isPrototypeRoute && UserLevelPrototype" />

  <div v-else class="user-level-management">
    <div class="page-header animate-fade-in-down">
      <div class="header-content">
        <h2 class="page-title">用户群体管理</h2>
        <p class="page-desc">配置用户等级与分类倍率矩阵</p>
      </div>
      <el-button type="primary" class="add-btn" @click="openCreate">
        <el-icon><Plus /></el-icon>
        新增等级
      </el-button>
    </div>

    <div class="card table-card animate-fade-in-up stagger-1">
      <el-table :data="levelList" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="等级名称" min-width="160">
          <template #default="{ row }">
            <span class="level-name">{{ row.name }}</span>
            <el-tag v-if="row.isRetail === 1" size="small" type="info" effect="light">零售</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="100" />
        <el-table-column prop="userCount" label="用户数" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="light">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="390" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.isRetail !== 1" link type="primary" @click="openMatrix(row)">
              <el-icon><SetUp /></el-icon>
              倍率矩阵
            </el-button>
            <el-button link type="success" @click="openPreview(row)">
              <el-icon><View /></el-icon>
              价格预览
            </el-button>
            <el-button v-if="row.isRetail !== 1" link type="warning" @click="openEdit(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button
              v-if="row.isRetail !== 1"
              link
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button v-if="row.isRetail !== 1" link type="danger" @click="removeLevel(row)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="levelDialogVisible"
      :title="editingLevel ? '编辑等级' : '新增等级'"
      width="420px"
    >
      <el-form label-width="80px">
        <el-form-item label="等级名称">
          <el-input v-model="levelForm.name" maxlength="50" placeholder="请输入等级名称" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="levelForm.sortOrder" :min="0" :controls="false" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="levelDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitLevel">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="matrixDialogVisible" :title="`倍率矩阵 - ${currentLevel?.name || ''}`" width="720px">
      <div class="matrix-toolbar">
        <el-button @click="fillAll">
          <el-icon><MagicStick /></el-icon>
          一键填充
        </el-button>
        <el-button @click="copyVisible = true">
          <el-icon><CopyDocument /></el-icon>
          复制等级
        </el-button>
        <span class="matrix-tip">倍率范围 (0, 1]，缺口需要补齐后才能启用等级</span>
      </div>
      <el-table :data="discountList" max-height="430" v-loading="matrixLoading">
        <el-table-column prop="categoryName" label="商品分类" min-width="200" />
        <el-table-column label="倍率" width="220">
          <template #default="{ row }">
            <el-input-number
              v-model="row.rate"
              :min="0.0001"
              :max="1"
              :precision="4"
              :step="0.01"
              :controls="false"
              placeholder="未配置"
            />
            <el-tag v-if="row.missing" size="small" type="danger" effect="light">缺口</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="matrixDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="matrixLoading" @click="saveMatrix">保存矩阵</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="copyVisible" title="复制倍率矩阵" width="420px">
      <el-form label-width="100px">
        <el-form-item label="来源等级">
          <el-select v-model="copySourceLevelId" placeholder="请选择来源等级" style="width: 100%">
            <el-option
              v-for="level in copySourceLevels"
              :key="level.id"
              :label="level.name"
              :value="level.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="copyVisible = false">取消</el-button>
        <el-button type="primary" :loading="matrixLoading" @click="copyMatrix">复制</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewVisible" :title="`价格预览 - ${currentLevel?.name || ''}`" width="900px">
      <div class="preview-toolbar">
        <el-select v-model="previewCategoryId" placeholder="全部分类" clearable style="width: 220px">
          <el-option
            v-for="category in categories"
            :key="category.id"
            :label="category.name"
            :value="category.id"
          />
        </el-select>
        <el-button type="primary" @click="loadPreview">查询</el-button>
      </div>
      <el-table :data="previewList" stripe v-loading="previewLoading">
        <el-table-column prop="name" label="商品" min-width="180" />
        <el-table-column label="零售区间" width="170">
          <template #default="{ row }">{{ formatRange(row.retailMinPrice, row.retailMaxPrice) }}</template>
        </el-table-column>
        <el-table-column label="倍率" width="100">
          <template #default="{ row }">{{ formatRate(row.discountRate) }}</template>
        </el-table-column>
        <el-table-column label="等级价位" width="170">
          <template #default="{ row }">{{ formatRange(row.minPrice, row.maxPrice) }}</template>
        </el-table-column>
        <el-table-column label="折扣规则" width="120">
          <template #default="{ row }">
            {{ getExcludeDiscountText(row.excludeDiscount) }}
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="previewPagination.page"
          v-model:page-size="previewPagination.limit"
          :total="previewPagination.total"
          layout="total, prev, pager, next"
          @current-change="loadPreview"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  copyUserLevelDiscounts,
  createUserLevel,
  deleteUserLevel,
  fillUserLevelDiscounts,
  getUserLevelDiscounts,
  getUserLevelList,
  previewUserLevelProducts,
  saveUserLevelDiscounts,
  updateUserLevelInfo,
  updateUserLevelStatus,
  type LevelDiscount,
  type UserLevel,
  type UserLevelProductPreview
} from '@/api/admin/userLevel'
import { getCategoryList, type Category } from '@/api/admin/category'
import { getExcludeDiscountText } from '@/constants/discount'

const route = useRoute()
const isPrototypeRoute = computed(
  () => import.meta.env.DEV && route.query.prototype === '1'
)
const UserLevelPrototype = import.meta.env.DEV
  ? defineAsyncComponent(() => import('./__prototype/UserLevelPrototype.vue'))
  : null
const loading = ref(false)
const submitLoading = ref(false)
const matrixLoading = ref(false)
const previewLoading = ref(false)
const levelList = ref<UserLevel[]>([])
const categories = ref<Category[]>([])
const discountList = ref<LevelDiscount[]>([])
const currentLevel = ref<UserLevel | null>(null)
const editingLevel = ref<UserLevel | null>(null)
const levelDialogVisible = ref(false)
const matrixDialogVisible = ref(false)
const copyVisible = ref(false)
const previewVisible = ref(false)
const copySourceLevelId = ref<number | null>(null)
const previewCategoryId = ref<number | null>(null)
const previewList = ref<UserLevelProductPreview[]>([])
const levelForm = reactive({ name: '', sortOrder: 0 })
const previewPagination = reactive({ page: 1, limit: 20, total: 0 })

const copySourceLevels = computed(() =>
  levelList.value.filter(level => level.id !== currentLevel.value?.id && level.isRetail !== 1)
)

const loadLevels = async () => {
  loading.value = true
  try {
    levelList.value = await getUserLevelList() || []
  } finally {
    loading.value = false
  }
}

const loadCategories = async () => {
  const result: any = await getCategoryList({ page: 1, limit: 200 })
  categories.value = result.records || []
}

const openCreate = () => {
  editingLevel.value = null
  levelForm.name = ''
  levelForm.sortOrder = 0
  levelDialogVisible.value = true
}

const openEdit = (level: UserLevel) => {
  editingLevel.value = level
  levelForm.name = level.name
  levelForm.sortOrder = level.sortOrder
  levelDialogVisible.value = true
}

const submitLevel = async () => {
  if (!levelForm.name.trim()) {
    ElMessage.warning('请输入等级名称')
    return
  }
  submitLoading.value = true
  try {
    if (editingLevel.value) {
      await updateUserLevelInfo(editingLevel.value.id, {
        name: levelForm.name.trim(),
        sortOrder: levelForm.sortOrder
      })
    } else {
      await createUserLevel({ name: levelForm.name.trim(), sortOrder: levelForm.sortOrder })
    }
    ElMessage.success('保存成功')
    levelDialogVisible.value = false
    loadLevels()
  } finally {
    submitLoading.value = false
  }
}

const toggleStatus = async (level: UserLevel) => {
  const next = level.status === 1 ? 0 : 1
  await ElMessageBox.confirm(
    next === 1 ? '启用前必须确保所有分类倍率已配置，是否继续？' : '停用后该等级用户仍按原倍率取价，是否继续？',
    '提示',
    { type: 'warning' }
  )
  await updateUserLevelStatus(level.id, next)
  ElMessage.success('状态已更新')
  loadLevels()
}

const removeLevel = async (level: UserLevel) => {
  await ElMessageBox.confirm('删除后不可恢复，且仅允许删除无用户引用的等级。', '警告', { type: 'warning' })
  await deleteUserLevel(level.id)
  ElMessage.success('删除成功')
  loadLevels()
}

const openMatrix = async (level: UserLevel) => {
  currentLevel.value = level
  matrixDialogVisible.value = true
  matrixLoading.value = true
  try {
    discountList.value = await getUserLevelDiscounts(level.id) || []
  } finally {
    matrixLoading.value = false
  }
}

const saveMatrix = async () => {
  if (!currentLevel.value) return
  const items = discountList.value
    .filter(item => item.rate != null)
    .map(item => ({ categoryId: item.categoryId, rate: Number(item.rate) }))
  if (!items.length) {
    ElMessage.warning('请至少配置一个分类倍率')
    return
  }
  matrixLoading.value = true
  try {
    await saveUserLevelDiscounts(currentLevel.value.id, items)
    ElMessage.success('倍率矩阵已保存')
    discountList.value = await getUserLevelDiscounts(currentLevel.value.id) || []
    loadLevels()
  } finally {
    matrixLoading.value = false
  }
}

const fillAll = async () => {
  if (!currentLevel.value) return
  const { value } = await ElMessageBox.prompt('请输入要填充到全部分类的倍率，范围 (0, 1]', '一键填充', {
    inputPattern: /^(0(\.\d{1,4})?|1(\.0{1,4})?)$/,
    inputErrorMessage: '请输入 0 到 1 之间的倍率，最多四位小数'
  })
  const rate = Number(value)
  if (rate <= 0 || rate > 1) {
    ElMessage.warning('倍率必须在 (0, 1] 之间')
    return
  }
  await fillUserLevelDiscounts(currentLevel.value.id, rate)
  ElMessage.success('已填充全部分类')
  discountList.value = await getUserLevelDiscounts(currentLevel.value.id) || []
}

const copyMatrix = async () => {
  if (!currentLevel.value || !copySourceLevelId.value) {
    ElMessage.warning('请选择来源等级')
    return
  }
  matrixLoading.value = true
  try {
    await copyUserLevelDiscounts(currentLevel.value.id, copySourceLevelId.value)
    ElMessage.success('复制完成，缺口仍需手动补齐')
    copyVisible.value = false
    discountList.value = await getUserLevelDiscounts(currentLevel.value.id) || []
  } finally {
    matrixLoading.value = false
  }
}

const openPreview = async (level: UserLevel) => {
  currentLevel.value = level
  previewCategoryId.value = null
  previewPagination.page = 1
  previewVisible.value = true
  await loadPreview()
}

const loadPreview = async () => {
  if (!currentLevel.value) return
  previewLoading.value = true
  try {
    const result = await previewUserLevelProducts(currentLevel.value.id, {
      categoryId: previewCategoryId.value || undefined,
      page: previewPagination.page,
      limit: previewPagination.limit
    })
    previewList.value = result.records || []
    previewPagination.total = result.total || 0
  } finally {
    previewLoading.value = false
  }
}

const formatPrice = (value?: number) =>
  value == null ? '-' : `¥${Number(value).toFixed(2)}`

const formatRange = (min?: number, max?: number) => {
  if (min == null) return '-'
  if (max == null || Number(max) === Number(min)) return formatPrice(min)
  return `${formatPrice(min)} - ${formatPrice(max)}`
}

const formatRate = (rate?: number) =>
  rate == null ? '-' : Number(rate).toFixed(4)

watch(isPrototypeRoute, (isPrototype) => {
  if (!isPrototype) {
    loadLevels()
    loadCategories()
  }
}, {
  immediate: true
})
</script>

<style scoped>
.user-level-management {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-lg);
}

.header-content {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.page-title {
  margin: 0;
  font-size: var(--font-size-2xl);
  font-weight: 600;
  color: var(--text-primary);
}

.page-desc {
  margin: 0;
  color: var(--text-secondary);
}

.level-name {
  margin-right: 8px;
  font-weight: 500;
}

.matrix-toolbar,
.preview-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.matrix-tip {
  color: var(--text-secondary);
  font-size: 13px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
