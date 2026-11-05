import { expect, test, type Page, type Route } from '@playwright/test'

const H5_ORIGIN = 'http://127.0.0.1:4174'

interface H5ApiState {
  createCalls: number
  prepayCalls: number
  previewAmount: number
  priceChangeOnce: boolean
  createdPayAmount: number
}

const preview = (amount: number) => ({
  items: [{
    skuId: 9301,
    productId: 9201,
    productName: 'Price guard product',
    spec: '',
    price: amount,
    quantity: 1,
    subtotal: amount
  }],
  goodsAmount: amount,
  freightAmount: 0,
  payAmount: amount
})

const order = (payAmount: number) => ({
  id: 100,
  orderNo: 'SO-E2E-100',
  status: 1,
  statusText: 'Pending payment',
  totalAmount: payAmount,
  freightAmount: 0,
  payAmount,
  createTime: '2026-11-05 12:00:00',
  items: []
})

async function fulfill(route: Route, data: unknown, code = 200, message = 'ok') {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code, message, data })
  })
}

async function installH5Api(page: Page): Promise<H5ApiState> {
  const state: H5ApiState = {
    createCalls: 0,
    prepayCalls: 0,
    previewAmount: 35,
    priceChangeOnce: true,
    createdPayAmount: 30
  }

  await page.route('**/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname

    if (path === '/api/user/product/9201') {
      await fulfill(route, {
        spu: {
          id: 9201,
          categoryId: 1,
          name: 'Price guard product',
          subtitle: 'H5 E2E',
          images: [],
          originalPrice: 35,
          status: 1,
          isRecommend: 0
        },
        skus: [{
          id: 9301,
          spuId: 9201,
          specs: [],
          price: 35,
          stock: 10,
          status: 1
        }],
        specTemplates: [],
        specItemsMap: {},
        minPrice: 35,
        maxPrice: 35
      })
      return
    }
    if (path === '/api/user/address/list') {
      await fulfill(route, [{
        id: 1,
        userId: 2,
        consignee: 'E2E user',
        phone: '13800000000',
        province: 'Guangdong',
        city: 'Shenzhen',
        district: 'Nanshan',
        detail: 'Release road 1',
        isDefault: 1
      }])
      return
    }
    if (path === '/api/user/logistics/list') {
      await fulfill(route, [{
        id: 1,
        name: 'Standard delivery',
        code: 'standard',
        sortOrder: 1,
        status: 1
      }])
      return
    }
    if (path === '/api/user/order/preview') {
      await fulfill(route, preview(state.previewAmount))
      return
    }
    if (path === '/api/user/order/token') {
      await fulfill(route, `idem-${state.createCalls + 1}`)
      return
    }
    if (path === '/api/user/order/create') {
      state.createCalls += 1
      if (state.priceChangeOnce && state.createCalls === 1) {
        state.previewAmount = 30
        await fulfill(route, {
          expectedPayAmount: 35,
          preview: preview(30)
        }, 40901, 'Price changed')
        return
      }
      await fulfill(route, order(state.createdPayAmount))
      return
    }
    if (path === '/api/user/pay/prepay') {
      state.prepayCalls += 1
      await fulfill(route, {
        timeStamp: '1',
        nonceStr: 'nonce',
        packageValue: 'prepay_id=e2e',
        paySign: 'signature'
      })
      return
    }
    if (path === '/api/user/order/100') {
      await fulfill(route, order(state.createdPayAmount))
      return
    }

    await fulfill(route, null)
  })

  return state
}

test.beforeEach(async ({ context }) => {
  await context.addInitScript(() => {
    localStorage.setItem('token', 'e2e-user-token')
    localStorage.setItem('userInfo', JSON.stringify({
      id: 2,
      nickname: 'E2E user',
      role: 0,
      levelId: 2
    }))
    localStorage.setItem('PRIVACY_AGREED', 'true')
  })
})

async function openCheckout(page: Page) {
  await page.goto(`${H5_ORIGIN}/#/pages/product/detail?id=9201`)
  await expect(page.getByText('Price guard product', { exact: true })).toBeVisible()
  await expect(page.getByText('35.00', { exact: true })).toBeVisible()
  await page.getByText('立即购买', { exact: true }).click()
  await expect(page).toHaveURL(/pages\/checkout\/index/)
  await expect(page.locator('.pay-price')).toContainText('35.00')
}

test('H5 checkout keeps data and creates no order when price change is cancelled', async ({ page }) => {
  const state = await installH5Api(page)
  await openCheckout(page)

  await page.locator('.pay-btn').click()
  await expect(page.getByText('商品价格已更新', { exact: true })).toBeVisible()
  await page.getByText('暂不', { exact: true }).click()
  await expect(page.locator('.pay-price')).toContainText('30.00')
  expect(state.createCalls).toBe(1)
  expect(state.prepayCalls).toBe(0)
})

test('H5 checkout confirms a server price change and pays the new amount', async ({ page }) => {
  const state = await installH5Api(page)
  await openCheckout(page)

  await page.locator('.pay-btn').click()
  await expect(page.getByText('商品价格已更新', { exact: true })).toBeVisible()
  await page.getByText('确认下单', { exact: true }).click()
  await expect.poll(() => state.prepayCalls).toBe(1)
  expect(state.createCalls).toBe(2)
})

test('H5 checkout blocks prepay when the created order amount differs', async ({ page }) => {
  const state = await installH5Api(page)
  state.priceChangeOnce = false
  state.previewAmount = 30
  state.createdPayAmount = 40

  await page.goto(`${H5_ORIGIN}/#/pages/product/detail?id=9201`)
  await page.getByText('立即购买', { exact: true }).click()
  await expect(page).toHaveURL(/pages\/checkout\/index/)
  await expect(page.locator('.pay-price')).toContainText('30.00')

  await page.locator('.pay-btn').click()
  await expect(page.getByText('订单金额异常', { exact: true })).toBeVisible()
  await expect(page.getByText(/已停止支付/)).toBeVisible()
  expect(state.prepayCalls).toBe(0)
})
