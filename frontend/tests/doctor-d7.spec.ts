import { test, expect, type Page } from '@playwright/test'

const password = 'fictional-browser-password'
async function registerDoctor(page: Page) {
  const name = `d7-doctor-${crypto.randomUUID()}`
  await page.goto('/login')
  await page.getByRole('button', { name: '患者或医生注册' }).click()
  await page.getByLabel('账号', { exact: true }).fill(name)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByLabel('注册身份').selectOption('DOCTOR')
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('注册成功')
  await page.getByLabel('账号', { exact: true }).fill(name)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByLabel('图形验证码', { exact: true }).fill('AB7K')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  return name
}
function notificationRoutes(page: Page, activeAppointmentId: string | null = null) {
  const notifications = [
    { id: 11, cursorNo: 11, eventType: 'SUBSTITUTE_INVITED', objectId: 'request-11', payload: '请确认或拒绝替诊邀请', createdAt: Date.parse('2026-11-17T08:00:00+08:00'), readAt: null },
    { id: 12, cursorNo: 12, eventType: 'CHECK_IN', objectId: 'appointment-12', payload: '预约患者已签到', createdAt: Date.parse('2026-11-17T08:01:00+08:00'), readAt: 1 },
  ]
  page.route('**/api/v1/notifications?*', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(notifications) }))
  page.route('**/api/v1/notifications/unread', route => route.fulfill({ status: 200, contentType: 'application/json', body: '1' }))
  page.route('**/api/v1/notifications/stream', route => route.abort())
  page.route('**/api/v1/doctor/home', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ pendingApplicationCount: 1, activeAppointmentId }) }))
  page.route('**/api/v1/notifications/11/read', route => route.fulfill({ status: 204 }))
  page.route('**/api/v1/doctor/catalog/profile', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ id: 'doctor-1', displayName: '林知远', title: '主治医师', biography: '虚构资料', credentialStatus: 'CERTIFIED', enabled: true, departmentId: 'dept-1', departmentName: '内科' }) }))
}

test('正式通知按待处理与普通分区展示，打开即已读并定位申请', async ({ page }) => {
  await registerDoctor(page)
  notificationRoutes(page)
  await page.getByRole('button', { name: '站内通知', exact: true }).click()
  await expect(page).toHaveURL(/\/doctor\/notifications$/)
  await expect(page.getByRole('heading', { name: '站内通知' })).toBeVisible()
  await expect(page.getByText('未读 1')).toBeVisible()
  await expect(page.getByText('业务待处理 1')).toBeVisible()
  await expect(page.getByText('请确认或拒绝替诊邀请')).toBeVisible()
  await page.getByRole('button', { name: /替诊邀请/ }).click()
  await expect(page.getByRole('dialog')).toContainText('请确认或拒绝替诊邀请')
  await expect(page.getByText('未读 0')).toBeVisible()
  await page.getByRole('button', { name: '前往处理' }).click()
  await expect(page).toHaveURL(/\/doctor\/governance\?request=request-11$/)
  await page.setViewportSize({ width: 375, height: 812 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
})

test('正式个人资料只读展示真实身份，接诊中退出保留当前接诊', async ({ page }) => {
  await registerDoctor(page)
  notificationRoutes(page, 'appointment-1')
  await page.route('**/api/v1/profile', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ id: 'doctor-1', identifier: 'd7-doctor', roles: ['DOCTOR'], enabled: true, credentialStatus: 'CERTIFIED', displayName: null, phoneNumber: '13800138000' }) }))
  await page.route('**/api/v1/auth/logout', route => route.fulfill({ status: 204 }))
  await page.getByRole('button', { name: '个人资料', exact: true }).click()
  await expect(page.getByRole('heading', { name: '个人资料' })).toBeVisible()
  await expect(page.getByRole('region', { name: '医生资料' })).toContainText('林知远')
  await expect(page.getByRole('region', { name: '医生资料' })).toContainText('138****8000')
  await expect(page.getByRole('region', { name: '医生资料' })).toContainText('内科')
  await expect(page.getByText('接诊中')).toBeVisible()
  await expect(page.getByText('资料编辑、改密和短信验证暂未开放')).toBeVisible()
  page.once('dialog', dialog => { expect(dialog.message()).toContain('未结束接诊'); void dialog.accept() })
  await page.getByRole('region', { name: '账户与设置' }).getByRole('button', { name: '退出登录' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await page.setViewportSize({ width: 375, height: 812 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
})

