import type { Category, Product, CartItem, Address, Order } from '@/types'

export const MOCK_ENABLED = false

export const MOCK_CATEGORIES: Category[] = [
  { id: 1, name: '锁具', icon: '🔐', sortOrder: 1, status: 1 },
  { id: 2, name: '胶类', icon: '🧴', sortOrder: 2, status: 1 },
  { id: 3, name: '工具', icon: '🛠', sortOrder: 3, status: 1 },
  { id: 4, name: '灯具', icon: '💡', sortOrder: 4, status: 1 },
  { id: 5, name: '水管', icon: '🚿', sortOrder: 5, status: 1 },
  { id: 6, name: '开关', icon: '🔌', sortOrder: 6, status: 1 },
  { id: 7, name: '螺丝', icon: '🔩', sortOrder: 7, status: 1 },
  { id: 8, name: '其他', icon: '📦', sortOrder: 8, status: 1 }
]

export const MOCK_PRODUCTS: Product[] = [
  { id: 1, categoryId: 1, name: '防盗门锁 指纹密码锁', subtitle: 'C级锁芯 半导体指纹', price: 399, originalPrice: 599, stock: 100, salesCount: 2580, images: [], status: 1, isRecommend: 1 },
  { id: 2, categoryId: 1, name: '卧室门锁 欧式把手', subtitle: '静音设计 多色可选', price: 159, originalPrice: 239, stock: 200, salesCount: 1850, images: [], status: 1, isRecommend: 1 },
  { id: 3, categoryId: 2, name: '玻璃胶 万能胶', subtitle: '防水防霉 快干型', price: 29, originalPrice: 45, stock: 500, salesCount: 3200, images: [], status: 1, isRecommend: 1 },
  { id: 4, categoryId: 2, name: '结构胶 粘钢胶', subtitle: '高强度 耐候性强', price: 68, originalPrice: 99, stock: 300, salesCount: 980, images: [], status: 1, isRecommend: 0 },
  { id: 5, categoryId: 3, name: '工具箱 家用套装', subtitle: '28件套 钢制工具', price: 199, originalPrice: 299, stock: 150, salesCount: 1200, images: [], status: 1, isRecommend: 1 },
  { id: 6, categoryId: 3, name: '电钻 充电式电动工具', subtitle: '锂电池 大功率', price: 289, originalPrice: 399, stock: 80, salesCount: 650, images: [], status: 1, isRecommend: 1 },
  { id: 7, categoryId: 4, name: 'LED吸顶灯 超薄圆形', subtitle: '三色变光 遥控控制', price: 128, originalPrice: 199, stock: 200, salesCount: 2100, images: [], status: 1, isRecommend: 1 },
  { id: 8, categoryId: 4, name: '吊灯 创意餐厅灯', subtitle: '现代简约风格', price: 358, originalPrice: 520, stock: 50, salesCount: 420, images: [], status: 1, isRecommend: 0 },
  { id: 9, categoryId: 5, name: 'PPR热水管 4分', subtitle: '纳米抗菌 食品级', price: 15, originalPrice: 22, stock: 1000, salesCount: 5600, images: [], status: 1, isRecommend: 1 },
  { id: 10, categoryId: 5, name: 'PVC下水管 50管', subtitle: '防臭防堵 家装必备', price: 12, originalPrice: 18, stock: 800, salesCount: 4300, images: [], status: 1, isRecommend: 0 },
  { id: 11, categoryId: 6, name: '开关插座 86型面板', subtitle: 'USB充电 免布线', price: 45, originalPrice: 68, stock: 400, salesCount: 1800, images: [], status: 1, isRecommend: 1 },
  { id: 12, categoryId: 6, name: '空开漏电保护器', subtitle: '63A大电流 家用配电', price: 89, originalPrice: 129, stock: 150, salesCount: 720, images: [], status: 1, isRecommend: 0 }
]

export const MOCK_BANNERS = [
  { id: 1, image: 'https://picsum.photos/750/320?random=1', title: '新品上市' },
  { id: 2, image: 'https://picsum.photos/750/320?random=2', title: '限时优惠' },
  { id: 3, image: 'https://picsum.photos/750/320?random=3', title: '品牌专区' }
]

export const MOCK_ADDRESSES: Address[] = [
  { id: 1, userId: 1, consignee: '张三', phone: '13800138000', province: '广东省', city: '深圳市', district: '南山区', detail: '科技园南路88号A栋1001', isDefault: 1 },
  { id: 2, userId: 1, consignee: '李四', phone: '13900139000', province: '广东省', city: '广州市', district: '天河区', detail: '体育西路123号', isDefault: 0 }
]

export const MOCK_CART: CartItem[] = [
  { cartId: 1, productId: 1, productName: '防盗门锁 指纹密码锁', productImage: '/static/images/face.jpg', price: 399, quantity: 1, subtotal: 399, selected: true },
  { cartId: 2, productId: 3, productName: '玻璃胶 万能胶', productImage: '/static/images/face.jpg', price: 29, quantity: 2, subtotal: 58, selected: true }
]

export const MOCK_ORDERS: Order[] = [
  {
    id: 1,
    orderNo: 'HM202604010001',
    status: 1,
    statusText: '待付款',
    totalAmount: 457,
    freightAmount: 0,
    payAmount: 457,
    createTime: '2026-04-01 10:30:00',
    receiverName: '张三',
    receiverPhone: '138****8000',
    receiverAddress: '广东省深圳市南山区科技园南路88号',
    items: [
      { id: 1, orderId: 1, productId: 1, productName: '防盗门锁 指纹密码锁', productImage: '/static/images/face.jpg', price: 399, quantity: 1, subtotal: 399 }
    ]
  },
  {
    id: 2,
    orderNo: 'HM202604010002',
    status: 2,
    statusText: '待发货',
    totalAmount: 128,
    freightAmount: 0,
    payAmount: 128,
    createTime: '2026-03-30 15:20:00',
    payTime: '2026-03-30 15:25:00',
    receiverName: '张三',
    receiverPhone: '138****8000',
    receiverAddress: '广东省深圳市南山区科技园南路88号',
    items: [
      { id: 2, orderId: 2, productId: 7, productName: 'LED吸顶灯 超薄圆形', productImage: '/static/images/face.jpg', price: 128, quantity: 1, subtotal: 128 }
    ]
  }
]

export const MOCK_USER_INFO = {
  id: 1,
  nickname: '五金会员',
  avatarUrl: '',
  phone: '13800138000',
  role: 1
}

export function getMockProductImages() {
  return MOCK_PRODUCTS.map(p => ({ ...p, images: ['/static/images/face.jpg'] }))
}
