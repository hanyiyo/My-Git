import { test, expect } from '@playwright/test'

test('账户管理展示统一账户目录和治理边界', async ({ page }) => {
  const apiRequests: string[] = []
  page.on('request', request => { if (request.url().includes('/api/')) apiRequests.push(request.url()) })
  await page.goto('/prototype/admin-accounts')
  await expect(page.getByRole('heading', { name: '账户管理' })).toBeVisible()
  await expect(page.getByText('统一账户目录')).toBeVisible()
  await expect(page.getByText('林知夏')).toBeVisible()
  await expect(page.getByText('医疗内容权限')).toBeVisible()
  await expect(page.getByText('默认未开启')).toBeVisible()
  await expect(page.getByText('医疗正文、附件、预约内容和医生资质审核均在独立模块处理。')).toBeVisible()
  expect(apiRequests).toEqual([])
})

test('账户筛选同步 URL，详情展示脱敏信息和治理记录', async ({ page }) => {
  await page.goto('/prototype/admin-accounts')
  await page.getByLabel('账户角色').selectOption('DOCTOR')
  await expect(page).toHaveURL(/role=DOCTOR/)
  await expect(page.getByText('周宁')).toBeVisible()
  await expect(page.getByText('林知夏')).not.toBeVisible()
  await page.getByLabel('账户状态').selectOption('LOCKED')
  await expect(page).toHaveURL(/status=LOCKED/)
  await expect(page.getByText('马凌')).toBeVisible()
  await page.getByRole('button', { name: '查看ACC-1098详情' }).click()
  const drawer = page.getByRole('complementary', { name: '账户详情' })
  await expect(drawer).toContainText('需关注')
  await expect(drawer).toContainText('136 **** 1234')
  await expect(drawer).toContainText('最近治理记录')
  await expect(drawer).toContainText('登录锁定')
})

test('账户高风险治理操作需要二次确认并更新状态', async ({ page }) => {
  await page.goto('/prototype/admin-accounts')
  await page.getByRole('button', { name: '查看ACC-1007详情' }).click()
  const drawer = page.getByRole('complementary', { name: '账户详情' })
  await drawer.getByRole('button', { name: '停用账户' }).click()
  const confirm = page.getByRole('dialog', { name: '确认停用账户' })
  await expect(confirm).toContainText('不会取消预约')
  await confirm.getByRole('button', { name: '确认停用账户' }).click()
  await expect(drawer.getByRole('status')).toContainText('停用账户已完成')
  await expect(drawer).toContainText('已停用')
  await drawer.getByRole('button', { name: '重置登录状态' }).click()
  await expect(page.getByRole('dialog', { name: '确认重置登录状态' })).toBeVisible()
})

test('账户管理在移动宽度无横向溢出并能打开侧栏', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto('/prototype/admin-accounts')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.getByRole('button', { name: '打开侧栏' }).click()
  await expect(page.getByRole('complementary', { name: '管理员端导航' })).toBeVisible()
  await page.getByRole('button', { name: '关闭侧栏' }).click()
})
