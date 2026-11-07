import { test, expect, type Page, type Browser } from '@playwright/test'

const password = 'fictional-browser-password'
interface Agent { page: Page; token: string; id: string; identifier: string }
async function call(agent: Agent, path: string, method = 'GET', data?: unknown) {
  const csrf = method === 'GET' ? '' : (await agent.page.request.get('/api/v1/auth/csrf').then(response => response.json())).token as string
  const response = await agent.page.request.fetch('/api/v1' + path, { method, data,
    headers: { ...(agent.token ? { Authorization: `Bearer ${agent.token}` } : {}), ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}) } })
  expect(response.ok(), `${method} ${path}: ${await response.text()}`).toBeTruthy()
  return response.status() === 204 || !(await response.text()) ? null : response.json()
}
async function agent(browser: Browser, role: 'PATIENT' | 'DOCTOR' | 'ADMIN'): Promise<Agent> {
  const page = await browser.newPage({ baseURL: test.info().project.use.baseURL }), identifier = role === 'ADMIN' ? 'admin-foundation-test' : `workflow-${crypto.randomUUID()}`
  const result: Agent = { page, token: '', id: '', identifier }
  if (role !== 'ADMIN') result.id = (await call(result, '/auth/register', 'POST', { identifier, password, role })).account.id as string
  const challenge = await call(result, '/auth/captcha')
  const tokens = await call(result, '/auth/login', 'POST', { identifier, password: role === 'ADMIN' ? 'fictional-test-administrator-password' : password, challengeId: challenge.challengeId, captcha: 'AB7K' })
  result.token = tokens.accessToken as string; result.id = tokens.account.id as string
  return result
}

test('患者与医生双向图文接诊、结束只读并追加更正', async ({ browser }) => {
  test.setTimeout(180000)
  const admin = await agent(browser, 'ADMIN'), doctor = await agent(browser, 'DOCTOR'), patient = await agent(browser, 'PATIENT')
  try {
    const department = await call(admin, '/admin/catalog/departments', 'POST', { name: `临床演示-${crypto.randomUUID().slice(0, 8)}`, description: '虚构材料', active: true })
    await call(admin, `/admin/catalog/doctors/${doctor.id}`, 'PUT', { displayName: '虚构接诊医生', title: '主治医师', biography: '虚构资料', departmentId: department.id, credentialStatus: 'CERTIFIED' })
    const schedule = await call(admin, '/admin/catalog/schedules', 'POST', { doctorId: doctor.id, departmentId: department.id, clinicDate: new Date(Date.now() + 2 * 86400000).toISOString().slice(0, 10), session: 'AM', serviceMode: 'ONLINE', capacity: 1, feeMinor: 1000 })
    const appointment = await call(patient, '/patient/appointments', 'POST', { scheduleId: schedule.id, source: 'ADVANCE', idempotencyKey: crypto.randomUUID() })
    await call(patient, `/patient/appointments/${appointment.id}/simulate-payment`, 'POST')
    await call(admin, '/admin/test-clock', 'POST', { millis: schedule.startsAt as number })
    await patient.page.goto('/patient/orders')
    await patient.page.getByRole('button', { name: '签到', exact: true }).click()
    await expect(patient.page.getByText('签到与候诊：候诊中 · 预约优先')).toBeVisible()
    await patient.page.getByRole('link', { name: '问诊材料与结果' }).click()
    await patient.page.getByLabel('症状文本', { exact: true }).fill('虚构患者症状')
    await patient.page.getByLabel('共享医疗附件').setInputFiles({ name: 'fictional.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7\nfictional') })
    await patient.page.getByRole('button', { name: '提交并确认共享' }).click()
    await expect(patient.page.getByText('虚构患者症状', { exact: true })).toBeVisible()
    await doctor.page.goto('/doctor/reception')
    await doctor.page.getByRole('button', { name: '叫号', exact: true }).click()
    await doctor.page.getByRole('button', { name: '开始接诊', exact: true }).click()
    await doctor.page.getByRole('link', { name: '打开问诊记录' }).click()
    await expect(doctor.page.getByText('虚构患者症状', { exact: true })).toBeVisible()
    await patient.page.getByLabel('图文消息', { exact: true }).fill('患者发送的虚构问题')
    await patient.page.getByRole('button', { name: '发送消息', exact: true }).click()
    await expect(patient.page.getByText(/患者：\s*患者发送的虚构问题/)).toBeVisible()
    await doctor.page.getByRole('button', { name: '刷新问诊记录' }).click()
    await expect(doctor.page.getByText(/患者：\s*患者发送的虚构问题/)).toBeVisible()
    await doctor.page.getByLabel('回复患者').fill('医生发送的虚构回复')
    await doctor.page.getByRole('button', { name: '发送回复' }).click()
    await expect(doctor.page.getByText(/医生：\s*医生发送的虚构回复/)).toBeVisible()
    await patient.page.getByRole('button', { name: '刷新问诊记录' }).click()
    await expect(patient.page.getByText(/医生：\s*医生发送的虚构回复/)).toBeVisible()
    await doctor.page.getByLabel('问诊结果/面诊登记').fill('虚构只读结果')
    await doctor.page.getByRole('button', { name: '提交结果并结束接诊' }).click()
    await expect(doctor.page.getByText('当前结果：虚构只读结果')).toBeVisible()
    await expect(doctor.page.getByText(`作者：${doctor.id} · 版本：1`, { exact: false })).toBeVisible()
    await doctor.page.getByLabel('更正原因').fill('虚构更正原因')
    await doctor.page.getByLabel('更正内容').fill('虚构追加说明')
    await doctor.page.getByRole('button', { name: '追加更正', exact: true }).click()
    await expect(doctor.page.getByText('虚构更正原因：虚构追加说明')).toBeVisible()
    await patient.page.getByRole('button', { name: '刷新问诊记录' }).click()
    await expect(patient.page.getByText('问诊状态：已结束（只读）')).toBeVisible()
    await expect(patient.page.getByText('虚构只读结果', { exact: true })).toBeVisible()
    await expect(patient.page.getByText('虚构更正原因：虚构追加说明')).toBeVisible()
    await expect(patient.page.getByRole('button', { name: '发送消息', exact: true })).toHaveCount(0)
    await expect(patient.page.getByRole('button', { name: '提交并确认共享' })).toHaveCount(0)
  } finally {
    await call(admin, '/admin/test-clock', 'POST', {})
    await Promise.all([admin.page.context().close(), doctor.page.context().close(), patient.page.context().close()])
  }
})
