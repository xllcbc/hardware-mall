import { expect, test, type Page, type Route } from '@playwright/test'

const ADMIN_ORIGIN = 'http://127.0.0.1:4173'

interface AdminApiState {
  pricingRequests: URL[]
}

async function fulfill(route: Route, data: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, message: 'ok', data })
  })
}

async function installAdminApi(page: Page): Promise<AdminApiState> {
  const state: AdminApiState = { pricingRequests: [] }
  const levels = [
    { id: 1, name: '零售', isRetail: 1, sortOrder: 100, status: 1, userCount: 8 },
    { id: 2, name: '批发A', isRetail: 0, sortOrder: 10, status: 1, userCount: 2 }
  ]
  const categories = [
    { id: 1, name: '锁具', parentId: 0, status: 1, sortOrder: 1 },
    { id: 2, name: '工具', parentId: 0, status: 1, sortOrder: 2 }
  ]

  await page.route('**/api/**', async route => {
    const request = route.request()
    const url = new URL(request.url())

    if (url.pathname === '/api/admin/user-level/list') {
      await fulfill(route, levels)
      return
    }
    if (url.pathname === '/api/admin/category/list') {
      await fulfill(route, { records: categories, total: categories.length })
      return
    }
    if (url.pathname === '/api/admin/spu/pricing-list') {
      state.pricingRequests.push(url)
      const levelId = url.searchParams.get('levelId')
      const discounted = levelId === '2'
      await fulfill(route, {
        records: [{
          id: 1,
          categoryId: 1,
          name: '不锈钢门锁',
          subtitle: 'E2E product',
          retailMinPrice: 50,
          retailMaxPrice: 50,
          discountRate: discounted ? 0.8 : 1,
          effectiveRate: discounted ? 0.8 : 1,
          settlementMinPrice: discounted ? 40 : 50,
          settlementMaxPrice: discounted ? 40 : 50,
          rateMissing: false,
          excludeDiscount: 0,
          status: 1,
          isRecommend: 0,
          salesCount: 3
        }],
        total: 1
      })
      return
    }
    if (url.pathname === '/api/admin/sku/counts') {
      await fulfill(route, { 1: 2 })
      return
    }
    if (url.pathname === '/api/admin/user-level/2/discounts') {
      await fulfill(route, [
        { categoryId: 1, categoryName: '锁具', rate: 0.8, missing: false },
        { categoryId: 2, categoryName: '工具', missing: true }
      ])
      return
    }
    if (url.pathname === '/api/admin/user/list') {
      const levelId = url.searchParams.get('levelId')
      const records = levelId === '2'
        ? [{
            id: 2,
            nickname: '批发用户',
            role: 0,
            status: 1,
            levelId: 2,
            levelName: '批发A',
            phone: '13800000000',
            createTime: '2026-11-05 10:00:00'
          }]
        : []
      await fulfill(route, { records, total: records.length })
      return
    }

    await fulfill(route, null)
  })

  return state
}

test.beforeEach(async ({ context }) => {
  await context.addInitScript(() => {
    localStorage.setItem('token', 'e2e-admin-token')
    localStorage.setItem('userInfo', JSON.stringify({
      id: 1,
      username: 'release-admin',
      role: 1
    }))
  })
})

test('admin pricing view, matrix, deep link and 1280px layout', async ({ page }) => {
  const state = await installAdminApi(page)

  await page.goto(`${ADMIN_ORIGIN}/spu?levelId=2`)
  await expect(page.getByRole('heading', { name: '商品管理' })).toBeVisible()
  await expect(page.locator('.price-view-summary')).toContainText('批发A')
  await expect(page.getByText('不锈钢门锁', { exact: true })).toBeVisible()
  await expect(page.getByText('¥40.00', { exact: true })).toBeVisible()
  await expect(page.getByText('¥50.00', { exact: true })).toBeVisible()
  await expect(page.getByText('0.8000', { exact: true })).toBeVisible()
  expect(state.pricingRequests.at(-1)?.searchParams.get('levelId')).toBe('2')

  await page.locator('.price-view-select').click()
  await page.getByRole('option', { name: '零售基准' }).click()
  await expect(page).toHaveURL(/\/spu$/)
  await expect(page.locator('.price-view-summary')).toContainText('零售基准')
  expect(state.pricingRequests.at(-1)?.searchParams.has('levelId')).toBe(false)

  await page.setViewportSize({ width: 1280, height: 800 })
  const overflowsViewport = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1
  )
  expect(overflowsViewport).toBe(false)
  await page.locator('.el-switch').click()
  await expect(page.getByText('完整字段', { exact: true })).toBeVisible()

  await page.goto(`${ADMIN_ORIGIN}/user-level`)
  await expect(page.getByRole('heading', { name: '用户群体管理' })).toBeVisible()
  const levelRow = page.locator('.el-table__row').filter({ hasText: '批发A' })
  await expect(levelRow).toBeVisible()
  await levelRow.getByRole('button', { name: '倍率矩阵' }).click()
  const matrixDialog = page.getByRole('dialog', { name: /倍率矩阵 - 批发A/ })
  await expect(matrixDialog).toBeVisible()
  await expect(matrixDialog.getByText('锁具', { exact: true })).toBeVisible()
  await expect(matrixDialog.locator('input').first()).toHaveValue('0.8000')
  await expect(matrixDialog.getByRole('button', { name: '一键填充' })).toBeVisible()
  await expect(matrixDialog.getByRole('button', { name: '复制等级' })).toBeVisible()
  await expect(matrixDialog.getByRole('button', { name: '保存矩阵' })).toBeVisible()
  await matrixDialog.getByRole('button', { name: '取消' }).click()

  await levelRow.getByRole('button', { name: '查看批发A归属用户' }).click()
  await expect(page).toHaveURL(/\/user\?levelId=2$/)
  await expect(page.getByText('批发用户', { exact: true })).toBeVisible()
  await expect(
    page.locator('.user-table tbody tr')
      .filter({ hasText: '批发用户' })
      .getByText('批发A', { exact: true })
  ).toBeVisible()
})
