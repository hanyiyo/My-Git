export type DirectoryAction = 'enable' | 'disable' | 'reset'
export type DirectoryOperation = { id: number; action: string; actor: string; time: number; description: string }
export type DirectoryAccount = { id: string; name: string; login: string; phone: string; enabled: boolean; createdAt: number; operations: DirectoryOperation[] }
export type DirectoryDraft = { name: string; login: string; phone: string; password: string; confirmation: string }
export const currentAdminId = 'ADM-001'
export const emptyDirectoryDraft = (): DirectoryDraft => ({ name: '', login: '', phone: '', password: '', confirmation: '' })

export function createDirectoryDemo(now: number): DirectoryAccount[] {
  return ['运营管理员', '林知远', '陈念安', '许嘉宁', '沈清和', '顾思齐', '周予安', '程雨桐', '江望舒', '叶明川', '宋知微', '温以宁', '陆景行', '苏晚晴'].map((name, index) => ({
    id: `ADM-${String(index + 1).padStart(3, '0')}`, name, login: index === 0 ? 'admin.operations' : `admin.staff${String(index + 1).padStart(2, '0')}`,
    phone: `1380000${String(index + 1).padStart(4, '0')}`, enabled: index < 10, createdAt: now - (index + 1) * 86400000, operations: [],
  }))
}
export function directoryDraftError(draft: DirectoryDraft, accounts: DirectoryAccount[], creating: boolean): string {
  if (!draft.name.trim()) return '请填写管理员姓名。'
  if (Array.from(draft.name.trim()).length > 120) return '管理员姓名最多120字。'
  if (!/^1[3-9]\d{9}$/.test(draft.phone.trim())) return '请输入有效的中国大陆11位手机号。'
  if (!creating) return ''
  const login = draft.login.trim().toLowerCase()
  if (!/^[a-z0-9][a-z0-9@._+\-]{2,189}$/.test(login)) return '登录账号需为3–190位，以字母或数字开头，允许字母、数字及 @._+-。'
  if (accounts.some(account => account.login.toLowerCase() === login)) return '该登录账号已存在，请使用其他账号。'
  if (!draft.password.trim() || draft.password.length < 12) return '演示初始密码不能为空，且至少12个字符。'
  if (new TextEncoder().encode(draft.password).length > 72) return '演示初始密码UTF-8编码不能超过72字节。'
  if (draft.password !== draft.confirmation) return '两次输入的演示初始密码不一致。'
  return ''
}
export function directoryActionReason(action: DirectoryAction, account: DirectoryAccount, accounts: DirectoryAccount[]): string {
  if (action === 'disable') {
    if (!account.enabled) return '该账户已经停用。'
    if (accounts.filter(item => item.enabled).length <= 1) return '至少保留一个启用的管理员账户。'
    if (account.id === currentAdminId) return '不能停用当前登录账户。'
  }
  if (action === 'enable' && account.enabled) return '该账户已经启用。'
  if (action === 'reset' && account.id === currentAdminId) return '不能重置当前登录账户，请通过退出登录结束本次会话。'
  return ''
}
export const directoryActionLabel = (action: DirectoryAction) => ({ enable: '启用账户', disable: '停用账户', reset: '重置登录状态' })[action]
export const directoryActionImpact = (action: DirectoryAction) => ({ enable: '目标账户将恢复登录使用，资料和历史业务记录保留。', disable: '目标账户将不能登录或继续访问管理功能，资料和历史业务记录保留。', reset: '目标账户的现有登录将失效，需重新登录；密码、启用状态和业务记录保持不变。' })[action]
export const directoryPhone = (phone: string) => `${phone.slice(0, 3)} **** ${phone.slice(-4)}`
export function directoryTime(time: number): string {
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(time)
}
