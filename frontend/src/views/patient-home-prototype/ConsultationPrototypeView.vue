<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from './PrototypeIcon.vue'

type SessionKind = 'AI' | 'DOCTOR'
interface ChatMessage { id: string; sender: 'patient' | 'assistant' | 'doctor' | 'system'; text: string; time: string }
interface ConsultationSession { id: string; kind: SessionKind; title: string; subtitle: string; date: string; status: string; readOnly: boolean; messages: ChatMessage[] }

const props = withDefaults(defineProps<{ initialConversation?: string }>(), { initialConversation: 'ai' })
const sessions = ref<ConsultationSession[]>([
  { id: 'ai', kind: 'AI', title: 'AI 助手', subtitle: '一般健康建议 · 就医信息整理', date: '今天 09:26', status: '可继续交流', readOnly: false, messages: [{ id: 'ai-welcome', sender: 'assistant', text: '你好，林同学。我可以帮你整理就医流程、预约信息和一般健康建议。你可以先告诉我想了解什么。', time: '09:26' }, { id: 'ai-boundary', sender: 'system', text: 'AI 内容仅供参考，不提供诊断、处方或替代医生判断。', time: '09:26' }] },
  { id: 'today-online-waiting', kind: 'DOCTOR', title: '许知遥医生', subtitle: '内科 · 线上问诊 · 今天 09:00–11:30', date: '今天 09:18', status: '医生已叫号 · 问诊中', readOnly: false, messages: [{ id: 'doctor-welcome', sender: 'doctor', text: '你好，我是许知遥医生。你可以先描述这次最困扰你的症状和持续时间。', time: '09:18' }, { id: 'patient-note', sender: 'patient', text: '最近早上起床后嗓子有些不舒服，想先说明一下情况。', time: '09:20' }] },
  { id: 'completed-consultation', kind: 'DOCTOR', title: '陈予安医生', subtitle: '全科医学科 · 已完成接诊', date: '11月06日', status: '医生已提交结果 · 只读', readOnly: true, messages: [{ id: 'completed-patient', sender: 'patient', text: '谢谢医生，我会按提醒安排后续复诊。', time: '11月06日 15:12' }, { id: 'completed-doctor', sender: 'doctor', text: '本次接诊已完成，详细结果请回到“我的挂号”查看。', time: '11月06日 15:16' }] },
])
const selectedId = ref(sessions.value.some(item => item.id === props.initialConversation) ? props.initialConversation : 'ai')
const draft = ref('')
const selectedSession = computed(() => sessions.value.find(item => item.id === selectedId.value) ?? sessions.value[0]!)
const isAi = computed(() => selectedSession.value.kind === 'AI')

function chooseSession(session: ConsultationSession) {
  selectedId.value = session.id
  draft.value = ''
}
function send() {
  const text = draft.value.trim()
  if (!text || selectedSession.value.readOnly) return
  const session = selectedSession.value
  session.messages.push({ id: `${session.id}-${Date.now()}`, sender: 'patient', text, time: '刚刚' })
  draft.value = ''
  if (session.kind === 'AI') {
    window.setTimeout(() => session.messages.push({ id: `${session.id}-reply-${Date.now()}`, sender: 'assistant', text: '我已记录你的问题。可以继续补充具体时间、就医方式或想了解的流程，我会提供一般信息供你参考。', time: '刚刚' }), 180)
  }
}
</script>

