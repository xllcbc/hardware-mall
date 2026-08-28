<template>
  <div class="spu-management">
    <el-image-viewer
      v-if="previewVisible"
      :url-list="previewImages"
      @close="previewVisible = false"
    />

    <div class="page-header animate-fade-in-down">
      <div class="header-content">
        <h2 class="page-title">商品管理</h2>
        <p class="page-desc">管理商品型号及规格配置</p>
      </div>
      <el-button type="primary" class="add-btn" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        新增商品
      </el-button>
    </div>

    <div class="card search-card animate-fade-in-up stagger-1">
      <el-form :inline="true" :model="queryForm" class="search-form">
        <el-form-item label="商品分类">
          <el-select
            v-model="queryForm.categoryId"
            placeholder="全部"
            clearable
            class="search-select"
          >
            <el-option
              v-for="item in categoryList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="商品名称">
          <el-input
            v-model="queryForm.keyword"
            placeholder="请输入商品名称"
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
        class="spu-table"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="商品图片" width="100">
          <template #default="{ row }">
            <div class="product-image-wrapper">
              <el-image
                v-if="row.images && row.images.length > 0"
                :src="row.images[0]"
                class="product-image"
                fit="cover"
                @click="previewImage(row.images, 0)"
              />
              <div v-else class="no-image">
                <el-icon><Picture /></el-icon>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="商品名称" min-width="200">
          <template #default="{ row }">
            <div class="product-name-cell">
              <span class="product-name">{{ row.name }}</span>
              <span class="product-subtitle">{{ row.subtitle }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" label="分类" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="light">{{ row.categoryName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="SKU数量" width="100">
          <template #default="{ row }">
            <span class="sku-count">{{ getSkuCount(row.id) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="salesCount" label="销量" width="100" />
        <el-table-column label="推荐" width="80">
          <template #default="{ row }">
            <el-tag
              :type="row.isRecommend === 1 ? 'warning' : 'info'"
              size="small"
              effect="light"
            >
              {{ row.isRecommend === 1 ? '推荐' : '普通' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 1 ? 'success' : 'info'"
              size="small"
              effect="light"
            >
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button link type="success" @click="handleConfigSku(row)">
              <el-icon><Setting /></el-icon>
              规格配置
            </el-button>
            <el-button
              link
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">
              <el-icon><Delete /></el-icon>
              删除
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
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑商品' : '新增商品'"
      width="800px"
      class="spu-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="商品分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择分类" class="form-select">
            <el-option
              v-for="item in categoryList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入商品名称" />
        </el-form-item>
        <el-form-item label="商品副标题">
          <el-input v-model="form.subtitle" placeholder="请输入商品副标题" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="商品原价">
              <el-input-number
                v-model="form.originalPrice"
                :min="0"
                :precision="2"
                :controls="false"
                class="price-input"
              >
                <template #append>元</template>
              </el-input-number>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商品重量">
              <el-input-number
                v-model="form.weight"
                :min="0"
                :precision="2"
                :controls="false"
              >
                <template #append>kg</template>
              </el-input-number>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="商品图片" class="image-form-item">
          <div class="image-upload-container">
            <div class="image-list">
              <div
                v-for="(img, index) in form.images"
                :key="index"
                class="image-item"
              >
                <el-image
                  :src="img"
                  :preview-src-list="form.images"
                  :initial-index="index"
                  fit="cover"
                  class="uploaded-image"
                />
                <div class="image-mask">
                  <el-icon @click="previewImage(form.images, index)"><ZoomIn /></el-icon>
                  <el-icon @click="removeImage(index)"><Delete /></el-icon>
                </div>
                <el-badge
                  v-if="index === 0"
                  value="主图"
                  class="main-badge"
                />
              </div>

              <el-upload
                v-if="form.images.length < 5"
                class="image-uploader"
                :show-file-list="false"
                :before-upload="beforeImageUpload"
                :http-request="handleImageUpload"
                accept="image/jpeg,image/png,image/gif,image/jpg"
              >
                <div class="upload-trigger">
                  <el-icon class="upload-icon"><Plus /></el-icon>
                  <span class="upload-text">{{ form.images.length }}/5</span>
                </div>
              </el-upload>
            </div>
            <div class="image-tip">最多上传5张图片，支持 JPG、PNG、GIF 格式</div>
          </div>
        </el-form-item>

        <el-form-item label="商品描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入商品描述"
          />
        </el-form-item>
        <el-form-item label="商品状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="上架"
            inactive-text="下架"
          />
        </el-form-item>
        <el-form-item label="首页推荐">
          <el-switch
            v-model="form.isRecommend"
            :active-value="1"
            :inactive-value="0"
            active-text="推荐"
            inactive-text="不推荐"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="confirmSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="skuDialogVisible"
      :title="`规格配置 - ${currentSpu?.name}`"
      width="900px"
      class="sku-dialog"
    >
      <div class="sku-config">
        <div class="sku-actions">
          <el-button type="primary" @click="handleGenerateSkus">
            <el-icon><Refresh /></el-icon>
            笛卡尔积生成SKU
          </el-button>
          <span class="sku-tip">根据分类规格模板自动生成所有规格组合</span>
        </div>

        <el-table
          :data="skuList"
          stripe
          border
          class="sku-table"
        >
          <el-table-column label="规格组合" min-width="200">
            <template #default="{ row }">
              <div class="specs-display">
                <el-tag
                  v-for="spec in row.specs"
                  :key="spec.templateId"
                  size="small"
                  class="spec-tag"
                >
                  {{ spec.name }}: {{ spec.value }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="SKU图片" width="100">
            <template #default="{ row }">
              <div class="sku-image-wrapper" @click="handleSkuImageUpload(row)">
                <el-image
                  v-if="row.image"
                  :src="row.image"
                  class="sku-image"
                  fit="cover"
                />
                <div v-else class="sku-image-placeholder">
                  <el-icon><Plus /></el-icon>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="价格(元)" width="150">
            <template #default="{ row }">
              <el-input-number
                v-model="row.price"
                :min="0"
                :precision="2"
                :controls="false"
                size="small"
                class="sku-price-input"
              />
            </template>
          </el-table-column>
          <el-table-column label="库存" width="120">
            <template #default="{ row }">
              <el-input-number
                v-model="row.stock"
                :min="0"
                :controls="false"
                size="small"
                class="sku-stock-input"
              />
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-switch v-model="row.status" :active-value="1" :inactive-value="0" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="danger" size="small" @click="handleDeleteSku(row)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="skuDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="skuSubmitLoading" @click="confirmSkuSubmit">保存配置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ElImageViewer } from 'element-plus'
import { getCategoryList } from '@/api/admin/category'
import { getSpuList, createSpu, updateSpu, deleteSpu, updateSpuStatus, Spu } from '@/api/admin/spu'
import { getSkusBySpu, getSkuCounts, createSku, updateSku, deleteSku, generateSkus, Sku } from '@/api/admin/sku'
import { uploadProductImage } from '@/api/admin/upload'

const loading = ref(false)
const submitLoading = ref(false)
const skuSubmitLoading = ref(false)
const tableData = ref<any[]>([])
const skuList = ref<Sku[]>([])
const deletedSkuIds = ref<number[]>([])
const categoryList = ref<any[]>([])
const currentSpu = ref<Spu | null>(null)
const skuCountMap = ref<Record<number, number>>({})
const previewVisible = ref(false)
const previewImages = ref<string[]>([])
const dialogVisible = ref(false)
const skuDialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()

const queryForm = reactive({
  categoryId: null as number | null,
  keyword: ''
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
  subtitle: '',
  description: '',
  originalPrice: 0,
  weight: null as number | null,
  status: 1,
  isRecommend: 0,
  images: [] as string[]
})

const rules = {
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }]
}

const getSkuCount = (spuId: number) => {
  return skuCountMap.value[spuId] || 0
}

const loadCategoryList = async () => {
  try {
    const res: any = await getCategoryList()
    categoryList.value = res.records || []
  } catch {
    // error handled by interceptor
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await getSpuList({
      page: pagination.page,
      limit: pagination.limit,
      categoryId: queryForm.categoryId || undefined,
      keyword: queryForm.keyword || undefined
    })
    tableData.value = (res.records || []).map((item: any) => {
      const category = categoryList.value.find(c => c.id === item.categoryId)
      return {
        ...item,
        categoryName: category?.name || ''
      }
    })
    pagination.total = res.total || 0

    const spuIds = tableData.value.map((item: any) => item.id as number)
    if (spuIds.length) {
      try {
        const counts = await getSkuCounts(spuIds)
        tableData.value.forEach((item: any) => {
          skuCountMap.value[item.id] = counts[item.id] || 0
        })
      } catch {
        // error handled by interceptor
      }
    }
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadData()
}

const handleReset = () => {
  queryForm.categoryId = null
  queryForm.keyword = ''
  pagination.page = 1
  loadData()
}

const handleAdd = () => {
  isEdit.value = false
  Object.assign(form, {
    id: null,
    categoryId: queryForm.categoryId,
    name: '',
    subtitle: '',
    description: '',
    originalPrice: 0,
    weight: null,
    status: 1,
    isRecommend: 0,
    images: []
  })
  dialogVisible.value = true
}

const handleEdit = (row: any) => {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    categoryId: row.categoryId,
    name: row.name,
    subtitle: row.subtitle || '',
    description: row.description || '',
    originalPrice: row.originalPrice || 0,
    weight: row.weight,
    status: row.status,
    isRecommend: row.isRecommend || 0,
    images: row.images ? [...row.images] : []
  })
  dialogVisible.value = true
}

const handleToggleStatus = async (row: any) => {
  try {
    const action = row.status === 1 ? '下架' : '上架'
    await ElMessageBox.confirm(`确定要${action}该商品吗？`, '提示')
    const newStatus = row.status === 1 ? 0 : 1
    await updateSpuStatus(row.id, newStatus)
    ElMessage.success('操作成功')
    loadData()
  } catch {
    // error handled by interceptor
  }
}

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定要删除该商品吗？删除后不可恢复！', '警告', { type: 'warning' })
    await deleteSpu(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {
    // error handled by interceptor
  }
}

const handleConfigSku = async (row: any) => {
  currentSpu.value = row
  skuDialogVisible.value = true
  skuSubmitLoading.value = true
  deletedSkuIds.value = []
  try {
    const res = await getSkusBySpu(row.id)
    skuList.value = res || []
  } catch {
    skuList.value = []
  } finally {
    skuSubmitLoading.value = false
  }
}

const handleGenerateSkus = async () => {
  if (!currentSpu.value) return
  const spuId = currentSpu.value.id as number
  try {
    const res = await generateSkus(spuId)
    skuList.value = res || []
    deletedSkuIds.value = []
    ElMessage.success('已生成预览，点击保存配置提交')
  } catch {
    // error handled by interceptor
  }
}

const handleDeleteSku = (row: Sku) => {
  skuList.value = skuList.value.filter(s => s !== row)
  if (row.id) {
    deletedSkuIds.value.push(row.id)
  }
}

const handleSkuImageUpload = (row: Sku) => {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*'
  input.onchange = async (e: any) => {
    const file = e.target.files[0]
    if (!file) return
    try {
      const result = await uploadProductImage(file)
      row.image = result.url
    } catch {
      ElMessage.error('图片上传失败')
    }
  }
  input.click()
}

const confirmSkuSubmit = async () => {
  skuSubmitLoading.value = true
  try {
    for (const sku of skuList.value) {
      if (sku.id) {
        await updateSku(sku.id, sku)
      } else {
        await createSku({
          spuId: currentSpu.value!.id!,
          specs: sku.specs,
          price: sku.price,
          stock: sku.stock,
          image: sku.image,
          status: sku.status || 1
        })
      }
    }
    for (const id of deletedSkuIds.value) {
      await deleteSku(id)
    }
    ElMessage.success('保存成功')
    skuDialogVisible.value = false
    loadData()
  } catch {
    // error handled by interceptor
  } finally {
    skuSubmitLoading.value = false
  }
}

const beforeImageUpload = (file: File) => {
  const isImage = file.type === 'image/jpeg' || file.type === 'image/png' || file.type === 'image/gif' || file.type === 'image/jpg'
  const isLt5M = file.size / 1024 / 1024 < 5

  if (!isImage) {
    ElMessage.error('只能上传 JPG、PNG、GIF 格式的图片!')
    return false
  }
  if (!isLt5M) {
    ElMessage.error('图片大小不能超过 5MB!')
    return false
  }
  return true
}

const handleImageUpload = async (options: any) => {
  const { file, onSuccess, onError } = options

  try {
    const res = await uploadProductImage(file)
    form.images.push(res.url)
    ElMessage.success('图片上传成功')
    onSuccess()
  } catch (error) {
    ElMessage.error('图片上传失败')
    onError(error)
  }
}

const removeImage = (index: number) => {
  form.images.splice(index, 1)
}

const previewImage = (images: string[], _index: number) => {
  previewImages.value = images
  previewVisible.value = true
}

const confirmSubmit = async () => {
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateSpu(form.id as number, form as Spu)
      ElMessage.success('编辑成功')
    } else {
      await createSpu(form as Spu)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch {
    // error handled by interceptor
  } finally {
    submitLoading.value = false
  }
}

onMounted(() => {
  loadCategoryList().then(() => {
    loadData()
  })
})
</script>

<style scoped>
.spu-management {
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

.product-image-wrapper {
  width: 60px;
  height: 60px;
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--bg-page);
  cursor: pointer;
}

.product-image {
  width: 100%;
  height: 100%;
}

.no-image {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
  font-size: 24px;
}

.product-name-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.product-name {
  font-weight: 500;
  color: var(--text-primary);
}

.product-subtitle {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
}

.sku-count {
  font-family: var(--font-mono);
  color: var(--primary-color);
}

.form-select {
  width: 100%;
}

.price-input {
  width: 100%;
}

.image-form-item {
  margin-bottom: 18px;
}

.image-upload-container {
  width: 100%;
}

.image-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-md);
}

