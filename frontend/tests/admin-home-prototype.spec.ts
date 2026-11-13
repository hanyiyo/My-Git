import { test, expect } from '@playwright/test'

test('管理员首页展示运营驾驶舱与完整管理导航', async ({ page }) => {
  const apiRequests: string[] = []
  page.on('request', request => { if (request.url().includes('/api/')) apiRequests.push(request.url()) })
  await page.goto('/prototype/admin-home?variant=A')
  await expect(page.getByRole('heading', { name: '早上好，管理员。' })).toBeVisible()
  const navigation = page.getByRole('navigation', { name: '管理员功能导航' })
  for (const label of ['运营总览', '预约与订单', '退款 / 异常', '患者', '医生', '科室', '排班', '号源', '药品与库存', '药费订单', '审批与替诊', '新闻通知', '医疗访问审核', '审计日志', '全局规则', 'AI 配置', '管理员账户']) {
    await expect(navigation.getByRole('button', { name: label, exact: true })).toBeVisible()
  }
  await expect(page.getByText('医疗内容权限')).toBeVisible()
  await expect(page.getByText('默认未开启')).toBeVisible()
  await expect(page.getByText('演示数据 · 不连接真实业务接口')).toBeVisible()
  expect(apiRequests).toEqual([])
})

test('管理员首页支持时间切换、刷新、待办抽屉和快捷入口', async ({ page }) => {
  await page.goto('/prototype/admin-home?variant=A')
  await page.getByRole('button', { name: '近 7 日' }).click()
  await expect(page.getByText('运营总览 / 近 7 日')).toBeVisible()
  await page.getByRole('button', { name: '刷新运营数据' }).click()
  await expect(page.getByText('数据更新于 刚刚')).toBeVisible()
  await page.getByRole('button', { name: '停诊替诊终审详情' }).click()
  const dialog = page.getByRole('dialog', { name: '待处理事项详情' })
  await expect(dialog).toContainText('停诊替诊终审')
  await dialog.getByRole('button', { name: '进入审批与替诊' }).click()
  await expect(dialog).not.toBeVisible()
  await expect(page).toHaveURL(/module=%E5%AE%A1%E6%89%B9%E4%B8%8E%E6%9B%BF%E8%AF%8A/)
  await page.getByRole('button', { name: '收起侧栏' }).click()
  await expect(page.getByRole('button', { name: '展开侧栏' })).toHaveAttribute('aria-expanded', 'false')
})

test('管理员首页在平板宽度没有横向溢出并能打开移动导航', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 768 })
  await page.goto('/prototype/admin-home?variant=A')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.setViewportSize({ width: 375, height: 812 })
  await page.reload()
  await page.getByRole('button', { name: '打开侧栏' }).click()
  await expect(page.getByRole('complementary', { name: '管理员端导航' })).toBeVisible()
  await page.getByRole('button', { name: '关闭侧栏' }).click()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})
