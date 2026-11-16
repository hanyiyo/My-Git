<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { api } from '../api'
import { useNotificationRefresh } from '../useNotificationRefresh'

interface QueueEntry {
  appointmentId: string
  scheduleId: string
  doctorId: string
  patientIdentifier: string
  clinicDate: string
  session: string
  serviceMode: string
  source: string
  queueGroup: number
  queuedAt: number
  checkedInAt: number
  restorationCount: number
  state: string
  calledAt?: number | null
  startedAt?: number | null
  missedAt?: number | null
  completedAt?: number | null
}

const queue = ref<QueueEntry[]>([])
const error = ref('')
const loading = ref(false)
const busy = ref(false)
const search = ref('')
const dateFilter = ref('')
const scheduleFilter = ref('')
const modeFilter = ref('ALL')
const preview = ref<QueueEntry | null>(null)
let requestNo = 0

const stateLabels: Record<string, string> = { WAITING: '候诊中', CALLED: '已叫号', IN_CONSULTATION: '接诊中', MISSED: '已过号', COMPLETED: '已结束' }
const groupLabels: Record<number, string> = { 1: '预约优先', 2: '现场挂号', 3: '恢复排队' }
const scheduleOptions = computed(() => {
  const seen = new Map<string, string>()
  for (const entry of queue.value) seen.set(entry.scheduleId, `${entry.clinicDate} · ${entry.session === 'AM' ? '上午' : '下午'} · ${entry.serviceMode === 'ONLINE' ? '线上' : '线下'}`)
  return [...seen].map(([id, label]) => ({ id, label }))
})
const activeEncounter = computed(() => queue.value.find(entry => entry.state === 'CALLED' || entry.state === 'IN_CONSULTATION') ?? null)
const waitingHeadId = computed(() => queue.value.find(entry => entry.state === 'WAITING')?.appointmentId ?? null)
const visibleQueue = computed(() => {
  const needle = search.value.trim().toLowerCase()
  return queue.value.filter(entry => {
    const matchesSearch = !needle || entry.patientIdentifier.toLowerCase().includes(needle) || entry.appointmentId.toLowerCase().includes(needle)
    return matchesSearch && (modeFilter.value === 'ALL' || entry.serviceMode === modeFilter.value)
  })
})
function contextLabel(entry: QueueEntry) { return `${entry.clinicDate} · ${entry.session === 'AM' ? '上午' : '下午'} · ${entry.serviceMode === 'ONLINE' ? '线上问诊' : '线下面诊'}` }
function canCall(entry: QueueEntry) { return entry.state === 'WAITING' && entry.appointmentId === waitingHeadId.value && !activeEncounter.value }
function formatTime(value?: number | null) { return value ? new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(value) : '—' }
async function load() {
  const current = ++requestNo
  loading.value = true; error.value = ''
  const query = new URLSearchParams()
  if (dateFilter.value) query.set('date', dateFilter.value)
  if (scheduleFilter.value) query.set('scheduleId', scheduleFilter.value)
  try {
    const result = await api<QueueEntry[]>(`/doctor/reception${query.size ? `?${query.toString()}` : ''}`)
    if (current === requestNo) queue.value = result
  } catch (failure) {
    if (current === requestNo) error.value = failure instanceof Error ? failure.message : '候诊队列加载失败，请重试'
  } finally { if (current === requestNo) loading.value = false }
}
async function action(id: string, operation: 'call' | 'start' | 'miss') {
  busy.value = true; error.value = ''
  try { await api(`/doctor/reception/${id}/${operation}`, 'POST'); closePreview(); await load() }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '操作失败，请重试' }
  finally { busy.value = false }
}
async function openPreview(entry: QueueEntry) { preview.value = entry; await nextTick(); document.querySelector<HTMLDialogElement>('#doctor-reception-preview')?.showModal() }
function closePreview() { const dialog = document.querySelector<HTMLDialogElement>('#doctor-reception-preview'); if (dialog?.open) dialog.close(); preview.value = null }
watch([dateFilter, scheduleFilter], () => { void load() })
onMounted(load)
useNotificationRefresh(load)
</script>

