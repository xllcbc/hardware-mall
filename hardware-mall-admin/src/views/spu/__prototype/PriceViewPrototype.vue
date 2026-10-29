<template>
  <div class="spu-management">
    <div class="prototype-banner">
      <span>PROTOTYPE</span>
      <p>在现有商品管理页增加价格视角，不改动原表格和操作入口。</p>
      <code>levelId={{ currentLevel.id }}</code>
      <div class="layout-switch">
        <button :class="{ active: isCompact }" @click="setLayout('compact')">紧凑视图</button>
        <button :class="{ active: !isCompact }" @click="setLayout('full')">完整字段</button>
      </div>
    </div>

    <div class="page-header animate-fade-in-down">
      <div class="header-content">
        <h2 class="page-title">商品管理</h2>
        <p class="page-desc">管理商品型号及规格配置</p>
      </div>
      <div class="header-actions">
        <el-button @click="goToMembers">
          <el-icon><User /></el-icon>
          归属用户
        </el-button>
        <el-button @click="goToRules">
          <el-icon><PriceTag /></el-icon>
          返回分级规则
        </el-button>
        <el-button type="primary" @click="openProductDialog()">
          <el-icon><Plus /></el-icon>
          新增商品
        </el-button>
      </div>
    </div>

    <div class="card search-card animate-fade-in-up stagger-1">
      <el-form :inline="true" class="search-form">
        <el-form-item label="价格视角">
          <el-select
            :model-value="currentLevel.id"
            class="price-view-select"
            @change="changeLevel"
          >
            <el-option
              v-for="level in enabledLevels"
              :key="level.id"
              :label="level.name"
              :value="level.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="商品分类">
          <el-select
            v-model="queryForm.categoryId"
            placeholder="全部"
            clearable
            class="search-select"
            @change="applyFilters"
          >
            <el-option
              v-for="category in categories"
              :key="category.id"
              :label="category.name"
              :value="category.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="商品名称">
          <el-input
            v-model="queryForm.keyword"
            placeholder="请输入商品名称"
            clearable
            class="search-input"
            @keyup.enter="applyFilters"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="applyFilters">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="resetFilters">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>

      <div class="price-view-summary">
        <span>
          当前按 <strong>{{ currentLevel.name }}</strong> 展示
        </span>
        <span>归属用户 {{ formatNumber(currentLevel.userCount) }}</span>
        <span>覆盖分类 {{ configuredCategoryCount }} / {{ categories.length }}</span>
        <span v-if="currentMissing.length" class="warning">
          {{ currentMissing.map(item => item.name).join('、') }}未配置，按零售价兜底
        </span>
        <span v-else class="success">分类倍率完整</span>
      </div>
    </div>

    <div class="card table-card animate-fade-in-up stagger-2">
      <el-table :data="pagedProducts" stripe class="spu-table">
        <el-table-column v-if="!isCompact" prop="id" label="ID" width="80" />
        <el-table-column v-if="!isCompact" label="商品图片" width="100">
          <template #default="{ row }">
            <div class="product-image-wrapper">
              <div class="prototype-product-icon" :class="`tone-${row.categoryId % 4}`">
                <el-icon><component :is="row.icon" /></el-icon>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="商品名称" min-width="210">
          <template #default="{ row }">
            <div v-if="isCompact" class="compact-product-cell">
              <div class="product-image-wrapper compact-image">
                <div class="prototype-product-icon" :class="`tone-${row.categoryId % 4}`">
                  <el-icon><component :is="row.icon" /></el-icon>
                </div>
              </div>
              <div class="product-name-cell">
                <span class="compact-name-line">
                  <span class="product-name">{{ row.name }}</span>
                  <el-tag
                    v-if="row.isRecommend === 1"
                    type="warning"
                    size="small"
                    effect="light"
                  >
                    推荐
                  </el-tag>
                </span>
                <span class="product-subtitle">{{ row.subtitle }}</span>
                <span class="compact-meta">
                  ID {{ row.id }} · SKU {{ row.skus.length }} · 销量 {{ row.salesCount }}
                </span>
              </div>
            </div>
            <div v-else class="product-name-cell">
              <span class="product-name">{{ row.name }}</span>
              <span class="product-subtitle">{{ row.subtitle }} · {{ row.code }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="价格" width="220">
          <template #default="{ row }">
            <div class="price-view-cell">
              <strong>{{ priceInfo(row).levelLabel }}</strong>
              <span v-if="!currentLevel.isRetail">零售 {{ priceInfo(row).retailLabel }}</span>
              <em :class="priceInfo(row).state">{{ priceInfo(row).ruleLabel }}</em>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="light">{{ categoryName(row.categoryId) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="!isCompact" label="SKU数量" width="90">
          <template #default="{ row }">
            <span class="sku-count">{{ row.skus.length }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="!isCompact" prop="salesCount" label="销量" width="80" />
        <el-table-column v-if="!isCompact" label="推荐" width="80">
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
        <el-table-column v-if="!isCompact" label="折扣规则" width="110">
          <template #default="{ row }">
            <el-tag
              :type="row.excludeDiscount === 1 ? 'info' : 'success'"
              size="small"
              effect="light"
            >
              {{ row.excludeDiscount === 1 ? '不参与折扣' : '参与折扣' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="isCompact" label="状态与规则" width="120">
          <template #default="{ row }">
            <div class="compact-status">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="light">
                {{ row.status === 1 ? '上架' : '下架' }}
              </el-tag>
              <span :class="{ excluded: row.excludeDiscount === 1 }">
                {{ row.excludeDiscount === 1 ? '不参与折扣' : '参与折扣' }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-else label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="light">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          :width="isCompact ? 150 : 300"
          :fixed="isCompact ? false : 'right'"
        >
          <template #default="{ row }">
            <div v-if="isCompact" class="compact-actions">
              <el-tooltip content="编辑商品">
                <el-button link type="primary" @click="openProductDialog(row)">
                  <el-icon><Edit /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="规格配置">
                <el-button link type="success" @click="openSkuDialog(row)">
                  <el-icon><Setting /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip :content="row.status === 1 ? '下架' : '上架'">
                <el-button
                  link
                  :type="row.status === 1 ? 'warning' : 'success'"
                  @click="toggleStatus(row)"
                >
                  <el-icon><Switch /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="删除商品">
                <el-button link type="danger" @click="handlePrototypeAction('删除商品')">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
            <template v-else>
              <el-button link type="primary" @click="openProductDialog(row)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button link type="success" @click="openSkuDialog(row)">
                <el-icon><Setting /></el-icon>
                规格配置
              </el-button>
              <el-button
                link
                :type="row.status === 1 ? 'warning' : 'success'"
                @click="toggleStatus(row)"
              >
                {{ row.status === 1 ? '下架' : '上架' }}
              </el-button>
              <el-button link type="danger" @click="handlePrototypeAction('删除商品')">
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.limit"
          :total="filteredProducts.length"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
        />
      </div>
    </div>

    <el-dialog
      v-model="productDialogVisible"
      :title="editingProduct ? '编辑商品' : '新增商品'"
      width="760px"
      class="spu-dialog"
    >
      <el-form :model="productForm" label-width="100px">
        <el-form-item label="商品分类">
          <el-select v-model="productForm.categoryId" style="width: 100%">
            <el-option
              v-for="category in categories"
              :key="category.id"
              :label="category.name"
              :value="category.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="商品名称">
          <el-input v-model="productForm.name" />
        </el-form-item>
        <el-form-item label="商品副标题">
          <el-input v-model="productForm.subtitle" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="商品原价">
              <el-input-number
                v-model="productForm.originalPrice"
                :min="0"
                :precision="2"
                :controls="false"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商品状态">
              <el-switch
                v-model="productForm.status"
                :active-value="1"
                :inactive-value="0"
                active-text="上架"
                inactive-text="下架"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="首页推荐">
          <el-switch
            v-model="productForm.isRecommend"
            :active-value="1"
            :inactive-value="0"
            active-text="推荐"
            inactive-text="不推荐"
          />
        </el-form-item>
        <el-form-item label="折扣规则">
          <div class="discount-setting">
            <el-switch
              v-model="productForm.excludeDiscount"
              :active-value="1"
              :inactive-value="0"
              active-text="不参与折扣"
              inactive-text="参与折扣"
            />
            <span>
              开启后，该商品在所有用户等级下都按 SKU 零售价，不应用分类倍率。
            </span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="productDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveProduct">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="skuDialogVisible"
      :title="`规格配置 - ${currentProduct?.name || ''}`"
      width="min(1180px, calc(100vw - 32px))"
      top="4vh"
      class="sku-dialog"
      :close-on-click-modal="false"
    >
      <div class="sku-config">
        <div class="sku-config-section">
          <div class="sku-section-title">
            <span class="section-step">1</span>
            选择规格
          </div>
          <div class="sku-section-hint">
            从当前分类的规格模板中选择这个商品实际使用的规格值，未选择的值不会生成 SKU。
          </div>
          <div class="spec-select-list">
            <div
              v-for="selection in specSelections"
              :key="selection.templateId"
              class="spec-select-row"
            >
              <div class="spec-dimension">
                <el-checkbox v-model="selection.enabled">
                  {{ selection.name }}
                </el-checkbox>
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
                  :key="item.id"
                  :label="item.value"
                  :value="item.id"
                />
              </el-select>
            </div>
          </div>
          <div class="generate-bar">
            <span class="sku-tip">
              已选择 {{ selectedDimensionCount }} 个规格维度，预计生成
              {{ previewCombinationCount }} 个组合
            </span>
            <el-button type="primary" @click="generateCombinations">
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
            已有组合会保留 SKU ID、图片、价格和库存；取消保留后，保存时会软删除该组合。
          </div>
          <div class="sku-toolbar">
            <el-button size="small" @click="setAllRetained(true)">全部保留</el-button>
            <el-button size="small" @click="setAllRetained(false)">全部取消</el-button>
            <el-button
              size="small"
              :disabled="retainedSkuCount === 0"
              @click="batchAction('价格')"
            >
              批量设置价格
            </el-button>
            <el-button
              size="small"
              :disabled="retainedSkuCount === 0"
              @click="batchAction('库存')"
            >
              批量设置库存
            </el-button>
            <span class="sku-toolbar-spacer"></span>
            <el-checkbox v-model="onlyRetained">仅显示保留项</el-checkbox>
            <span class="sku-count-summary">
              共 {{ skuRows.length }} 个，保留 <strong>{{ retainedSkuCount }}</strong> 个
            </span>
          </div>
          <el-table
            :data="visibleSkuRows"
            stripe
            border
            row-key="id"
            max-height="480"
            class="sku-table"
          >
            <el-table-column label="保留" width="68" align="center">
              <template #default="{ row }">
                <el-checkbox v-model="row.retained" />
              </template>
            </el-table-column>
            <el-table-column label="规格组合" min-width="240">
              <template #default="{ row }">
                <div class="specs-display">
                  <el-tag
                    v-for="spec in row.specs"
                    :key="spec"
                    size="small"
                    class="spec-tag"
                  >
                    {{ spec }}
                  </el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="SKU图片" width="90" align="center" header-align="center">
              <template #default>
                <div class="sku-image-placeholder">
                  <el-icon><Picture /></el-icon>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="零售价(元)" width="130" align="center" header-align="center">
              <template #default="{ row }">
                <div class="sku-number-field">
                  <el-input-number
                    v-model="row.retailPrice"
                    :min="0"
                    :precision="2"
                    :controls="false"
                    size="small"
                    class="sku-price-input"
                  />
                </div>
              </template>
            </el-table-column>
            <el-table-column
              :label="`${currentLevel.name}价`"
              width="125"
              align="center"
              header-align="center"
            >
              <template #default="{ row }">
                <span class="sku-level-price">
                  {{ formatCurrency(levelPrice(currentProduct!, row.retailPrice)) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="库存" width="105" align="center" header-align="center">
              <template #default="{ row }">
                <div class="sku-number-field">
                  <el-input-number
                    v-model="row.stock"
                    :min="0"
                    :controls="false"
                    size="small"
                    class="sku-stock-input"
                  />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="72" align="center" header-align="center">
              <template #default="{ row }">
                <el-switch v-model="row.status" :active-value="1" :inactive-value="0" />
              </template>
            </el-table-column>
            <el-table-column label="来源" width="76" align="center" header-align="center">
              <template #default="{ row }">
                <el-tag :type="row.id ? 'success' : 'primary'" size="small" effect="light">
                  {{ row.id ? '已有' : '新增' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              label="操作"
              width="90"
              fixed="right"
              align="center"
              header-align="center"
            >
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
        </div>
      </div>
      <template #footer>
        <el-button @click="skuDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handlePrototypeAction('保存规格配置')">
          保存配置
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  draftCategories,
  draftLevels,
  formatNumber,
  missingCategories,
  type DraftLevel
} from '../../user-level/__prototype/prototypeData'

interface DemoSpecSku {
  id: number
  specs: string[]
  retailPrice: number
  stock: number
}

interface DemoProduct {
  id: number
  code: string
  name: string
  subtitle: string
  categoryId: number
  icon: string
  salesCount: number
  isRecommend: number
  status: number
  excludeDiscount: number
  skus: DemoSpecSku[]
}

interface SkuConfigRow extends DemoSpecSku {
  retained: boolean
  status: number
}

interface SpecSelection {
  templateId: number
  name: string
  enabled: boolean
  itemIds: number[]
  items: Array<{ id: number; value: string }>
}

const route = useRoute()
const router = useRouter()
const categories = draftCategories
const enabledLevels = draftLevels.filter(level => level.status === 1)
const queryForm = reactive({
  categoryId: Number(route.query.categoryId) || null as number | null,
  keyword: ''
})
const pagination = reactive({ page: 1, limit: 10 })
const productDialogVisible = ref(false)
const skuDialogVisible = ref(false)
const editingProduct = ref<DemoProduct | null>(null)
const currentProduct = ref<DemoProduct | null>(null)
const skuRows = ref<SkuConfigRow[]>([])
const specSelections = ref<SpecSelection[]>([])
const onlyRetained = ref(false)
const productForm = reactive({
  categoryId: 1,
  name: '',
  subtitle: '',
  originalPrice: 0,
  status: 1,
  isRecommend: 0,
  excludeDiscount: 0
})

const products = ref<DemoProduct[]>([
  {
    id: 101,
    code: 'SPU-3048',
    name: '304不锈钢静音锁体',
    subtitle: '5850双舌 / 拉丝银',
    categoryId: 1,
    icon: 'Lock',
    salesCount: 428,
    isRecommend: 1,
    status: 1,
    excludeDiscount: 0,
    skus: [
      { id: 1001, specs: ['拉丝银', '标准型'], retailPrice: 218, stock: 86 },
      { id: 1002, specs: ['拉丝银', '加长型'], retailPrice: 238, stock: 54 },
      { id: 1003, specs: ['哑光黑', '标准型'], retailPrice: 258, stock: 32 }
    ]
  },
  {
    id: 102,
    code: 'SPU-6801',
    name: '智能指纹门锁',
    subtitle: '半导体识别 / 远程临时密码',
    categoryId: 1,
    icon: 'Key',
    salesCount: 196,
    isRecommend: 1,
    status: 1,
    excludeDiscount: 1,
    skus: [
      { id: 2001, specs: ['曜石黑', '标准锁体'], retailPrice: 699, stock: 28 },
      { id: 2002, specs: ['香槟金', '标准锁体'], retailPrice: 899, stock: 17 }
    ]
  },
  {
    id: 103,
    code: 'SPU-H160',
    name: '铝合金极简拉手',
    subtitle: '160mm孔距 / 哑光黑',
    categoryId: 2,
    icon: 'Link',
    salesCount: 365,
    isRecommend: 0,
    status: 1,
    excludeDiscount: 0,
    skus: [
      { id: 3001, specs: ['128mm', '哑光黑'], retailPrice: 68, stock: 144 },
      { id: 3002, specs: ['160mm', '哑光黑'], retailPrice: 89, stock: 102 },
      { id: 3003, specs: ['192mm', '哑光黑'], retailPrice: 109, stock: 76 }
    ]
  },
  {
    id: 104,
    code: 'SPU-HG40',
    name: '液压缓冲合页',
    subtitle: '4寸 / 不锈钢加厚',
    categoryId: 3,
    icon: 'Connection',
    salesCount: 892,
    isRecommend: 1,
    status: 1,
    excludeDiscount: 0,
    skus: [
      { id: 4001, specs: ['4寸', '不锈钢'], retailPrice: 42, stock: 260 },
      { id: 4002, specs: ['5寸', '不锈钢'], retailPrice: 56, stock: 184 },
      { id: 4003, specs: ['5寸', '黑钛'], retailPrice: 68, stock: 95 }
    ]
  },
  {
    id: 105,
    code: 'SPU-C908',
    name: '强磁静音门吸',
    subtitle: '加厚款 / 墙装',
    categoryId: 4,
    icon: 'Box',
    salesCount: 534,
    isRecommend: 0,
    status: 1,
    excludeDiscount: 0,
    skus: [
      { id: 5001, specs: ['短款', '不锈钢'], retailPrice: 32, stock: 208 },
      { id: 5002, specs: ['长款', '不锈钢'], retailPrice: 42, stock: 126 },
      { id: 5003, specs: ['长款', '黑钛'], retailPrice: 52, stock: 63 }
    ]
  },
  {
    id: 106,
    code: 'SPU-C120',
    name: '暗装闭门器',
    subtitle: '65kg承重 / 可调闭门速度',
    categoryId: 4,
    icon: 'Setting',
    salesCount: 278,
    isRecommend: 0,
    status: 1,
    excludeDiscount: 0,
    skus: [
      { id: 6001, specs: ['标准型', '银色'], retailPrice: 168, stock: 48 },
      { id: 6002, specs: ['加强型', '银色'], retailPrice: 198, stock: 29 }
    ]
  },
  {
    id: 107,
    code: 'SPU-F12',
    name: '304内六角螺栓套装',
    subtitle: 'M6-M12 / 混装盒',
    categoryId: 5,
    icon: 'Grid',
    salesCount: 1128,
    isRecommend: 1,
    status: 1,
    excludeDiscount: 0,
    skus: [
      { id: 7001, specs: ['M6', '120件'], retailPrice: 18, stock: 356 },
      { id: 7002, specs: ['M8', '96件'], retailPrice: 28, stock: 248 },
      { id: 7003, specs: ['M10', '72件'], retailPrice: 38, stock: 172 }
    ]
  },
  {
    id: 108,
    code: 'SPU-T088',
    name: '棘轮扳手套装',
    subtitle: '46件套 / 铬钒钢',
    categoryId: 6,
    icon: 'Tools',
    salesCount: 217,
    isRecommend: 0,
    status: 0,
    excludeDiscount: 0,
    skus: [
      { id: 8001, specs: ['46件', '塑箱'], retailPrice: 98, stock: 44 },
      { id: 8002, specs: ['72件', '塑箱'], retailPrice: 138, stock: 26 },
      { id: 8003, specs: ['72件', '铁箱'], retailPrice: 178, stock: 18 }
    ]
  },
  {
    id: 109,
    code: 'SPU-PK08',
    name: '工程门锁套装',
    subtitle: '锁体 / 执手 / 合页组合包',
    categoryId: 7,
    icon: 'Suitcase',
    salesCount: 142,
    isRecommend: 0,
    status: 1,
    excludeDiscount: 0,
    skus: [
      { id: 9001, specs: ['基础包', '拉丝银'], retailPrice: 368, stock: 36 },
      { id: 9002, specs: ['加强包', '拉丝银'], retailPrice: 428, stock: 22 },
      { id: 9003, specs: ['加强包', '哑光黑'], retailPrice: 498, stock: 15 }
    ]
  }
])

const currentLevel = computed<DraftLevel>(() => {
  const levelId = Number(route.query.levelId)
  return draftLevels.find(level => level.id === levelId && level.status === 1)
    || draftLevels.find(level => level.id === 1)
    || draftLevels[0]
})
const isCompact = computed(() => route.query.layout !== 'full')

const currentMissing = computed(() => missingCategories(currentLevel.value))
const configuredCategoryCount = computed(
  () => categories.length - currentMissing.value.length
)
const filteredProducts = computed(() => {
  const keyword = queryForm.keyword.trim().toLowerCase()
  return products.value.filter(product => {
    const matchesCategory =
      !queryForm.categoryId || product.categoryId === queryForm.categoryId
    const matchesKeyword =
      !keyword ||
      `${product.name} ${product.subtitle} ${product.code}`.toLowerCase().includes(keyword)
    return matchesCategory && matchesKeyword
  })
})
const pagedProducts = computed(() => {
  const start = (pagination.page - 1) * pagination.limit
  return filteredProducts.value.slice(start, start + pagination.limit)
})
const selectedDimensionCount = computed(
  () => specSelections.value.filter(selection => selection.enabled).length
)
const previewCombinationCount = computed(() =>
  specSelections.value
    .filter(selection => selection.enabled)
    .reduce((total, selection) => total * Math.max(selection.itemIds.length, 0), 1)
)
const retainedSkuCount = computed(
  () => skuRows.value.filter(row => row.retained).length
)
const visibleSkuRows = computed(() =>
  onlyRetained.value
    ? skuRows.value.filter(row => row.retained)
    : skuRows.value
)

const categoryName = (categoryId: number) =>
  categories.find(category => category.id === categoryId)?.name || '-'

const activeRate = (product: DemoProduct) => {
  if (currentLevel.value.isRetail || product.excludeDiscount === 1) return 1
  return currentLevel.value.rates[product.categoryId] ?? 1
}

const rateIsMissing = (product: DemoProduct) =>
  !currentLevel.value.isRetail
  && product.excludeDiscount !== 1
  && currentLevel.value.rates[product.categoryId] == null

const levelPrice = (product: DemoProduct, retailPrice: number) =>
  Math.round((retailPrice * activeRate(product) + Number.EPSILON) * 100) / 100

const formatCurrency = (value: number) => `¥${value.toFixed(2)}`
const formatRange = (min: number, max: number) =>
  min === max ? formatCurrency(min) : `${formatCurrency(min)}–${formatCurrency(max)}`

const priceInfo = (product: DemoProduct) => {
  if (!product.skus.length) {
    return {
      levelLabel: '待配置 SKU',
      retailLabel: '-',
      state: 'missing',
      ruleLabel: '尚未配置规格'
    }
  }
  const retailPrices = product.skus.map(sku => sku.retailPrice)
  const levelPrices = product.skus.map(sku => levelPrice(product, sku.retailPrice))
  const state = rateIsMissing(product)
    ? 'missing'
    : product.excludeDiscount === 1
      ? 'excluded'
      : 'normal'
  return {
    levelLabel: formatRange(Math.min(...levelPrices), Math.max(...levelPrices)),
    retailLabel: formatRange(Math.min(...retailPrices), Math.max(...retailPrices)),
    state,
    ruleLabel: rateIsMissing(product)
      ? '未配置 · 零售兜底'
      : product.excludeDiscount === 1
        ? '不参与折扣'
        : currentLevel.value.isRetail
          ? '零售基准'
          : `× ${activeRate(product).toFixed(4)}`
  }
}

const changeLevel = (levelId: number) => {
  router.replace({
    path: route.path,
    query: {
      ...route.query,
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

const applyFilters = () => {
  pagination.page = 1
}

const resetFilters = () => {
  queryForm.categoryId = null
  queryForm.keyword = ''
  pagination.page = 1
  router.replace({
    path: route.path,
    query: {
      prototype: 'price-view',
      levelId: String(currentLevel.value.id)
    }
  })
}

const openProductDialog = (product?: DemoProduct) => {
  editingProduct.value = product || null
  Object.assign(productForm, {
    categoryId: product?.categoryId || queryForm.categoryId || 1,
    name: product?.name || '',
    subtitle: product?.subtitle || '',
    originalPrice: product
      ? Math.min(...product.skus.map(sku => sku.retailPrice))
      : 0,
    status: product?.status ?? 1,
    isRecommend: product?.isRecommend ?? 0,
    excludeDiscount: product?.excludeDiscount ?? 0
  })
  productDialogVisible.value = true
}

const saveProduct = () => {
  if (!productForm.name.trim()) {
    ElMessage.warning('请输入商品名称')
    return
  }
  if (editingProduct.value) {
    Object.assign(editingProduct.value, {
      categoryId: productForm.categoryId,
      name: productForm.name.trim(),
      subtitle: productForm.subtitle.trim(),
      status: productForm.status,
      isRecommend: productForm.isRecommend,
      excludeDiscount: productForm.excludeDiscount
    })
  } else {
    products.value.unshift({
      id: Math.max(...products.value.map(product => product.id)) + 1,
      code: `SPU-NEW${products.value.length + 1}`,
      name: productForm.name.trim(),
      subtitle: productForm.subtitle.trim() || '新建商品',
      categoryId: productForm.categoryId,
      icon: 'Box',
      salesCount: 0,
      isRecommend: productForm.isRecommend,
      status: productForm.status,
      excludeDiscount: productForm.excludeDiscount,
      skus: []
    })
  }
  productDialogVisible.value = false
  ElMessage.success(editingProduct.value ? '原型商品已更新' : '原型商品已创建')
}

const openSkuDialog = (product: DemoProduct) => {
  currentProduct.value = product
  skuRows.value = product.skus.map(sku => ({
    ...sku,
    retained: true,
    status: 1
  }))
  const dimensionNames =
    product.categoryId === 1
      ? ['表面处理', '锁体规格']
      : product.categoryId === 2
        ? ['孔距', '颜色']
        : ['规格一', '规格二']
  specSelections.value = [0, 1].map(index => {
    const values = Array.from(
      new Set(product.skus.map(sku => sku.specs[index]).filter(Boolean))
    )
    return {
      templateId: index + 1,
      name: dimensionNames[index] || `规格${index + 1}`,
      enabled: values.length > 0,
      itemIds: values.map((_, itemIndex) => itemIndex + 1),
      items: values.map((value, itemIndex) => ({
        id: itemIndex + 1,
        value
      }))
    }
  })
  onlyRetained.value = false
  skuDialogVisible.value = true
}

const generateCombinations = () => {
  ElMessage.success(`已按当前选择预生成 ${previewCombinationCount.value} 个规格组合`)
}

const setAllRetained = (retained: boolean) => {
  skuRows.value.forEach(row => {
    row.retained = retained
  })
}

const batchAction = (field: string) => {
  ElMessage.info(`将只对当前保留的 ${retainedSkuCount.value} 个 SKU 批量设置${field}`)
}

const toggleStatus = (product: DemoProduct) => {
  product.status = product.status === 1 ? 0 : 1
  ElMessage.success(`已在原型中${product.status === 1 ? '上架' : '下架'}`)
}

const handlePrototypeAction = (action: string) => {
  ElMessage.info(`原型保留“${action}”入口，正式实现继续复用现有功能`)
}

const goToMembers = () => {
  router.push({
    path: '/user',
    query: {
      prototype: 'price-view',
      levelId: String(currentLevel.value.id)
    }
  })
}

const goToRules = () => {
  router.push({
    path: '/user-level',
    query: {
      prototype: '1',
      variant: 'B'
    }
  })
}
</script>

<style scoped>
.spu-management {
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

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
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
.search-input,
.price-view-select {
  width: 180px;
}

.price-view-select :deep(.el-select__wrapper) {
  box-shadow: 0 0 0 1px var(--border-accent) inset;
  background: var(--primary-bg);
}

.price-view-summary {
  min-height: 32px;
  margin-top: 4px;
  padding: 7px 10px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 16px;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  background: var(--bg-page);
  font-size: 11px;
}

.price-view-summary strong {
  color: var(--text-primary);
}

.price-view-summary .warning {
  color: var(--warning);
}

.price-view-summary .success {
  color: var(--success);
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
}

.prototype-product-icon {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  font-size: 24px;
}

.tone-0 {
  color: #8d6548;
  background: #f3e7dc;
}

.tone-1 {
  color: #4d687d;
  background: #e7eef3;
}

.tone-2 {
  color: #697353;
  background: #eef0e5;
}

.tone-3 {
  color: #6c6179;
  background: #eee9f2;
}

.product-name-cell,
.price-view-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.compact-product-cell {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.compact-image {
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
}

.compact-image .prototype-product-icon {
  font-size: 17px;
}

.compact-product-cell .product-name-cell {
  min-width: 0;
}

.compact-name-line {
  display: flex;
  align-items: center;
  gap: 6px;
}

.compact-meta {
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: 8px;
}

.compact-status {
  display: grid;
  justify-items: start;
  gap: 3px;
}

.compact-status > span {
  color: var(--success);
  font-size: 8px;
}

.compact-status > span.excluded {
  color: var(--text-tertiary);
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

.price-view-cell strong {
  color: var(--text-primary);
  font-family: var(--font-mono);
  font-size: 12px;
}

.price-view-cell span {
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: 9px;
}

.price-view-cell em {
  width: max-content;
  color: var(--success);
  font-size: 9px;
  font-style: normal;
}

.price-view-cell em.missing {
  color: var(--warning);
}

.price-view-cell em.excluded {
  color: var(--text-tertiary);
}

.discount-setting {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.discount-setting > span {
  color: var(--text-tertiary);
  font-size: var(--font-size-xs);
  line-height: 1.5;
}

.pagination-wrapper {
  margin-top: var(--space-lg);
  display: flex;
  justify-content: flex-end;
}

.sku-config {
  padding: var(--space-md) 0;
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
  font-size: var(--font-size-base);
  font-weight: 600;
}

.section-step {
  display: inline-flex;
  width: 22px;
  height: 22px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  background: var(--primary-color);
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
}

.sku-section-hint {
  margin: var(--space-xs) 0 var(--space-md) 30px;
  color: var(--text-tertiary);
  font-size: var(--font-size-xs);
}

.spec-select-list {
  display: grid;
  gap: var(--space-sm);
}

.spec-select-row {
  padding: var(--space-sm) var(--space-md);
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  align-items: center;
  gap: var(--space-md);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  background: var(--bg-page);
}

.spec-dimension {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.spec-value-select {
  width: 100%;
}

.generate-bar,
.sku-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--space-sm);
}

.generate-bar {
  margin-top: var(--space-md);
}

.sku-toolbar {
  margin-bottom: var(--space-md);
}

.sku-toolbar-spacer {
  flex: 1;
}

.sku-count-summary {
  color: var(--text-tertiary);
  font-size: var(--font-size-xs);
}

.sku-count-summary strong {
  color: var(--primary-color);
}

.sku-actions {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  margin-bottom: var(--space-lg);
}

.sku-tip {
  color: var(--text-tertiary);
  font-size: var(--font-size-sm);
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

.sku-level-price {
  display: block;
  width: 100%;
  color: var(--danger);
  font-family: var(--font-mono);
  text-align: center;
}

.sku-number-field {
  width: 100%;
  display: flex;
  justify-content: center;
}

.sku-number-field :deep(.el-input-number) {
  width: 100%;
  max-width: 104px;
}

.sku-number-field :deep(.el-input__wrapper) {
  width: 100%;
  box-sizing: border-box;
  padding-right: 6px;
  padding-left: 6px;
}

.sku-number-field :deep(.el-input__inner) {
  width: 100%;
  text-align: center;
}

.sku-image-placeholder {
  width: 50px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--border-color);
  border-radius: var(--radius-md);
  color: var(--text-tertiary);
}

@media (max-width: 820px) {
  .prototype-banner,
  .page-header,
  .header-actions {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .prototype-banner code {
    width: 100%;
    margin-left: 0;
  }

  .search-select,
  .search-input,
  .price-view-select {
    width: 100%;
  }

  .spec-select-row {
    grid-template-columns: 1fr;
  }

  .sku-toolbar-spacer {
    display: none;
  }
}
</style>
