import { test, expect, type Page } from '@playwright/test'

const url = '/prototype/admin-home?module=AI 配置'
const card = (page: Page, name: string) => page.getByRole('article', { name, exact: true })
const editor = (page: Page, name: string) => page.getByRole('dialog', { name: `编辑${name}`, exact: true })
const confirm = (page: Page) => page.getByRole('dialog', { name: '确认 AI 配置变更', exact: true })
async function edit(page: Page, name: string) { await card(page, name).getByRole('button', { name: `编辑${name}` }).click(); await expect(editor(page, name)).toBeVisible() }
async function save(page: Page, name: string) {
  await editor(page, name).getByRole('button', { name: '下一步：确认变更' }).click()
  await expect(confirm(page)).toBeVisible()
  await confirm(page).getByRole('button', { name: '确认保存', exact: true }).click()
  await expect(confirm(page)).not.toBeVisible()
}
async function setExternal(page: Page) {
  const form = editor(page, '接口与模型')
  await form.getByLabel('运行模式', { exact: true }).selectOption('external')
  await form.getByLabel('HTTPS 接口地址', { exact: true }).fill('https://ai.example.invalid/v1')
  await form.getByLabel('模型名称', { exact: true }).fill('demo-model')
  await form.getByRole('button', { name: '设置演示密钥', exact: true }).click()
}

test.beforeEach(async ({ page }) => { await page.clock.setFixedTime(new Date('2026-11-14T02:00:00Z')); await page.goto(url) })

test('现有导航进入五组演示配置，默认值与隐私边界明确，无配置请求', async ({ page }) => {
  const requests: string[] = []
  page.on('request', request => { if (request.url().includes('/api/') || !request.url().startsWith(new URL(page.url()).origin)) requests.push(request.url()) })
  await page.goto('/prototype/admin-home')
  await page.getByRole('navigation', { name: '管理员功能导航' }).getByRole('button', { name: 'AI 配置', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'AI 配置工作台' })).toBeVisible()
  await expect(page.getByLabel('AI 总开关', { exact: true })).toContainText('已启用')
  await expect(page.getByLabel('AI 配置分组').getByRole('article')).toHaveCount(4)
  await expect(card(page, '患者导诊')).toContainText('10 轮')
  await expect(card(page, '患者导诊')).toContainText('不诊断、不开药')
  await expect(card(page, '医生症状摘要')).toContainText('1000 字')
  await expect(card(page, '医生症状摘要')).toContainText('不能扩大医疗材料授权范围')
  await expect(card(page, '接口与模型')).toContainText('2048 token')
  await expect(card(page, '接口与模型')).toContainText('0.2')
  await expect(card(page, '记录策略')).toContainText('30 天')
  await expect(card(page, '记录策略').getByRole('link', { name: '查看调用记录' })).toHaveAttribute('href', '/admin/ai')
  await expect(page.locator('.ai-config-workspace input')).toHaveCount(0)
  await expect(page.getByLabel('本次演示修改记录')).toContainText('暂无修改记录')
  expect(requests).toEqual([])
})

test('总开关启停必须确认，取消不落值，关闭后保留子项并允许配置', async ({ page }) => {
  await edit(page, '患者导诊'); await editor(page, '患者导诊').getByLabel('最大对话轮次', { exact: true }).fill('15'); await save(page, '患者导诊')
  await page.getByRole('button', { name: '停用 AI', exact: true }).click()
  await expect(confirm(page)).toContainText('在途请求返回也不再展示新结果')
  await expect(page.getByLabel('AI 总开关', { exact: true })).toContainText('已启用')
  await confirm(page).getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.getByLabel('AI 总开关', { exact: true })).toContainText('已启用')
  await page.getByRole('button', { name: '停用 AI', exact: true }).click()
  await confirm(page).getByRole('button', { name: '确认保存', exact: true }).click()
  await expect(page.getByLabel('AI 总开关', { exact: true })).toContainText('已停用')
  await expect(card(page, '患者导诊')).toContainText('15 轮')
  await edit(page, '患者导诊'); await editor(page, '患者导诊').getByLabel('图片上传', { exact: true }).selectOption('on'); await save(page, '患者导诊')
  await page.getByRole('button', { name: '启用 AI', exact: true }).click()
  await expect(confirm(page)).toBeVisible()
  await confirm(page).getByRole('button', { name: '确认保存', exact: true }).click()
  await expect(page.getByLabel('AI 总开关', { exact: true })).toContainText('已启用')
  await expect(card(page, '患者导诊')).toContainText('15 轮')
  await expect(page.getByLabel('本次演示修改记录').locator('details')).toHaveCount(4)
})