<template>
  <RouterLink class="doctor-reception-back" to="/doctor">返回工作台</RouterLink>
  <section class="doctor-reception" aria-labelledby="doctor-reception-title">
    <header class="doctor-reception-heading"><div><p class="eyebrow">RECEPTION QUEUE</p><h1 id="doctor-reception-title">候诊与接诊</h1><p class="hint">先预览患者，再明确叫当前队首。搜索只筛选当前显示，不改变真实队列顺序。</p></div><button type="button" class="secondary" :disabled="loading" @click="load">刷新队列</button></header>
    <form class="doctor-reception-filters" @submit.prevent>
      <label>日期<input v-model="dateFilter" type="date" aria-label="按日期筛选" /></label>
      <label>排班<select v-model="scheduleFilter" aria-label="按排班筛选"><option value="">全部排班</option><option v-for="item in scheduleOptions" :key="item.id" :value="item.id">{{ item.label }}</option></select></label>
      <label>方式<select v-model="modeFilter" aria-label="按就诊方式筛选"><option value="ALL">全部方式</option><option value="ONLINE">线上问诊</option><option value="OFFLINE">线下面诊</option></select></label>
      <label class="search-field">搜索患者或预约<input v-model="search" type="search" placeholder="输入姓名或预约编号" /></label>
    </form>
    <p v-if="error" role="alert" class="notice">{{ error }} <button type="button" class="inline-action" @click="load">重试</button></p>
    <p v-if="activeEncounter" class="active-banner" role="status">当前接诊：{{ activeEncounter.patientIdentifier }} · {{ contextLabel(activeEncounter) }}。完成当前接诊后才能叫下一位。</p>
    <p v-if="loading && !queue.length" class="hint" role="status">队列加载中…</p>
    <p v-else-if="!visibleQueue.length" class="empty-state">暂无符合筛选条件的候诊记录。</p>
    <ol v-else class="doctor-queue-list" aria-label="候诊队列">
      <li v-for="(entry, index) in visibleQueue" :key="entry.appointmentId" :class="['doctor-queue-item', { active: entry.appointmentId === activeEncounter?.appointmentId }]">
        <div class="queue-position">{{ index + 1 }}</div>
        <div class="queue-main"><div class="queue-title"><strong>{{ entry.patientIdentifier }}</strong><span>{{ stateLabels[entry.state] ?? entry.state }}</span></div><p>{{ contextLabel(entry) }} · {{ groupLabels[entry.queueGroup] ?? `第 ${entry.queueGroup} 组` }}</p><p class="hint">预约 {{ entry.appointmentId }} · 入队 {{ formatTime(entry.queuedAt) }} · 恢复 {{ entry.restorationCount }} 次</p></div>
        <div class="queue-actions"><button type="button" class="secondary" @click="openPreview(entry)">查看患者</button><button v-if="entry.state === 'WAITING'" type="button" :disabled="busy || !canCall(entry)" @click="action(entry.appointmentId, 'call')">{{ canCall(entry) ? '叫号并开始接诊' : activeEncounter ? '当前接诊中' : '等待前一位' }}</button><button v-if="entry.state === 'CALLED'" type="button" :disabled="busy" @click="action(entry.appointmentId, 'start')">开始接诊</button><button v-if="entry.state === 'CALLED' || entry.state === 'IN_CONSULTATION'" type="button" class="danger" :disabled="busy" @click="action(entry.appointmentId, 'miss')">标记过号</button><RouterLink v-if="entry.state === 'IN_CONSULTATION' || entry.state === 'COMPLETED'" class="secondary-link" :to="`/doctor/clinical/${entry.appointmentId}`">打开问诊记录</RouterLink></div>
      </li>
    </ol>
  </section>
  <dialog id="doctor-reception-preview" aria-label="患者候诊预览" @cancel="closePreview" @close="closePreview"><template v-if="preview"><header><h2>患者候诊预览</h2><button type="button" aria-label="关闭患者候诊预览" @click="closePreview">×</button></header><dl><div><dt>患者</dt><dd>{{ preview.patientIdentifier }}</dd></div><div><dt>场次</dt><dd>{{ contextLabel(preview) }}</dd></div><div><dt>队列状态</dt><dd>{{ stateLabels[preview.state] ?? preview.state }}</dd></div><div><dt>候诊组</dt><dd>{{ groupLabels[preview.queueGroup] ?? preview.queueGroup }}</dd></div><div><dt>叫号时间</dt><dd>{{ formatTime(preview.calledAt) }}</dd></div></dl><p class="hint">预览不会改变候诊状态或队列顺序。</p><footer><button type="button" class="secondary" @click="closePreview">关闭</button><button v-if="preview.state === 'WAITING' && canCall(preview)" type="button" :disabled="busy" @click="action(preview.appointmentId, 'call')">叫号并开始接诊</button></footer></template></dialog>
</template>

