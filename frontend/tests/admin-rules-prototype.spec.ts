import { test, expect, type Page } from '@playwright/test'

const url = '/prototype/admin-home?module=全局规则'
const card = (page: Page, name: string) => page.getByRole('article', { name, exact: true })
const editor = (page: Page, name: string) => page.getByRole('dialog', { name: `编辑${name}`, exact: true })
const confirmation = (page: Page) => page.getByRole('dialog', { name: '确认规则变更', exact: true })
async function edit(page: Page, name: string) {
  await card(page, name).getByRole('button', { name: `编辑${name}` }).click()
  await expect(editor(page, name)).toBeVisible()
}
async function save(page: Page, name: string) {
  await editor(page, name).getByRole('button', { name: '下一步：确认变更' }).click()
  await expect(confirmation(page)).toBeVisible()
  await confirmation(page).getByRole('button', { name: '确认保存', exact: true }).click()
  await expect(confirmation(page)).not.toBeVisible()
}

test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-11-14T02:00:00Z'))
  await page.goto(url)
})

test('原菜单进入5组12项规则，当前值与默认业务规则一致，明确演示边界', async ({ page }) => {
  const requests: string[] = []
  page.on('request', request => { if (request.url().includes('/api/')) requests.push(request.url()) })
  await page.goto('/prototype/admin-home')
  await page.getByRole('navigation', { name: '管理员功能导航' }).getByRole('button', { name: '全局规则', exact: true }).click()
  await expect(page.getByRole('heading', { name: '全局规则工作台' })).toBeVisible()
  await expect(page.getByText('虚构演示数据', { exact: true })).toBeVisible()
  await expect(page.getByLabel('全局规则分组').getByRole('article')).toHaveCount(5)
  await expect(page.locator('.rules-card dt')).toHaveCount(12)
  for (const [group, values] of [
    ['门诊时段', ['08:00', '12:00', '14:00', '18:00']],
    ['签到与候诊', ['30 分钟', '15 分钟', '5 分钟']],
    ['审批时限', ['7 天', '24 小时']],
    ['药费支付', ['24 小时']],
    ['默认挂号费', ['10.00 元', '20.00 元']],
  ] as const) {
    await expect(card(page, group).locator('dd')).toHaveText([...values])
  }
  await expect(card(page, '签到与候诊')).toContainText('07:30 至 11:45（不含截止时刻）')
  await expect(page.getByLabel('规则生效说明')).toContainText('不改变真实业务')
  await expect(page.getByLabel('最近修改')).toContainText('尚未修改')
  await expect(page.getByLabel('本次演示修改记录')).toContainText('暂无修改记录')
  await expect(page.locator('.rules-workspace input')).toHaveCount(0)
  expect(requests).toEqual([])
})

test('无变更禁用提交，对比前不落值，可返回修改，确认后只更新本组', async ({ page }) => {
  await edit(page, '药费支付')
  await expect(editor(page, '药费支付').getByRole('button', { name: '下一步：确认变更' })).toBeDisabled()
  await editor(page, '药费支付').getByLabel('药费待缴有效期').fill('48')
  await editor(page, '药费支付').getByRole('button', { name: '下一步：确认变更' }).click()
  await expect(confirmation(page).getByLabel('规则变更对比')).toContainText('24 小时')
  await expect(confirmation(page).getByLabel('规则变更对比')).toContainText('48 小时')
  await expect(card(page, '药费支付').locator('dd')).toHaveText('24 小时')
  await confirmation(page).getByRole('button', { name: '返回修改' }).click()
  await expect(editor(page, '药费支付').getByLabel('药费待缴有效期')).toHaveValue('48')
  await editor(page, '药费支付').getByLabel('药费待缴有效期').fill('36')
  await save(page, '药费支付')
  await expect(card(page, '药费支付').locator('dd')).toHaveText('36 小时')
  await expect(card(page, '审批时限')).toContainText('24 小时')
  await expect(page.getByRole('status')).toContainText('真实业务配置未修改')
})