<template>
  <section class="ph-consultation-page" data-testid="consultation-page" aria-labelledby="consultation-page-title">
    <header class="ph-consultation-heading"><div><span class="ph-eyebrow">PATIENT SERVICES · CONSULTATION ROOM</span><h1 id="consultation-page-title">图文诊室</h1><p>选择一个会话继续交流。医生会话与 AI 助手彼此独立。</p></div><div class="ph-consultation-heading-status"><span class="ph-live-dot"></span><span>{{ isAi ? 'AI 助手在线' : selectedSession.status }}</span></div></header>
    <div class="ph-consultation-workspace">
      <aside class="ph-consultation-sessions" aria-label="近一个月会话"><div class="ph-consultation-sessions-heading"><div><span class="ph-eyebrow">RECENT SESSIONS</span><h2>会话记录</h2></div><button class="ph-icon-button" aria-label="新建AI会话" title="新建AI会话" @click="selectedId = 'ai'"><Icon name="plus" /></button></div><p class="ph-consultation-retention">近一个月 · 点击会话查看记录</p><button v-for="session in sessions" :key="session.id" type="button" :class="['ph-consultation-session', { active: session.id === selectedId, doctor: session.kind === 'DOCTOR' }]" @click="chooseSession(session)"><span :class="['ph-consultation-session-avatar', session.kind === 'AI' ? 'ai' : 'doctor']">{{ session.kind === 'AI' ? 'AI' : session.title[0] }}</span><span><strong>{{ session.title }}</strong><small>{{ session.subtitle }}</small><time>{{ session.date }}</time></span><Icon name="chevron" /></button></aside>
      <section class="ph-consultation-chat" aria-label="当前聊天窗口"><header class="ph-consultation-chat-heading"><div class="ph-consultation-chat-person"><span :class="['ph-consultation-chat-avatar', isAi ? 'ai' : 'doctor']">{{ isAi ? 'AI' : selectedSession.title[0] }}</span><div><h2>{{ selectedSession.title }}</h2><p>{{ selectedSession.subtitle }}</p></div></div><span :class="['ph-consultation-chat-status', { readonly: selectedSession.readOnly }]">{{ selectedSession.status }}</span></header><div class="ph-consultation-chat-notice"><Icon :name="isAi ? 'sparkles' : 'shield'" /><span>{{ isAi ? 'AI 仅提供一般建议，不构成诊断或病历结论。' : selectedSession.readOnly ? '医生已提交结果，本次医生会话已转为只读。' : '请勿在聊天中发送紧急求助；如有急症请及时线下就医。' }}</span></div><div class="ph-consultation-messages" aria-live="polite"><div v-for="message in selectedSession.messages" :key="message.id" :class="['ph-consultation-message-row', message.sender]"><span v-if="message.sender === 'assistant'" class="ph-consultation-message-avatar ai">AI</span><span v-else-if="message.sender === 'doctor'" class="ph-consultation-message-avatar doctor">{{ selectedSession.title[0] }}</span><div class="ph-consultation-message"><small v-if="message.sender !== 'system'">{{ message.sender === 'patient' ? '我' : message.sender === 'doctor' ? selectedSession.title : 'AI 助手' }} · {{ message.time }}</small><p>{{ message.text }}</p></div></div></div><form class="ph-consultation-composer" @submit.prevent="send"><div class="ph-consultation-composer-tools"><button type="button" class="ph-consultation-attachment" disabled title="图片上传即将支持"><Icon name="image" /> 图片即将支持</button><span v-if="selectedSession.readOnly">本次医生会话已结束</span><span v-else>{{ isAi ? 'AI 会话独立保存' : '消息将发送给医生' }}</span></div><div class="ph-consultation-composer-row"><textarea v-model="draft" :disabled="selectedSession.readOnly" aria-label="输入消息" :placeholder="selectedSession.readOnly ? '医生会话已结束，当前仅可查看历史消息' : isAi ? '向 AI 助手描述你想了解的问题……' : '描述症状、感受或想告诉医生的情况……'" rows="2"></textarea><button class="ph-primary" type="submit" :disabled="selectedSession.readOnly || !draft.trim()" aria-label="发送消息"><Icon name="arrow" /></button></div></form></section>
    </div>
    <p class="ph-consultation-footnote"><Icon name="shield" /> 医生聊天记录与 AI 助手会话相互隔离，当前仅展示虚构演示内容。</p>
  </section>
</template>
