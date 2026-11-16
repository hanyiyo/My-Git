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
