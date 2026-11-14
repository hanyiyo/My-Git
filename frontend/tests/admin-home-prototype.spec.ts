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

test('管理员首页可在药品与库存模块内维护药品', async ({ page }) => {
  const apiRequests: string[] = []
  page.on('request', request => { if (request.url().includes('/api/')) apiRequests.push(request.url()) })
  await page.goto('/prototype/admin-home?module=%E8%8D%AF%E5%93%81%E4%B8%8E%E5%BA%93%E5%AD%98')
  await expect(page.getByRole('heading', { name: '药品与库存工作台' })).toBeVisible()
  await expect(page.getByRole('button', { name: '药品与库存', exact: true })).toHaveClass(/active/)
  await expect(page.getByLabel('搜索药品')).toBeVisible()
  await expect(page.getByLabel('药品状态')).toHaveValue('ALL')
  await expect(page.getByText('演示数据 · 不连接真实业务接口')).toBeVisible()
  await expect(page.getByText('对乙酰氨基酚片')).toBeVisible()
  await expect(page.getByText('氯雷他定片')).toBeVisible()
  await expect(page.getByText('布洛芬缓释胶囊')).toBeVisible()
  await expect(page.getByText('库存预警').first()).toBeVisible()
  expect(apiRequests).toEqual([])
})

test('管理员首页可在药费订单模块查看筛选和订单详情', async ({ page }) => {
  const apiRequests: string[] = []
  page.on('request', request => { if (request.url().includes('/api/')) apiRequests.push(request.url()) })
  await page.goto('/prototype/admin-home?module=%E8%8D%AF%E8%B4%B9%E8%AE%A2%E5%8D%95')
  await expect(page.getByRole('heading', { name: '药费订单工作台' })).toBeVisible()
  await expect(page.getByRole('button', { name: '药费订单', exact: true })).toHaveClass(/active/)
  const summary = page.getByRole('region', { name: '药费订单摘要' })
  await expect(summary.getByText('今日订单总数')).toBeVisible()
  await expect(summary.getByText('待缴费')).toBeVisible()
  await expect(summary.getByText('已支付')).toBeVisible()
  await expect(summary.getByText('已失效')).toBeVisible()
  await expect(page.getByLabel('订单状态')).toHaveValue('ALL')
  await expect(page.getByText('即将到期').first()).toBeVisible()
  await expect(page.getByText('MEDPAY-20261113-002')).toBeVisible()
  await expect(page.getByText('MEDPAY-20261113-001')).toBeVisible()
  await expect(page.getByText('MEDPAY-20261112-005')).not.toBeVisible()
  expect(apiRequests).toEqual([])

  await page.getByLabel('订单状态').selectOption('PAID')
  await expect(page.getByText('MEDPAY-20261113-001')).toBeVisible()
  await expect(page.getByText('MEDPAY-20261113-002')).not.toBeVisible()
  await page.getByLabel('订单状态').selectOption('ALL')
  await page.getByLabel('搜索药费订单').fill('阿莫西林')
  await expect(page.getByText('MEDPAY-20261113-002')).toBeVisible()
  await expect(page.getByText('MEDPAY-20261113-001')).not.toBeVisible()
  await page.getByLabel('搜索药费订单').fill('')

  await page.getByRole('button', { name: '查看MEDPAY-20261113-002详情' }).click()
  const drawer = page.getByRole('complementary', { name: '药费订单详情' })
  await expect(drawer).toContainText('药品明细')
  await expect(drawer).toContainText('关联挂号摘要')
  await expect(drawer).toContainText('陈亦安')
  await expect(drawer).toContainText('P-1021')
  await expect(drawer).not.toContainText('每日')
  await drawer.getByRole('button', { name: '复制订单号' }).click()
  await expect(page.getByRole('status')).toContainText('药费订单号')
  await drawer.getByRole('button', { name: '查看预约与订单' }).click()
  await expect(page).toHaveURL(/prototype\/admin-orders\?tab=appointments&date=ALL&keyword=APT-20261113-002/)
})

test('管理员药费订单日期范围无效时停留在页面并提示修正', async ({ page }) => {
  await page.goto('/prototype/admin-home?module=%E8%8D%AF%E8%B4%B9%E8%AE%A2%E5%8D%95')
  await page.getByLabel('开始日期').fill('2026-11-13')
  await page.getByLabel('结束日期').fill('2026-11-12')
  await expect(page.getByRole('alert')).toContainText('结束日期不能早于开始日期')
  await expect(page.getByText('日期范围无效')).toBeVisible()
})

test('管理员首页不再提供重复的账户管理入口', async ({ page }) => {
  await page.goto('/prototype/admin-home?variant=A')
  await expect(page.getByRole('button', { name: '账户管理', exact: true })).toHaveCount(0)
})

test('管理员首页资源导航可以进入三个资源工作台', async ({ page }) => {
  const entries = [
    ['患者', '/prototype/admin-patients', '患者资源工作台'],
    ['医生', '/prototype/admin-doctors', '医生治理工作台'],
    ['科室', '/prototype/admin-departments', '科室管理工作台'],
  ] as const
  for (const [label, path, heading] of entries) {
    await page.goto('/prototype/admin-home?variant=A')
    await page.getByRole('button', { name: label, exact: true }).click()
    await expect(page).toHaveURL(new RegExp(path.replaceAll('/', '\\/')))
    await expect(page.getByRole('heading', { name: heading })).toBeVisible()
  }
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
