export type AiKey = 'enabled' | 'patientPrompt' | 'fallback' | 'rounds' | 'images' | 'summaryPrompt' | 'automatic' | 'summaryLength' | 'multimodal' | 'mode' | 'endpoint' | 'keyConfigured' | 'model' | 'tokens' | 'temperature' | 'logging' | 'retention'
export type AiConfig = Record<AiKey, string>
export type AiField = { key: AiKey; label: string; kind: 'text' | 'long' | 'integer' | 'decimal' | 'toggle' | 'mode' | 'key'; help: string; min?: number; max?: number; unit?: string }
export type AiGroup = { id: string; name: string; icon: string; description: string; impact: string; fields: AiField[] }
export type AiChange = { label: string; before: string; after: string }
export type AiHistory = { id: number; group: string; time: number; changes: AiChange[] }

export const defaultAiConfig: AiConfig = {
  enabled: 'on', patientPrompt: '仅提供医院业务咨询与科室建议，不诊断、不开药。涉及医疗材料时先核对患者授权范围。',
  fallback: '该问题超出导诊助手的服务范围，请咨询负责医生；如有紧急不适，请及时就医。', rounds: '10', images: 'off',
  summaryPrompt: '仅依据本次已授权医患图文整理症状、持续时间与病史，标明不确定信息，不生成诊断或用药建议。',
  automatic: 'on', summaryLength: '1000', multimodal: 'off', mode: 'local', endpoint: '', keyConfigured: 'off', model: '', tokens: '2048', temperature: '0.2', logging: 'on', retention: '30',
}
export const aiGroups: AiGroup[] = [
  { id: 'master', name: 'AI 总开关', icon: 'sparkles', description: '统一控制患者导诊与医生摘要', impact: '停用目标：隐藏患者 AI 入口，停止新生成，在途请求返回也不再展示新结果；保留子项配置和已有记录。已外发材料不能通过停用撤回。本页仅演示，不联动真实业务。', fields: [
    { key: 'enabled', label: 'AI 总开关', kind: 'toggle', help: '启用和停用均需确认' },
  ] },
  { id: 'patient', name: '患者导诊', icon: 'user', description: '医院咨询与科室建议', impact: '固定约束：不诊断、不开药；医疗材料外发需患者明确授权。图片开关仅演示目标能力，本地演示尚不解析图片。', fields: [
    { key: 'patientPrompt', label: '回答边界提示词', kind: 'long', help: '必填，最多 4000 字', max: 4000 },
    { key: 'fallback', label: '超出范围的兜底文案', kind: 'long', help: '必填，最多 500 字', max: 500 },
    { key: 'rounds', label: '最大对话轮次', kind: 'integer', help: '1–50 轮 · 整数', min: 1, max: 50, unit: '轮' },
    { key: 'images', label: '图片上传', kind: 'toggle', help: '仅演示开关，不代表模型已具备图片解析能力' },
  ] },
  { id: 'doctor', name: '医生症状摘要', icon: 'doctor', description: '整理已授权症状与病史', impact: '仅供负责医生辅助接诊，不生成诊断或用药建议；配置不能扩大医疗材料授权范围，新增材料仍需授权。摘要失败不阻断接诊。', fields: [
    { key: 'summaryPrompt', label: '摘要模板', kind: 'long', help: '必填，最多 4000 字', max: 4000 },
    { key: 'automatic', label: '自动生成摘要', kind: 'toggle', help: '目标行为：获得有效授权后自动生成' },
    { key: 'summaryLength', label: '最大摘要长度', kind: 'integer', help: '100–5000 字 · 整数', min: 100, max: 5000, unit: '字' },
    { key: 'multimodal', label: '多模态解析', kind: 'toggle', help: '仅演示目标能力，本地演示尚不解析图片' },
  ] },
  { id: 'provider', name: '接口与模型', icon: 'settings', description: '两类 AI 共用基础配置', impact: '默认本地演示；外部模式必须具备 HTTPS 地址、模型和演示密钥。不发真实请求，失败不自动切换本地结果。新配置仅用于后续生成，不扩大既有授权。', fields: [
    { key: 'mode', label: '运行模式', kind: 'mode', help: '两种模式在本页均为模拟，不调用真实模型' },
    { key: 'endpoint', label: 'HTTPS 接口地址', kind: 'text', help: '外部模式必填；最多 500 字，不含凭据、查询参数或片段', max: 500 },
    { key: 'model', label: '模型名称', kind: 'text', help: '外部模式必填，最多 100 字', max: 100 },
    { key: 'keyConfigured', label: '演示密钥', kind: 'key', help: '只模拟配置状态，不输入或保存真实密钥' },
    { key: 'tokens', label: '最大输出 token', kind: 'integer', help: '128–32768 token · 整数', min: 128, max: 32768, unit: 'token' },
    { key: 'temperature', label: 'temperature', kind: 'decimal', help: '0–1，数值越低越稳定', min: 0, max: 1 },
  ] },
  { id: 'records', name: '记录策略', icon: 'file', description: '仅保留必要调用元数据', impact: '日志仅包含提供方、用途、状态、耗时等元数据，不展示完整对话、医疗正文或附件。关闭保存不删除已有业务记录；保留策略仅演示，不执行清理。', fields: [
    { key: 'logging', label: '保存调用元数据', kind: 'toggle', help: '不改变独立受控医疗业务记录的保留规则' },
    { key: 'retention', label: '元数据保留天数', kind: 'integer', help: '1–365 天 · 整数；关闭保存时仍保留此配置', min: 1, max: 365, unit: '天' },
  ] },
]

