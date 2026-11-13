import { test, expect } from '@playwright/test'

test('旧账户管理路径兼容跳转到患者资源工作台', async ({ page }) => {
  await page.goto('/prototype/admin-accounts')
  await expect(page).toHaveURL(/\/prototype\/admin-patients/)
  await expect(page.getByRole('heading', { name: '患者资源工作台' })).toBeVisible()
  await expect(page.getByText('林知夏')).toBeVisible()
  await expect(page.getByText('医疗内容权限')).toBeVisible()
  await expect(page.getByText('默认未开启')).toBeVisible()
})

test('旧账户管理路径在移动宽度也兼容跳转', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto('/prototype/admin-accounts')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.getByRole('button', { name: '打开侧栏' }).click()
  await expect(page.getByRole('complementary', { name: '管理员端导航' })).toBeVisible()
  await page.getByRole('button', { name: '关闭侧栏' }).click()
})
