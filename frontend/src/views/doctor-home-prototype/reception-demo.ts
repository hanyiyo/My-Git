// Development-only fictional workflow. All tabs on the same origin share this snapshot.
import { computed, readonly, ref } from 'vue'
import { patients, type QueuePatient } from './data'
import { initialSchedules, initialApplications, isPendingApplication, scheduleStart, suspensionBlock, scheduleNumbers, type DemoSchedule, type SuspensionApplication } from './schedule-data'

export type VisitStatus = 'PENDING_VISIT' | 'WAITING' | 'IN_PROGRESS' | 'MISSED' | 'COMPLETED' | 'EXPIRED' | 'CANCELLED'
export interface ClinicalDraft { complaint: string; present: string; history: string; diagnosis: string; advice: string; medicines: { medicineId: string; quantity: number; usage: string }[] }
export interface DemoMessage { id: string; sender: 'patient' | 'doctor'; text: string; at: number }
export interface MedicineLine { medicineId: string; name: string; specification: string; price: number; quantity: number; usage: string }
export interface DemoVisit extends QueuePatient {
  owner: string; status: VisitStatus; checkedAt: number | null; restored: boolean; calledAt: number | null; answered: boolean
  messages: DemoMessage[]; authorizedIds: string[]; historyAuthorized: boolean
  summary: { text: string; messageIds: string[]; at: number; error: boolean } | null
  draft: ClinicalDraft; draftVersion: number; savedAt: number | null
  result: { clinical: ClinicalDraft; medicines: MedicineLine[]; at: number; author: string } | null
  payment: { status: 'PENDING' | 'PAID' | 'EXPIRED'; total: number; expiresAt: number } | null
}
export interface DemoMedicine { id: string; name: string; specification: string; price: number; stock: number; held: number; enabled: boolean }
interface DemoState { version: 1; revision: number; clockOffset: number; visits: DemoVisit[]; medicines: DemoMedicine[]; aiFailure: boolean; schedules: DemoSchedule[]; applications: SuspensionApplication[] }
export const receptionKey = 'medical-reception-prototype-v1'
export const blankDraft = (): ClinicalDraft => ({ complaint: '', present: '', history: '', diagnosis: '', advice: '', medicines: [] })
const copy = <T>(value: T): T => JSON.parse(JSON.stringify(value))
const dateAt = (time: string) => Date.parse(`2026-11-12T${time}:00+08:00`)
export const sessionBounds = (group: 'am' | 'pm') => ({ start: dateAt(group === 'am' ? '08:00' : '14:00'), end: dateAt(group === 'am' ? '12:00' : '18:00') })
export const formatTime = (at: number) => new Date(at).toLocaleTimeString('zh-CN', { timeZone: 'Asia/Shanghai', hour: '2-digit', minute: '2-digit' })
export const statusText: Record<VisitStatus, string> = { PENDING_VISIT: '待签到', WAITING: '候诊中', IN_PROGRESS: '就诊中', MISSED: '已过号', COMPLETED: '已完成', EXPIRED: '已作废', CANCELLED: '已取消' }
export const money = (cents: number) => `¥${(cents / 100).toFixed(2)}`

