import { test, expect, type Page } from '@playwright/test'
const password = 'fictional-browser-password'
async function register(page: Page, role: 'PATIENT' | 'DOCTOR') {
  const name = `browser-${crypto.randomUUID()}`
  await page.goto('/login')
  await page.getByRole('button', { name: '患者或医生注册' }).click()
  await page.getByLabel('账号', { exact: true }).fill(name)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByLabel('注册身份').selectOption(role)
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('注册成功')
  await expect(page.getByAltText('图形验证码')).toBeVisible()
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
  await login(page, name)
  await expect(page).toHaveURL(/\/patient$/)
  await expect(page.getByText(`欢迎，${name}`)).toBeVisible()
  const refresh = (await context.cookies()).find(cookie => cookie.name === 'MEDICAL_REFRESH')
  expect(refresh?.httpOnly).toBeTruthy(); expect(refresh?.sameSite).toBe('Strict')
  expect(await page.evaluate(() => [localStorage.getItem('accessToken'), sessionStorage.getItem('refreshToken')])).toEqual([null, null])
  await page.reload(); await expect(page.getByText(`欢迎，${name}`)).toBeVisible()
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
  const newer = page.waitForResponse('**/api/v1/auth/captcha')
  await page.getByRole('button', { name: '换一张' }).click()
  await newer
  const older = page.waitForResponse('**/api/v1/auth/captcha')
  release()
  await older
  await login(page, name)
  await expect(page.getByRole('status')).toContainText('验证码无效')
  await expect(page.getByLabel('账号', { exact: true })).toHaveValue(name)
  await login(page, name)
  await expect(page).toHaveURL(/\/patient$/)
})
