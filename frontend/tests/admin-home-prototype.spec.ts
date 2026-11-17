import { test, expect } from '@playwright/test'

test('管理员首页展示运营驾驶舱与完整管理导航', async ({ page }) => {
  const apiRequests: string[] = []
  page.on('request', request => { if (request.url().includes('/api/')) apiRequests.push(request.url()) })
  await page.goto('/prototype/admin-home?variant=A')
  await expect(page.getByRole('heading', { name: '早上好，管理员。' })).toBeVisible()
  const navigation = page.getByRole('navigation', { name: '管理员功能导航' })
  for (const label of ['运营总览', '预约与订单', '退款 / 异常', '患者', '医生', '科室', '排班', '号源', '药品与库存', '药费订单', '审批与替诊', '新闻通知', '审计日志', '全局规则', 'AI 配置', '管理员账户']) {
    await expect(navigation.getByRole('button', { name: label, exact: true })).toBeVisible()
  }
  await expect(page.getByText('医疗内容权限')).toBeVisible()
  await expect(page.getByText('默认未开启')).toBeVisible()
  await expect(navigation.getByRole('button', { name: '医疗访问审核', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '医疗访问审核详情', exact: true })).toHaveCount(0)
  await expect(page.getByText('审批、退款、访问审核')).toHaveCount(0)
  await expect(page.locator('.admin-stat').filter({ hasText: '待处理事项' }).locator('strong')).toHaveText('12')
  await expect(page.locator('.admin-stat').filter({ hasText: '待处理事项' })).toContainText('2 项紧急')
  await expect(page.getByRole('heading', { name: '今天有 6 项运营事项需要关注' })).toBeVisible()
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

test('管理员首页可在审批与替诊模块查看默认待复核工单', async ({ page }) => {
  const apiRequests: string[] = []
  page.on('request', request => { if (request.url().includes('/api/')) apiRequests.push(request.url()) })
  await page.goto('/prototype/admin-home?module=%E5%AE%A1%E6%89%B9%E4%B8%8E%E6%9B%BF%E8%AF%8A')
  await expect(page.getByRole('heading', { name: '审批与替诊工作台' })).toBeVisible()
  await expect(page.getByRole('button', { name: '审批与替诊', exact: true })).toHaveClass(/active/)
  await expect(page.getByLabel('处理状态')).toHaveValue('PENDING')
  await expect(page.getByText('待管理员复核').first()).toBeVisible()
  await expect(page.getByText('SUS-20261113-001')).toBeVisible()
  await expect(page.getByText('SUS-20261113-003')).toBeVisible()
  await expect(page.getByText('SUS-20261112-004')).not.toBeVisible()
  await expect(page.getByLabel('处理状态').locator('option')).toHaveCount(3)
  await expect(page.getByLabel('提交开始日期')).toBeVisible()
  await expect(page.getByLabel('搜索审批工单')).toBeVisible()
  expect(apiRequests).toEqual([])
  await page.getByLabel('处理状态').selectOption('PROCESSED')
  await expect(page.getByText('SUS-20261112-004')).toBeVisible()
  await expect(page.getByText('SUS-20261113-001')).not.toBeVisible()
})

test('审批详情展示校验与患者影响，普通替诊确认后同步状态', async ({ page }) => {
  await page.goto('/prototype/admin-home?module=%E5%AE%A1%E6%89%B9%E4%B8%8E%E6%9B%BF%E8%AF%8A')
  await page.getByRole('button', { name: '查看SUS-20261113-001详情' }).click()
  const drawer = page.getByRole('complementary', { name: '审批工单详情' })
  await expect(drawer).toContainText('校验结果')
  await expect(drawer).toContainText('患者影响与处理预览')
  await expect(drawer).toContainText('林知夏')
  await expect(drawer).toContainText('自动迁移')
  await drawer.getByRole('button', { name: '查看排班' }).click()
  await expect(page).toHaveURL(/prototype\/admin-schedules\?date=2026-11-15/)

  await page.goBack()
  await page.getByRole('button', { name: '查看SUS-20261113-001详情' }).click()
  await page.getByRole('button', { name: '通过替诊' }).click()
  await expect(page.getByRole('dialog', { name: '审批确认' })).toContainText('自动迁移 2 条有效挂号')
  await page.getByRole('dialog', { name: '审批确认' }).getByRole('button', { name: '确认提交' }).click()
  await expect(page.getByRole('status')).toContainText('已通过替诊')
  await expect(page.getByRole('complementary', { name: '审批工单详情' })).toContainText('已替诊')
})

test('驳回与紧急处理都要求表单完成后才能提交', async ({ page }) => {
  await page.goto('/prototype/admin-home?module=%E5%AE%A1%E6%89%B9%E4%B8%8E%E6%9B%BF%E8%AF%8A')
  await page.getByRole('button', { name: '查看SUS-20261113-002详情' }).click()
  await page.getByRole('button', { name: '驳回申请' }).click()
  const rejectDialog = page.getByRole('dialog', { name: '审批确认' })
  await rejectDialog.getByRole('button', { name: '确认提交' }).click()
  await expect(rejectDialog.getByRole('alert')).toContainText('请填写驳回理由')
  await rejectDialog.getByLabel('驳回理由').fill('排班信息仍需科室确认')
  await rejectDialog.getByRole('button', { name: '确认提交' }).click()
  await expect(page.getByRole('status')).toContainText('已驳回申请')
  await page.getByRole('button', { name: '关闭审批工单详情' }).click()

  await page.getByRole('button', { name: '查看SUS-20261113-007详情' }).click()
  await page.getByRole('button', { name: '进入紧急处理' }).click()
  const emergencyDialog = page.getByRole('dialog', { name: '审批确认' })
  await emergencyDialog.getByRole('button', { name: '确认提交' }).click()
  await expect(emergencyDialog.getByRole('alert')).toContainText('请填写紧急处理理由')
  await emergencyDialog.getByLabel('紧急处理理由').fill('距开诊不足两小时，立即执行患者分流')
  await emergencyDialog.getByRole('button', { name: '确认提交' }).click()
  await expect(emergencyDialog.getByRole('alert')).toContainText('逐一选择所有患者')
  const choices = emergencyDialog.locator('select')
  await choices.nth(0).selectOption('ACCEPT')
  await choices.nth(1).selectOption('REJECT')
  await emergencyDialog.getByRole('button', { name: '确认提交' }).click()
  await expect(page.getByRole('status')).toContainText('已完成紧急处理')
  await expect(page.getByRole('complementary', { name: '审批工单详情' })).toContainText('紧急停诊')
})

test('非管理员复核工单只读，并可跳转预约与订单', async ({ page }) => {
  await page.goto('/prototype/admin-home?module=%E5%AE%A1%E6%89%B9%E4%B8%8E%E6%9B%BF%E8%AF%8A')
  await page.getByLabel('处理状态').selectOption('ALL')
  await page.getByRole('button', { name: '查看SUS-20261113-003详情' }).click()
  const drawer = page.getByRole('complementary', { name: '审批工单详情' })
  await expect(drawer).toContainText('管理员只能查看')
  await expect(drawer.getByRole('button', { name: '通过替诊' })).toHaveCount(0)
  await drawer.getByRole('button', { name: '查看预约与订单' }).click()
  await expect(page).toHaveURL(/prototype\/admin-orders\?tab=appointments&date=2026-11-14&keyword=SUS-20261113-003/)
})

test('审批与替诊工作台在移动宽度没有横向溢出', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto('/prototype/admin-home?module=%E5%AE%A1%E6%89%B9%E4%B8%8E%E6%9B%BF%E8%AF%8A')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await expect(page.getByRole('heading', { name: '审批与替诊工作台' })).toBeVisible()
  await page.getByRole('button', { name: '打开侧栏' }).click()
  await expect(page.getByRole('complementary', { name: '管理员端导航' })).toBeVisible()
})

test('管理员药费订单日期范围无效时停留在页面并提示修正', async ({ page }) => {
  await page.goto('/prototype/admin-home?module=%E8%8D%AF%E8%B4%B9%E8%AE%A2%E5%8D%95')
  await page.getByLabel('开始日期').fill('2026-11-13')
  await page.getByLabel('结束日期').fill('2026-11-12')
  await expect(page.getByRole('alert')).toContainText('结束日期不能早于开始日期')
  await expect(page.getByText('日期范围无效')).toBeVisible()
})

test('药费订单内容区使用管理员工作台宽度而不是全局窄页面样式', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/prototype/admin-home?module=%E8%8D%AF%E8%B4%B9%E8%AE%A2%E5%8D%95')
  const content = page.locator('.medicine-orders-content')
  await expect(content).toHaveCSS('max-width', 'none')
  expect(await content.evaluate(element => element.getBoundingClientRect().width)).toBeGreaterThan(900)
  await expect(page.locator('.medicine-orders-summary')).toHaveCSS('display', 'grid')
  await expect(page.locator('.orders-table-wrap')).toHaveCSS('overflow', 'auto')
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
