export interface DraftCategory {
  id: number
  name: string
  code: string
}

export interface DraftLevel {
  id: number
  name: string
  shortName: string
  isRetail: boolean
  sortOrder: number
  status: number
  userCount: number
  description: string
  owner: string
  updatedAt: string
  rates: Record<number, number | null>
}

export const draftCategories: DraftCategory[] = [
  { id: 1, name: '锁具', code: 'LOCK' },
  { id: 2, name: '拉手', code: 'HANDLE' },
  { id: 3, name: '合页', code: 'HINGE' },
  { id: 4, name: '门吸闭门器', code: 'CLOSER' },
  { id: 5, name: '紧固件', code: 'FASTENER' },
  { id: 6, name: '工具', code: 'TOOL' },
  { id: 7, name: '工程五金', code: 'PROJECT' }
]

export const draftLevels: DraftLevel[] = [
  {
    id: 1,
    name: '零售用户',
    shortName: '零售',
    isRetail: true,
    sortOrder: 10,
    status: 1,
    userCount: 8426,
    description: '默认注册用户，按商品零售价结算',
    owner: '系统默认',
    updatedAt: '2026-10-20 09:12',
    rates: {
      1: 1,
      2: 1,
      3: 1,
      4: 1,
      5: 1,
      6: 1,
      7: 1
    }
  },
  {
    id: 2,
    name: '批发商',
    shortName: '批发',
    isRetail: false,
    sortOrder: 20,
    status: 1,
    userCount: 1284,
    description: '常规批发客户，按分类倍率取价',
    owner: '运营组',
    updatedAt: '2026-10-26 16:40',
    rates: {
      1: 0.78,
      2: 0.82,
      3: 0.8,
      4: 0.84,
      5: 0.72,
      6: 0.76,
      7: 0.81
    }
  },
  {
    id: 3,
    name: '工程客户',
    shortName: '工程',
    isRetail: false,
    sortOrder: 30,
    status: 1,
    userCount: 356,
    description: '项目型客户，重点五金件采用项目价',
    owner: '大客户组',
    updatedAt: '2026-10-25 11:08',
    rates: {
      1: 0.7,
      2: 0.75,
      3: 0.72,
      4: null,
      5: 0.68,
      6: 0.7,
      7: 0.74
    }
  },
  {
    id: 4,
    name: '长期合作',
    shortName: '合作',
    isRetail: false,
    sortOrder: 40,
    status: 0,
    userCount: 92,
    description: '年度协议客户，配置完成后启用',
    owner: '大客户组',
    updatedAt: '2026-10-24 14:22',
    rates: {
      1: 0.64,
      2: 0.68,
      3: 0.66,
      4: 0.69,
      5: 0.62,
      6: null,
      7: null
    }
  },
  {
    id: 5,
    name: '临时采购',
    shortName: '临时',
    isRetail: false,
    sortOrder: 50,
    status: 0,
    userCount: 0,
    description: '预留分级，当前未分配任何用户',
    owner: '未分配',
    updatedAt: '2026-10-18 10:05',
    rates: {
      1: 0.9,
      2: 0.9,
      3: 0.9,
      4: 0.9,
      5: 0.9,
      6: 0.9,
      7: 0.9
    }
  }
]

export const formatNumber = (value: number) => new Intl.NumberFormat('zh-CN').format(value)

export const formatRate = (value: number | null | undefined) =>
  value == null ? '未配置' : value.toFixed(4)

export const missingCategories = (level: DraftLevel) =>
  draftCategories.filter(category => level.rates[category.id] == null)

export const coverageRate = (level: DraftLevel) =>
  Math.round(
    ((draftCategories.length - missingCategories(level).length) / draftCategories.length) * 100
  )

export const totalUsers = (levels: DraftLevel[]) =>
  levels.reduce((total, level) => total + level.userCount, 0)

export const totalMissing = (levels: DraftLevel[]) =>
  levels.reduce((total, level) => total + missingCategories(level).length, 0)