export function aiDisplay(field: AiField, config: AiConfig): string {
  const value = config[field.key]
  if (field.kind === 'toggle') return value === 'on' ? '开启' : '关闭'
  if (field.kind === 'key') return value === 'on' ? '已配置（演示）' : '未配置'
  if (field.kind === 'mode') return value === 'local' ? '本地演示' : '外部模型（演示）'
  return value ? `${value}${field.unit ? ` ${field.unit}` : ''}` : '未设置'
}
export function aiChanges(group: AiGroup, before: AiConfig, after: AiConfig): AiChange[] {
  return group.fields.filter(field => before[field.key] !== after[field.key]).map(field => ({ label: field.label, before: aiDisplay(field, before), after: aiDisplay(field, after) }))
}
export function prepareAi(group: AiGroup, draft: AiConfig): { config: AiConfig; error: string } {
  const config = { ...draft }
  for (const field of group.fields) {
    const value = draft[field.key].trim()
    config[field.key] = value
    if (field.kind === 'long' && !value) return { config, error: `请填写${field.label}。` }
    if (field.max && (field.kind === 'text' || field.kind === 'long') && Array.from(value).length > field.max) return { config, error: `${field.label}最多 ${field.max} 字。` }
    if (field.kind === 'integer' || field.kind === 'decimal') {
      if (!value) return { config, error: `请填写${field.label}。` }
      if (!(field.kind === 'integer' ? /^\d+$/ : /^\d+(\.\d+)?$/).test(value)) return { config, error: `${field.label}须为${field.kind === 'integer' ? '整数' : '0–1 的十进制数值'}。` }
      const number = Number(value)
      if (!Number.isFinite(number) || number < field.min! || number > field.max!) return { config, error: `${field.label}范围为 ${field.min}–${field.max}${field.unit || ''}。` }
      config[field.key] = field.kind === 'integer' ? String(number) : value.replace(/^0+(?=\d)/, '').replace(/(\.\d*?)0+$/, '$1').replace(/\.$/, '')
    }
  }
  if (group.id === 'provider') {
    if (config.endpoint) {
      try {
        const parsed = new URL(config.endpoint)
        if (parsed.protocol !== 'https:' || !parsed.hostname || parsed.username || parsed.password || parsed.search || parsed.hash) throw new Error('invalid')
      } catch { return { config, error: '请输入有效的 HTTPS 接口地址，不含凭据、查询参数或片段。' } }
    }
    if (config.mode === 'external' && (!config.endpoint || !config.model || config.keyConfigured !== 'on')) return { config, error: '外部模式须填写 HTTPS 接口地址、模型名称并设置演示密钥。' }
  }
  return { config, error: '' }
}
export function aiTime(time: number): string {
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(time)
}