test('逐组编辑，无变更禁用，对比前不保存，返回修改保留草稿', async ({ page }) => {
  await edit(page, '患者导诊')
  const form = editor(page, '患者导诊')
  await expect(form.getByRole('button', { name: '下一步：确认变更' })).toBeDisabled()
  await form.getByLabel('最大对话轮次', { exact: true }).fill('010')
  await expect(form.getByRole('button', { name: '下一步：确认变更' })).toBeDisabled()
  await form.getByLabel('回答边界提示词', { exact: true }).fill('仅回答医院业务和科室建议。')
  await form.getByLabel('超出范围的兜底文案', { exact: true }).fill('请咨询负责医生。')
  await form.getByLabel('最大对话轮次', { exact: true }).fill('20')
  await form.getByRole('button', { name: '下一步：确认变更' }).click()
  await expect(confirm(page).getByLabel('AI 配置变更对比')).toContainText('10 轮')
  await expect(confirm(page).getByLabel('AI 配置变更对比')).toContainText('20 轮')
  await expect(card(page, '患者导诊')).toContainText('10 轮')
  await confirm(page).getByRole('button', { name: '返回修改' }).click()
  await expect(form.getByLabel('最大对话轮次', { exact: true })).toHaveValue('20')
  await save(page, '患者导诊')
  await expect(card(page, '患者导诊')).toContainText('20 轮')
  await expect(card(page, '医生症状摘要')).toContainText('1000 字')
  await expect(page.getByRole('status')).toContainText('真实业务配置未修改')
})

test('全部子组可编辑，记录前后值，医院时间准确，刷新保留而重载重置', async ({ page }) => {
  for (const [name, field, value] of [['患者导诊', '最大对话轮次', '12'], ['医生症状摘要', '最大摘要长度', '1200'], ['接口与模型', '最大输出 token', '4096'], ['记录策略', '元数据保留天数', '60']]) {
    await edit(page, name!); await editor(page, name!).getByLabel(field!, { exact: true }).fill(value!); await save(page, name!)
  }
  await edit(page, '医生症状摘要')
  await editor(page, '医生症状摘要').getByLabel('自动生成摘要', { exact: true }).selectOption('off')
  await editor(page, '医生症状摘要').getByLabel('多模态解析', { exact: true }).selectOption('on')
  await editor(page, '医生症状摘要').getByLabel('摘要模板', { exact: true }).fill('只整理已授权症状，不生成诊断。')
  await save(page, '医生症状摘要')
  await edit(page, '记录策略'); await editor(page, '记录策略').getByLabel('保存调用元数据', { exact: true }).selectOption('off'); await save(page, '记录策略')
  const history = page.getByLabel('本次演示修改记录')
  await expect(history.locator('details')).toHaveCount(6)
  await history.locator('summary').first().click()
  await expect(history.locator('details').first()).toContainText('开启')
  await expect(history.locator('details').first()).toContainText('关闭')
  await expect(page.getByLabel('最近修改')).toContainText('运营管理员 · 2026-11-14 10:00:00')
  await page.clock.setFixedTime(new Date('2026-11-14T02:05:00Z'))
  await page.getByRole('button', { name: '刷新配置', exact: true }).click()
  await expect(card(page, '记录策略')).toContainText('60 天')
  await expect(history.locator('details')).toHaveCount(6)
  await expect(page.getByLabel('最近修改')).toContainText('10:00:00')
  await expect(page.locator('.rules-footer')).toContainText('2026-11-14 10:05:00')
  await page.reload()
  await expect(card(page, '记录策略')).toContainText('30 天')
  await expect(history.locator('details')).toHaveCount(0)
})

