import type { PrototypeOrder } from '../patient-home-prototype/order-data'
import { canCheckIn, canRestore, sessionBounds, statusText, type DemoVisit } from './reception-demo'

export function linkedPatientOrder(visit: DemoVisit, time: number, queueIds: string[]): PrototypeOrder {
  const position = queueIds.indexOf(visit.id)
  return { id: visit.id, doctor: '许知遥', title: '副主任医师', department: '内科', specialty: '呼吸消化 · 慢病管理', mode: visit.group === 'pm' ? '线上问诊' : '线下面诊', source: visit.source as PrototypeOrder['source'], status: visit.status,
    date: '2026-11-12', dateLabel: '联动演示 · 11月12日', time: visit.group === 'pm' ? '14:00–18:00' : '08:00–12:00', room: visit.group === 'pm' ? '线上图文诊室' : '门诊二楼 · 205 诊室',
    fee: visit.group === 'pm' ? '¥18.00' : '¥12.00', credential: `DEMO-${visit.number}`, checkinWindow: visit.group === 'pm' ? '13:30–17:45' : '07:30–11:45', checkinOpen: canCheckIn(visit, time),
    queueNumber: visit.checkedAt ? visit.number : undefined, queuePosition: position >= 0 ? `${visit.restored ? '恢复排队 · ' : ''}第 ${position + 1} 位` : statusText[visit.status], ahead: Math.max(0, position), waitMinutes: Math.max(0, position) * 5,
    roomAvailable: visit.group === 'pm' && ['WAITING', 'IN_PROGRESS', 'COMPLETED'].includes(visit.status), restoreUsed: visit.restored || !canRestore(visit, time),
    reason: visit.status === 'MISSED' && time >= sessionBounds(visit.group).end ? '本次门诊时段已经结束，无法恢复排队；挂号费不自动退款。' : visit.status === 'EXPIRED' ? '本次挂号已作废（未按时签到或二次过号），不能恢复排队。' : visit.status === 'CANCELLED' ? '患者主动取消挂号，模拟退款处理中。' : undefined,
    result: visit.result ? `诊断：${visit.result.clinical.diagnosis}；处理建议：${visit.result.clinical.advice}` : undefined }
}
