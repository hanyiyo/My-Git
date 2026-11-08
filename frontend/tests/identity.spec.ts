import { test, expect, type Page } from '@playwright/test'
const password = 'fictional-browser-password'
async function register(page: Page, role: 'PATIENT' | 'DOCTOR') {
  const name = `browser-${crypto.randomUUID()}`
  await page.goto('/login')
  await page.getByRole('button', { name: '患者或医生注册' }).click()
  await page.getByLabel('账号', { exact: true }).fill(name)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByLabel('确认密码', { exact: true }).fill(password)
  await page.getByRole('radio', { name: role === 'DOCTOR' ? /医生端/ : /患者端/ }).check()
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
test('手机宽度下登录注册与管理员入口不产生横向滚动', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 375, height: 812 }); await page.goto('/login')
  await expect(page.getByAltText('图形验证码')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
  await page.screenshot({ path: testInfo.outputPath('auth-navy-mobile.png'), fullPage: true })
  await page.getByRole('tab', { name: '注册', exact: true }).click()
  await expect(page.getByLabel('确认密码', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
  await page.getByRole('radio', { name: /管理员端/ }).check()
  await expect(page.getByLabel('图形验证码', { exact: true })).toBeVisible()
  await expect(page.getByRole('tab', { name: '注册', exact: true })).toHaveCount(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
})

test('深蓝左右布局支持密码显隐，管理员入口只有登录，患者医生可切换注册', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 1000 }); await page.goto('/login')
  await expect(page.getByAltText('图形验证码')).toBeVisible()
  const identity = await page.getByRole('group', { name: '选择你的身份' }).boundingBox()
  const form = await page.locator('#account-form').boundingBox()
  expect(identity).not.toBeNull(); expect(form).not.toBeNull()
  expect(identity!.x + identity!.width).toBeLessThan(form!.x)
  await expect(page.getByText(/演示填充|原型切换|模拟验证码/)).toHaveCount(0)
  await page.screenshot({ path: testInfo.outputPath('auth-navy-desktop.png'), fullPage: true })
  await page.getByRole('tab', { name: '登录', exact: true }).focus()
  await page.keyboard.press('ArrowRight')
  await expect(page.getByRole('tab', { name: '注册', exact: true })).toBeFocused()
  await expect(page.getByLabel('确认密码', { exact: true })).toBeVisible()
  await page.keyboard.press('Tab')
  await expect(page.getByLabel('账号', { exact: true })).toBeFocused()
  await page.getByRole('tab', { name: '注册', exact: true }).press('Home')
  await expect(page.getByRole('tab', { name: '登录', exact: true })).toBeFocused()
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '显示密码' }).click()
  await expect(page.getByLabel('密码', { exact: true })).toHaveAttribute('type', 'text')
  await page.getByRole('button', { name: '隐藏密码' }).click()
  await expect(page.getByLabel('密码', { exact: true })).toHaveAttribute('type', 'password')
  await page.getByRole('tab', { name: '注册', exact: true }).click()
  await page.getByRole('radio', { name: /管理员端/ }).check()
  await expect(page.getByRole('tab', { name: '登录', exact: true })).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByText('管理员账号只能由管理员在后台创建。')).toBeVisible()
  await expect(page.getByLabel('图形验证码', { exact: true })).toBeVisible()
  await expect(page.getByRole('tab', { name: '注册', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '患者或医生注册' })).toHaveCount(0)
  await expect(page.getByLabel('确认密码', { exact: true })).toHaveCount(0)
  await page.getByRole('tab', { name: '登录', exact: true }).press('End')
  await expect(page.getByRole('tab', { name: '登录', exact: true })).toBeFocused()
  await page.getByRole('radio', { name: /医生端/ }).check()
  await page.getByRole('tab', { name: '注册', exact: true }).click()
  await expect(page.getByText('医生注册后需通过资质审核。')).toBeVisible()
  await page.getByRole('radio', { name: /患者端/ }).check()
  await expect(page.getByLabel('确认密码', { exact: true })).toBeVisible()
  await expect(page.getByText('医生注册后需通过资质审核。')).toHaveCount(0)
})

test('注册密码不一致时保留输入且不发送注册请求', async ({ page }) => {
  let requests = 0
  page.on('request', request => {
    if (request.method() === 'POST' && request.url().endsWith('/api/v1/auth/register')) requests++
  })
  await page.goto('/login')
  await page.getByRole('tab', { name: '注册', exact: true }).click()
  await page.getByLabel('账号', { exact: true }).fill('fictional-password-mismatch')
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByLabel('确认密码', { exact: true }).fill('another-fictional-password')
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await expect(page.getByRole('status')).toHaveText('两次输入的密码不一致')
  await expect(page.getByLabel('账号', { exact: true })).toHaveValue('fictional-password-mismatch')
  await expect(page.getByLabel('密码', { exact: true })).toHaveValue(password)
  expect(requests).toBe(0)
})

test('首次验证码尚未返回时注册仍等待挑战，并显示成功后可登录', async ({ page }) => {
  let release!: () => void
  let fetched!: () => void
  const held = new Promise<void>(resolve => { release = resolve })
  const received = new Promise<void>(resolve => { fetched = resolve })
  await page.route('**/api/v1/auth/captcha', async route => {
    const response = await route.fetch()
    fetched(); await held; await route.fulfill({ response })
  }, { times: 1 })
  await page.goto('/login'); await received
  const name = `browser-${crypto.randomUUID()}`
  try {
    await page.getByRole('tab', { name: '注册', exact: true }).click()
    await page.getByLabel('账号', { exact: true }).fill(name)
    await page.getByLabel('密码', { exact: true }).fill(password)
    await page.getByLabel('确认密码', { exact: true }).fill(password)
    const registered = page.waitForResponse('**/api/v1/auth/register')
    await page.getByRole('button', { name: '注册', exact: true }).click()
    expect((await registered).status()).toBe(201)
    await expect(page.getByRole('tab', { name: '登录', exact: true })).toHaveAttribute('aria-selected', 'true')
    await expect(page.locator('button[type="submit"]')).toBeDisabled()
  } finally { release() }
  await expect(page.getByRole('status')).toContainText('注册成功')
  await login(page, name)
  await expect(page).toHaveURL(/\/patient$/)
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
