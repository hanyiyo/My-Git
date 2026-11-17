import { test, expect, type Page } from '@playwright/test'

const password = 'fictional-browser-password'
let accessToken = ''
test.beforeEach(() => { accessToken = '' })
async function csrf(page: Page) {
  const response = await page.request.get('/api/v1/auth/csrf')
  return (await response.json()).token as string
}
async function post(page: Page, path: string, body: unknown) {
  const token = await csrf(page)
  return page.request.post('/api/v1' + path, { data: body, headers: { 'X-XSRF-TOKEN': token, ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}) } })
}
async function put(page: Page, path: string, body: unknown) {
  const token = await csrf(page)
  return page.request.put('/api/v1' + path, { data: body, headers: { 'X-XSRF-TOKEN': token, ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}) } })
}
async function login(page: Page, identifier: string, pass: string) {
  const challenge = await page.request.get('/api/v1/auth/captcha').then(response => response.json())
  const result = await post(page, '/auth/login', { identifier, password: pass, challengeId: challenge.challengeId, captcha: 'AB7K' })
  expect(result.status()).toBe(200)
  accessToken = (await result.json()).accessToken as string
}
async function register(page: Page, identifier: string, role: string) {
  const result = await post(page, '/auth/register', { identifier, password, role,
    ...(role === 'PATIENT' ? { displayName: '测试患者', phoneNumber: '13800138000', challengeId: (await page.request.get('/api/v1/auth/captcha').then(response => response.json())).challengeId, captcha: 'AB7K' } : {}) })
  expect(result.status()).toBe(201)
  return (await result.json()).account.id as string
}

test('患者订单页展示待支付订单并完成模拟支付与取消退款', async ({ page }) => {
  test.setTimeout(180000)
  const admin = 'admin-foundation-test'
  await login(page, admin, 'fictional-test-administrator-password')
  const department = await post(page, '/admin/catalog/departments', { name: `订单科室-${crypto.randomUUID().slice(0, 8)}`, description: '订单演示', active: true }).then(response => response.json())
  const doctorIdentifier = `order-doctor-${crypto.randomUUID()}`
  const doctorId = await register(page, doctorIdentifier, 'DOCTOR')
  await put(page, `/admin/catalog/doctors/${doctorId}`, { displayName: '订单演示医生', title: '主治医师', biography: '虚构资料', departmentId: department.id, credentialStatus: 'CERTIFIED' }).then(response => expect(response.status()).toBe(200))
  const date = new Date(Date.now() + 2 * 86400000).toISOString().slice(0, 10)
  const schedule = await post(page, '/admin/catalog/schedules', { doctorId, departmentId: department.id, clinicDate: date, session: 'AM', serviceMode: 'ONLINE', capacity: 1, feeMinor: 2000 }).then(async response => { expect(response.status()).toBe(201); return response.json() })
  const patientIdentifier = `order-patient-${crypto.randomUUID()}`
  await register(page, patientIdentifier, 'PATIENT')
  await login(page, patientIdentifier, password)
  await page.goto('/patient/catalog')
  await page.getByLabel('选择日期').fill(date)
  await page.getByRole('button', { name: '线上问诊', exact: true }).click()
  const resource = page.getByRole('listitem').filter({ hasText: '订单演示医生' })
  await expect(resource).toContainText('线上')
  await resource.getByRole('button', { name: '预约挂号', exact: true }).click()
  const confirmation = page.getByRole('dialog')
  await confirmation.getByRole('button', { name: '确认挂号', exact: true }).click()
  await expect(confirmation.getByRole('heading', { name: '已提交，待付款' })).toBeVisible()
  await confirmation.getByRole('button', { name: '去我的挂号' }).click()
  const appointment = (await page.getByRole('listitem').first().getAttribute('data-appointment-id')) ?? ''
  expect(appointment).not.toBe('')
  await expect(page.getByRole('heading', { name: '我的预约订单' })).toBeVisible()
  const order = page.getByRole('listitem').filter({ hasText: appointment })
  await expect(order).toContainText('待支付')
  await expect(order).toContainText('订单演示医生')
  await expect(order).toContainText(`${date} 上午`)
  await expect(order).toContainText('线上')
  await expect(order).toContainText('¥20.00')
  await expect(order).toContainText('支付截止')
  await order.getByRole('button', { name: '模拟支付' }).click()
  await expect(page.getByText('暂无待付款挂号。')).toBeVisible()
  await page.getByRole('link', { name: '查看全部挂号', exact: true }).click()
  await expect(order).toContainText('已确认')
  await expect(order).toContainText('支付事实：已支付 ¥20.00（模拟支付）')
  await expect(order).toContainText('支付时间：')
  await expect(order).toContainText('预约凭证')
  await order.getByRole('button', { name: '取消预约' }).click()
  await expect(order).toContainText('已取消')
  await expect(order).toContainText('取消事实：患者取消（')
  await expect(order).toContainText('退款处理中')
  await expect(order).toContainText('退款原因：患者取消')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
})