test('整数及小数范围拒绝空白、非数字、小数轮次及指数写法，边界可保存', async ({ page }) => {
  for (const [name, label, invalid, min, max] of [
    ['患者导诊', '最大对话轮次', ['', '0', '51', '1.5', 'abc', '1e1'], '1', '50'],
    ['医生症状摘要', '最大摘要长度', ['', '99', '5001', '-1'], '100', '5000'],
    ['接口与模型', '最大输出 token', ['', '127', '32769', '2048.5'], '128', '32768'],
    ['记录策略', '元数据保留天数', ['', '0', '366', '1e2'], '1', '365'],
    ['接口与模型', 'temperature', ['', '-0.1', '1.01', 'NaN', '1e0'], '0', '1'],
  ] as const) {
    await edit(page, name)
    const form = editor(page, name)
    for (const value of invalid) {
      await form.getByLabel(label, { exact: true }).fill(value)
      await form.getByRole('button', { name: '下一步：确认变更' }).click()
      await expect(form.getByRole('alert')).toBeVisible()
      await expect(form.getByLabel(label, { exact: true })).toHaveValue(value)
    }
    await form.getByLabel(label, { exact: true }).fill(min); await save(page, name)
    await edit(page, name); await editor(page, name).getByLabel(label, { exact: true }).fill(max); await save(page, name)
  }
  await edit(page, '接口与模型')
  await editor(page, '接口与模型').getByLabel('temperature', { exact: true }).fill('00.0000000100')
  await save(page, '接口与模型')
  await edit(page, '接口与模型')
  await expect(editor(page, '接口与模型').getByLabel('temperature', { exact: true })).toHaveValue('0.00000001')
  await editor(page, '接口与模型').getByLabel('最大输出 token', { exact: true }).fill('4096')
  await save(page, '接口与模型')
})

test('提示词模板兜底必填限长，错误留表单，4000及500字边界可保存', async ({ page }) => {
  for (const [name, label, max] of [['患者导诊', '回答边界提示词', 4000], ['患者导诊', '超出范围的兜底文案', 500], ['医生症状摘要', '摘要模板', 4000]] as const) {
    await edit(page, name)
    const form = editor(page, name)
    for (const [value, error] of [['  ', '请填写'], ['文'.repeat(max + 1), `最多 ${max} 字`]]) {
      await form.getByLabel(label, { exact: true }).fill(value!)
      await form.getByRole('button', { name: '下一步：确认变更' }).click()
      await expect(form.getByRole('alert')).toContainText(error!)
    }
    await form.getByLabel(label, { exact: true }).fill('文'.repeat(max)); await save(page, name)
  }
})

test('外部模式须地址模型密钥齐全，HTTPS及长度校验，不接受地址内的凭据', async ({ page }) => {
  await edit(page, '接口与模型'); const form = editor(page, '接口与模型')
  await form.getByLabel('运行模式', { exact: true }).selectOption('external')
  await form.getByRole('button', { name: '下一步：确认变更' }).click()
  await expect(form.getByRole('alert')).toContainText('须填写')
  for (const value of ['http://ai.example.invalid', 'not-url', 'https://user:secret@example.invalid', 'https://example.invalid?key=secret', 'https://example.invalid#secret', 'https://' + 'a'.repeat(501)]) {
    await form.getByLabel('HTTPS 接口地址', { exact: true }).fill(value)
    await form.getByRole('button', { name: '下一步：确认变更' }).click()
    await expect(form.getByRole('alert')).toBeVisible()
  }
  await form.getByLabel('HTTPS 接口地址', { exact: true }).fill('https://ai.example.invalid/v1')
  await form.getByLabel('模型名称', { exact: true }).fill('m'.repeat(101))
  await form.getByRole('button', { name: '下一步：确认变更' }).click()
  await expect(form.getByRole('alert')).toContainText('最多 100 字')
  await form.getByLabel('模型名称', { exact: true }).fill('demo-model')
  await form.getByRole('button', { name: '下一步：确认变更' }).click()
  await expect(form.getByRole('alert')).toContainText('演示密钥')
  await form.getByRole('button', { name: '设置演示密钥', exact: true }).click()
  await expect(form.locator('input[type=password]')).toHaveCount(0)
  await save(page, '接口与模型')
  await expect(card(page, '接口与模型')).toContainText('外部模型（演示）')
  await expect(card(page, '接口与模型')).toContainText('已配置（演示）')
})

