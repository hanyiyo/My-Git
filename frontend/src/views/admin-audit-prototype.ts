// Fictional operations only; production collection is not complete across these modules.
export const auditModules = ['账户管理', '科室管理', '排班管理', '号源管理', '药品库存', '审批替诊', '新闻通知', '规则配置', '医疗访问'] as const
export type AuditModule = typeof auditModules[number]
export interface AuditEvent {
  id: string
  actorId: string
  actorName: string
  module: AuditModule
  action: string
  objectType: string
  objectId: string
  description: string
  createdAt: number
}

export function hospitalDate(time: number) {
  const parts = new Intl.DateTimeFormat('en', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(time)
  const part = (type: string) => parts.find(p => p.type === type)!.value
  return `${part('year')}-${part('month')}-${part('day')}`
}

export function formatAuditTime(time: number) {
  return new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }).format(time).replaceAll('/', '-')
}

export function createAuditDemo(loadedAt: number): AuditEvent[] {
  const templates: { module: AuditModule; action: string; type: string; prefix: string; description: string }[] = [
    { module: '药品库存', action: '调整药品库存', type: '药品', prefix: 'MED', description: '已确认药品总库存，保留现有库存锁定。' },
    { module: '审批替诊', action: '复核替诊方案', type: '出诊安排', prefix: 'SCH', description: '复核替诊安排并确认生效，后续通知按业务流程发送。' },
    { module: '新闻通知', action: '发布医院新闻', type: '医院新闻', prefix: 'NEWS', description: '公开资讯已发布，内容历史按新闻规则保留。' },
    { module: '账户管理', action: '停用账户', type: '账户', prefix: 'ACC', description: '已停用账户，历史业务记录保留。' },
    { module: '科室管理', action: '任命科主任', type: '科室', prefix: 'DEP', description: '已确认科主任任命并更新科室管理职责。' },
    { module: '排班管理', action: '发布排班', type: '出诊安排', prefix: 'SCH', description: '已发布出诊安排，对符合条件的患者开放预约。' },
    { module: '号源管理', action: '调整总号源', type: '门诊场次', prefix: 'SLOT', description: '已调整无挂号和临时占号场次的总号源。' },
    { module: '规则配置', action: '更新预约规则', type: '规则版本', prefix: 'RULE', description: '新规则用于后续业务，既有承诺继续使用原快照。' },
    { module: '医疗访问', action: '登记医疗访问', type: '访问授权', prefix: 'REVIEW', description: '访问理由已隐藏' },
    { module: '药品库存', action: '停用药品', type: '药品', prefix: 'MED', description: '已停用药品，医生不能继续开具该品项。' },
    { module: '新闻通知', action: '撤回医院新闻', type: '医院新闻', prefix: 'NEWS', description: '已撤回公开资讯，保留原内容及发布历史。' },
    { module: '审批替诊', action: '确认停诊', type: '出诊安排', prefix: 'SCH', description: '已确认停诊，关联预约按停诊流程处理。' },
  ]
  const actors = [{ id: 'ADM-001', name: '林知远' }, { id: 'ADM-002', name: '周予安' }, { id: 'ADM-003', name: '陈书宁' }]
  const start = Date.parse(`${hospitalDate(loadedAt)}T00:00:00+08:00`)
  return Array.from({ length: 36 }, (_, index) => {
    const item = templates[index % templates.length]!
    const actor = actors[index % actors.length]!
    // At most the previous second, so today's fixture never claims a future action.
    const createdAt = Math.min(loadedAt - 1000, start + 15 * 3600000 + 42 * 60000 + 18 * 1000) - Math.floor(index / 4) * 86400000 - (index % 4) * 37 * 60000
    const serial = String(index + 1).padStart(3, '0')
    return { id: `AUD-${hospitalDate(createdAt).replaceAll('-', '')}-${serial}`, actorId: actor.id, actorName: actor.name, module: item.module, action: item.action, objectType: item.type, objectId: `${item.prefix}-${serial}`, description: item.description, createdAt }
  })
}
