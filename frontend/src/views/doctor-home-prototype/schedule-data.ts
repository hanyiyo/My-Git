import { schedules as todayFixtures } from './data'
import type { DemoVisit } from './reception-demo'

export const demoDate = '2026-11-12'
export type ScheduleMode = 'all' | 'online' | 'offline'
export interface DemoSchedule {
  id: string; date: string; period: 'am' | 'pm'; mode: 'online' | 'offline'; capacity: number; paid: number; held: number
  status: 'PUBLISHED' | 'SUSPENDED' | 'SUBSTITUTED'; replacement?: string; substituteFor?: string
}
export interface SuspensionApplication {
  id: string; scheduleId: string; reason: string; submittedAt: number; deadline: number; affected: number
  status: 'PENDING_HEAD' | 'PENDING_ADMIN' | 'REJECTED' | 'EXPIRED' | 'APPROVED'; note?: string
  applicantRole: 'doctor' | 'head'
}
export const applicationLabels = { PENDING_HEAD: '待科主任审批', PENDING_ADMIN: '待管理员复核', REJECTED: '已驳回', EXPIRED: '已过期', APPROVED: '已通过' }
export const isPendingApplication = (a: SuspensionApplication) => a.status === 'PENDING_HEAD' || a.status === 'PENDING_ADMIN'
export const scheduleStart = (s: DemoSchedule) => Date.parse(`${s.date}T${s.period === 'am' ? '08:00' : '14:00'}:00+08:00`)
export const scheduleEnd = (s: DemoSchedule) => Date.parse(`${s.date}T${s.period === 'am' ? '12:00' : '18:00'}:00+08:00`)
export const scheduleTime = (s: DemoSchedule) => s.period === 'am' ? '08:00–12:00' : '14:00–18:00'
export const periodLabel = (s: DemoSchedule) => s.period === 'am' ? '上午' : '下午'
export const modeLabel = (s: DemoSchedule) => s.mode === 'online' ? '线上问诊' : '线下面诊'
export const placeLabel = (s: DemoSchedule) => s.mode === 'online' ? '线上图文诊室' : '门诊二楼 · 205 诊室'
export const dateLabel = (date: string) => new Date(`${date}T12:00:00+08:00`).toLocaleDateString('zh-CN', { timeZone: 'Asia/Shanghai', month: 'long', day: 'numeric', weekday: 'long' })
export const dateTimeLabel = (at: number) => new Date(at).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })

export function initialSchedules(): DemoSchedule[] {
  const result: DemoSchedule[] = []
  // Fictional published dates, not a tool for doctors to create new schedules.
  for (const month of [10, 11, 12]) {
    const last = new Date(Date.UTC(2026, month, 0)).getUTCDate()
    for (let day = 1; day <= last; day++) {
      const weekday = new Date(Date.UTC(2026, month - 1, day)).getUTCDay()
      if (weekday === 0 || weekday === 6) continue
      for (const period of ['am', 'pm'] as const) {
        if (period === 'pm' && weekday === 3) continue
        const date = `2026-${month}-${String(day).padStart(2, '0')}`
        const current = date === demoDate
        result.push({ id: `${date}-${period}`, date, period, mode: period === 'am' ? 'offline' : 'online', capacity: period === 'am' ? 20 : 16,
          paid: current ? 0 : (day * 3 + (period === 'pm' ? 2 : 0)) % 12, held: current || date < demoDate ? 0 : day % 3, status: 'PUBLISHED' })
      }
    }
  }
  // Keep the existing capacities while deriving today's paid counts from actual demo visits.
  for (const fixture of todayFixtures) result.find(s => s.date === demoDate && s.period === fixture.id)!.capacity = fixture.capacity
  Object.assign(result.find(s => s.id === '2026-11-06-pm')!, { status: 'SUSPENDED', held: 0, paid: 0 })
  Object.assign(result.find(s => s.id === '2026-11-17-am')!, { status: 'SUBSTITUTED', replacement: '陈予安', held: 0 })
  Object.assign(result.find(s => s.id === '2026-11-19-pm')!, { substituteFor: '林牧' })
  return result
}
export function initialApplications(schedules: DemoSchedule[]): SuspensionApplication[] {
  const s = schedules.find(item => item.id === '2026-11-20-pm')!
  s.held = 0
  return [{ id: 'suspension-demo-001', scheduleId: s.id, reason: '参加院内业务培训，申请本场停诊。',
    submittedAt: Date.parse('2026-11-11T09:00:00+08:00'), deadline: Date.parse('2026-11-18T09:00:00+08:00'), affected: s.paid, status: 'PENDING_ADMIN', applicantRole: 'doctor', note: '科主任已选择直接停诊方案，等待管理员复核。' }]
}
export function scheduleNumbers(s: DemoSchedule, visits: DemoVisit[]) {
  const own = s.date === demoDate ? visits.filter(v => v.group === s.period) : []
  const registered = s.date === demoDate ? own.filter(v => v.status !== 'CANCELLED').length : s.paid
  return { registered, held: s.held, remaining: Math.max(0, s.capacity - registered - s.held),
    waiting: own.filter(v => v.status === 'WAITING').length, active: own.filter(v => v.status === 'IN_PROGRESS').length, completed: own.filter(v => v.status === 'COMPLETED').length }
}
export function scheduleApplication(s: DemoSchedule, applications: SuspensionApplication[]) {
  return applications.filter(a => a.scheduleId === s.id).sort((a, b) => b.submittedAt - a.submittedAt)[0]
}
export function scheduleStatus(s: DemoSchedule, applications: SuspensionApplication[], time: number) {
  if (s.status === 'SUSPENDED') return '已停诊'
  if (s.status === 'SUBSTITUTED') return '已替诊'
  if (scheduleEnd(s) <= time) return '已结束'
  if (scheduleApplication(s, applications) && isPendingApplication(scheduleApplication(s, applications)!)) return '暂停新增挂号'
  return time >= scheduleStart(s) ? '出诊中' : '待出诊'
}
export function suspensionBlock(s: DemoSchedule, applications: SuspensionApplication[], time: number) {
  if (s.status !== 'PUBLISHED') return '本场已停诊或已替诊，不能申请。'
  if (scheduleApplication(s, applications) && isPendingApplication(scheduleApplication(s, applications)!)) return '本场已有未结束申请，请查看申请。'
  if (time > scheduleStart(s) - 24 * 3600000) return '须至少提前 24 小时申请；紧急停诊请联系管理员。'
  return ''
}
