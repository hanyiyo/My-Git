<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, notificationStream } from '../api'

interface Notification { id: number; cursorNo: number | null; eventType: string; objectId: string; payload: string; createdAt: number; readAt: number | null }
interface Home { pendingApplicationCount: number; activeAppointmentId: string | null }
const router = useRouter()
const messages = ref<Notification[]>([])
const unread = ref(0)
const pendingCount = ref(0)
const tab = ref<'pending' | 'normal'>('pending')
const selected = ref<Notification | null>(null)
const state = ref('正在连接')
const error = ref('')
let cursor = 0
let controller: AbortController | null = null
let timer: ReturnType<typeof setTimeout> | undefined
let polling: ReturnType<typeof setInterval> | undefined
let syncing: Promise<void> | null = null
let generation = 0
const pendingTypes = new Set(['SUBSTITUTE_INVITED', 'SUSPENSION_REVIEW'])
const pendingMessages = computed(() => messages.value.filter(item => pendingTypes.has(item.eventType)))
const normalMessages = computed(() => messages.value.filter(item => !pendingTypes.has(item.eventType)))
const visibleMessages = computed(() => tab.value === 'pending' ? pendingMessages.value : normalMessages.value)
const unreadLabel = computed(() => `未读 ${unread.value}`)
function formatTime(value: number) { return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Shanghai' }).format(value) }
function merge(batch: Notification[]) {
  for (const item of batch) {
    cursor = Math.max(cursor, item.cursorNo ?? 0)
    const existing = messages.value.find(value => value.id === item.id)
    if (existing) Object.assign(existing, item); else messages.value.push(item)
  }
  messages.value.sort((a, b) => (b.cursorNo ?? b.id) - (a.cursorNo ?? a.id))
}
function sync(current = generation) {
  if (syncing) return syncing
  const pending = (async () => {
    let batch: Notification[]
    do {
      batch = await api<Notification[]>(`/notifications?after=${cursor}&limit=100`)
      if (current !== generation) return
      merge(batch)
    } while (batch.length === 100)
    const [count, home] = await Promise.all([api<number>('/notifications/unread'), api<Home>('/doctor/home')])
    if (current === generation) { unread.value = count; pendingCount.value = home.pendingApplicationCount; error.value = '' }
  })().catch(failure => { if (current === generation) error.value = failure instanceof Error ? failure.message : '通知暂时不可用' }).finally(() => { if (syncing === pending) syncing = null })
  syncing = pending
  return pending
}
async function connect(current: number) {
  if (current !== generation) return
  controller = new AbortController()
  try { await notificationStream(cursor, controller.signal, () => { state.value = '实时连接'; void sync(current) }) } catch { /* polling keeps the persisted inbox usable */ }
  if (current === generation) { state.value = '轮询补齐'; timer = setTimeout(() => connect(current), 10000) }
}
async function open(item: Notification) {
  selected.value = item
  if (!item.readAt) {
    try { await api(`/notifications/${item.id}/read`, 'POST'); item.readAt = Date.now(); unread.value = Math.max(0, unread.value - 1) }
    catch (failure) { error.value = failure instanceof Error ? failure.message : '通知暂时无法标记为已读' }
  }
}
function closeDetail() { selected.value = null }
function openTarget(item: Notification) {
  closeDetail()
  if (item.eventType === 'SUBSTITUTE_INVITED' || item.eventType === 'SUSPENSION_REVIEW') void router.push(`/doctor/governance?request=${encodeURIComponent(item.objectId)}`)
  else if (item.eventType === 'CLINICAL_MESSAGE') void router.push(`/doctor/clinical/${encodeURIComponent(item.objectId)}`)
  else void router.push('/doctor')
}
function reset() {
  generation += 1; controller?.abort(); controller = null
  if (timer) clearTimeout(timer)
  cursor = 0; messages.value = []; unread.value = 0; pendingCount.value = 0; syncing = null
}
onMounted(() => { const current = generation; void sync(current); void connect(current); polling = setInterval(() => void sync(generation), 10000) })
onUnmounted(() => { reset(); if (polling) clearInterval(polling) })
</script>

<template>
  <section class="doctor-notifications" aria-labelledby="doctor-notifications-title">
    <header class="doctor-notifications-heading"><div><p class="eyebrow">PERSONAL INBOX</p><h1 id="doctor-notifications-title">站内通知</h1><p>通知阅读只更新收件记录，不会代替替诊确认或审批。</p></div><span class="notification-connection">{{ state }}</span></header>
    <div class="notification-summary" aria-label="通知摘要"><strong>{{ unreadLabel }}</strong><span>业务待处理 {{ pendingCount }}</span><span>通知断线会自动轮询补齐</span></div>
    <p v-if="error" class="doctor-notifications-error" role="alert">{{ error }} <button type="button" @click="sync()">重试</button></p>
    <div class="notification-tabs" role="tablist" aria-label="通知分区"><button type="button" role="tab" :aria-selected="tab === 'pending'" @click="tab = 'pending'">待处理 <b>{{ pendingMessages.length }}</b></button><button type="button" role="tab" :aria-selected="tab === 'normal'" @click="tab = 'normal'">普通通知 <b>{{ normalMessages.length }}</b></button></div>
    <section class="notification-list card" :aria-label="tab === 'pending' ? '待处理通知' : '普通通知'"><p v-if="!visibleMessages.length" class="notification-empty">暂无{{ tab === 'pending' ? '待处理通知' : '普通通知' }}</p><ul v-else><li v-for="item in visibleMessages" :key="item.id" :class="{ unread: !item.readAt }"><button type="button" class="notification-item" @click="open(item)"><span><strong>{{ item.eventType === 'SUBSTITUTE_INVITED' ? '替诊邀请' : item.eventType === 'SUSPENSION_REVIEW' ? '申请审核提醒' : item.eventType === 'CLINICAL_MESSAGE' ? '接诊消息' : '业务通知' }}</strong><small>{{ formatTime(item.createdAt) }} · {{ item.readAt ? '已读' : '未读' }}</small></span><p>{{ item.payload }}</p><i aria-hidden="true">›</i></button></li></ul></section>
    <dialog v-if="selected" open class="notification-detail" aria-labelledby="notification-detail-title"><div class="notification-detail-card"><button type="button" class="notification-close" aria-label="关闭通知详情" @click="closeDetail">×</button><p class="eyebrow">通知详情</p><h2 id="notification-detail-title">{{ selected.eventType === 'SUBSTITUTE_INVITED' ? '替诊邀请' : selected.eventType === 'SUSPENSION_REVIEW' ? '申请审核提醒' : '业务通知' }}</h2><p>{{ selected.payload }}</p><small>{{ formatTime(selected.createdAt) }}</small><div class="notification-actions"><button v-if="selected.eventType === 'SUBSTITUTE_INVITED' || selected.eventType === 'SUSPENSION_REVIEW' || selected.eventType === 'CLINICAL_MESSAGE'" type="button" @click="openTarget(selected)">前往处理</button><button type="button" class="secondary" @click="closeDetail">关闭</button></div></div></dialog>
  </section>
</template>

<style scoped>
.doctor-notifications { display: grid; gap: 18px; }.doctor-notifications-heading { display: flex; justify-content: space-between; gap: 18px; align-items: end; }.doctor-notifications h1 { margin: 0 0 8px; }.doctor-notifications-heading p:not(.eyebrow) { margin: 0; color: #64748b; }.eyebrow { margin: 0 0 6px; color: #2563eb; letter-spacing: .14em; font-size: .76rem; font-weight: 700; }.notification-connection { color: #2563eb; font-size: .9rem; }.notification-summary { display: flex; flex-wrap: wrap; gap: 14px; align-items: center; padding: 14px 16px; border-radius: 12px; background: #eff6ff; color: #334155; }.notification-summary strong { color: #1d4ed8; }.doctor-notifications-error { margin: 0; color: #b42318; }.doctor-notifications-error button { border: 0; color: #1d4ed8; background: transparent; cursor: pointer; text-decoration: underline; }.notification-tabs { display: flex; gap: 8px; border-bottom: 1px solid #dbe4ef; }.notification-tabs button { border: 0; border-bottom: 3px solid transparent; padding: 10px 12px; color: #64748b; background: transparent; cursor: pointer; }.notification-tabs button[aria-selected='true'] { border-bottom-color: #2563eb; color: #1d4ed8; font-weight: 700; }.notification-tabs b { margin-left: 4px; }.notification-list { padding: 0; overflow: hidden; }.notification-list ul { list-style: none; padding: 0; margin: 0; }.notification-list li + li { border-top: 1px solid #e5e7eb; }.notification-item { display: grid; grid-template-columns: 1fr auto; gap: 8px 14px; width: 100%; padding: 16px 18px; border: 0; color: inherit; background: #fff; text-align: left; cursor: pointer; }.notification-item:hover { background: #f8fbff; }.notification-item span { display: flex; gap: 10px; align-items: center; }.notification-item strong { color: #1e3a8a; }.notification-item small { color: #64748b; }.notification-item p { grid-column: 1; margin: 0; color: #475569; }.notification-item i { grid-column: 2; grid-row: 1 / span 2; align-self: center; font-size: 1.5rem; color: #94a3b8; }.notification-list li.unread .notification-item strong::before { content: ''; display: inline-block; width: 7px; height: 7px; margin-right: 6px; border-radius: 50%; background: #2563eb; vertical-align: middle; }.notification-empty { padding: 36px 20px; margin: 0; color: #64748b; text-align: center; }.notification-detail { position: fixed; inset: 0; z-index: 10; display: grid; place-items: center; width: 100%; height: 100%; padding: 20px; border: 0; background: #102a4366; }.notification-detail-card { position: relative; width: min(520px, 100%); padding: 28px; border-radius: 16px; background: #fff; box-shadow: 0 20px 60px #102a4340; }.notification-detail-card h2 { margin: 0 0 12px; }.notification-detail-card small { color: #64748b; }.notification-close { position: absolute; top: 12px; right: 14px; border: 0; color: #64748b; background: transparent; font-size: 1.5rem; cursor: pointer; }.notification-actions { display: flex; gap: 10px; margin-top: 24px; }.notification-actions button { border: 0; border-radius: 8px; padding: 10px 14px; color: #fff; background: #2563eb; cursor: pointer; }.notification-actions .secondary { color: #334155; background: #e2e8f0; }
@media (max-width: 520px) { .doctor-notifications-heading { align-items: start; flex-direction: column; }.notification-item span { align-items: start; flex-direction: column; gap: 3px; } }
</style>

