export const defaultRules = {
  amStart: '08:00', amEnd: '12:00', pmStart: '14:00', pmEnd: '18:00',
  checkInBeforeMinutes: 30, checkInCutoffMinutes: 15, missedWaitMinutes: 5,
  approvalMaxDays: 7, approvalCutoffHours: 24, medicinePaymentHours: 24,
  ordinaryFeeMinor: 1000, expertFeeMinor: 2000,
}
export type Rules = typeof defaultRules
export type RuleKey = keyof Rules
export interface RuleField { key: RuleKey; label: string; kind: 'time' | 'integer' | 'money'; unit: string; min: number; max: number }
export interface RuleGroup { id: string; name: string; icon: string; description: string; impact: string; fields: RuleField[] }
export interface RuleChange { label: string; before: string; after: string }
export interface RuleHistory { id: number; group: string; actor: string; time: number; changes: RuleChange[] }

export const ruleGroups: RuleGroup[] = [
  { id: 'sessions', name: '门诊时段', icon: 'calendar', description: '统一上午、下午的门诊服务时间。', impact: '目标规则：后续发布的排班采用新时段，已有排班继续保留原时段。', fields: [
    { key: 'amStart', label: '上午开始', kind: 'time', unit: '', min: 0, max: 0 },
    { key: 'amEnd', label: '上午结束', kind: 'time', unit: '', min: 0, max: 0 },
    { key: 'pmStart', label: '下午开始', kind: 'time', unit: '', min: 0, max: 0 },
    { key: 'pmEnd', label: '下午结束', kind: 'time', unit: '', min: 0, max: 0 },
  ] },
  { id: 'reception', name: '签到与候诊', icon: 'clock', description: '设置签到窗口和线上叫号等待时间。', impact: '目标规则：新业务采用新的签到及叫号规则，既有预约与已开始叫号保留原规则快照。', fields: [
    { key: 'checkInBeforeMinutes', label: '开诊前可提前签到', kind: 'integer', unit: '分钟', min: 0, max: 1440 },
    { key: 'checkInCutoffMinutes', label: '结束前截止签到', kind: 'integer', unit: '分钟', min: 0, max: 1440 },
    { key: 'missedWaitMinutes', label: '线上叫号无应答等待', kind: 'integer', unit: '分钟', min: 1, max: 240 },
  ] },
  { id: 'approval', name: '审批时限', icon: 'file', description: '规定停诊审批的最长时限与硬截止。', impact: '目标规则：新发起审批使用新时限，已有审批保留原截止约定。', fields: [
    { key: 'approvalMaxDays', label: '停诊审批最长时限', kind: 'integer', unit: '天', min: 1, max: 365 },
    { key: 'approvalCutoffHours', label: '开诊前审批硬截止', kind: 'integer', unit: '小时', min: 1, max: 8760 },
  ] },
  { id: 'medicine', name: '药费支付', icon: 'wallet', description: '设置开药后的药费待缴有效期。', impact: '目标规则：新建药费单采用新有效期，已有药费单的金额和截止时间不变。', fields: [
    { key: 'medicinePaymentHours', label: '药费待缴有效期', kind: 'integer', unit: '小时', min: 1, max: 720 },
  ] },
  { id: 'fees', name: '默认挂号费', icon: 'hospital', description: '维护普通、专家门诊的默认费用。', impact: '目标规则：后续新建排班使用新的默认费用，已发布排班及已有订单金额不变。', fields: [
    { key: 'ordinaryFeeMinor', label: '普通医生默认挂号费', kind: 'money', unit: '元', min: 0, max: 1000000 },
    { key: 'expertFeeMinor', label: '专家医生默认挂号费', kind: 'money', unit: '元', min: 0, max: 1000000 },
  ] },
]