test('五组均能保存并按新到旧记录实际变更，刷新保留而重载重置', async ({ page }) => {
  for (const [name, label, value] of [
    ['门诊时段', '上午开始', '08:30'],
    ['签到与候诊', '线上叫号无应答等待', '6'],
    ['审批时限', '停诊审批最长时限', '8'],
    ['药费支付', '药费待缴有效期', '36'],
    ['默认挂号费', '普通医生默认挂号费', '12.35'],
  ]) {
    await edit(page, name!)
    await editor(page, name!).getByLabel(label!).fill(value!)
    await save(page, name!)
  }
  const history = page.getByLabel('本次演示修改记录')
  await expect(history.locator('details')).toHaveCount(5)
  await history.locator('summary').first().click()
  await expect(history.locator('details').first()).toContainText('10.00 元')
  await expect(history.locator('details').first()).toContainText('12.35 元')
  await expect(page.getByLabel('最近修改')).toContainText('运营管理员 · 2026-11-14 10:00:00')
  await page.clock.setFixedTime(new Date('2026-11-14T02:05:00Z'))
  await page.getByRole('button', { name: '刷新规则', exact: true }).click()
  await expect(page.getByLabel('最近修改')).toContainText('2026-11-14 10:00:00')
  await expect(page.locator('.rules-footer')).toContainText('2026-11-14 10:05:00')
  await expect(card(page, '默认挂号费')).toContainText('12.35 元')
  await expect(history.locator('details')).toHaveCount(5)
  await expect(page.getByRole('status')).toContainText('保留本页已确认保存')
  await page.reload()
  await expect(card(page, '默认挂号费')).toContainText('10.00 元')
  await expect(page.getByLabel('本次演示修改记录').locator('details')).toHaveCount(0)
})

test('整数必填与范围校验保留草稿，错误不能进入确认或修改当前值', async ({ page }) => {
  await edit(page, '药费支付')
  const form = editor(page, '药费支付')
  for (const [value, message] of [['', '请填写'], ['1.5', '须为整数'], ['abc', '须为整数'], ['0', '1–720小时'], ['721', '1–720小时'], ['1e2', '须为整数'], ['-1', '须为整数']]) {
    await form.getByLabel('药费待缴有效期').fill(value!)
    await form.getByRole('button', { name: '下一步：确认变更' }).click()
    await expect(form.getByRole('alert')).toContainText(message!)
    await expect(form.getByLabel('药费待缴有效期')).toHaveValue(value!)
    await expect(card(page, '药费支付').locator('dd')).toHaveText('24 小时')
  }
  await form.getByLabel('药费待缴有效期').fill('720')
  await save(page, '药费支付')
  await expect(card(page, '药费支付').locator('dd')).toHaveText('720 小时')
  await edit(page, '药费支付')
  await editor(page, '药费支付').getByLabel('药费待缴有效期').fill('1')
  await save(page, '药费支付')
  await expect(card(page, '药费支付').locator('dd')).toHaveText('1 小时')
})

test('金额校验两位小数和范围，整数分保存，允许普通费高于专家费', async ({ page }) => {
  await edit(page, '默认挂号费')
  const form = editor(page, '默认挂号费')
  for (const [value, message] of [['1.001', '最多两位小数'], ['-1', '非负金额'], ['1000000.01', '0–1000000元'], ['1e3', '非负金额']]) {
    await form.getByLabel('普通医生默认挂号费').fill(value!)
    await form.getByRole('button', { name: '下一步：确认变更' }).click()
    await expect(form.getByRole('alert')).toContainText(message!)
  }
  await form.getByLabel('普通医生默认挂号费').fill('30.29')
  await form.getByLabel('专家医生默认挂号费').fill('0')
  await save(page, '默认挂号费')
  await expect(card(page, '默认挂号费')).toContainText('30.29 元')
  await expect(card(page, '默认挂号费')).toContainText('0.00 元')
  await edit(page, '默认挂号费')
  await expect(editor(page, '默认挂号费').getByLabel('普通医生默认挂号费')).toHaveValue('30.29')
  await editor(page, '默认挂号费').getByLabel('普通医生默认挂号费').fill('030.29')
  await expect(editor(page, '默认挂号费').getByRole('button', { name: '下一步：确认变更' })).toBeDisabled()
  await editor(page, '默认挂号费').getByLabel('普通医生默认挂号费').fill('1000000')
  await save(page, '默认挂号费')
  await expect(card(page, '默认挂号费')).toContainText('1000000.00 元')
})