test('门诊结束后过号订单禁止恢复排队并说明不退款', async ({ page }) => {
  const patientIdentifier = `ended-patient-${crypto.randomUUID()}`
  await register(page, patientIdentifier, 'PATIENT')
  await login(page, patientIdentifier, password)
  const now = Date.now()
  await page.route('**/api/v1/patient/appointments', route => route.fulfill({ json: [{
    id: 'ended-appointment', doctorName: '虚构医生', departmentName: '演示科室', clinicDate: '2026-11-07',
    session: 'AM', serviceMode: 'OFFLINE', feeMinor: 2000, holdExpiresAt: now - 10000,
    startsAt: now - 10000, endsAt: now - 1, serverNow: now, state: 'CONFIRMED', credential: 'EXAMPLE',
    payment: null, refund: null, reception: { state: 'MISSED', queueGroup: 1, calledAt: now - 4000, checkedInAt: now - 5000, restorationCount: 0 }
  }] }))
  await page.clock.setFixedTime(new Date(now - 86400000))
  await page.goto('/patient/orders')
  await expect(page.getByRole('button', { name: '恢复排队', exact: true })).toBeDisabled()
  await expect(page.getByText('本次门诊时段已经结束，无法恢复排队；此情况不退款。')).toBeVisible()
})

test('签到按钮按服务器窗口开放，不受患者设备时间影响', async ({ page }) => {
  const patientIdentifier = `window-patient-${crypto.randomUUID()}`
  await register(page, patientIdentifier, 'PATIENT')
  await login(page, patientIdentifier, password)
  const now = Date.now()
  await page.route('**/api/v1/patient/appointments', route => route.fulfill({ json:
    [0, 86400000].map((offset, index) => ({
      id: `window-${index}`, doctorName: '虚构医生', departmentName: '演示科室', clinicDate: '2026-11-07',
      session: 'AM', serviceMode: 'OFFLINE', feeMinor: 2000, holdExpiresAt: now - 1,
      startsAt: now + offset, endsAt: now + offset + 14400000, serverNow: now, state: 'CONFIRMED',
      credential: 'EXAMPLE', payment: null, refund: null, reception: null
    }))
  }))
  await page.clock.setFixedTime(new Date(now + 172800000))
  await page.goto('/patient/orders')
  await expect(page.locator('[data-appointment-id="window-0"]').getByRole('button', { name: '签到', exact: true })).toBeEnabled()
  await expect(page.locator('[data-appointment-id="window-1"]').getByRole('button', { name: '签到', exact: true })).toBeDisabled()
})

test('通知刷新不打断取消操作，旧刷新响应不能覆盖取消结果', async ({ page }) => {
  const identifier = `refresh-patient-${crypto.randomUUID()}`
  await register(page, identifier, 'PATIENT')
  await login(page, identifier, password)
  const now = Date.now()
  const order = {
    id: 'refresh-order', doctorName: '虚构医生', departmentName: '演示科室', clinicDate: '2026-11-13',
    session: 'AM', serviceMode: 'ONLINE', feeMinor: 1000, holdExpiresAt: now + 600000,
    startsAt: now + 86400000, endsAt: now + 100000000, serverNow: now, state: 'CONFIRMED',
    credential: 'EXAMPLE', payment: null, refund: null, reception: null
  }
  let deferNext = false, release!: () => void, started!: () => void
  const pending = new Promise<void>(resolve => { release = resolve })
  const refreshing = new Promise<void>(resolve => { started = resolve })
  await page.route('**/api/v1/patient/appointments', async route => {
    const snapshot = { ...order }
    if (deferNext) { deferNext = false; started(); await pending }
    await route.fulfill({ json: [snapshot] })
  })
  await page.route('**/api/v1/patient/appointments/refresh-order/cancel', async route => {
    order.state = 'CANCELLED'
    await route.fulfill({ json: order })
  })
  await page.goto('/patient/orders')
  const cancel = page.getByRole('button', { name: '取消预约', exact: true })
  await expect(cancel).toBeVisible()
  deferNext = true
  await page.evaluate(() => window.dispatchEvent(new CustomEvent('medical-notification', { detail: { objectId: 'refresh-order' } })))
  await refreshing
  await expect(cancel).toBeEnabled()
  await cancel.click()
  await expect(page.getByText('状态：已取消')).toBeVisible()
  const staleResponse = page.waitForResponse(response => response.url().endsWith('/patient/appointments'))
  release()
  await (await staleResponse).finished()
  await page.evaluate(() => new Promise(requestAnimationFrame))
  await expect(page.getByText('状态：已取消')).toBeVisible()
})
