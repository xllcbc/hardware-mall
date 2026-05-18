<template>
  <div class="dashboard">
    <div class="page-header animate-fade-in-down">
      <div class="header-content">
        <h2 class="page-title">仪表盘</h2>
        <p class="page-desc">欢迎使用五金商城管理系统</p>
      </div>
      <div class="header-time">
        <span class="time-label">更新时间</span>
        <span class="time-value">{{ currentTime }}</span>
      </div>
    </div>

    <div class="stats-grid">
      <div 
        v-for="(stat, index) in statsData" 
        :key="stat.label"
        class="stat-card animate-fade-in-up"
        :class="[`stat-${stat.type}`, `stagger-${index + 1}`]"
      >
        <div class="stat-icon-wrapper" :style="{ background: stat.iconBg }">
          <el-icon class="stat-icon" :style="{ color: stat.iconColor }">
            <component :is="stat.icon" />
          </el-icon>
        </div>
        <div class="stat-content">
          <p class="stat-label">{{ stat.label }}</p>
          <p class="stat-value" :class="{ 'count-number': stat.animated }">
            {{ stat.value }}
          </p>
        </div>
        <div class="stat-trend" :class="stat.trend > 0 ? 'up' : 'down'">
          <el-icon><ArrowUp v-if="stat.trend > 0" /><ArrowDown v-else /></el-icon>
          <span>{{ Math.abs(stat.trend) }}%</span>
        </div>
      </div>
    </div>

    <div class="dashboard-content">
      <el-row :gutter="24">
        <el-col :span="16">
          <div class="card recent-orders animate-fade-in-up stagger-5">
            <div class="card-header">
              <h3 class="card-title">最新订单</h3>
              <el-button type="primary" link @click="$router.push('/order')">
                查看全部
                <el-icon class="arrow-icon"><Right /></el-icon>
              </el-button>
            </div>
            <el-table :data="recentOrders" stripe class="orders-table">
              <el-table-column prop="orderNo" label="订单号" width="180">
                <template #default="{ row }">
                  <span class="order-no">{{ row.orderNo }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="userId" label="用户ID" width="100" />
              <el-table-column prop="totalAmount" label="金额" width="120">
                <template #default="{ row }">
                  <span class="amount">¥{{ Number(row.totalAmount).toFixed(2) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="statusText" label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="getStatusType(row.status)" size="small" effect="light">
                    {{ row.statusText }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createTime" label="下单时间" />
            </el-table>
          </div>
        </el-col>
        
        <el-col :span="8">
          <div class="card quick-actions animate-fade-in-up stagger-6">
            <div class="card-header">
              <h3 class="card-title">快捷操作</h3>
            </div>
            <div class="action-list">
              <div 
                v-for="(action, index) in quickActions" 
                :key="action.label"
                class="action-item"
                :style="{ animationDelay: `${0.3 + index * 0.1}s` }"
                @click="$router.push(action.path)"
              >
                <div class="action-icon" :style="{ background: action.iconBg }">
                  <el-icon :style="{ color: action.iconColor }">
                    <component :is="action.icon" />
                  </el-icon>
                </div>
                <div class="action-content">
                  <p class="action-label">{{ action.label }}</p>
                  <p class="action-desc">{{ action.desc }}</p>
                </div>
                <el-icon class="action-arrow"><Right /></el-icon>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ArrowUp, ArrowDown, Right } from '@element-plus/icons-vue'
import { getDashboardStats, getRecentOrders } from '@/api/admin/dashboard'

const currentTime = ref('')
let timeInterval: number

const statsData = ref([
  { 
    label: '今日订单', 
    value: '--', 
    icon: 'Document', 
    type: 'copper',
    iconBg: 'linear-gradient(135deg, #C9956C, #DDB892)',
    iconColor: '#fff',
    trend: 0,
    animated: true
  },
  { 
    label: '今日销售额', 
    value: '--', 
    icon: 'Money', 
    type: 'green',
    iconBg: 'linear-gradient(135deg, #5D9E6A, #7BC98F)',
    iconColor: '#fff',
    trend: 0,
    animated: true
  },
  { 
    label: '待发货', 
    value: '--', 
    icon: 'Clock', 
    type: 'amber',
    iconBg: 'linear-gradient(135deg, #D4A84B, #E8C87A)',
    iconColor: '#fff',
    trend: 0,
    animated: true
  },
  { 
    label: '商品总数', 
    value: '--', 
    icon: 'Goods', 
    type: 'steel',
    iconBg: 'linear-gradient(135deg, #5B7C99, #7A9BBA)',
    iconColor: '#fff',
    trend: 0,
    animated: true
  }
])

const recentOrders = ref<any[]>([])

const loadDashboardData = async () => {
  try {
    const [stats, orders] = await Promise.all([
      getDashboardStats(),
      getRecentOrders(5)
    ])
    
    statsData.value = [
      { 
        label: '今日订单', 
        value: String(stats.todayOrders || 0), 
        icon: 'Document', 
        type: 'copper',
        iconBg: 'linear-gradient(135deg, #C9956C, #DDB892)',
        iconColor: '#fff',
        trend: stats.trend?.ordersTrend || 0,
        animated: true
      },
      { 
        label: '今日销售额', 
        value: `¥${(stats.todaySales || 0).toFixed(2)}`, 
        icon: 'Money', 
        type: 'green',
        iconBg: 'linear-gradient(135deg, #5D9E6A, #7BC98F)',
        iconColor: '#fff',
        trend: stats.trend?.salesTrend || 0,
        animated: true
      },
      { 
        label: '待发货', 
        value: String(stats.pendingShip || 0), 
        icon: 'Clock', 
        type: 'amber',
        iconBg: 'linear-gradient(135deg, #D4A84B, #E8C87A)',
        iconColor: '#fff',
        trend: stats.trend?.shipTrend || 0,
        animated: true
      },
      { 
        label: '商品总数', 
        value: String(stats.totalProducts || 0), 
        icon: 'Goods', 
        type: 'steel',
        iconBg: 'linear-gradient(135deg, #5B7C99, #7A9BBA)',
        iconColor: '#fff',
        trend: stats.trend?.productTrend || 0,
        animated: true
      }
    ]
    
    recentOrders.value = orders || []
  } catch (error) {
    console.error('Failed to load dashboard data:', error)
  }
}

const quickActions = [
  { 
    label: '待发货订单', 
    desc: '处理等待发货的订单',
    icon: 'Van',
    path: '/order?status=2',
    iconBg: 'linear-gradient(135deg, #F59E0B, #FBBF24)',
    iconColor: '#fff'
  },
  { 
    label: '商品管理', 
    desc: '添加、编辑、上下架商品',
    icon: 'Goods',
    path: '/spu',
    iconBg: 'linear-gradient(135deg, #10B981, #34D399)',
    iconColor: '#fff'
  },
  { 
    label: '物流配置', 
    desc: '管理物流公司和运费规则',
    icon: 'SetUp',
    path: '/logistics',
    iconBg: 'linear-gradient(135deg, #6366F1, #818CF8)',
    iconColor: '#fff'
  }
]

const getStatusType = (status: number): string => {
  const types: Record<number, string> = {
    1: 'warning',
    2: 'primary',
    3: 'success',
    4: 'info',
    5: 'info',
    6: 'danger',
    7: 'danger'
  }
  return types[status] || 'info'
}

const updateTime = () => {
  const now = new Date()
  currentTime.value = now.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

onMounted(() => {
  updateTime()
  timeInterval = window.setInterval(updateTime, 60000)
  loadDashboardData()
})

onUnmounted(() => {
  clearInterval(timeInterval)
})
</script>

<style scoped>
.dashboard {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--space-xl);
  padding: var(--space-lg);
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-color);
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

.header-time {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.time-label {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
}

.time-value {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  font-weight: 500;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-lg);
  margin-bottom: var(--space-xl);
}

.stat-card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  gap: var(--space-md);
  position: relative;
  overflow: hidden;
  opacity: 0;
  animation: fadeInUp 0.4s ease forwards;
  transition: all var(--transition-base);
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}

.stat-icon-wrapper {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.stat-icon {
  font-size: 28px;
}

.stat-content {
  flex: 1;
  min-width: 0;
}

.stat-label {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  margin: 0 0 4px;
  white-space: nowrap;
}

.stat-value {
  font-size: var(--font-size-2xl);
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
  font-family: var(--font-mono);
}

.stat-trend {
  position: absolute;
  top: var(--space-md);
  right: var(--space-md);
  display: flex;
  align-items: center;
  gap: 2px;
  font-size: var(--font-size-xs);
  font-weight: 600;
  padding: 2px 8px;
  border-radius: var(--radius-full);
}

.stat-trend.up {
  color: var(--success);
  background: var(--success-light);
}

.stat-trend.down {
  color: var(--danger);
  background: var(--danger-light);
}

.dashboard-content {
  margin-top: var(--space-xl);
}

.card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
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

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-lg);
}

.card-title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.arrow-icon {
  margin-left: 4px;
  transition: transform var(--transition-fast);
}

.card-header .el-button:hover .arrow-icon {
  transform: translateX(4px);
}

.orders-table {
  margin: 0 calc(-1 * var(--space-lg));
  padding: 0;
}

.order-no {
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
  color: var(--primary-color);
}

.amount {
  font-weight: 600;
  color: var(--danger);
}

.quick-actions {
  height: 100%;
}

.action-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.action-item {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-base);
  opacity: 0;
  animation: fadeInLeft 0.3s ease forwards;
}

.action-item:hover {
  background: var(--bg-page);
}

.action-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.action-icon .el-icon {
  font-size: 22px;
}

.action-content {
  flex: 1;
  min-width: 0;
}

.action-label {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 2px;
}

.action-desc {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  margin: 0;
}

.action-arrow {
  font-size: 16px;
  color: var(--text-tertiary);
  transition: transform var(--transition-fast);
}

.action-item:hover .action-arrow {
  transform: translateX(4px);
  color: var(--primary-color);
}

@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
  
  .page-header {
    flex-direction: column;
    gap: var(--space-md);
  }
  
  .header-time {
    align-items: flex-start;
  }
}
</style>
