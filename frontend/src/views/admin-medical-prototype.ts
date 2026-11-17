export type Scope = 'RECORD' | 'ATTACHMENTS' | 'ALL'
export type AccessState = '有效' | '已撤回' | '已过期'
export type ContentKind = 'MATERIAL' | 'MESSAGE' | 'RESULT' | 'CORRECTION' | 'ATTACHMENT'
export interface MedicalAppointment {
  id: string; patient: string; patientId: string; doctor: string; department: string
  date: string; period: string; mode: string; state: string
}
export interface Reviewer { id: string; name: string; qualified: boolean; grantedBy: string }
export interface AccessGrant {
  id: string; appointmentId: string; actor: string; scope: Scope; reason: string
  createdAt: number; expiresAt: number; revoked: boolean
}
export interface MedicalEntry { id: string; kind: ContentKind; title: string; body: string }
export interface ReviewAudit { id: string; grantId: string; appointmentId: string; actor: string; action: string; target: string; reason: string; time: number }
export interface ContentFlag { appointmentId: string; targetId: string; limited: boolean }
export const scopeLabels: Record<Scope, string> = { RECORD: '医疗正文', ATTACHMENTS: '医疗附件', ALL: '正文和附件' }
export const kindLabels: Record<ContentKind, string> = { MATERIAL: '患者材料', MESSAGE: '图文消息', RESULT: '医疗结果', CORRECTION: '追加更正', ATTACHMENT: '医疗附件' }

export function hospitalDate(time: number) {
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(time)
}
export function displayTime(time: number) {
  return new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(time)
}
export function grantState(grant: AccessGrant, now: number): AccessState {
  return grant.revoked ? '已撤回' : grant.expiresAt <= now ? '已过期' : '有效'
}
export function createMedicalDemo(now: number) {
  const day = 86400000
  const appointments: MedicalAppointment[] = [
    { id: 'APT-M001', patient: '林晓', patientId: 'P-1001', doctor: '陈予安', department: '全科医学科', date: hospitalDate(now), period: '上午', mode: '线上问诊', state: '已完成' },
    { id: 'APT-M002', patient: '周宁', patientId: 'P-1002', doctor: '许知遥', department: '内科', date: hospitalDate(now - day * 2), period: '下午', mode: '线下面诊', state: '已完成' },
    { id: 'APT-M003', patient: '吴桐', patientId: 'P-1003', doctor: '宋清和', department: '皮肤科', date: hospitalDate(now - day * 5), period: '上午', mode: '线上问诊', state: '已取消' },
    { id: 'APT-M004', patient: '沈悦', patientId: 'P-1004', doctor: '陈予安', department: '全科医学科', date: hospitalDate(now - day * 12), period: '下午', mode: '线下面诊', state: '已完成' },
    { id: 'APT-M005', patient: '陆远', patientId: 'P-1005', doctor: '许知遥', department: '内科', date: hospitalDate(now - day * 45), period: '上午', mode: '线上问诊', state: '已完成' },
  ]
  const reviewers: Reviewer[] = [
    { id: 'ADMIN-01', name: '运营管理员', qualified: true, grantedBy: '审核管理员' },
    { id: 'ADMIN-02', name: '审核管理员', qualified: true, grantedBy: '运营管理员' },
    { id: 'ADMIN-03', name: '值班管理员', qualified: false, grantedBy: '尚未授予' },
  ]
  const grants: AccessGrant[] = [
    { id: 'REV-1001', appointmentId: 'APT-M001', actor: 'ADMIN-01', scope: 'RECORD', reason: '复核内容展示范围（演示）', createdAt: now - 10 * 60000, expiresAt: now + 20 * 60000, revoked: false },
    { id: 'REV-1002', appointmentId: 'APT-M002', actor: 'ADMIN-01', scope: 'ATTACHMENTS', reason: '附件展示复核（演示）', createdAt: now - day, expiresAt: now - day + 30 * 60000, revoked: false },
    { id: 'REV-1003', appointmentId: 'APT-M004', actor: 'ADMIN-01', scope: 'ALL', reason: '已结束的范围核验（演示）', createdAt: now - day * 3, expiresAt: now - day * 3 + 30 * 60000, revoked: true },
  ]
  const audits: ReviewAudit[] = grants.map(g => ({ id: `audit-${g.id}`, grantId: g.id, appointmentId: g.appointmentId, actor: g.actor, action: '登记访问授权', target: scopeLabels[g.scope], reason: g.reason, time: g.createdAt }))
  const flags: ContentFlag[] = []
  return { appointments, reviewers, grants, audits, flags }
}

// shortcut: 全部内容为本地虚构样例；真实接口阶段必须由后端按授权范围返回内容。
export function readDemoContent(appointmentId: string, scope: Scope): MedicalEntry[] {
  if (appointmentId === 'APT-M003') return []
  const entries: MedicalEntry[] = [
    { id: `${appointmentId}-material`, kind: 'MATERIAL', title: '患者材料 · 版本 1', body: '演示材料：患者自述近期咽部不适，提交本次就诊说明。仅用于界面验收。' },
    { id: `${appointmentId}-message`, kind: 'MESSAGE', title: '医患图文 · 医生回复', body: '演示回复：请补充不适持续时间，本条属于本次问诊交流记录。' },
    { id: `${appointmentId}-result`, kind: 'RESULT', title: '医疗结果 · 版本 1', body: '演示结果：本次接诊已完成，医疗结果按原作者及版本保留，不支持管理员改写。' },
    { id: `${appointmentId}-correction`, kind: 'CORRECTION', title: '追加更正 · 原医生', body: '演示更正：补充随访说明；此记录为追加版本，不覆盖原医疗结果。' },
    { id: `${appointmentId}-attachment`, kind: 'ATTACHMENT', title: '复查资料示例.png', body: 'PNG · 虚构演示附件' },
  ]
  return entries.filter(entry => scope === 'ALL' || (scope === 'ATTACHMENTS') === (entry.kind === 'ATTACHMENT'))
}
