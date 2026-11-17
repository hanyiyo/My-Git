<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Icon from './PrototypeIcon.vue'
import ClinicalResult from '../doctor-home-prototype/ClinicalResult.vue'
import { authorizeHistory, authorizeMessages, formatTime, sendVisitMessage, statusText, useReceptionDemo } from '../doctor-home-prototype/reception-demo'

type SessionKind = 'AI' | 'DOCTOR'
interface ChatMessage { id: string; sender: 'patient' | 'assistant' | 'doctor' | 'system'; text: string; time: string }
interface ConsultationSession { id: string; kind: SessionKind; title: string; subtitle: string; date: string; status: string; readOnly: boolean; messages: ChatMessage[] }

const props = withDefaults(defineProps<{ initialConversation?: string }>(), { initialConversation: 'ai' })
const legacySessions = ref<ConsultationSession[]>([
  { id: 'ai', kind: 'AI', title: 'AI 助手', subtitle: '一般健康建议 · 就医信息整理', date: '今天 09:26', status: '可继续交流', readOnly: false, messages: [{ id: 'ai-welcome', sender: 'assistant', text: '你好，林同学。我可以帮你整理就医流程、预约信息和一般健康建议。你可以先告诉我想了解什么。', time: '09:26' }, { id: 'ai-boundary', sender: 'system', text: 'AI 内容仅供参考，不提供诊断、处方或替代医生判断。', time: '09:26' }] },
  { id: 'completed-consultation', kind: 'DOCTOR', title: '陈予安医生', subtitle: '全科医学科 · 已完成接诊', date: '11月06日', status: '医生已提交结果 · 只读', readOnly: true, messages: [{ id: 'completed-patient', sender: 'patient', text: '谢谢医生，我会按提醒安排后续复诊。', time: '11月06日 15:12' }, { id: 'completed-doctor', sender: 'doctor', text: '本次接诊已完成，详细结果请回到“我的挂号”查看。', time: '11月06日 15:16' }] },
])
const { state: linkedState, error: linkedError, busy } = useReceptionDemo()
const sessions = computed<ConsultationSession[]>(() => [legacySessions.value[0]!, ...linkedState.value.visits.filter(v => v.owner === 'lin' && v.group === 'pm').map(v => ({ id: v.id, kind: 'DOCTOR' as const, title: '许知遥医生', subtitle: '内科 · 线上问诊 · 11月12日 14:00–18:00', date: '11月12日', status: statusText[v.status], readOnly: !['WAITING', 'IN_PROGRESS'].includes(v.status), messages: v.messages.map(m => ({ id: m.id, sender: m.sender, text: m.text, time: formatTime(m.at) })) })), ...legacySessions.value.slice(1)])
const consentDialog = ref<HTMLDialogElement>(), consentIds = ref<string[]>([]), consentChecked = ref(false)
const linkedVisit = computed(() => linkedState.value.visits.find(v => v.id === selectedId.value && v.owner === 'lin'))
function openConsent() { consentIds.value = linkedVisit.value!.messages.map(m => m.id); consentChecked.value = false; consentDialog.value?.showModal() }
async function confirmConsent() { if (consentChecked.value && await authorizeMessages(linkedVisit.value!.id, true, consentIds.value)) consentDialog.value?.close() }
const selectedId = ref(sessions.value.some(item => item.id === props.initialConversation) ? props.initialConversation : 'ai')
const draft = ref('')
const selectedSession = computed(() => sessions.value.find(item => item.id === selectedId.value) ?? sessions.value[0]!)
const isAi = computed(() => selectedSession.value.kind === 'AI')
const readOnlyNotice = computed(() => linkedVisit.value?.status === 'PENDING_VISIT' ? '请先在我的挂号完成签到，之后可提前留言。' : linkedVisit.value?.status === 'MISSED' ? '本次叫号已过号，请在我的挂号申请恢复排队。' : linkedVisit.value && ['EXPIRED', 'CANCELLED'].includes(linkedVisit.value.status) ? '本次挂号已失效，当前会话只读。' : '医生已提交结果，本次医生会话已转为只读。')