test('切回本地保留外部配置，清除密钥需确认且未保存清除可取消', async ({ page }) => {
  await edit(page, '接口与模型'); await setExternal(page); await save(page, '接口与模型')
  await edit(page, '接口与模型'); let form = editor(page, '接口与模型')
  await form.getByLabel('运行模式', { exact: true }).selectOption('local'); await save(page, '接口与模型')
  await expect(card(page, '接口与模型')).toContainText('demo-model')
  await expect(card(page, '接口与模型')).toContainText('已配置（演示）')
  await edit(page, '接口与模型'); form = editor(page, '接口与模型')
  await form.getByRole('button', { name: '清除', exact: true }).click()
  await form.getByRole('button', { name: '取消', exact: true }).click()
  await page.getByRole('button', { name: '放弃修改', exact: true }).click()
  await expect(card(page, '接口与模型')).toContainText('已配置（演示）')
  await edit(page, '接口与模型'); await editor(page, '接口与模型').getByRole('button', { name: '清除', exact: true }).click(); await save(page, '接口与模型')
  await expect(card(page, '接口与模型')).toContainText('未配置')
  await page.getByLabel('本次演示修改记录').locator('summary').first().click()
  await expect(page.getByLabel('本次演示修改记录').locator('details').first()).toContainText('未配置')
  expect(await page.evaluate(() => Object.keys(localStorage).some(key => /ai.*config|secret|keyConfigured/i.test(key)))).toBe(false)
})

test('模拟成功失败超时，切换清空旧结果，不发外部请求、不改变模式或阻止保存', async ({ page }) => {
  await edit(page, '接口与模型'); await setExternal(page); await save(page, '接口与模型')
  const requests: string[] = []
  page.on('request', request => { if (request.url().includes('/api/') || request.url().includes('example.invalid')) requests.push(request.url()) })
  await page.getByRole('button', { name: '测试配置', exact: true }).click()
  const testForm = page.getByRole('dialog', { name: '测试 AI 配置', exact: true })
  for (const [value, text] of [['success', '模拟成功'], ['failure', '模拟连接失败'], ['timeout', '模拟超时']]) {
    await testForm.getByLabel('模拟场景', { exact: true }).selectOption(value!)
    await expect(testForm.getByRole('status')).toHaveCount(0)
    await testForm.getByRole('button', { name: '运行模拟测试', exact: true }).click()
    await expect(testForm.getByRole('status')).toContainText(text!)
    await expect(card(page, '接口与模型')).toContainText('外部模型（演示）')
  }
  await testForm.getByRole('button', { name: '关闭', exact: true }).click()
  await expect(page.getByRole('button', { name: '测试配置', exact: true })).toBeFocused()
  await edit(page, '接口与模型'); await editor(page, '接口与模型').getByLabel('最大输出 token', { exact: true }).fill('4096'); await save(page, '接口与模型')
  await page.getByRole('button', { name: '测试配置', exact: true }).click(); await expect(testForm.getByRole('status')).toHaveCount(0)
  await page.keyboard.press('Escape'); expect(requests).toEqual([])
})

