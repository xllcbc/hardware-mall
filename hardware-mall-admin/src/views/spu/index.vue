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
      width="min(1180px, calc(100vw - 32px))"
      top="4vh"
      class="sku-dialog"
      :close-on-click-modal="false"
    >
      <div v-loading="skuSubmitLoading" class="sku-config">
        <div class="sku-config-section">
          <div class="sku-section-title">
            <span class="section-step">1</span>
            选择规格
          </div>
          <div class="sku-section-hint">
            从当前分类的规格模板中选择这个商品实际使用的规格值，未选择的值不会生成 SKU。
          </div>

          <div v-if="!specSelections.length" class="sku-empty-tip">
            当前分类未配置规格模板，已有 SKU 仍可在下方调整价格、库存和保留状态。
          </div>
          <div v-else class="spec-select-list">
            <div
              v-for="selection in specSelections"
              :key="selection.templateId"
              class="spec-select-row"
            >
              <div class="spec-dimension">
                <el-checkbox v-model="selection.enabled" @change="handleSpecDimensionToggle(selection)">
                  {{ selection.name }}
                </el-checkbox>
                <el-tag v-if="selection.archived" type="warning" size="small" effect="light">已停用</el-tag>
              </div>
              <el-select
                v-model="selection.itemIds"
                multiple
                collapse-tags
                collapse-tags-tooltip
                filterable
                clearable
                :disabled="!selection.enabled"
                placeholder="请选择规格值"
                class="spec-value-select"
              >
                <el-option
                  v-for="item in selection.items"
                  :key="item.itemId"
                  :label="item.archived ? `${item.value}（已停用）` : item.value"
                  :value="item.itemId"
                  :disabled="item.archived && !selection.itemIds.includes(item.itemId)"
                />
              </el-select>
            </div>
          </div>

          <div class="generate-bar">
            <span class="sku-tip">
              已选择 {{ selectedDimensionCount }} 个规格维度，预计生成 {{ previewCombinationCount }} 个组合
            </span>
            <el-button
              type="primary"
              :disabled="!specSelections.length"
              @click="handleGenerateSkus"
            >
              <el-icon><Connection /></el-icon>
              生成规格组合
            </el-button>
          </div>
        </div>

        <div class="sku-config-section">
          <div class="sku-section-title">
            <span class="section-step">2</span>
            勾选保留组合
          </div>
          <div class="sku-section-hint">
            已存在的组合会保留原 SKU ID、图片、价格和库存；取消“保留”后保存时会软删除该组合。
          </div>

          <div class="sku-toolbar">
            <el-button size="small" @click="selectAllSkuRows">全部保留</el-button>
            <el-button size="small" @click="clearAllSkuRows">全部取消</el-button>
            <el-button
              size="small"
              :disabled="retainedSkuCount === 0"
              @click="openBatchDialog('price')"
            >
              批量设置价格
            </el-button>
            <el-button
              size="small"
              :disabled="retainedSkuCount === 0"
              @click="openBatchDialog('stock')"
            >
              批量设置库存
            </el-button>
            <span class="sku-toolbar-spacer" />
            <el-checkbox v-model="onlyRetained">仅显示保留项</el-checkbox>
            <span class="sku-count-summary">
              共 {{ skuList.length }} 个，保留 <strong>{{ retainedSkuCount }}</strong> 个
            </span>
          </div>

          <el-table
            :data="visibleSkuRows"
            stripe
            border
            row-key="rowKey"
            :row-class-name="skuRowClassName"
            max-height="480"
            class="sku-table"
          >
            <el-table-column label="保留" width="68" align="center">
              <template #default="{ row }">
                <el-checkbox v-model="row.retained" aria-label="保留该组合" />
              </template>
            </el-table-column>
            <el-table-column label="规格组合" min-width="240">
              <template #default="{ row }">
                <span v-if="row.legacy" class="default-sku-label">默认规格（无规格）</span>
                <div v-else class="specs-display">
                  <el-tag
                    v-for="spec in row.specs"
                    :key="`${spec.templateId}-${spec.itemId}`"
                    size="small"
                    class="spec-tag"
                  >
                    {{ spec.name }}: {{ spec.value }}
                  </el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="SKU图片" width="94">
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
            <el-table-column label="来源" width="82">
              <template #default="{ row }">
                <el-tag :type="row.id ? 'success' : 'primary'" size="small" effect="light">
                  {{ row.id ? '已有' : '新增' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button
                  link
                  type="danger"
                  size="small"
                  :disabled="!row.retained"
                  @click="row.retained = false"
                >
                  取消保留
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!visibleSkuRows.length" class="sku-table-empty">
            请选择规格值并生成组合
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="skuDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="skuSubmitLoading" @click="confirmSkuSubmit">保存配置</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="batchDialogVisible"
      :title="batchMode === 'price' ? '批量设置价格' : '批量设置库存'"
      width="420px"
      append-to-body
      class="sku-batch-dialog"
    >
      <el-form label-width="90px">
        <el-form-item :label="batchMode === 'price' ? '价格(元)' : '库存'">
          <el-input-number
            v-model="batchValue"
            :min="0"
            :precision="batchMode === 'price' ? 2 : 0"
            :controls="false"
            class="batch-value-input"
          />
        </el-form-item>
        <div class="batch-dialog-tip">
          将应用到当前保留的 {{ retainedSkuCount }} 个组合
        </div>
      </el-form>
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="applyBatchValue">应用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ElImageViewer } from 'element-plus'
import { getCategoryList } from '@/api/admin/category'
import { getSpuList, createSpu, updateSpu, deleteSpu, updateSpuStatus, Spu } from '@/api/admin/spu'
import {
  getSkuCounts,
  getSkuConfig,
  saveSkuConfig,
  Sku,
  SkuConfig,
  SkuConfigItem,
  SpecVO
} from '@/api/admin/sku'
import { uploadProductImage } from '@/api/admin/upload'
import {
  IMAGE_UPLOAD_ACCEPT,
  validateImageFile
} from '@/constants/upload'

interface SkuConfigRow extends Sku {
  rowKey: string
  retained: boolean
  legacy: boolean
}

interface SpecSelection {
  templateId: number
  name: string
  archived: boolean
  enabled: boolean
  itemIds: number[]
  items: SkuConfigItem[]
}

const loading = ref(false)
const submitLoading = ref(false)
const skuSubmitLoading = ref(false)
const tableData = ref<any[]>([])
const skuList = ref<SkuConfigRow[]>([])
const specSelections = ref<SpecSelection[]>([])
const onlyRetained = ref(false)
const batchDialogVisible = ref(false)
const batchMode = ref<'price' | 'stock'>('price')
const batchValue = ref(0)
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

const specKey = (specs: SpecVO[]) => {
  return [...specs]
    .map(spec => `${spec.templateId ?? 'default'}:${spec.itemId ?? 'default'}`)
    .sort()
    .join('|')
}

const isLegacySpecs = (specs: SpecVO[] = []) => {
  return specs.some(spec => spec.templateId == null || spec.itemId == null)
}

const toSkuConfigRow = (sku: Sku): SkuConfigRow => {
  const legacy = isLegacySpecs(sku.specs)
  return {
    ...sku,
    rowKey: legacy ? `legacy:${sku.id ?? specKey(sku.specs)}` : specKey(sku.specs),
    retained: true,
    legacy
  }
}

const applySkuConfig = (config: SkuConfig) => {
  const skus = config.skus || []

  specSelections.value = (config.templates || []).map(template => {
    const selectedItemIds = new Set<number>()
    skus.forEach(sku => {
      ;(sku.specs || []).forEach(spec => {
        if (spec.templateId === template.templateId && spec.itemId != null) {
          selectedItemIds.add(spec.itemId)
        }
      })
    })
    return {
      templateId: template.templateId,
      name: template.name,
      archived: Boolean(template.archived),
      enabled: selectedItemIds.size > 0,
      itemIds: [...selectedItemIds],
      items: template.items || []
    }
  })

  skuList.value = skus.map(toSkuConfigRow)
  onlyRetained.value = false
}

const retainedSkuCount = computed(() => skuList.value.filter(row => row.retained).length)

const selectedDimensionCount = computed(() => {
  return specSelections.value.filter(selection => selection.enabled && selection.itemIds.length > 0).length
})

const previewCombinationCount = computed(() => {
  const dimensions = specSelections.value.filter(
    selection => selection.enabled && selection.itemIds.length > 0
  )
  if (!dimensions.length) return 0
  return dimensions.reduce((total, selection) => total * selection.itemIds.length, 1)
})

const visibleSkuRows = computed(() => {
  return onlyRetained.value ? skuList.value.filter(row => row.retained) : skuList.value
})

const handleConfigSku = async (row: any) => {
  currentSpu.value = row
  skuDialogVisible.value = true
  skuSubmitLoading.value = true
  try {
    const res = await getSkuConfig(row.id)
    applySkuConfig(res)
  } catch {
    specSelections.value = []
    skuList.value = []
  } finally {
    skuSubmitLoading.value = false
  }
}

const handleSpecDimensionToggle = (selection: SpecSelection) => {
  if (selection.enabled && selection.itemIds.length === 0) {
    selection.itemIds = selection.items
      .filter(item => !item.archived)
      .map(item => item.itemId)
  }
}

const buildSpecCombinations = () => {
  const dimensions = specSelections.value
    .filter(selection => selection.enabled && selection.itemIds.length > 0)
    .map(selection => ({
      template: selection,
      items: selection.items.filter(item => selection.itemIds.includes(item.itemId))
    }))

  return dimensions.reduce<SpecVO[][]>((combinations, dimension) => {
    const next: SpecVO[][] = []
    combinations.forEach(combination => {
      dimension.items.forEach(item => {
        next.push([
          ...combination,
          {
            templateId: dimension.template.templateId,
            itemId: item.itemId,
            name: dimension.template.name,
            value: item.value
          }
        ])
      })
    })
    return next
  }, [[]])
}

const handleGenerateSkus = () => {
  if (!currentSpu.value) return

  const dimensions = specSelections.value.filter(
    selection => selection.enabled && selection.itemIds.length > 0
  )
  if (!dimensions.length) {
    ElMessage.warning('请至少选择一个规格值')
    return
  }

  if (previewCombinationCount.value > 200) {
    ElMessage.warning(`当前会生成 ${previewCombinationCount.value} 个组合，超过 200 个上限，请减少规格值`)
    return
  }

  const existingByKey = new Map(
    skuList.value
      .filter(row => !row.legacy)
      .map(row => [row.rowKey, row])
  )
  const legacyRows = skuList.value.filter(row => row.legacy)
  const rows = buildSpecCombinations().map(specs => {
    const rowKey = specKey(specs)
    const existing = existingByKey.get(rowKey)
    if (existing) {
      return {
        ...existing,
        specs
      }
    }
    return {
      spuId: currentSpu.value!.id!,
      specs,
      price: currentSpu.value?.originalPrice || 0,
      stock: 0,
      status: 1,
      rowKey,
      retained: true,
      legacy: false
    }
  })

  skuList.value = [...rows, ...legacyRows]
  ElMessage.success(`已生成 ${rows.length} 个规格组合`)
}

const selectAllSkuRows = () => {
  skuList.value.forEach(row => {
    row.retained = true
  })
}

const clearAllSkuRows = () => {
  skuList.value.forEach(row => {
    row.retained = false
  })
}

const skuRowClassName = ({ row }: { row: SkuConfigRow }) => {
  return row.retained ? '' : 'sku-row-unretained'
}

const openBatchDialog = (mode: 'price' | 'stock') => {
  batchMode.value = mode
  batchValue.value = 0
  batchDialogVisible.value = true
}

const applyBatchValue = () => {
  skuList.value.forEach(row => {
    if (!row.retained) return
    if (batchMode.value === 'price') {
      row.price = batchValue.value
    } else {
      row.stock = Math.max(0, Math.floor(batchValue.value))
    }
  })
  batchDialogVisible.value = false
  ElMessage.success(`已更新 ${retainedSkuCount.value} 个保留组合`)
}

const handleSkuImageUpload = (row: SkuConfigRow) => {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = IMAGE_UPLOAD_ACCEPT
  input.onchange = async (e: any) => {
    const file = e.target.files[0]
    if (!file) return
    const validationError = validateImageFile(file)
    if (validationError) {
      ElMessage.error(validationError)
      return
    }
    try {
      const result = await uploadProductImage(file, currentSpu.value?.categoryId)
      row.image = result.url
      ElMessage.success('图片上传成功')
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '图片上传失败')
    }
  }
  input.click()
}

