import { test, expect, type Page } from '@playwright/test'

const today = '2026-11-10', tomorrow = '2026-11-11'
const serverNow = Date.parse('2026-11-10T09:00:00+08:00')
const people = [
  { id: 'doctor-a', displayName: '陈予安', title: '主治医师', biography: '常见疾病与健康咨询', departmentId: 'general' },
  { id: 'doctor-b', displayName: '许知遥', title: '副主任医师', biography: '内科常见疾病', departmentId: 'internal' },
]
function schedule(id: string, doctor = people[0]!, date = today, mode = 'OFFLINE', session = 'AM', slots = 4) {
  const startsAt = Date.parse(`${date}T${session === 'AM' ? '08' : '14'}:00:00+08:00`)
  return { id, doctorId: doctor.id, doctorName: doctor.displayName, departmentId: doctor.departmentId,
    departmentName: doctor.departmentId === 'general' ? '全科医学科' : '内科', clinicDate: date, session,
    serviceMode: mode, startsAt, endsAt: startsAt + 14400000, zoneId: 'Asia/Shanghai', capacity: slots,
    feeMinor: 1200, state: 'PUBLISHED', availableSlots: slots, advanceAvailable: date !== today, walkInAvailable: date === today }
}
const resources = [schedule('today'), schedule('future', people[0], tomorrow), schedule('online', people[1], today, 'ONLINE'), schedule('afternoon', people[0], today, 'OFFLINE', 'PM', 2)]
async function fixture(page: Page) {
  await page.clock.setFixedTime(new Date(serverNow))
  const requests: URL[] = [], submissions: Record<string, string>[] = [], errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url()), path = url.pathname
    let json: unknown = []
    if (path.endsWith('/auth/refresh')) json = { accessToken: 'example', account: { id: 'patient', roles: ['PATIENT'], displayName: '林晓', identifier: 'patient' } }
    else if (path.endsWith('/auth/csrf')) json = { token: 'example' }
    else if (path.endsWith('/catalog/hospital')) json = { name: '大学校医院', address: '校园路1号', description: '门诊服务' }
    else if (path.endsWith('/catalog/departments')) json = [{ id: 'general', name: '全科医学科' }, { id: 'internal', name: '内科' }]
    else if (path.endsWith('/catalog/doctors')) json = people
    else if (path.endsWith('/catalog/schedules')) {
      requests.push(url)
      json = resources.filter(item => Object.entries({ date: item.clinicDate, mode: item.serviceMode, doctorId: item.doctorId,
        departmentId: item.departmentId, session: item.session }).every(([key, value]) => !url.searchParams.has(key) || url.searchParams.get(key) === value))
    } else if (path.endsWith('/patient/appointments') && route.request().method() === 'POST') {
      submissions.push(route.request().postDataJSON())
      json = { id: 'hold', state: 'PENDING_PAYMENT', serverNow, holdExpiresAt: serverNow + 600000 }
    } else if (path.endsWith('/notifications/unread')) json = 0
    else if (path.endsWith('/notifications/stream')) { await route.abort(); return }
    await route.fulfill({ json })
  })
  return { requests, submissions, errors }
}

