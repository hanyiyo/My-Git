import { test, expect, type Page } from '@playwright/test'

const url = '/prototype/admin-home?module=管理员账户'
const table = (page: Page) => page.getByRole('table', { name: '管理员账户', exact: true })
const drawer = (page: Page) => page.getByRole('dialog', { name: '管理员账户详情', exact: true })
const form = (page: Page, name = '新增管理员') => page.getByRole('dialog', { name, exact: true })
const stat = (page: Page, name: string) => page.getByLabel('管理员账户摘要').locator('article').filter({ hasText: name }).locator('strong')
const search = (page: Page) => page.getByLabel('管理员账户关键词', { exact: true })
const state = (page: Page) => page.getByLabel('管理员启用状态', { exact: true })
const password = 'DemoPassword-2026'
async function createForm(page: Page) {
  await page.getByRole('button', { name: '新增管理员', exact: true }).click()
  await form(page).getByLabel('管理员姓名', { exact: true }).fill('测试管理员')
  await form(page).getByLabel('手机号', { exact: true }).fill('13900000099')
  await form(page).getByLabel('登录账号', { exact: true }).fill('test.admin')
  await form(page).getByLabel('演示初始密码', { exact: true }).fill(password)
  await form(page).getByLabel('确认演示初始密码', { exact: true }).fill(password)
}
async function openAccount(page: Page, id = 'ADM-002') { await table(page).getByRole('button', { name: `查看${id}详情`, exact: true }).click(); await expect(drawer(page)).toBeVisible() }
async function confirmAction(page: Page, name: string) {
  await expect(form(page, `确认${name}`)).toBeVisible()
  await form(page, `确认${name}`).getByRole('button', { name: `确认${name}`, exact: true }).click()
  await expect(form(page, `确认${name}`)).not.toBeVisible()
}

test.beforeEach(async ({ page }) => { await page.clock.setFixedTime(new Date('2026-11-15T02:00:00Z')); await page.goto(url) })

test('原菜单进入管理员目录，固定摘要与倒序分页、隐私及演示边界', async ({ page }) => {
  const requests: string[] = []
  page.on('request', request => { if (request.url().includes('/api/')) requests.push(request.url()) })
  await page.goto('/prototype/admin-home')
  await page.getByRole('navigation', { name: '管理员功能导航' }).getByRole('button', { name: '管理员账户', exact: true }).click()
  await expect(page.getByRole('heading', { name: '管理员账户工作台', exact: true })).toBeVisible()
  await expect(stat(page, '管理员总数')).toHaveText('14'); await expect(stat(page, '已启用')).toHaveText('10'); await expect(stat(page, '已停用')).toHaveText('4')
  await expect(state(page)).toHaveValue('ALL'); await expect(table(page).locator('tbody tr')).toHaveCount(10)
  await expect(table(page).locator('tbody tr').first()).toContainText('ADM-001')
  await expect(table(page).locator('tbody tr').first()).toContainText('当前账户')
  await expect(table(page)).toContainText('138 **** 0001'); await expect(table(page)).not.toContainText('13800000001')
  const times = await table(page).locator('time').evaluateAll(elements => elements.map(element => element.getAttribute('datetime')))
  expect(times).toEqual([...times].sort().reverse())
  await expect(page.getByLabel('管理员分页')).toContainText('1 / 2')
  await expect(page.getByRole('button', { name: '上一页', exact: true })).toBeDisabled()
  for (const label of [/删除/, /锁定登录/, /重置密码/, /医疗访问审核/]) await expect(page.locator('.admin-directory-workspace').getByRole('button', { name: label })).toHaveCount(0)
  expect(requests).toEqual([])
})