function newVisit(patient: QueuePatient, status: VisitStatus): DemoVisit {
  return { ...patient, owner: patient.name === '林知夏' ? 'lin' : patient.id, status, checkedAt: status === 'WAITING' ? dateAt(patient.checkin) : null,
    restored: false, calledAt: null, answered: false, messages: [], authorizedIds: [], historyAuthorized: false, summary: null,
    draft: blankDraft(), draftVersion: 0, savedAt: null, result: null, payment: null }
}
function initialState(): DemoState {
  const schedules = initialSchedules(), applications = initialApplications(schedules)
  return { version: 1, revision: 0, clockOffset: dateAt('10:20') - Date.now(), aiFailure: false, schedules, applications,
    visits: [...patients.map(patient => newVisit(patient, 'WAITING')),
      newVisit({ id: 'today-online-waiting', number: 'P001', name: '林知夏', age: 21, sex: '女', source: '预约挂号', symptom: '咽部不适，希望线上咨询', checkin: '', group: 'pm' }, 'PENDING_VISIT'),
      newVisit({ id: 'online-002', number: 'P002', name: '陈亦安', age: 22, sex: '男', source: '当日挂号', symptom: '餐后胃部不适', checkin: '', group: 'pm' }, 'PENDING_VISIT')],
    medicines: [
      { id: 'med-1', name: '演示药品 A', specification: '10片 / 盒', price: 1250, stock: 20, held: 0, enabled: true },
      { id: 'med-2', name: '演示药品 B', specification: '100ml / 瓶', price: 1860, stock: 8, held: 0, enabled: true },
      { id: 'med-3', name: '演示药品 C', specification: '12粒 / 盒', price: 900, stock: 0, held: 0, enabled: true },
      { id: 'med-4', name: '演示药品 D', specification: '20片 / 盒', price: 1600, stock: 10, held: 0, enabled: false },
    ] }
}
const warning = ref(''), error = ref(''), busy = ref(false)
function readState(): DemoState {
  const raw = localStorage.getItem(receptionKey)
  if (!raw) return initialState()
  const data = JSON.parse(raw) as DemoState
  if (data.version !== 1 || !Number.isFinite(data.clockOffset) || !Number.isInteger(data.revision) || !Array.isArray(data.visits) || !Array.isArray(data.medicines)
    || initialState().visits.some(v => !data.visits.some(item => item.id === v.id))
    || data.visits.some(v => !statusText[v.status] || !v.draft || !Array.isArray(v.draft.medicines) || !Array.isArray(v.messages) || !Array.isArray(v.authorizedIds))
    || data.medicines.some(m => !Number.isInteger(m.stock) || m.stock < 0 || !Number.isInteger(m.held) || m.held < 0)) throw new Error('演示进度损坏')
  // Extend the existing demo snapshot without discarding conversations, drafts or payments.
  if (data.schedules === undefined && data.applications === undefined) { data.schedules = initialSchedules(); data.applications = initialApplications(data.schedules) }
  if (!Array.isArray(data.schedules) || !Array.isArray(data.applications)
    || initialSchedules().some(s => !data.schedules.some(item => item.id === s.id))
    || data.schedules.some(s => !['PUBLISHED', 'SUSPENDED', 'SUBSTITUTED'].includes(s.status) || !Number.isInteger(s.capacity) || !Number.isInteger(s.paid) || !Number.isInteger(s.held) || s.held < 0 || s.paid < 0 || s.capacity < s.paid + s.held)
    || data.applications.some(a => !data.schedules.some(s => s.id === a.scheduleId) || !['PENDING_HEAD', 'PENDING_ADMIN', 'REJECTED', 'EXPIRED', 'APPROVED'].includes(a.status) || typeof a.reason !== 'string' || !Number.isFinite(a.deadline) || !Number.isFinite(a.submittedAt))) throw new Error('排班演示进度损坏')
  return data
}
let loaded: DemoState
try { loaded = readState() } catch { loaded = initialState(); warning.value = '演示进度无法读取，请在演示设置中确认重置后继续。' }
const state = ref(loaded), wallTime = ref(Date.now())
const now = computed(() => wallTime.value + state.value.clockOffset)
const active = computed(() => state.value.visits.find(v => v.status === 'IN_PROGRESS'))

