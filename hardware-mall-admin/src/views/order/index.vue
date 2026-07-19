<template>
  <div class="order-management">
    <div class="page-header animate-fade-in-down">
      <div class="header-content">
        <h2 class="page-title">订单管理</h2>
        <p class="page-desc">查看和处理用户订单</p>
      </div>
    </div>

    <div v-if="filterUserId" class="card filter-tip animate-fade-in">
      <el-icon><User /></el-icon>
      <span>正在显示用户 {{ filterUserId }} 的订单</span>
      <el-button link type="primary" @click="clearUserFilter">清除筛选</el-button>
    </div>

    <div class="card search-card animate-fade-in-up stagger-1">
      <el-form :inline="true" :model="queryForm" class="search-form">
        <el-form-item label="订单状态">
          <el-select 
            v-model="queryForm.status" 
            placeholder="全部" 
            clearable 
            class="search-select"
          >
            <el-option label="全部" :value="null" />
            <el-option label="待付款" :value="ORDER_STATUS.PENDING_PAYMENT" />
            <el-option label="待发货" :value="ORDER_STATUS.PENDING_SHIPMENT" />
            <el-option label="已发货" :value="ORDER_STATUS.SHIPPED" />
            <el-option label="已完成" :value="ORDER_STATUS.COMPLETED" />
            <el-option label="已取消" :value="ORDER_STATUS.CANCELLED" />
            <el-option label="已退款" :value="ORDER_STATUS.REFUNDED" />
            <el-option label="退款中" :value="ORDER_STATUS.REFUNDING" />
          </el-select>
        </el-form-item>
        <el-form-item label="订单号">
          <el-input 
            v-model="queryForm.orderNo" 
            placeholder="请输入订单号" 
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
        class="order-table"
      >
        <el-table-column prop="orderNo" label="订单号" width="180">
          <template #default="{ row }">
            <span class="order-no">{{ row.orderNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="userId" label="用户ID" width="80" />
        <el-table-column label="收货人" width="120">
          <template #default="{ row }">
            <div class="receiver-cell">
              <span class="receiver-name">{{ row.receiverName }}</span>
              <span class="receiver-phone">{{ row.receiverPhone }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="receiverAddress" label="收货地址" min-width="200">
          <template #default="{ row }">
            <span class="address">{{ row.receiverAddress }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="金额" width="120">
          <template #default="{ row }">
            <span class="amount">¥{{ Number(row.totalAmount).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="statusText" label="状态" width="100">
          <template #default="{ row }">
            <el-tag 
              :type="getStatusType(row.status)" 
              size="small"
              effect="light"
            >
              {{ row.statusText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="下单时间" width="160">
          <template #default="{ row }">
            <span class="time">{{ row.createTime }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看</el-button>
            <el-button 
              link 
              type="success" 
              v-if="row.status === 2" 
              @click="handleShip(row)"
            >
              发货
            </el-button>
            <el-button 
              link 
              type="danger" 
              v-if="row.status === 2 || row.status === 3" 
              @click="handleRefund(row)"
            >
              退款
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

    <el-dialog 
      v-model="detailVisible" 
      title="订单详情" 
      width="700px"
      class="order-dialog"
    >
      <el-descriptions :column="2" border v-if="currentOrder" class="order-descriptions">
        <el-descriptions-item label="订单号">
          <span class="order-no">{{ currentOrder.orderNo }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="订单状态">
          <el-tag :type="getStatusType(currentOrder.status)" size="small" effect="light">
            {{ currentOrder.statusText }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="收货人">{{ currentOrder.receiverName }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ currentOrder.receiverPhone }}</el-descriptions-item>
        <el-descriptions-item label="收货地址" :span="2">{{ currentOrder.receiverAddress }}</el-descriptions-item>
        <el-descriptions-item label="物流方式">{{ currentOrder.logisticsName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="物流单号">{{ currentOrder.logisticsNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="订单金额">
          <span class="amount">¥{{ Number(currentOrder.totalAmount).toFixed(2) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ currentOrder.createTime }}</el-descriptions-item>
      </el-descriptions>
      
      <el-divider>商品明细</el-divider>
      <el-table :data="currentOrder?.items" size="small" class="items-table">
        <el-table-column prop="productName" label="商品名称" />
        <el-table-column prop="productSpec" label="规格" width="120" />
        <el-table-column prop="price" label="单价" width="100">
          <template #default="{ row }">
            <span class="price">¥{{ Number(row.price).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="80" />
        <el-table-column prop="subtotal" label="小计" width="100">
          <template #default="{ row }">
            <span class="subtotal">¥{{ Number(row.subtotal).toFixed(2) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog 
      v-model="shipVisible" 
      title="发货" 
      width="400px"
      class="ship-dialog"
    >
      <el-form ref="shipFormRef" :model="shipForm" :rules="shipRules" label-width="80px">
        <el-form-item label="物流公司">
          <el-select v-model="shipForm.logisticsId" placeholder="请选择物流" class="form-select">
            <el-option 
              v-for="item in logisticsList" 
              :key="item.id" 
              :label="item.name" 
              :value="item.id" 
            />
          </el-select>
        </el-form-item>
        <el-form-item label="物流单号" prop="logisticsNo">
          <el-input v-model="shipForm.logisticsNo" placeholder="请输入物流单号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmShip">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getOrderList, shipOrder, refundOrder } from '@/api/admin/order'
import { getLogisticsList } from '@/api/admin/logistics'
import { ORDER_STATUS, ORDER_STATUS_TEXT, ORDER_STATUS_TYPE } from '@/constants/status'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const tableData = ref<any[]>([])
const detailVisible = ref(false)
const shipVisible = ref(false)
const currentOrder = ref<any>(null)
const logisticsList = ref<any[]>([])
const filterUserId = ref<number | null>(null)
const shipFormRef = ref()

const queryForm = reactive({
  status: null as number | null,
  orderNo: ''
})

const pagination = reactive({
  page: 1,
  limit: 20,
  total: 0
})

const shipForm = reactive({
  logisticsId: null as number | null,
  logisticsNo: ''
})

const shipRules = {
  logisticsId: [{ required: true, message: '请选择物流', trigger: 'change' }],
  logisticsNo: [{ required: true, message: '请输入物流单号', trigger: 'blur' }]
}

const getStatusType = (status: number | null | undefined): string => {
  if (status == null) return 'info'
  return ORDER_STATUS_TYPE[status] || 'info'
}

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await getOrderList({
      page: pagination.page,
      limit: pagination.limit,
      status: queryForm.status || undefined,
      orderNo: queryForm.orderNo || undefined,
      userId: filterUserId.value || undefined
    })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

const loadLogisticsList = async () => {
  try {
    const res: any = await getLogisticsList({ status: 1 })
    logisticsList.value = res.records || []
  } catch {
    // error handled by interceptor
  }
}

const clearUserFilter = () => {
  filterUserId.value = null
  router.replace({ path: '/order' })
  loadData()
}

watch(() => route.query.userId, (newUserId) => {
  if (newUserId) {
    filterUserId.value = Number(newUserId)
    loadData()
  }
}, { immediate: true })

const handleSearch = () => {
  pagination.page = 1
  loadData()
}

const handleReset = () => {
  queryForm.status = null
  queryForm.orderNo = ''
  handleSearch()
}

const handleView = (row: any) => {
  currentOrder.value = row
  detailVisible.value = true
}

const handleShip = async (row: any) => {
  currentOrder.value = row
  shipForm.logisticsId = null
  shipForm.logisticsNo = ''
  await loadLogisticsList()
  shipVisible.value = true
}

const confirmShip = async () => {
  if (!shipFormRef.value) return
  await shipFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        await shipOrder(currentOrder.value.id, shipForm.logisticsId!, shipForm.logisticsNo)
        ElMessage.success('发货成功')
        shipVisible.value = false
        loadData()
      } catch {
        // error handled by interceptor
      }
    }
  })
}

const handleRefund = async (row: any) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入退款原因', '退款', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '请填写退款原因(必填)',
      inputValidator: (v: string) => (!!v && v.trim().length > 0) || '请输入退款原因',
      type: 'warning'
    })
    await refundOrder(row.id, value)
    ElMessage.success('退款处理成功')
    loadData()
  } catch {
    // error handled by interceptor
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.order-management {
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

.search-card {
  padding: var(--space-lg);
  margin-bottom: var(--space-lg);
}

.filter-tip {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-md) var(--space-lg);
  margin-bottom: var(--space-lg);
  background: var(--primary-bg);
  border: 1px solid var(--border-accent);
  border-radius: var(--radius-md);
  color: var(--primary-color);
  font-size: var(--font-size-sm);
}

.filter-tip .el-icon {
  font-size: 16px;
}

.filter-tip span {
  flex: 1;
}

.search-select {
  width: 140px;
}

.search-input {
  width: 200px;
}

.table-card {
  padding: var(--space-lg);
}

.order-table {
  margin: 0 calc(-1 * var(--space-lg));
}

.order-no {
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
  color: var(--primary-color);
}

.receiver-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.receiver-name {
  font-weight: 500;
  color: var(--text-primary);
}

.receiver-phone {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
}

.address {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.amount {
  font-weight: 600;
  color: var(--danger);
  font-family: var(--font-mono);
}

.time {
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
}

.pagination-wrapper {
  margin-top: var(--space-lg);
  display: flex;
  justify-content: flex-end;
}

.form-select {
  width: 100%;
}

.order-descriptions {
  margin-bottom: var(--space-md);
}

.items-table {
  margin-top: var(--space-md);
}

.price,
.subtotal {
  font-family: var(--font-mono);
}

.subtotal {
  font-weight: 600;
  color: var(--danger);
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
