import { test, expect } from '@playwright/test'

const pages = [
  ['/prototype/admin-home', '早上好，管理员。'],
  ['/prototype/admin-orders', '预约运营工作台'],
  ['/prototype/admin-patients', '患者资源工作台'],
  ['/prototype/admin-doctors', '医生治理工作台'],
  ['/prototype/admin-departments', '科室管理工作台'],
  ['/prototype/admin-schedules', '排班管理工作台'],
  ['/prototype/admin-slots', '号源管理工作台'],
  ['/prototype/admin-home?module=新闻通知', '新闻通知工作台'],
] as const

for (const [path, heading] of pages) {
  test(`管理员入口隐藏医疗访问审核：${path}`, async ({ page }) => {
    await page.goto(path)
    await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible()
    const navigation = page.getByRole('navigation', { name: '管理员功能导航' })
    await expect(navigation.getByRole('button', { name: '医疗访问审核', exact: true })).toHaveCount(0)
    await expect(navigation.getByRole('button', { name: '新闻通知', exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: '医疗内容审核', exact: true })).toHaveCount(0)
    await page.setViewportSize({ width: 375, height: 812 })
    await page.getByRole('button', { name: '打开侧栏', exact: true }).click()
    await expect(navigation.getByRole('button', { name: '医疗访问审核', exact: true })).toHaveCount(0)
    await expect(navigation.getByRole('button', { name: '新闻通知', exact: true })).toBeVisible()
  })
}