test('筛选支持姓名账号编号、空结果与重置，分页刷新保留，摘要不随筛选变化', async ({ page }) => {
  await page.getByRole('button', { name: '下一页', exact: true }).click()
  await expect(table(page).locator('tbody tr')).toHaveCount(4); await expect(page.getByLabel('管理员分页')).toContainText('2 / 2')
  await page.getByRole('button', { name: '刷新账户', exact: true }).click()
  await expect(page.getByLabel('管理员分页')).toContainText('2 / 2')
  for (const value of [' 林知远 ', '  ADMIN.STAFF02 ', 'adm-002']) {
    await search(page).fill(value); await expect(table(page).locator('tbody tr')).toHaveCount(1); await expect(table(page)).toContainText('ADM-002')
  }
  await expect(stat(page, '管理员总数')).toHaveText('14'); await expect(stat(page, '已启用')).toHaveText('10')
  await state(page).selectOption('DISABLED'); await expect(page.getByRole('heading', { name: '暂无符合条件的管理员' })).toBeVisible()
  await expect(page.getByRole('button', { name: '下一页', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '重置筛选', exact: true }).first().click()
  await expect(state(page)).toHaveValue('ALL'); await expect(search(page)).toHaveValue(''); await expect(page.getByLabel('管理员分页')).toContainText('1 / 2')
  await state(page).selectOption('DISABLED'); await expect(table(page).locator('tbody tr')).toHaveCount(4); await expect(stat(page, '已停用')).toHaveText('4')
})

test('创建归一化账号并清除筛选打开详情，创建记录不包含密码，重开表单已清空', async ({ page }) => {
  const requests: string[] = []
  page.on('request', request => { if (request.url().includes('/api/')) requests.push(request.url()) })
  await state(page).selectOption('DISABLED'); await search(page).fill('nothing')
  await createForm(page); await form(page).getByLabel('登录账号', { exact: true }).fill('  New.Admin+01@EXAMPLE.TEST  ')
  await form(page).getByRole('button', { name: '创建管理员', exact: true }).click()
  await expect(drawer(page)).toContainText('测试管理员'); await expect(drawer(page)).toContainText('new.admin+01@example.test')
  await expect(drawer(page)).toContainText('13900000099'); await expect(drawer(page)).toContainText('已启用')
  await expect(drawer(page).getByLabel('本页操作记录')).toContainText('新增管理员')
  await expect(drawer(page)).toContainText('2026-11-15 10:00:00')
  await expect(page.locator('.admin-directory-workspace')).not.toContainText(password)
  await expect(page.locator('input[type=password]')).toHaveCount(0)
  await drawer(page).getByRole('button', { name: '关闭账户详情', exact: true }).click()
  await expect(state(page)).toHaveValue('ALL'); await expect(search(page)).toHaveValue('')
  await expect(table(page).locator('tbody tr').first()).toContainText('ADM-015'); await expect(stat(page, '管理员总数')).toHaveText('15')
  await page.getByRole('button', { name: '新增管理员', exact: true }).click()
  await expect(form(page).getByLabel('演示初始密码', { exact: true })).toHaveValue('')
  await expect(form(page).getByLabel('确认演示初始密码', { exact: true })).toHaveValue('')
  expect(await page.evaluate(value => Object.values(localStorage).some(item => item.includes(value)), password)).toBe(false)
  expect(requests).toEqual([])
})

test('姓名手机号账号重复与格式、长度边界校验，错误留在表单', async ({ page }) => {
  await createForm(page)
  for (const [label, value, message] of [
    ['管理员姓名', ' ', '请填写管理员姓名'], ['管理员姓名', '名'.repeat(121), '最多120字'],
    ['手机号', '12800000001', '有效的中国大陆'], ['手机号', '1380000000', '有效的中国大陆'],
    ['登录账号', '  ADMIN.OPERATIONS ', '已存在'], ['登录账号', 'ab', '3–190'], ['登录账号', 'a'.repeat(191), '3–190'],
    ['登录账号', '.abc', '以字母或数字开头'], ['登录账号', 'ab cd', '3–190'], ['登录账号', '中文账号', '3–190'],
  ]) {
    const input = form(page).getByLabel(label!, { exact: true }), previous = await input.inputValue()
    await input.fill(value!); await form(page).getByRole('button', { name: '创建管理员', exact: true }).click()
    await expect(form(page).getByRole('alert')).toContainText(message!); await expect(input).toHaveValue(value!)
    await expect(stat(page, '管理员总数')).toHaveText('14'); await input.fill(previous)
  }
  await form(page).getByLabel('管理员姓名', { exact: true }).fill('名'.repeat(120)); await form(page).getByLabel('登录账号', { exact: true }).fill('a'.repeat(190))
  await form(page).getByRole('button', { name: '创建管理员', exact: true }).click()
  await expect(drawer(page)).toBeVisible(); await expect(drawer(page)).toContainText('a'.repeat(190))
})

test('密码至少12字符、UTF8最多72字节及两次确认，边界密码可以创建', async ({ page }) => {
  await createForm(page)
  for (const [value, confirmation, message] of [
    ['', '', '至少12个字符'], [' '.repeat(12), ' '.repeat(12), '不能为空'], ['a'.repeat(11), 'a'.repeat(11), '至少12个字符'],
    ['密'.repeat(25), '密'.repeat(25), '72字节'], ['a'.repeat(73), 'a'.repeat(73), '72字节'],
    [password, 'different-password', '不一致'],
  ]) {
    await form(page).getByLabel('演示初始密码', { exact: true }).fill(value!)
    await form(page).getByLabel('确认演示初始密码', { exact: true }).fill(confirmation!)
    await form(page).getByRole('button', { name: '创建管理员', exact: true }).click()
    await expect(form(page).getByRole('alert')).toContainText(message!)
  }
  for (const [index, valid] of ['a'.repeat(12), '密'.repeat(24), 'a'.repeat(72)].entries()) {
    if (index > 0) { await drawer(page).getByRole('button', { name: '关闭账户详情', exact: true }).click(); await createForm(page) }
    await form(page).getByLabel('登录账号', { exact: true }).fill(`password.test${index}`)
    await form(page).getByLabel('演示初始密码', { exact: true }).fill(valid)
    await form(page).getByLabel('确认演示初始密码', { exact: true }).fill(valid)
    await form(page).getByRole('button', { name: '创建管理员', exact: true }).click()
    await expect(drawer(page)).toBeVisible(); await expect(drawer(page).getByLabel('本页操作记录')).not.toContainText(valid)
  }
})

test('编辑资料账号只读，无变化不保存，成功后同步抽屉及保留筛选', async ({ page }) => {
  await search(page).fill('林知远'); await openAccount(page)
  await drawer(page).getByRole('button', { name: '编辑资料', exact: true }).click()
  const edit = form(page, '编辑管理员资料')
  await expect(edit.getByRole('button', { name: '保存资料', exact: true })).toBeDisabled()
  await expect(edit.getByLabel('登录账号', { exact: true })).toHaveAttribute('readonly')
  await expect(edit.locator('input[type=password]')).toHaveCount(0)
  await edit.getByLabel('管理员姓名', { exact: true }).fill(' 林知远 ')
  await expect(edit.getByRole('button', { name: '保存资料', exact: true })).toBeDisabled()
  await edit.getByLabel('管理员姓名', { exact: true }).fill('林知远更新')
  await edit.getByLabel('手机号', { exact: true }).fill('13800000099')
  await edit.getByRole('button', { name: '保存资料', exact: true }).click()
  await expect(drawer(page)).toContainText('林知远更新'); await expect(drawer(page)).toContainText('13800000099')
  await expect(drawer(page).getByLabel('本页操作记录')).toContainText('更新姓名、手机号')
  await page.keyboard.press('Escape'); await expect(search(page)).toHaveValue('林知远')
  await expect(table(page)).toContainText('林知远更新'); await expect(table(page)).toContainText('138 **** 0099')
})

test('启停确认前不落值，取消无记录，启停保留筛选并标记详情不在结果内', async ({ page }) => {
  await state(page).selectOption('ENABLED'); await search(page).fill('adm-002'); await openAccount(page)
  await drawer(page).getByRole('button', { name: '停用账户', exact: true }).click()
  await expect(form(page, '确认停用账户')).toContainText('admin.staff02')
  await expect(drawer(page)).toContainText('已启用')
  await form(page, '确认停用账户').getByRole('button', { name: '取消', exact: true }).click()
  await expect(drawer(page).getByLabel('本页操作记录')).toContainText('暂无本页操作记录')
  await drawer(page).getByRole('button', { name: '停用账户', exact: true }).click(); await confirmAction(page, '停用账户')
  await expect(drawer(page)).toContainText('已停用'); await expect(drawer(page)).toContainText('不在当前筛选结果中')
  await expect(drawer(page).getByLabel('本页操作记录')).toContainText('停用账户')
  await expect(stat(page, '已启用')).toHaveText('9')
  await drawer(page).getByRole('button', { name: '启用账户', exact: true }).click(); await confirmAction(page, '启用账户')
  await expect(drawer(page)).toContainText('已启用'); await expect(drawer(page)).not.toContainText('不在当前筛选结果中')
  await expect(drawer(page).getByLabel('本页操作记录').locator('article')).toHaveCount(2)
  await page.keyboard.press('Escape'); await expect(state(page)).toHaveValue('ENABLED'); await expect(search(page)).toHaveValue('adm-002')
})

test('当前账户禁止停用与登录重置，筛选不会误判其他管理员为最后一个', async ({ page }) => {
  await openAccount(page, 'ADM-001')
  await expect(drawer(page).getByRole('button', { name: '停用账户', exact: true })).toBeDisabled()
  await expect(drawer(page).getByRole('button', { name: '重置登录状态', exact: true })).toBeDisabled()
  await expect(drawer(page)).toContainText('不能停用当前登录账户'); await expect(drawer(page)).toContainText('不能重置当前登录账户')
  await page.keyboard.press('Escape'); await search(page).fill('adm-002'); await openAccount(page)
  await expect(drawer(page).getByRole('button', { name: '停用账户', exact: true })).toBeEnabled()
})

test('完整目录只剩一个启用管理员时禁止继续停用，保护不受筛选影响', async ({ page }) => {
  await state(page).selectOption('ENABLED')
  for (let index = 2; index <= 10; index++) {
    const id = `ADM-${String(index).padStart(3, '0')}`
    await search(page).fill(id)
    await table(page).getByRole('button', { name: `停用${id}`, exact: true }).click()
    await confirmAction(page, '停用账户')
  }
  await search(page).fill(''); await expect(stat(page, '已启用')).toHaveText('1')
  await expect(table(page).locator('tbody tr')).toHaveCount(1)
  await expect(table(page).getByRole('button', { name: '停用ADM-001', exact: true })).toBeDisabled()
  await expect(table(page)).toContainText('至少保留一个启用的管理员账户')
  await openAccount(page, 'ADM-001'); await expect(drawer(page)).toContainText('至少保留一个启用的管理员账户')
  await expect(drawer(page).getByRole('button', { name: '停用账户', exact: true })).toBeDisabled()
})

test('重置其他管理员登录需确认，取消无记录，确认不改状态密码资料', async ({ page }) => {
  await openAccount(page)
  await drawer(page).getByRole('button', { name: '重置登录状态', exact: true }).click()
  await expect(form(page, '确认重置登录状态')).toContainText('密码、启用状态和业务记录保持不变')
  await page.keyboard.press('Escape'); await expect(drawer(page).getByLabel('本页操作记录').locator('article')).toHaveCount(0)
  await drawer(page).getByRole('button', { name: '重置登录状态', exact: true }).click(); await confirmAction(page, '重置登录状态')
  await expect(drawer(page)).toContainText('已启用'); await expect(drawer(page)).toContainText('13800000002')
  await expect(drawer(page).getByLabel('本页操作记录')).toContainText('重置登录状态')
  await expect(drawer(page).getByRole('status')).toContainText('真实账户与登录未改变')
  await expect(drawer(page).getByLabel('本页操作记录')).toContainText('运营管理员 · 2026-11-15 10:00:00')
})

test('新增和编辑的取消关闭Esc遮罩确认放弃，继续保留输入，实际关闭清理密码并恢复焦点', async ({ page }) => {
  for (const method of ['cancel', 'close', 'escape', 'backdrop']) {
    await createForm(page)
    if (method === 'cancel') await form(page).getByRole('button', { name: '取消', exact: true }).click()
    if (method === 'close') await form(page).getByRole('button', { name: '关闭管理员弹窗', exact: true }).click()
    if (method === 'escape') await page.keyboard.press('Escape')
    if (method === 'backdrop') await page.mouse.click(5, 5)
    await expect(form(page, '放弃未保存修改')).toBeVisible()
    await form(page, '放弃未保存修改').getByRole('button', { name: '继续编辑', exact: true }).click()
    await expect(form(page).getByLabel('演示初始密码', { exact: true })).toHaveValue(password)
    await page.keyboard.press('Escape'); await form(page, '放弃未保存修改').getByRole('button', { name: '放弃修改', exact: true }).click()
    await expect(page.locator('input[type=password]')).toHaveCount(0)
    await expect(page.getByRole('button', { name: '新增管理员', exact: true })).toBeFocused()
  }
  await openAccount(page); await drawer(page).getByRole('button', { name: '编辑资料', exact: true }).click()
  await form(page, '编辑管理员资料').getByLabel('管理员姓名', { exact: true }).fill('未保存')
  await page.keyboard.press('Escape'); await form(page, '放弃未保存修改').getByRole('button', { name: '放弃修改', exact: true }).click()
  await expect(drawer(page).getByRole('heading', { name: '林知远', exact: true })).toBeVisible()
  await expect(drawer(page).getByRole('button', { name: '编辑资料', exact: true })).toBeFocused()
})

test('复制两种标识成功与失败反馈，刷新保留会话而重载重置', async ({ page, context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write'])
  await openAccount(page)
  await drawer(page).getByRole('button', { name: '复制账户编号', exact: true }).click()
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe('ADM-002')
  await drawer(page).getByRole('button', { name: '复制登录账号', exact: true }).click()
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe('admin.staff02')
  await page.evaluate(() => Object.defineProperty(navigator.clipboard, 'writeText', { configurable: true, value: () => Promise.reject(new Error('denied')) }))
  await drawer(page).getByRole('button', { name: '复制登录账号', exact: true }).click(); await expect(drawer(page).getByRole('status')).toContainText('请手动复制登录账号：admin.staff02')
  await drawer(page).getByRole('button', { name: '停用账户', exact: true }).click(); await confirmAction(page, '停用账户')
  await page.clock.setFixedTime(new Date('2026-11-15T02:05:00Z'))
  await drawer(page).getByRole('button', { name: '刷新账户', exact: true }).click()
  await expect(drawer(page)).toContainText('已停用'); await expect(drawer(page).getByLabel('本页操作记录').locator('article')).toHaveCount(1)
  await expect(drawer(page).getByLabel('本页操作记录')).toContainText('2026-11-15 10:00:00')
  await page.keyboard.press('Escape'); await expect(page.locator('.audit-footnote')).toContainText('2026-11-15 10:05:00')
  await page.reload(); await expect(stat(page, '已启用')).toHaveText('10'); await openAccount(page)
  await expect(drawer(page)).toContainText('已启用'); await expect(drawer(page).getByLabel('本页操作记录').locator('article')).toHaveCount(0)
})

test('键盘行与操作按钮独立可用，抽屉和二次弹窗焦点正确', async ({ page }) => {
  const row = table(page).locator('tbody tr').nth(1)
  await row.focus(); await row.press('Enter'); await expect(drawer(page)).toBeVisible()
  await page.keyboard.press('Escape'); await expect(row).toBeFocused()
  const edit = row.getByRole('button', { name: '编辑ADM-002资料', exact: true })
  await edit.focus(); await edit.press('Enter'); await expect(form(page, '编辑管理员资料')).toBeVisible()
  await page.keyboard.press('Escape'); await expect(edit).toBeFocused(); await expect(drawer(page)).not.toBeVisible()
  await row.focus(); await expect(row).toBeFocused(); await row.press('Space'); await expect(drawer(page)).toBeVisible()
  await drawer(page).getByRole('button', { name: '重置登录状态', exact: true }).click()
  await page.keyboard.press('Escape'); await expect(drawer(page).getByRole('button', { name: '重置登录状态', exact: true })).toBeFocused()
  await drawer(page).getByRole('button', { name: '关闭账户详情', exact: true }).click(); await expect(row).toBeFocused()
})

test('桌面手机无页面溢出，新增编辑确认抽屉的所有按钮样式正常，手机菜单选择后关闭', async ({ page }) => {
  for (const viewport of [{ width: 1440, height: 1000 }, { width: 375, height: 812 }]) {
    await page.setViewportSize(viewport)
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.getByRole('button', { name: '新增管理员', exact: true }).click()
    for (const button of await form(page).getByRole('button').all()) {
      await expect(button).toHaveCSS('display', /^(inline-)?flex$/)
      const box = await button.boundingBox(); expect(box!.height).toBeGreaterThanOrEqual(30); expect(box!.x).toBeGreaterThanOrEqual(0); expect(box!.x + box!.width).toBeLessThanOrEqual(viewport.width)
    }
    await page.keyboard.press('Escape'); await openAccount(page)
    for (const button of await drawer(page).getByRole('button').all()) {
      await expect(button).toHaveCSS('display', /^(inline-)?flex$/)
      const box = await button.boundingBox(); expect(box!.height).toBeGreaterThanOrEqual(30); expect(box!.x).toBeGreaterThanOrEqual(0); expect(box!.x + box!.width).toBeLessThanOrEqual(viewport.width)
    }
    await drawer(page).getByRole('button', { name: '编辑资料', exact: true }).click()
    await form(page, '编辑管理员资料').getByLabel('管理员姓名', { exact: true }).fill('林知远')
    await page.keyboard.press('Escape')
    await drawer(page).getByRole('button', { name: '重置登录状态', exact: true }).click(); await page.mouse.move(0, 0)
    await expect(form(page, '确认重置登录状态').getByRole('button', { name: '确认重置登录状态', exact: true })).toHaveCSS('background-color', 'rgb(52, 124, 219)')
    await page.keyboard.press('Escape'); await page.keyboard.press('Escape')
  }
  await page.getByRole('button', { name: '打开侧栏', exact: true }).click()
  await page.getByRole('navigation', { name: '管理员功能导航' }).getByRole('button', { name: '管理员账户', exact: true }).click()
  await expect(page.getByRole('button', { name: '关闭侧栏', exact: true })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '管理员账户工作台', exact: true })).toBeVisible()
})