test('取消关闭Esc遮罩保护草稿，继续编辑保留数据，放弃恢复焦点且不生成记录', async ({ page }) => {
  for (const method of ['cancel', 'close', 'escape', 'backdrop']) {
    await edit(page, '患者导诊'); const form = editor(page, '患者导诊')
    await form.getByLabel('最大对话轮次', { exact: true }).fill('20')
    if (method === 'cancel') await form.getByRole('button', { name: '取消', exact: true }).click()
    if (method === 'close') await form.getByRole('button', { name: '关闭配置弹窗', exact: true }).click()
    if (method === 'escape') await page.keyboard.press('Escape')
    if (method === 'backdrop') await page.mouse.click(5, 5)
    const discard = page.getByRole('dialog', { name: '放弃未保存修改', exact: true })
    await expect(discard).toBeVisible(); await discard.getByRole('button', { name: '继续编辑', exact: true }).click()
    await expect(form.getByLabel('最大对话轮次', { exact: true })).toHaveValue('20')
    await page.keyboard.press('Escape'); await discard.getByRole('button', { name: '放弃修改', exact: true }).click()
    await expect(card(page, '患者导诊').getByRole('button')).toBeFocused()
    await expect(card(page, '患者导诊')).toContainText('10 轮')
  }
  await expect(page.getByLabel('本次演示修改记录').locator('details')).toHaveCount(0)
  await edit(page, '患者导诊'); await page.keyboard.press('Escape'); await expect(editor(page, '患者导诊')).not.toBeVisible()
})

test('确认页Esc不落值，键盘提交确认保存后恢复焦点', async ({ page }) => {
  await edit(page, '记录策略')
  await editor(page, '记录策略').getByLabel('元数据保留天数', { exact: true }).fill('90')
  await editor(page, '记录策略').getByLabel('元数据保留天数', { exact: true }).press('Enter')
  await expect(confirm(page)).toBeVisible(); await page.keyboard.press('Escape')
  await page.getByRole('button', { name: '继续编辑', exact: true }).click()
  await expect(confirm(page)).toBeVisible()
  await expect(card(page, '记录策略')).toContainText('30 天')
  await confirm(page).getByRole('button', { name: '确认保存', exact: true }).click()
  await expect(card(page, '记录策略')).toContainText('90 天')
  await expect(card(page, '记录策略').getByRole('button')).toBeFocused()
})

test('桌面手机卡片及全部弹窗按钮渲染正确，无横向溢出，菜单选择后关闭', async ({ page }) => {
  for (const viewport of [{ width: 1440, height: 1000 }, { width: 375, height: 812 }]) {
    await page.setViewportSize(viewport)
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    for (const name of ['患者导诊', '医生症状摘要', '接口与模型', '记录策略']) {
      await edit(page, name)
      const form = editor(page, name)
      for (const button of await form.getByRole('button').all()) {
        await expect(button).toHaveCSS('display', /^(inline-)?flex$/)
        const box = await button.boundingBox()
        expect(box!.height).toBeGreaterThanOrEqual(30)
        expect(box!.x).toBeGreaterThanOrEqual(0)
        expect(box!.x + box!.width).toBeLessThanOrEqual(viewport.width)
      }
      await expect.poll(() => form.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true)
      await page.keyboard.press('Escape')
    }
    await edit(page, '记录策略'); await editor(page, '记录策略').getByLabel('元数据保留天数', { exact: true }).fill('31')
    await page.mouse.move(0, 0)
    await expect(editor(page, '记录策略').getByRole('button', { name: '下一步：确认变更' })).toHaveCSS('background-color', 'rgb(52, 124, 219)')
    await editor(page, '记录策略').getByRole('button', { name: '下一步：确认变更' }).click(); await page.mouse.move(0, 0)
    await expect(confirm(page).getByRole('button', { name: '确认保存', exact: true })).toHaveCSS('background-color', 'rgb(52, 124, 219)')
    await page.keyboard.press('Escape'); await page.getByRole('button', { name: '放弃修改', exact: true }).click()
    await page.getByRole('button', { name: '测试配置', exact: true }).click()
    const testForm = page.getByRole('dialog', { name: '测试 AI 配置', exact: true })
    await page.mouse.move(0, 0)
    await expect(testForm.getByRole('button', { name: '运行模拟测试', exact: true })).toHaveCSS('background-color', 'rgb(52, 124, 219)')
    await page.keyboard.press('Escape')
  }
  await page.getByRole('button', { name: '打开侧栏', exact: true }).click()
  await page.getByRole('navigation', { name: '管理员功能导航' }).getByRole('button', { name: 'AI 配置', exact: true }).click()
  await expect(page.getByRole('button', { name: '关闭侧栏', exact: true })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: 'AI 配置工作台' })).toBeVisible()
})
