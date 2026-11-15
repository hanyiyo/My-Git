import { test, expect, type Page } from '@playwright/test'
const password = 'fictional-browser-password'
async function register(page: Page, role: 'PATIENT' | 'DOCTOR') {
  const name = `browser-${crypto.randomUUID()}`
  await page.goto('/login')
  await page.getByRole('button', { name: '患者或医生注册' }).click()
  if (role === 'PATIENT') {
    await page.getByLabel('姓名', { exact: true }).fill('测试患者')
    await page.getByLabel('手机号', { exact: true }).fill('13800138000')
  }
  await page.getByLabel('账号', { exact: true }).fill(name)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByLabel('注册身份').selectOption(role)
  if (role === 'PATIENT') await page.getByLabel('图形验证码', { exact: true }).fill('AB7K')
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('注册成功')
  await expect(page.getByAltText('图形验证码')).toBeVisible()
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeEnabled()
  return name
}
async function login(page: Page, name: string, pass = password) {
  await page.getByLabel('账号', { exact: true }).fill(name)
  await page.getByLabel('密码', { exact: true }).fill(pass)
  await expect(page.getByAltText('图形验证码')).toBeVisible()
  await page.getByLabel('图形验证码', { exact: true }).fill('AB7K')
  await page.getByRole('button', { name: '登录', exact: true }).click()
}
test('患者完成注册、验证码登录、刷新恢复会话及退出，角色路由阻止越权', async ({ page, context }) => {
  const name = await register(page, 'PATIENT')
  await page.route('**/api/v1/patient/appointments', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))
  await page.route('**/api/v1/catalog/departments', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify([{ id: 'dept-demo', name: '内科', description: '常见病门诊', active: true }]),
  }))
  await page.route('**/api/v1/catalog/doctors', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify([{ id: 'doctor-demo', displayName: '林医生', title: '主治医师', biography: '全科诊疗', credentialStatus: 'CERTIFIED', enabled: true, departmentId: 'dept-demo', departmentName: '内科' }]),
  }))
  await page.route('**/api/v1/catalog/schedules**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))
  await login(page, name)
  await expect(page).toHaveURL(/\/patient$/)
  await expect(page.getByRole('heading', { name: '欢迎回来，测试患者' })).toBeVisible()
  await expect(page.getByRole('region', { name: '快捷服务' })).toBeVisible()
  await expect(page.getByRole('region', { name: '医院信息' })).toContainText('校医院')
  await expect(page.getByRole('region', { name: '科室与医生' })).toContainText('内科')
  await expect(page.getByRole('region', { name: '科室与医生' })).toContainText('林医生')
  await expect(page.getByText('暂无当前预约', { exact: true })).toBeVisible()
  await page.getByRole('link', { name: /内科/ }).last().click()
  await expect(page).toHaveURL(/\/patient\/catalog\?.*departmentId=dept-demo/)
  await expect(page.getByLabel('科室', { exact: true })).toHaveValue('dept-demo')
  await expect(page.getByText('当前筛选暂无可用号源')).toBeVisible()
  await page.getByRole('link', { name: '首页', exact: true }).click()
  await expect(page).toHaveURL(/\/patient$/)
  await page.getByRole('link', { name: /林医生/ }).click()
  await expect(page).toHaveURL(/\/patient\/catalog\?.*doctorId=doctor-demo/)
  await page.goto('/patient/catalog?departmentId=missing&doctorId=missing&date=invalid&mode=WRONG&session=NOPE')
  await expect(page).toHaveURL(/\/patient\/catalog\?mode=OFFLINE&date=\d{4}-\d{2}-\d{2}$/)
  await expect(page.getByText('当前筛选暂无可用号源')).toBeVisible()
  await page.getByRole('link', { name: '首页', exact: true }).click()
  await expect(page).toHaveURL(/\/patient$/)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
  await page.route('**/api/v1/catalog/hospital', route => route.fulfill({
    status: 503,
    contentType: 'application/json',
    body: JSON.stringify({ error: { code: 'SERVICE_UNAVAILABLE', message: '医院信息暂不可用' } }),
  }), { times: 1 })
  await page.reload()
  await expect(page.getByRole('alert')).toContainText('医院信息暂不可用')
  await page.getByRole('button', { name: '重试加载' }).click()
  await expect(page.getByRole('region', { name: '医院信息' })).toContainText('校医院')
  const refresh = (await context.cookies()).find(cookie => cookie.name === 'MEDICAL_REFRESH')
  expect(refresh?.httpOnly).toBeTruthy(); expect(refresh?.sameSite).toBe('Strict')
  expect(await page.evaluate(() => [localStorage.getItem('accessToken'), sessionStorage.getItem('refreshToken')])).toEqual([null, null])
  await page.goto('/admin'); await expect(page).toHaveURL(/\/patient$/)
  await page.getByRole('button', { name: '退出登录' }).click(); await expect(page).toHaveURL(/\/login$/)
  await page.goto('/patient'); await expect(page).toHaveURL(/\/login$/)
})
test('医生注册进入待审核工作台，错误验证码后保留账号并支持刷新', async ({ page }) => {
  const name = await register(page, 'DOCTOR')
  await page.getByLabel('图形验证码', { exact: true }).fill('WRNG')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('验证码无效')
  await expect(page.getByLabel('账号', { exact: true })).toHaveValue(name)
  await page.getByRole('button', { name: '换一张' }).click()
  await login(page, name)
  await expect(page).toHaveURL(/\/doctor$/)
  await expect(page.getByText('医生资质：待审核')).toBeVisible()
})
test('患者首页预约摘要按状态提供下一步入口', async ({ page }) => {
  const name = await register(page, 'PATIENT')
  const now = Date.now()
  let state = 'PENDING_PAYMENT'
  await page.route('**/api/v1/patient/appointments', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify([{
      id: 'appointment-demo', doctorName: '林医生', departmentName: '内科', clinicDate: '2099-01-01', session: 'AM',
      serviceMode: 'OFFLINE', state, startsAt: now + 3600000, endsAt: now + 7200000,
      serverNow: now, credential: null, payment: null, reception: null,
    }]),
  }))
  await login(page, name)
  await expect(page.getByText('待支付', { exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: '去我的挂号完成支付' })).toHaveAttribute('href', '/patient/orders?state=PENDING_PAYMENT')
  state = 'COMPLETED'
  await page.reload()
  await expect(page.getByText('已完成', { exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: '查看问诊材料与结果' })).toHaveAttribute('href', '/patient/orders/appointment-demo/clinical')
})
test('患者首页预约加载失败显示重试并恢复为空状态', async ({ page }) => {
  const name = await register(page, 'PATIENT')
  await page.route('**/api/v1/patient/appointments', route => route.fulfill({
    status: 503, contentType: 'application/json', body: JSON.stringify({ error: { code: 'SERVICE_UNAVAILABLE', message: '预约服务暂不可用' } }),
  }), { times: 1 })
  await login(page, name)
  await expect(page.getByRole('alert')).toContainText('预约服务暂不可用')
  await page.getByRole('button', { name: '重试加载预约' }).click()
  await expect(page.getByText('暂无当前预约', { exact: true })).toBeVisible()
})
test('患者首页显示通知未读数并在新通知和已读事件后刷新', async ({ page }) => {
  const name = await register(page, 'PATIENT')
  let unread = 3
  await page.route('**/api/v1/notifications/unread', route => route.fulfill({ status: 200, contentType: 'application/json', body: String(unread) }))
  await page.route('**/api/v1/notifications?**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))
  await page.route('**/api/v1/notifications/stream', route => route.abort())
  await login(page, name)
  const notificationLink = page.locator('a').filter({ hasText: '未读提醒' }).first()
  await expect(notificationLink).toContainText('未读提醒：3')
  unread = 4
  await page.evaluate(() => window.dispatchEvent(new CustomEvent('medical-notification')))
  await expect(notificationLink).toContainText('未读提醒：4')
  unread = 2
  await page.evaluate(() => window.dispatchEvent(new CustomEvent('medical-notification-read')))
  await expect(notificationLink).toContainText('未读提醒：2')
  await notificationLink.click()
  await expect(page).toHaveURL(/\/notifications$/)
})
test('管理员使用配置的引导账户登录并刷新会话', async ({ page }) => {
  await page.goto('/login'); await login(page, 'admin-foundation-test', 'fictional-test-administrator-password')
  await expect(page).toHaveURL(/\/admin$/)
  await page.reload(); await expect(page.getByRole('heading', { name: '管理员工作台' })).toBeVisible()
  await page.getByRole('button', { name: '退出登录' }).click(); await expect(page).toHaveURL(/\/login$/)
})
test('手机宽度下登录表单不产生横向滚动', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 }); await page.goto('/login')
  await expect(page.getByAltText('图形验证码')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
})