.image-item {
  position: relative;
  width: 100px;
  height: 100px;
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 1px solid var(--border-color);
}

.uploaded-image {
  width: 100%;
  height: 100%;
}

.image-mask {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  opacity: 0;
  transition: opacity var(--transition-fast);
}

.image-item:hover .image-mask {
  opacity: 1;
}

.image-mask .el-icon {
  color: white;
  font-size: 18px;
  cursor: pointer;
}

.main-badge {
  position: absolute;
  top: 4px;
  left: 4px;
}

.image-uploader {
  width: 100px;
  height: 100px;
}

.upload-trigger {
  width: 100%;
  height: 100%;
  border: 2px dashed var(--border-color);
  border-radius: var(--radius-md);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.upload-trigger:hover {
  border-color: var(--primary-color);
  background: var(--primary-bg);
}

.upload-icon {
  font-size: 24px;
  color: var(--text-tertiary);
}

.upload-text {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  margin-top: 4px;
}

.image-tip {
  margin-top: var(--space-sm);
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
}

.pagination-wrapper {
  margin-top: var(--space-lg);
  display: flex;
  justify-content: flex-end;
}

.sku-config {
  padding: var(--space-md) 0;
}

.sku-actions {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  margin-bottom: var(--space-lg);
}

.sku-tip {
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
}

.sku-table {
  margin-bottom: var(--space-lg);
}

.specs-display {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.spec-tag {
  margin-right: 4px;
}

.sku-image-wrapper {
  width: 50px;
  height: 50px;
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 1px dashed var(--border-color);
  cursor: pointer;
}

.sku-image {
  width: 100%;
  height: 100%;
}

.sku-image-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
}

.sku-price-input,
.sku-stock-input {
  width: 100%;
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