export function inputValue(field: RuleField, rules: Rules) {
  return field.kind === 'money' ? (Number(rules[field.key]) / 100).toFixed(2) : String(rules[field.key])
}
export function displayValue(field: RuleField, rules: Rules) {
  return `${inputValue(field, rules)}${field.unit ? ` ${field.unit}` : ''}`
}
const minutes = (time: string) => Number(time.slice(0, 2)) * 60 + Number(time.slice(3))

export function prepareRules(group: RuleGroup, draft: Partial<Record<RuleKey, string>>, saved: Rules): { rules: Rules; error: string } {
  const rules = { ...saved }
  const failure = (error: string) => ({ rules, error })
  for (const field of group.fields) {
    const value = (draft[field.key] ?? '').trim()
    if (!value) return failure(`请填写${field.label}。`)
    if (field.kind === 'time') {
      if (!/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value)) return failure(`${field.label}须使用00:00–23:59的时间。`)
      Object.assign(rules, { [field.key]: value })
    } else {
      const money = field.kind === 'money'
      if (!(money ? /^\d+(?:\.\d{1,2})?$/ : /^\d+$/).test(value)) return failure(money ? `${field.label}须为非负金额，最多两位小数。` : `${field.label}须为整数。`)
      const number = Number(value)
      if (!Number.isFinite(number) || number < field.min || number > field.max) return failure(`${field.label}须在${field.min}–${field.max}${field.unit}之间。`)
      const [whole = '0', fraction = ''] = value.split('.')
      Object.assign(rules, { [field.key]: money ? Number(whole) * 100 + Number(fraction.padEnd(2, '0')) : number })
    }
  }
  const start = minutes(rules.amStart), end = minutes(rules.amEnd), pmStart = minutes(rules.pmStart), pmEnd = minutes(rules.pmEnd)
  if (!(start < end && end <= pmStart && pmStart < pmEnd)) return failure('门诊时间须满足：上午开始＜上午结束≤下午开始＜下午结束。')
  if (rules.checkInCutoffMinutes >= Math.min(end - start, pmEnd - pmStart)) return failure(group.id === 'sessions'
    ? '门诊时长与当前签到截止冲突。请先在“签到与候诊”调小结束前截止签到分钟数，再缩短门诊时段。'
    : '结束前截止签到分钟数必须小于上午和下午的门诊时长。')
  return { rules, error: '' }
}

export function changesFor(group: RuleGroup, before: Rules, after: Rules): RuleChange[] {
  return group.fields.filter(field => before[field.key] !== after[field.key]).map(field => ({ label: field.label, before: displayValue(field, before), after: displayValue(field, after) }))
}

function windowTime(value: number) {
  const day = Math.floor(value / 1440)
  const clock = ((value % 1440) + 1440) % 1440
  return `${day < 0 ? '前一天 ' : ''}${String(Math.floor(clock / 60)).padStart(2, '0')}:${String(clock % 60).padStart(2, '0')}`
}
export function ruleExample(group: RuleGroup, rules: Rules) {
  if (group.id === 'sessions') return `上午 ${rules.amStart}–${rules.amEnd}，下午 ${rules.pmStart}–${rules.pmEnd}。门诊时间以医院时间为准。`
  if (group.id === 'reception') return `上午签到 ${windowTime(minutes(rules.amStart) - rules.checkInBeforeMinutes)} 至 ${windowTime(minutes(rules.amEnd) - rules.checkInCutoffMinutes)}（不含截止时刻）；线上叫号后无应答至少等待 ${rules.missedWaitMinutes} 分钟。`
  if (group.id === 'approval') return `目标规则：申请后 ${rules.approvalMaxDays} 天与开诊前 ${rules.approvalCutoffHours} 小时，取较早的时间截止。`
  if (group.id === 'medicine') return `开药创建订单后 ${rules.medicinePaymentHours} 小时内待缴；超时释放库存，已保存的医疗记录保留。`
  return `默认费用用于后续排班；已有订单继续使用原金额快照。`
}

export function ruleTime(time: number) {
  return new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }).format(time).replaceAll('/', '-')
}