function chooseSession(session: ConsultationSession) {
  selectedId.value = session.id
  draft.value = ''
}
async function send() {
  const text = draft.value.trim()
  if (!text || selectedSession.value.readOnly) return
  const session = selectedSession.value
  if (linkedVisit.value) { if (await sendVisitMessage(session.id, 'patient', text)) draft.value = ''; return }
  session.messages.push({ id: `${session.id}-${Date.now()}`, sender: 'patient', text, time: '刚刚' })
  draft.value = ''
  if (session.kind === 'AI') {
    window.setTimeout(() => session.messages.push({ id: `${session.id}-reply-${Date.now()}`, sender: 'assistant', text: '我已记录你的问题。可以继续补充具体时间、就医方式或想了解的流程，我会提供一般信息供你参考。', time: '刚刚' }), 180)
  }
}
watch(() => props.initialConversation, id => { if (sessions.value.some(item => item.id === id)) { selectedId.value = id; draft.value = '' } })
</script>

<template>
  <section class="ph-consultation-page" data-testid="consultation-page" aria-labelledby="consultation-page-title">
    <header class="ph-consultation-heading"><div><span class="ph-eyebrow">PATIENT SERVICES · CONSULTATION ROOM</span><h1 id="consultation-page-title">图文诊室</h1><p>选择一个会话继续交流。医生会话与 AI 助手彼此独立。</p></div><div class="ph-consultation-heading-status"><span class="ph-live-dot"></span><span>{{ isAi ? 'AI 助手在线' : selectedSession.status }}</span></div></header>
    <p v-if="linkedError" role="alert">{{ linkedError }}</p>
    <div v-if="linkedVisit" class="rd-patient-consent"><div><strong>医生症状摘要 · 本次授权</strong><p>提供方：本地模拟摘要（不外发）；用途：向负责医生整理症状与病史；范围：你本次明确选择的医患文字消息。摘要仅医生可见。</p><small>已授权 {{ linkedVisit.authorizedIds.length }} 条 / 当前 {{ linkedVisit.messages.length }} 条。新消息须补充授权。</small></div><div><button class="ph-soft-button" :disabled="!linkedVisit.messages.length || busy" @click="openConsent">授权当前消息</button><button v-if="linkedVisit.authorizedIds.length" class="ph-soft-button" @click="authorizeMessages(linkedVisit.id, false)">撤回摘要授权</button><label><input type="checkbox" :checked="linkedVisit.historyAuthorized" @change="authorizeHistory(linkedVisit.id, ($event.target as HTMLInputElement).checked)">向本次医生共享 10月8日历史病历（演示）</label></div></div>
    <div class="ph-consultation-workspace">
      <aside class="ph-consultation-sessions" aria-label="近一个月会话"><div class="ph-consultation-sessions-heading"><div><span class="ph-eyebrow">RECENT SESSIONS</span><h2>会话记录</h2></div><button class="ph-icon-button" aria-label="新建AI会话" title="新建AI会话" @click="selectedId = 'ai'"><Icon name="plus" /></button></div><p class="ph-consultation-retention">近一个月 · 点击会话查看记录</p><button v-for="session in sessions" :key="session.id" type="button" :class="['ph-consultation-session', { active: session.id === selectedId, doctor: session.kind === 'DOCTOR' }]" @click="chooseSession(session)"><span :class="['ph-consultation-session-avatar', session.kind === 'AI' ? 'ai' : 'doctor']">{{ session.kind === 'AI' ? 'AI' : session.title[0] }}</span><span><strong>{{ session.title }}</strong><small>{{ session.subtitle }}</small><time>{{ session.date }}</time></span><Icon name="chevron" /></button></aside>
      <section class="ph-consultation-chat" aria-label="当前聊天窗口"><header class="ph-consultation-chat-heading"><div class="ph-consultation-chat-person"><span :class="['ph-consultation-chat-avatar', isAi ? 'ai' : 'doctor']">{{ isAi ? 'AI' : selectedSession.title[0] }}</span><div><h2>{{ selectedSession.title }}</h2><p>{{ selectedSession.subtitle }}</p></div></div><span :class="['ph-consultation-chat-status', { readonly: selectedSession.readOnly }]">{{ selectedSession.status }}</span></header><div class="ph-consultation-chat-notice"><Icon :name="isAi ? 'sparkles' : 'shield'" /><span>{{ isAi ? 'AI 仅提供一般建议，不构成诊断或病历结论。' : selectedSession.readOnly ? readOnlyNotice : '请勿在聊天中发送紧急求助；如有急症请及时线下就医。' }}</span></div><div class="ph-consultation-messages" aria-live="polite"><div v-for="message in selectedSession.messages" :key="message.id" :class="['ph-consultation-message-row', message.sender]"><span v-if="message.sender === 'assistant'" class="ph-consultation-message-avatar ai">AI</span><span v-else-if="message.sender === 'doctor'" class="ph-consultation-message-avatar doctor">{{ selectedSession.title[0] }}</span><div class="ph-consultation-message"><small v-if="message.sender !== 'system'">{{ message.sender === 'patient' ? '我' : message.sender === 'doctor' ? selectedSession.title : 'AI 助手' }} · {{ message.time }}</small><p>{{ message.text }}</p></div></div></div><form class="ph-consultation-composer" @submit.prevent="send"><div class="ph-consultation-composer-tools"><button type="button" class="ph-consultation-attachment" disabled title="图片上传即将支持"><Icon name="image" /> 图片即将支持</button><span v-if="selectedSession.readOnly">{{ readOnlyNotice }}</span><span v-else>{{ isAi ? 'AI 会话独立保存' : '消息将发送给医生' }}</span></div><div class="ph-consultation-composer-row"><textarea v-model="draft" :disabled="selectedSession.readOnly" aria-label="输入消息" :placeholder="selectedSession.readOnly ? readOnlyNotice : isAi ? '向 AI 助手描述你想了解的问题……' : '描述症状、感受或想告诉医生的情况……'" rows="2"></textarea><button class="ph-primary" type="submit" :disabled="selectedSession.readOnly || !draft.trim()" aria-label="发送消息"><Icon name="arrow" /></button></div></form></section>
    </div>
    <ClinicalResult v-if="linkedVisit?.result" :visit="linkedVisit"/>
    <dialog ref="consentDialog" class="rd-consent-dialog" aria-label="授权本次消息摘要"><h2>确认消息范围</h2><p>本地模拟摘要（不外发）将整理以下 {{ consentIds.length }} 条医患文字消息中的症状与病史，仅供许知遥医生参考。不会生成诊断或用药建议，也不会读取独立 AI 会话。之后新增的消息需要再次授权。</p><ul><li v-for="item in linkedVisit?.messages.filter(m => consentIds.includes(m.id))" :key="item.id">{{ item.sender === 'patient' ? '我' : '医生' }}：{{ item.text }}</li></ul><label><input v-model="consentChecked" type="checkbox">我同意本次提供方、用途和消息范围</label><div><button class="ph-soft-button" @click="consentDialog?.close()">暂不授权</button><button class="ph-primary" :disabled="!consentChecked || busy" @click="confirmConsent">确认授权</button></div></dialog>
    <p class="ph-consultation-footnote"><Icon name="shield" /> 医生聊天记录与 AI 助手会话相互隔离，当前仅展示虚构演示内容。</p>
  </section>
