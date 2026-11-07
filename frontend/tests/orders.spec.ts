import { test, expect, type Page } from '@playwright/test'

const password = 'fictional-browser-password'
let accessToken = ''
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
  const result = await post(page, '/auth/register', { identifier, password, role })
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
  await page.getByLabel('查询日期').fill(date)
  await page.getByRole('button', { name: '查询排班' }).click()
  const resource = page.getByRole('listitem').filter({ hasText: '订单演示医生' })
  await expect(resource).toContainText('线上')
  await resource.getByRole('button', { name: '提前预约' }).click()
  await expect(page.getByRole('status')).toContainText('预约登记已提交，待支付')
  await page.getByRole('link', { name: '返回工作台' }).click()
  await page.getByRole('link', { name: '我的预约订单' }).click()
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