const confirmSkuSubmit = async () => {
  if (!currentSpu.value?.id) return
  skuSubmitLoading.value = true
  try {
    const retainedRows = skuList.value.filter(row => row.retained)
    if (!retainedRows.length) {
      await ElMessageBox.confirm(
        '当前没有保留任何 SKU，保存后该商品将暂时无法销售。确定继续吗？',
        '警告',
        { type: 'warning' }
      )
    }

    const res = await saveSkuConfig(currentSpu.value.id, {
      skus: retainedRows.map(row => ({
        id: row.id,
        specs: row.specs,
        price: Number(row.price) || 0,
        stock: Number(row.stock) || 0,
        image: row.image,
        status: row.status ?? 1
      }))
    })
    applySkuConfig(res)
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
  const validationError = validateImageFile(file)
  if (validationError) {
    ElMessage.error(validationError)
    return false
  }
  return true
}

const handleImageUpload = async (options: any) => {
  const { file, onSuccess, onError } = options

  try {
    const res = await uploadProductImage(file, form.categoryId as number | undefined)
    form.images.push(res.url)
    ElMessage.success('图片上传成功')
    onSuccess()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '图片上传失败')
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
  max-height: calc(88vh - 150px);
  overflow-y: auto;
  padding: 0 4px 4px 0;
}

.sku-config-section + .sku-config-section {
  margin-top: var(--space-lg);
  padding-top: var(--space-lg);
  border-top: 1px solid var(--border-color);
}

.sku-section-title {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  color: var(--text-primary);
  font-size: var(--font-size-lg);
  font-weight: 600;
}

.section-step {
  display: inline-flex;
  width: 22px;
  height: 22px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--primary-color);
  color: #fff;
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.sku-section-hint {
  margin: 6px 0 var(--space-md) 30px;
  color: var(--text-tertiary);
  font-size: var(--font-size-sm);
}

.sku-empty-tip {
  padding: var(--space-md);
  border: 1px dashed var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-page);
  color: var(--text-tertiary);
  text-align: center;
}