test('登录服务暂不可用时保留账号，刷新后可重试登录', async ({ page }) => {
  const name = await register(page, 'PATIENT')
  // HTTP recovery tests stop Redis; this boundary injection verifies the browser's 503 handling.
  await page.route('**/api/v1/auth/login', route => route.fulfill({
    status: 503, contentType: 'application/json',
    body: JSON.stringify({ error: { code: 'SERVICE_UNAVAILABLE', message: '登录服务暂不可用，请稍后重试' } })
  }), { times: 1 })
  await login(page, name)
  await expect(page.getByRole('status')).toContainText('暂不可用')
  await expect(page.getByLabel('账号', { exact: true })).toHaveValue(name)
  await login(page, name)
  await expect(page).toHaveURL(/\/patient$/)
})

test('错误后的验证码仍在刷新时暂停输入和再次刷新，返回新挑战后能登录', async ({ page }) => {
  const name = await register(page, 'DOCTOR')
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeEnabled()
  let release!: () => void
  let fetched!: () => void
  const held = new Promise<void>(resolve => { release = resolve })
  const received = new Promise<void>(resolve => { fetched = resolve })
  await page.route('**/api/v1/auth/captcha', async route => {
    const response = await route.fetch()
    fetched()
    await held
    await route.fulfill({ response })
  }, { times: 1 })
  await page.getByLabel('图形验证码', { exact: true }).fill('WRNG')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await received
  try {
    await expect(page.getByLabel('图形验证码', { exact: true })).toBeDisabled()
    await expect(page.getByRole('button', { name: '刷新中…' })).toBeDisabled()
    await expect(page.locator('button[type="submit"]')).toBeDisabled()
  } finally { release() }
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeEnabled()
  await login(page, name)
  await expect(page).toHaveURL(/\/doctor$/)
})

test('迟到的验证码图片不能恢复旧挑战，失败后可获取新码登录', async ({ page }) => {
  const name = await register(page, 'PATIENT')
  let release!: () => void
  let fetched!: () => void
  const held = new Promise<void>(resolve => { release = resolve })
  const received = new Promise<void>(resolve => { fetched = resolve })
  await page.route('**/api/v1/auth/captcha', async route => {
    const response = await route.fetch()
    fetched()
    await held
    await route.fulfill({ response })
  }, { times: 1 })
  await page.getByRole('button', { name: '换一张' }).click()
  await received
  // Another tab can issue a newer challenge even while this page disables refresh.
  expect((await page.request.get('/api/v1/auth/captcha')).ok()).toBeTruthy()
  const older = page.waitForResponse('**/api/v1/auth/captcha')
  release()
  await older
  await login(page, name)
  await expect(page.getByRole('status')).toContainText('验证码无效')
  await expect(page.getByLabel('账号', { exact: true })).toHaveValue(name)
  await login(page, name)
  await expect(page).toHaveURL(/\/patient$/)
})