test('时段必须有序且不重叠，允许午间无间隔', async ({ page }) => {
  await edit(page, '门诊时段')
  const form = editor(page, '门诊时段')
  await form.getByLabel('上午开始').fill('')
  await form.getByRole('button', { name: '下一步：确认变更' }).click()
  await expect(form.getByRole('alert')).toContainText('请填写上午开始')
  await form.getByLabel('上午开始').fill('12:00')
  await form.getByRole('button', { name: '下一步：确认变更' }).click()
  await expect(form.getByRole('alert')).toContainText('上午开始＜上午结束')
  await form.getByLabel('上午开始').fill('08:00')
  await form.getByLabel('下午开始').fill('11:30')
  await form.getByRole('button', { name: '下一步：确认变更' }).click()
  await expect(form.getByRole('alert')).toContainText('上午结束≤下午开始')
  await form.getByLabel('下午开始').fill('12:00')
  await save(page, '门诊时段')
  await expect(card(page, '门诊时段').locator('dd')).toHaveText(['08:00', '12:00', '12:00', '18:00'])
})

test('缩短时段的跨组冲突明确提示，先改签到后可保存且不自动改其他组', async ({ page }) => {
  await edit(page, '门诊时段')
  await editor(page, '门诊时段').getByLabel('上午结束').fill('08:10')
  await editor(page, '门诊时段').getByRole('button', { name: '下一步：确认变更' }).click()
  await expect(editor(page, '门诊时段').getByRole('alert')).toContainText('请先在“签到与候诊”调小')
  await expect(card(page, '签到与候诊')).toContainText('15 分钟')
  await editor(page, '门诊时段').getByRole('button', { name: '取消', exact: true }).click()
  await page.getByRole('button', { name: '放弃修改', exact: true }).click()
  await edit(page, '签到与候诊')
  await editor(page, '签到与候诊').getByLabel('结束前截止签到').fill('0')
  await save(page, '签到与候诊')
  await edit(page, '门诊时段')
  await editor(page, '门诊时段').getByLabel('上午结束').fill('08:10')
  await save(page, '门诊时段')
  await expect(card(page, '门诊时段')).toContainText('08:10')
  await expect(card(page, '签到与候诊')).toContainText('至 08:10（不含截止时刻）')
})

test('签到值校验0边界与较短门诊时长，跨日窗口示例正确', async ({ page }) => {
  await edit(page, '签到与候诊')
  const form = editor(page, '签到与候诊')
  for (const [label, value, message] of [['开诊前可提前签到', '1441', '0–1440分钟'], ['线上叫号无应答等待', '241', '1–240分钟'], ['结束前截止签到', '240', '必须小于上午和下午']]) {
    await form.getByLabel(label!).fill(value!)
    await form.getByRole('button', { name: '下一步：确认变更' }).click()
    await expect(form.getByRole('alert')).toContainText(message!)
    await form.getByLabel(label!).fill(label === '开诊前可提前签到' ? '30' : label === '线上叫号无应答等待' ? '5' : '15')
  }
  await form.getByLabel('开诊前可提前签到').fill('1440')
  await form.getByLabel('结束前截止签到').fill('239')
  await form.getByLabel('线上叫号无应答等待').fill('240')
  await save(page, '签到与候诊')
  await expect(card(page, '签到与候诊')).toContainText('前一天 08:00 至 08:01')
})

test('审批天数小时各自校验，不添加不存在的比值限制', async ({ page }) => {
  await edit(page, '审批时限')
  const form = editor(page, '审批时限')
  await form.getByLabel('停诊审批最长时限').fill('366')
  await form.getByRole('button', { name: '下一步：确认变更' }).click()
  await expect(form.getByRole('alert')).toContainText('1–365天')
  await form.getByLabel('停诊审批最长时限').fill('1')
  await form.getByLabel('开诊前审批硬截止').fill('8761')
  await form.getByRole('button', { name: '下一步：确认变更' }).click()
  await expect(form.getByRole('alert')).toContainText('1–8760小时')
  await form.getByLabel('开诊前审批硬截止').fill('8760')
  await save(page, '审批时限')
  await expect(card(page, '审批时限')).toContainText('1 天')
  await expect(card(page, '审批时限')).toContainText('8760 小时')
})