.spec-select-list {
  display: grid;
  gap: var(--space-sm);
}

.spec-select-row {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  align-items: center;
  gap: var(--space-md);
  padding: 10px 0;
  border-bottom: 1px dashed var(--border-color);
}

.spec-select-row:last-child {
  border-bottom: 0;
}

.spec-dimension {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  color: var(--text-primary);
  font-weight: 500;
}

.spec-value-select {
  width: 100%;
}

.generate-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  margin-top: var(--space-sm);
  padding-top: var(--space-md);
  border-top: 1px solid var(--border-color);
}

.sku-tip {
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
}

.sku-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-xs);
  margin-bottom: var(--space-md);
}

.sku-toolbar-spacer {
  flex: 1;
}

.sku-count-summary {
  margin-left: var(--space-sm);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.sku-count-summary strong {
  color: var(--primary-color);
}

.sku-table {
  margin-bottom: var(--space-sm);
}

.sku-table-empty {
  padding: var(--space-lg);
  border: 1px dashed var(--border-color);
  border-radius: var(--radius-md);
  color: var(--text-tertiary);
  text-align: center;
}

.default-sku-label {
  color: var(--text-secondary);
  font-weight: 500;
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

.batch-value-input {
  width: 100%;
}

.batch-dialog-tip {
  margin-left: 90px;
  color: var(--text-tertiary);
  font-size: var(--font-size-sm);
}

:deep(.sku-row-unretained td.el-table__cell) {
  background: var(--bg-page);
  color: var(--text-tertiary);
}

:deep(.sku-row-unretained .spec-tag),
:deep(.sku-row-unretained .sku-image),
:deep(.sku-row-unretained .el-input-number),
:deep(.sku-row-unretained .el-switch) {
  opacity: 0.55;
}

@media (max-width: 900px) {
  .spec-select-row {
    grid-template-columns: 1fr;
    gap: var(--space-xs);
  }

  .sku-toolbar-spacer {
    display: none;
  }

  .sku-count-summary {
    width: 100%;
    margin-left: 0;
  }
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