test('默认筛选、日期快捷键、科室医生联动、URL前进后退和刷新恢复', async ({ page }) => {
  const state = await fixture(page)
  await page.goto('/patient/catalog')
  await expect(page.getByRole('heading', { name: '立即挂号', exact: true })).toBeVisible()
  await expect(page.getByLabel('选择日期')).toHaveValue(today)
  await expect(page.getByRole('list', { name: '号源列表' }).getByRole('listitem')).toHaveCount(2)
  await page.getByRole('button', { name: /明天/ }).click()
  await expect(page.getByLabel('选择日期')).toHaveValue(tomorrow)
  await expect(page).toHaveURL(/date=2026-11-11/)
  await page.getByLabel('医生', { exact: true }).selectOption('doctor-a')
  await expect(page.getByLabel('科室', { exact: true })).toHaveValue('general')
  await expect(page).toHaveURL(/doctorId=doctor-a/)
  await page.reload()
  await expect(page.getByLabel('医生', { exact: true })).toHaveValue('doctor-a')
  await page.getByRole('button', { name: '重置', exact: true }).first().click()
  await expect(page.getByLabel('选择日期')).toHaveValue(today)
  await expect(page.getByLabel('医生', { exact: true })).toHaveValue('')
  await expect(page).not.toHaveURL(/doctorId/)
  await page.goBack()
  await expect(page.getByLabel('医生', { exact: true })).toHaveValue('doctor-a')
  await expect(page.getByLabel('选择日期')).toHaveValue(tomorrow)
  await page.goForward()
  await expect(page.getByLabel('选择日期')).toHaveValue(today)
  await page.getByRole('button', { name: '下午', exact: true }).click()
  await expect(page.getByRole('list', { name: '号源列表' }).getByRole('listitem')).toHaveCount(1)
  expect(state.errors).toEqual([])
})

test('当日及未来来源、确认抽屉、服务端倒计时、待付款跳转和键盘焦点', async ({ page }) => {
  const state = await fixture(page)
  await page.goto('/patient/catalog')
  const action = page.getByRole('button', { name: '当日挂号', exact: true }).first()
  await action.click()
  await expect(page.getByRole('dialog')).toContainText('同一日期同一半天只能有一条有效登记')
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(action).toBeFocused()
  await action.click()
  await page.clock.setFixedTime(new Date('2030-01-01T09:00:00+08:00'))
  await page.getByRole('button', { name: '确认挂号', exact: true }).click()
  await expect(page.getByRole('dialog')).toContainText('已提交，待付款')
  await expect(page.locator('.hold-countdown strong')).toHaveText(/^(10:00|09:5\d)$/)
  expect(state.submissions[0]?.source).toBe('WALK_IN')
  expect(state.submissions[0]!.idempotencyKey.length).toBeLessThanOrEqual(80)
  await page.getByRole('button', { name: '继续浏览' }).click()
  await page.getByRole('button', { name: /明天/ }).click()
  await page.getByRole('button', { name: '预约挂号', exact: true }).click()
  await page.getByRole('button', { name: '确认挂号', exact: true }).click()
  await expect(page.getByRole('dialog')).toContainText('已提交，待付款')
  expect(state.submissions[1]?.source).toBe('ADVANCE')
  await page.getByRole('button', { name: '去我的挂号', exact: true }).click()
  await expect(page).toHaveURL(/\/patient\/orders\?state=PENDING_PAYMENT/)
  await expect(page.getByText('当前显示待付款挂号', { exact: false })).toBeVisible()
  expect(state.errors).toEqual([])
})

test('无号源和半天重复登记保留条件，旧请求不覆盖新筛选', async ({ page }) => {
  await fixture(page)
  await page.goto('/patient/catalog')
  await expect(page.getByRole('list', { name: '号源列表' })).toBeVisible()
  let release: () => void = () => {}, entered: () => void = () => {}
  const blocked = new Promise<void>(resolve => { release = resolve })
  const started = new Promise<void>(resolve => { entered = resolve })
  await page.route('**/api/v1/catalog/schedules?**', async route => {
    if (new URL(route.request().url()).searchParams.get('mode') !== 'ONLINE') { await route.fallback(); return }
    entered(); await blocked
    await route.fulfill({ json: [resources[2]] })
  })
  await page.getByRole('button', { name: '线上问诊', exact: true }).click()
  await started
  await page.getByRole('button', { name: '线下挂号', exact: true }).click()
  await expect(page.getByRole('list', { name: '号源列表' })).toContainText('陈予安')
  release()
  await expect(page.getByRole('list', { name: '号源列表' })).not.toContainText('许知遥')
  await page.getByLabel('选择日期').fill('2026-11-12')
  await expect(page.getByText('当前筛选暂无可用号源')).toBeVisible()
  await page.getByRole('button', { name: '重置', exact: true }).first().click()
  await page.route('**/api/v1/patient/appointments', route => route.fulfill({ status: 409, json: { error: { message: '该半天已有有效登记' } } }))
  await page.getByRole('button', { name: '当日挂号', exact: true }).first().click()
  await page.getByRole('button', { name: '确认挂号', exact: true }).click()
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('该半天已有有效登记')
  await expect(page.getByRole('dialog').getByRole('link', { name: '查看我的挂号' })).toBeVisible()
})