test('关闭、取消、Esc和遮罩统一确认放弃，继续编辑保留草稿并恢复原焦点', async ({ page }) => {
  const opener = card(page, '药费支付').getByRole('button')
  await edit(page, '药费支付')
  await page.keyboard.press('Escape')
  await expect(editor(page, '药费支付')).not.toBeVisible()
  await expect(opener).toBeFocused()
  for (const method of ['close', 'cancel', 'escape', 'backdrop']) {
    await edit(page, '药费支付')
    await editor(page, '药费支付').getByLabel('药费待缴有效期').fill('48')
    if (method === 'close') await editor(page, '药费支付').getByRole('button', { name: '关闭规则弹窗' }).click()
    if (method === 'cancel') await editor(page, '药费支付').getByRole('button', { name: '取消', exact: true }).click()
    if (method === 'escape') await page.keyboard.press('Escape')
    if (method === 'backdrop') await page.mouse.click(5, 5)
    const discard = page.getByRole('dialog', { name: '放弃未保存修改' })
    await expect(discard).toBeVisible()
    await discard.getByRole('button', { name: '继续编辑' }).click()
    await expect(editor(page, '药费支付').getByLabel('药费待缴有效期')).toHaveValue('48')
    await page.keyboard.press('Escape')
    await page.getByRole('button', { name: '放弃修改', exact: true }).click()
    await expect(card(page, '药费支付').locator('dd')).toHaveText('24 小时')
    await expect(page.getByLabel('本次演示修改记录').locator('details')).toHaveCount(0)
    await expect(opener).toBeFocused()
  }
})

test('确认阶段关闭不保存，可继续返回修改，键盘提交后焦点恢复', async ({ page }) => {
  await edit(page, '药费支付')
  await editor(page, '药费支付').getByLabel('药费待缴有效期').fill('48')
  await editor(page, '药费支付').getByLabel('药费待缴有效期').press('Enter')
  await expect(confirmation(page)).toBeVisible()
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: '继续编辑' }).click()
  await expect(confirmation(page)).toBeVisible()
  await confirmation(page).getByRole('button', { name: '返回修改' }).click()
  await expect(editor(page, '药费支付').getByLabel('药费待缴有效期')).toHaveValue('48')
  await save(page, '药费支付')
  await expect(card(page, '药费支付').getByRole('button')).toBeFocused()
})

test('桌面和手机无溢出，编辑确认按钮样式正确，手机菜单选择后关闭', async ({ page }) => {
  for (const viewport of [{ width: 1440, height: 1000 }, { width: 375, height: 812 }]) {
    await page.setViewportSize(viewport)
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await edit(page, '默认挂号费')
    const form = editor(page, '默认挂号费')
    await form.getByLabel('普通医生默认挂号费').fill('15.99')
    for (const button of await form.getByRole('button').all()) {
      await expect(button).toHaveCSS('display', /^(inline-)?flex$/)
      const box = await button.boundingBox()
      expect(box!.height).toBeGreaterThanOrEqual(30)
      expect(box!.x).toBeGreaterThanOrEqual(0)
      expect(box!.x + box!.width).toBeLessThanOrEqual(viewport.width)
    }
    await page.mouse.move(0, 0)
    await expect(form.getByRole('button', { name: '下一步：确认变更' })).toHaveCSS('background-color', 'rgb(52, 124, 219)')
    await form.getByRole('button', { name: '下一步：确认变更' }).click()
    await page.mouse.move(0, 0)
    await expect(confirmation(page).getByRole('button', { name: '确认保存' })).toHaveCSS('background-color', 'rgb(52, 124, 219)')
    await page.keyboard.press('Escape')
    await page.getByRole('button', { name: '放弃修改' }).click()
  }
  await page.getByRole('button', { name: '打开侧栏' }).click()
  await page.getByRole('navigation', { name: '管理员功能导航' }).getByRole('button', { name: '全局规则', exact: true }).click()
  await expect(page.getByRole('button', { name: '关闭侧栏', exact: true })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '全局规则工作台' })).toBeVisible()
})