function queue(data: DemoState, group: 'am' | 'pm') {
  return data.visits.filter(v => v.group === group && v.status === 'WAITING').sort((a, b) => Number(a.source === '当日挂号') - Number(b.source === '当日挂号') || (a.checkedAt! - b.checkedAt!))
}
export const waitingQueue = (group: 'am' | 'pm') => queue(state.value, group)
function visitById(data: DemoState, id: string) {
  const visit = data.visits.find(v => v.id === id)
  if (!visit) throw new Error('未找到本次挂号。')
  return visit
}
function expire(data: DemoState, time: number) {
  for (const application of data.applications) {
    if (isPendingApplication(application) && time > application.deadline) { application.status = 'EXPIRED'; application.note = '未在审批期限内完成全部审批，申请已过期，原出诊安排保留。' }
  }
  for (const visit of data.visits) {
    if (visit.status === 'PENDING_VISIT' && time >= sessionBounds(visit.group).end - 15 * 60000) visit.status = 'EXPIRED'
    if (visit.payment?.status === 'PENDING' && time >= visit.payment.expiresAt) {
      visit.payment.status = 'EXPIRED'
      for (const line of visit.result!.medicines) data.medicines.find(m => m.id === line.medicineId)!.held -= line.quantity
    }
  }
}
// Web Locks serialize read-modify-write across doctor/patient tabs (including concurrent messages/payment).
async function change(action: (data: DemoState, time: number) => void, reset = false): Promise<boolean> {
  if (!import.meta.env.DEV) return false
  busy.value = true; error.value = ''
  try {
    if (!navigator.locks) throw new Error('当前浏览器不支持联动演示，请使用新版 Chrome 或 Edge。')
    await navigator.locks.request(receptionKey, () => {
      if (warning.value && !reset) throw new Error(warning.value)
      const current = reset ? initialState() : readState()
      const time = Date.now() + current.clockOffset
      const previous = JSON.stringify(current)
      expire(current, time)
      let next = copy(current), rejected: unknown
      try { action(next, time) } catch (cause) { rejected = cause; next = current }
      if (rejected && previous === JSON.stringify(current)) throw rejected
      next.revision++
      localStorage.setItem(receptionKey, JSON.stringify(next))
      state.value = next; wallTime.value = Date.now(); warning.value = ''
      if (rejected) throw rejected
    })
    return true
  } catch (cause) { error.value = cause instanceof DOMException ? '浏览器无法保存演示进度，当前操作未保存。请允许浏览器存储或释放空间后重试。' : cause instanceof Error ? cause.message : '操作失败，进度未保存。'; return false }
  finally { busy.value = false }
}
function regenerate(data: DemoState, visit: DemoVisit, time: number) {
  const allowed = visit.messages.filter(m => visit.authorizedIds.includes(m.id))
  if (!allowed.length) { visit.summary = null; return }
  if (data.aiFailure) {
    if (visit.summary) visit.summary.error = true
    return
  }
  visit.summary = { text: allowed.map(m => `${m.sender === 'patient' ? '患者自述' : '医生询问'}：${m.text}`).join('\n'), messageIds: allowed.map(m => m.id), at: time, error: false }
}
export function useReceptionDemo() {
  return { state: computed(() => state.value), now, active, warning: readonly(warning), error: readonly(error), busy: readonly(busy) }
}
export function clearReceptionError() { error.value = '' }
export const resetReception = () => change(() => {}, true)
export const setDemoTime = (time: string) => change(data => {
  if (!/^\d{2}:\d{2}$/.test(time) || !Number.isFinite(dateAt(time))) throw new Error('请输入有效演示时间。')
  data.clockOffset = dateAt(time) - Date.now(); expire(data, dateAt(time))
})
export const advanceDemo = (minutes: number) => change(data => { data.clockOffset += minutes * 60000; expire(data, Date.now() + data.clockOffset) })
export const setScheduleDemoDateTime = (date: string, time: string) => change(data => {
  const at = Date.parse(`${date}T${time}:00+08:00`)
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !/^\d{2}:\d{2}$/.test(time) || !Number.isFinite(at)) throw new Error('请输入有效演示日期和时间。')
  data.clockOffset = at - Date.now(); expire(data, at)
})
export const submitSuspension = (scheduleId: string, reason: string, role: 'doctor' | 'head') => change((data, time) => {
  const schedule = data.schedules.find(s => s.id === scheduleId)
  if (!schedule) throw new Error('出诊安排不存在，请返回我的排班重新选择。')
  const blocked = suspensionBlock(schedule, data.applications, time)
  if (blocked) throw new Error(blocked)
  if (!reason.trim() || reason.trim().length > 500) throw new Error('请填写 1–500 字的停诊原因。')
  data.applications.push({ id: crypto.randomUUID(), scheduleId, reason: reason.trim(), submittedAt: time,
    deadline: Math.min(time + 7 * 24 * 3600000, scheduleStart(schedule) - 24 * 3600000),
    affected: scheduleNumbers(schedule, data.visits).registered, status: role === 'head' ? 'PENDING_ADMIN' : 'PENDING_HEAD', applicantRole: role })
  schedule.held = 0
})
export const setAiFailure = (failure: boolean) => change(data => { data.aiFailure = failure })
export const adjustStock = (id: string, stock: number) => change(data => {
  const medicine = data.medicines.find(m => m.id === id)!
  if (!Number.isInteger(stock) || stock < medicine.held) throw new Error('库存不能小于已锁定数量。')
  medicine.stock = stock
})
export const canCheckIn = (v: Pick<DemoVisit, 'group' | 'status'>, time: number) => v.status === 'PENDING_VISIT' && time >= sessionBounds(v.group).start - 30 * 60000 && time < sessionBounds(v.group).end - 15 * 60000
export const checkInVisit = (id: string) => change((data, time) => {
  const visit = visitById(data, id)
  if (visit.status === 'WAITING') return
  if (!canCheckIn(visit, time)) throw new Error('当前不在签到窗口内，或挂号状态已改变。')
  visit.status = 'WAITING'; visit.checkedAt = time; visit.checkin = formatTime(time)
})
export const callVisit = (id: string) => change((data, time) => {
  const visit = visitById(data, id)
  const unfinished = data.visits.find(v => v.status === 'IN_PROGRESS')
  if (unfinished) throw new Error('请先继续并完成当前接诊。')
  if (queue(data, visit.group)[0]?.id !== id) throw new Error('请按队列顺序叫号。')
  if (time < sessionBounds(visit.group).start) throw new Error('本场出诊尚未开始。')
  visit.status = 'IN_PROGRESS'; visit.calledAt = time; visit.answered = false
})
export const canMiss = (visit: Pick<DemoVisit, 'group' | 'status' | 'answered' | 'calledAt'>, time: number) => visit.status === 'IN_PROGRESS' && (visit.group === 'am' || (!visit.answered && visit.calledAt !== null && time - visit.calledAt >= 5 * 60000))
export const missVisit = (id: string) => change((data, time) => {
  const visit = visitById(data, id)
  if (!canMiss(visit, time)) throw new Error('线上叫号后须等待满 5 分钟且患者未应答，才可标记过号。')
  visit.status = visit.restored ? 'EXPIRED' : 'MISSED'
})
export const canRestore = (v: Pick<DemoVisit, 'status' | 'restored' | 'group'>, time: number) => v.status === 'MISSED' && !v.restored && time >= sessionBounds(v.group).start && time < sessionBounds(v.group).end
export const restoreVisit = (id: string) => change((data, time) => {
  const visit = visitById(data, id)
  if (!canRestore(visit, time)) throw new Error('本次门诊时段已经结束，或已用过恢复机会，无法恢复排队。')
  visit.status = 'WAITING'; visit.restored = true; visit.checkedAt = time
})
export const cancelVisit = (id: string) => change((data, time) => {
  const visit = visitById(data, id)
  if (!['PENDING_VISIT', 'WAITING', 'MISSED'].includes(visit.status) || time >= sessionBounds(visit.group).start) throw new Error('开诊后不能自行取消，请联系医院处理。')
  visit.status = 'CANCELLED'
})
export const sendVisitMessage = (id: string, sender: 'patient' | 'doctor', text: string) => change((data, time) => {
  const visit = visitById(data, id), trimmed = text.trim()
  if (visit.group !== 'pm' || !trimmed || trimmed.length > 2000) throw new Error('请输入 1–2000 字的文字消息。')
  if (!(visit.status === 'IN_PROGRESS' || (sender === 'patient' && visit.status === 'WAITING'))) throw new Error('当前诊室只读，或尚未签到。')
  visit.messages.push({ id: crypto.randomUUID(), sender, text: trimmed, at: time })
  if (sender === 'patient' && visit.status === 'IN_PROGRESS') visit.answered = true
  // New messages are outside the old explicit consent scope until the patient authorizes them.
  regenerate(data, visit, time)
})
export const authorizeMessages = (id: string, enabled: boolean, messageIds?: string[]) => change((data, time) => {
  const visit = visitById(data, id)
  visit.authorizedIds = enabled ? visit.messages.filter(m => !messageIds || messageIds.includes(m.id)).map(m => m.id) : []
  regenerate(data, visit, time)
})
export const authorizeHistory = (id: string, enabled: boolean) => change(data => { visitById(data, id).historyAuthorized = enabled })
export const refreshSummary = (id: string) => change((data, time) => { regenerate(data, visitById(data, id), time) })
export const saveClinicalDraft = (id: string, draft: ClinicalDraft, version: number) => change((data, time) => {
  const visit = visitById(data, id)
  if (visit.status !== 'IN_PROGRESS') throw new Error('本次接诊已不在编辑状态，草稿未写入。')
  if (visit.draftVersion !== version) throw new Error('另一标签页已更新草稿，请保留当前内容并重新载入最新草稿。')
  visit.draft = copy(draft); visit.savedAt = time; visit.draftVersion++
})
export const submitClinical = (id: string, version: number) => change((data, time) => {
  const visit = visitById(data, id)
  if (visit.status === 'COMPLETED') return
  if (visit.status !== 'IN_PROGRESS' || visit.draftVersion !== version) throw new Error('接诊或草稿已改变，请重新核对后提交。')
  const draft = visit.draft
  if (![draft.complaint, draft.diagnosis, draft.advice].every(text => text.trim())) throw new Error('请填写主诉、诊断和处理建议。')
  const lines: MedicineLine[] = []
  for (const line of draft.medicines) {
    const medicine = data.medicines.find(m => m.id === line.medicineId)
    if (!medicine?.enabled || !Number.isInteger(line.quantity) || line.quantity < 1 || medicine.stock - medicine.held < line.quantity || !line.usage.trim()) throw new Error('药品库存不足、已停用或用法用量未填写，请调整后重新核对。')
    if (lines.some(item => item.medicineId === medicine.id)) throw new Error('同一药品请合并数量。')
    lines.push({ medicineId: medicine.id, name: medicine.name, specification: medicine.specification, price: medicine.price, quantity: line.quantity, usage: line.usage.trim() })
  }
  for (const line of lines) data.medicines.find(m => m.id === line.medicineId)!.held += line.quantity
  visit.result = { clinical: copy(draft), medicines: lines, at: time, author: '许知遥' }; visit.status = 'COMPLETED'
  if (lines.length) visit.payment = { status: 'PENDING', total: lines.reduce((sum, l) => sum + l.price * l.quantity, 0), expiresAt: time + 30 * 60000 }
})
export const payMedicine = (id: string) => change((data, time) => {
  const visit = visitById(data, id)
  if (visit.payment?.status === 'PAID') return
  if (visit.payment?.status !== 'PENDING' || time >= visit.payment.expiresAt) throw new Error('药费订单已失效，无法支付，医疗结果仍然保留。')
  for (const line of visit.result!.medicines) { const medicine = data.medicines.find(m => m.id === line.medicineId)!; medicine.stock -= line.quantity; medicine.held -= line.quantity }
  visit.payment.status = 'PAID'
})
if (import.meta.env.DEV) {
  window.addEventListener('storage', event => {
    if (event.key !== receptionKey && event.key !== null) return
    try { state.value = readState(); wallTime.value = Date.now(); warning.value = '' }
    catch { warning.value = '其他标签的演示进度无法读取，请重置演示。' }
  })
  window.setInterval(() => {
    wallTime.value = Date.now()
    const needsExpiry = state.value.applications.some(a => isPendingApplication(a) && now.value > a.deadline) || state.value.visits.some(v => (v.status === 'PENDING_VISIT' && now.value >= sessionBounds(v.group).end - 15 * 60000) || (v.payment?.status === 'PENDING' && now.value >= v.payment.expiresAt))
    if (needsExpiry && !busy.value && !warning.value) void change(() => {})
  }, 1000)
}
