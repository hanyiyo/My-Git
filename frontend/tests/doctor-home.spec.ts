import { test, expect, type Page } from '@playwright/test'

const password = 'fictional-browser-password'
async function registerDoctor(page: Page) {
  const name = `formal-doctor-${crypto.randomUUID()}`
  await page.goto('/login')
  await page.getByRole('button', { name: '患者或医生注册' }).click()
  await page.getByLabel('账号', { exact: true }).fill(name)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByLabel('注册身份').selectOption('DOCTOR')
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('注册成功')
  return name
}
async function login(page: Page, name: string) {
  await page.getByLabel('账号', { exact: true }).fill(name)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByLabel('图形验证码', { exact: true }).fill('AB7K')
  await page.getByRole('button', { name: '登录', exact: true }).click()
}
const home = {
  account: { id: 'doctor-1', identifier: 'doctor-1', roles: ['DOCTOR'], enabled: true, credentialStatus: 'PENDING', displayName: null, phoneNumber: null },
  doctor: { id: 'doctor-1', displayName: '林医生', title: '主治医师', biography: '', credentialStatus: 'PENDING', enabled: true, departmentId: null, departmentName: null },
  hospitalDate: '2099-01-01', hospitalZoneId: 'Asia/Shanghai', currentSchedule: null, nextSchedule: null,
  waitingCount: 0, inConsultationCount: 0, completedCount: 0, pendingApplicationCount: 0, activeAppointmentId: null,
}

test('正式医生首页读取受保护聚合并提供六模块导航和临时标签', async ({ page }) => {
  const name = await registerDoctor(page)
  await page.route('**/api/v1/doctor/home', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(home) }))
  await page.route('**/api/v1/doctor/reception', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))
  await login(page, name)
  await expect(page).toHaveURL(/\/doctor$/)
  await expect(page.getByRole('heading', { name: '您好，林医生。' })).toBeVisible()
  await expect(page.getByRole('link', { name: '我的认证与排班' })).toBeVisible()
  await expect(page.getByRole('region', { name: '站内通知' })).toBeVisible()
  const nav = page.getByRole('navigation', { name: '医生端导航' })
  for (const label of ['首页', '接诊工作台', '我的排班', '申请处理', '站内通知', '个人资料']) await expect(nav.getByRole('button', { name: label, exact: true })).toBeVisible()
  await nav.getByRole('button', { name: '接诊工作台', exact: true }).click()
  await expect(page).toHaveURL(/\/doctor\/reception$/)
  await expect(page.getByRole('heading', { name: '候诊与接诊' })).toBeVisible()
  await expect(page.getByRole('navigation', { name: '医生临时工作区标签' }).getByRole('button', { name: '关闭接诊工作台标签' })).toBeVisible()
  await page.setViewportSize({ width: 375, height: 812 })
  await page.getByRole('button', { name: '打开医生导航' }).click()
  await expect(page.getByRole('button', { name: '打开医生导航' })).toHaveAttribute('aria-expanded', 'true')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
})

test('正式医生首页失败后可以重试且不显示演示数据', async ({ page }) => {
  const name = await registerDoctor(page)
  let attempts = 0
  await page.route('**/api/v1/doctor/home', route => {
    attempts += 1
    return route.fulfill(attempts === 1 ? { status: 503, contentType: 'application/json', body: JSON.stringify({ error: { message: '医生概况暂不可用' } }) } : { status: 200, contentType: 'application/json', body: JSON.stringify(home) })
  })
  await login(page, name)
  await expect(page.getByRole('alert')).toContainText('医生概况暂不可用')
  await expect(page.getByText('许知遥')).toHaveCount(0)
  await page.getByRole('button', { name: '重试加载' }).click()
  await expect(page.getByRole('heading', { name: '您好，林医生。' })).toBeVisible()
})

test('正式接诊工作台支持上下文筛选、只读预览和移动端操作', async ({ page }) => {
  const name = await registerDoctor(page)
  const entries = [
    { appointmentId: 'appointment-a', scheduleId: 'schedule-a', doctorId: 'doctor-1', patientIdentifier: '患者甲', clinicDate: '2099-01-01', session: 'AM', serviceMode: 'ONLINE', source: 'ADVANCE', queueGroup: 1, queuedAt: 1, checkedInAt: 1, restorationCount: 0, state: 'WAITING' },
    { appointmentId: 'appointment-b', scheduleId: 'schedule-a', doctorId: 'doctor-1', patientIdentifier: '患者乙', clinicDate: '2099-01-01', session: 'AM', serviceMode: 'OFFLINE', source: 'WALK_IN', queueGroup: 2, queuedAt: 2, checkedInAt: 2, restorationCount: 0, state: 'WAITING' },
  ]
  await page.route('**/api/v1/doctor/home', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(home) }))
  await page.route('**/api/v1/doctor/reception**', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(entries) }))
  await page.route('**/api/v1/notifications/unread', route => route.fulfill({ status: 200, contentType: 'application/json', body: '0' }))
  await page.route('**/api/v1/notifications?*', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))
  await page.route('**/api/v1/notifications/stream', route => route.abort())
  let writes = 0
  page.on('request', request => { if (request.method() === 'POST' && request.url().includes('/doctor/reception/')) writes += 1 })
  await login(page, name)
  await page.getByRole('button', { name: '接诊工作台', exact: true }).click()
  await expect(page.getByRole('heading', { name: '候诊与接诊' })).toBeVisible()
  await page.getByLabel('搜索患者或预约').fill('患者甲')
  await expect(page.getByText('患者甲', { exact: true })).toBeVisible()
  await expect(page.getByText('患者乙', { exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '查看患者', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '患者候诊预览' })).toContainText('患者甲')
  expect(writes).toBe(0)
  await page.getByRole('button', { name: '关闭患者候诊预览', exact: true }).click()
  await page.setViewportSize({ width: 375, height: 812 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
})