test('网络失败可重试，售罄保留筛选，提交重试使用同一幂等键', async ({ page }) => {
  const state = await fixture(page)
  await page.goto('/patient/catalog?date=2026-11-11&mode=OFFLINE&departmentId=general')
  let attempts = 0
  const keys: string[] = []
  await page.route('**/api/v1/patient/appointments', async route => {
    keys.push(route.request().postDataJSON().idempotencyKey)
    if (++attempts === 1) await route.fulfill({ status: 409, json: { error: { message: '号源已用完' } } })
    else await route.fulfill({ json: { id: 'hold', state: 'PENDING_PAYMENT', serverNow, holdExpiresAt: serverNow + 600000 } })
  })
  await page.getByRole('button', { name: '预约挂号', exact: true }).click()
  await page.getByRole('button', { name: '确认挂号', exact: true }).click()
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('该排班刚刚售罄')
  await expect(page).toHaveURL(/departmentId=general/)
  await page.getByRole('button', { name: '确认挂号', exact: true }).click()
  await expect(page.getByRole('dialog')).toContainText('已提交，待付款')
  expect(keys[0]).toBe(keys[1])
  await page.getByRole('button', { name: '继续浏览' }).click()
  await page.route('**/api/v1/catalog/schedules?**', route => route.abort())
  await page.getByRole('button', { name: /今天/ }).click()
  await expect(page.getByRole('alert')).toContainText('号源加载失败，请重试')
  await page.unroute('**/api/v1/catalog/schedules?**')
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.getByRole('list', { name: '号源列表' })).toBeVisible()
  await page.route('**/api/v1/patient/appointments', route => route.fulfill({ json: { id: 'expired-hold', state: 'EXPIRED', serverNow, holdExpiresAt: serverNow - 1 } }))
  await page.getByRole('button', { name: '当日挂号', exact: true }).first().click()
  await page.getByRole('button', { name: '确认挂号', exact: true }).click()
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('该登记状态已变化')
  await expect(page.getByRole('heading', { name: '已提交，待付款' })).toHaveCount(0)
  expect(state.errors).toEqual([])
})

test('桌面列对齐，375px筛选折叠与号源卡片无横向溢出', async ({ page }) => {
  await fixture(page)
  await page.setViewportSize({ width: 1600, height: 1000 })
  await page.goto('/patient/catalog')
  await expect(page.getByRole('list', { name: '号源列表' })).toBeVisible()
  const left = await page.locator('.registration-filters').boundingBox(), right = await page.locator('.registration-results').boundingBox()
  expect(Math.abs(left!.y - right!.y)).toBeLessThan(2)
  expect(right!.x - left!.x - left!.width).toBeLessThanOrEqual(24)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
  await page.screenshot({ path: '../.runtime/registration-desktop.png', fullPage: true })
  await page.setViewportSize({ width: 375, height: 812 })
  await expect(page.locator('.registration-filters')).toBeHidden()
  await page.getByRole('button', { name: '展开筛选' }).click()
  await expect(page.locator('.registration-filters')).toBeVisible()
  await page.getByRole('button', { name: '线上问诊', exact: true }).click()
  await expect(page.getByRole('list', { name: '号源列表' })).toContainText('许知遥')
  await page.getByRole('button', { name: '收起筛选' }).click()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
  await page.screenshot({ path: '../.runtime/registration-mobile.png', fullPage: true })
})