</template>

<style scoped>
.rd-patient-consent{display:flex;justify-content:space-between;gap:22px;padding:20px 24px;margin-bottom:22px;background:#f5fbfc;border:1px solid #dbedf0;border-radius:12px;color:#638894}.rd-patient-consent strong{font-size:13px}.rd-patient-consent p{font-size:11px;line-height:1.9;margin:8px 0}.rd-patient-consent small{font-size:10px}.rd-patient-consent>div:last-child{flex-shrink:0;max-width:280px;display:flex;flex-wrap:wrap;gap:8px;align-items:center}.rd-patient-consent button{font-size:11px;padding:7px 12px!important}.rd-patient-consent label{display:flex;gap:8px;align-items:center;font-size:10px}.rd-consent-dialog{width:min(560px,calc(100% - 30px));max-height:80vh;overflow-y:auto;border:1px solid #dcebf1;border-radius:18px;padding:28px;color:#53758d;font:13px/1.9 system-ui,"Microsoft YaHei",sans-serif}.rd-consent-dialog::backdrop{background:#26445966}.rd-consent-dialog h2{font-size:20px;margin-bottom:16px}.rd-consent-dialog p{margin:14px 0}.rd-consent-dialog ul{padding:15px 25px;background:#f5f9fc;border-radius:10px}.rd-consent-dialog li{overflow-wrap:anywhere;white-space:pre-wrap}.rd-consent-dialog>div{display:flex;justify-content:flex-end;gap:12px;margin-top:20px}.rd-consent-dialog button{padding:9px 15px!important}.rd-consent-dialog label{display:flex;align-items:center;gap:8px}.rd-consent-dialog input{width:16px;height:16px}@media(max-width:760px){.rd-patient-consent{flex-direction:column;padding:17px;gap:12px}.rd-patient-consent>div:last-child{max-width:none}}
</style>
