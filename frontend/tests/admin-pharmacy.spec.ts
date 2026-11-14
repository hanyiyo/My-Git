import { test, expect } from '@playwright/test'

const password = 'fictional-test-administrator-password'
async function login(page: import('@playwright/test').Page) {
  await page.goto('/login')
  await page.getByLabel('账号', { exact: true }).fill('admin-foundation-test')
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByLabel('图形验证码', { exact: true }).fill('AB7K')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/admin$/)
}

test('正式管理员药品工作台展示库存口径、筛选和详情抽屉', async ({ page }) => {
  await login(page)
  await page.goto('/admin/pharmacy')
  await expect(page.getByRole('heading', { name: '药品与库存工作台' })).toBeVisible()
  await expect(page.getByRole('region', { name: '药品库存摘要' })).toBeVisible()
  await expect(page.getByRole('region', { name: '药品库存表' })).toBeVisible()
  await expect(page.getByLabel('搜索药品')).toBeVisible()
  await expect(page.getByLabel('药品状态')).toHaveValue('ACTIVE')
  await expect(page.getByText('可用库存 ≤ 阈值')).toBeVisible()
  const dataRows = page.locator('tbody tr').filter({ has: page.getByRole('button', { name: '详情' }) })
  if (await dataRows.count() > 0) {
    const first = dataRows.first()
    await first.click()
    const drawer = page.getByRole('complementary', { name: '药品详情' })
    await expect(drawer).toContainText('可用库存')
    await expect(drawer).toContainText('库存锁定')
    await expect(drawer).toContainText('总库存')
    await expect(drawer).toContainText('消耗记录')
    await drawer.getByRole('button', { name: '关闭药品详情' }).click()
  }
})

test('管理员可新增药品、独立调整总库存并切换启停状态', async ({ page }) => {
  const name = `浏览器演示药品-${crypto.randomUUID().slice(0, 8)}`
  await login(page)
  await page.goto('/admin/pharmacy')
  await page.getByRole('button', { name: '＋新增药品' }).click()
  const create = page.getByRole('dialog', { name: '新增药品' })
  await create.getByLabel('药品名称').fill(name)
  await create.getByLabel('药品规格').fill('10片 / 盒')
  await create.getByLabel('单价（元）').fill('12.50')
  await create.getByLabel('预警阈值').fill('2')
  await create.getByRole('button', { name: '保存资料' }).click()
  await expect(page.getByRole('status')).toContainText('药品已创建')
  const row = page.getByRole('row').filter({ hasText: name })
  await expect(row).toContainText('0')
  await row.getByRole('button', { name: '调库存' }).click()
  const stock = page.getByRole('dialog', { name: '调整库存' })
  await expect(stock).toContainText('目标总库存')
  await stock.getByLabel('目标总库存').fill('8')
  await stock.getByRole('button', { name: '确认调整' }).click()
  await expect(page.getByRole('status')).toContainText('总库存已调整为 8')
  await expect(page.getByRole('row').filter({ hasText: name })).toContainText('8')
  await page.getByRole('complementary', { name: '药品详情' }).getByRole('button', { name: '关闭药品详情' }).click()
  await page.getByRole('row').filter({ hasText: name }).getByRole('button', { name: '停用' }).click()
  await expect(page.getByRole('status')).toContainText('已停用')
  await page.getByLabel('药品状态').selectOption('INACTIVE')
  await expect(page.getByRole('row').filter({ hasText: name })).toContainText('停用')
})

test('正式管理员药品页窄屏无横向溢出，药品入口保持可用', async ({ page }) => {
  await login(page)
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto('/admin/pharmacy')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button', { name: '打开侧栏' }).click()
  await expect(page.getByRole('complementary', { name: '管理员端导航' })).toBeVisible()
  await expect(page.getByRole('button', { name: '药品与库存', exact: true })).toHaveClass(/active/)
})