<style scoped>
.doctor-reception { display: grid; gap: 18px; }.doctor-reception-back { display: inline-block; margin-bottom: 10px; color: #2563eb; }.doctor-reception-heading { display: flex; justify-content: space-between; align-items: end; gap: 18px; }.doctor-reception-heading h1 { margin: 0 0 8px; }.doctor-reception-heading p { margin: 0; }.eyebrow { color: #2563eb; font-size: .76rem; font-weight: 700; letter-spacing: .14em; }.doctor-reception-filters { display: grid; grid-template-columns: 150px 1fr 160px minmax(220px, 1.4fr); gap: 12px; padding: 16px; border: 1px solid #dbe4ef; border-radius: 12px; background: #fff; }.doctor-reception-filters label { display: grid; gap: 5px; color: #475569; font-size: .86rem; }.doctor-reception-filters input, .doctor-reception-filters select { min-width: 0; border: 1px solid #cbd5e1; border-radius: 7px; padding: 9px 10px; background: #fff; }.notice { margin: 0; color: #b42318; }.inline-action { border: 0; color: #1d4ed8; background: transparent; cursor: pointer; }.active-banner { margin: 0; padding: 12px 14px; border-radius: 9px; color: #075985; background: #e0f2fe; }.empty-state { padding: 36px 16px; border: 1px dashed #cbd5e1; border-radius: 12px; color: #64748b; text-align: center; }.doctor-queue-list { display: grid; gap: 10px; margin: 0; padding: 0; list-style: none; }.doctor-queue-item { display: grid; grid-template-columns: 38px minmax(0, 1fr) auto; gap: 14px; align-items: center; padding: 16px; border: 1px solid #e2e8f0; border-radius: 12px; background: #fff; }.doctor-queue-item.active { border-color: #60a5fa; box-shadow: 0 0 0 2px #dbeafe; }.queue-position { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 50%; color: #1d4ed8; background: #eff6ff; font-weight: 700; }.queue-main { min-width: 0; }.queue-title { display: flex; gap: 10px; align-items: center; }.queue-title span { padding: 3px 8px; border-radius: 999px; color: #475569; background: #f1f5f9; font-size: .8rem; }.queue-main p { margin: 5px 0 0; }.queue-actions { display: flex; flex-wrap: wrap; justify-content: end; gap: 8px; }.queue-actions button, .queue-actions a, .doctor-reception-heading button, dialog button { border: 0; border-radius: 7px; padding: 9px 12px; color: #fff; background: #2563eb; cursor: pointer; text-decoration: none; font: inherit; }.queue-actions button:disabled { cursor: not-allowed; opacity: .55; }.queue-actions .secondary, .doctor-reception-heading .secondary, dialog .secondary, .secondary-link { color: #1d4ed8; background: #eff6ff; }.queue-actions .danger { color: #b42318; background: #fee2e2; }.doctor-reception-heading button { white-space: nowrap; }.doctor-reception-heading button:disabled { opacity: .55; }
dialog { width: min(440px, calc(100vw - 32px)); border: 0; border-radius: 14px; padding: 0; box-shadow: 0 18px 50px #0f172a33; }dialog::backdrop { background: #0f172a66; }dialog header, dialog footer { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 16px 18px; border-bottom: 1px solid #e2e8f0; }dialog header h2 { margin: 0; font-size: 1.1rem; }dialog header button { padding: 2px 8px; color: #475569; background: transparent; font-size: 1.3rem; }dialog dl { display: grid; gap: 10px; padding: 18px; margin: 0; }dialog dl div { display: flex; justify-content: space-between; gap: 12px; }dialog dt { color: #64748b; }dialog dd { margin: 0; color: #0f172a; font-weight: 600; text-align: right; }dialog > p { padding: 0 18px; }dialog footer { justify-content: end; border-top: 1px solid #e2e8f0; border-bottom: 0; }
@media (max-width: 760px) { .doctor-reception-heading { align-items: start; flex-direction: column; }.doctor-reception-filters { grid-template-columns: repeat(2, minmax(0, 1fr)); }.search-field { grid-column: 1 / -1; }.doctor-queue-item { grid-template-columns: 32px minmax(0, 1fr); }.queue-actions { grid-column: 2; justify-content: start; } }
@media (max-width: 380px) { .doctor-reception-filters { grid-template-columns: 1fr; }.search-field { grid-column: auto; }.doctor-queue-item { gap: 9px; padding: 12px; }.queue-actions button, .queue-actions a { flex: 1 1 auto; text-align: center; } }
</style>
