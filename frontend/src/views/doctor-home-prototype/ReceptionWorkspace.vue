<script setup lang="ts">
// Confirmed Q9–Q17 layout, inside the existing doctor shell; all data is fictional.
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import Icon from '../patient-home-prototype/PrototypeIcon.vue'
import ClinicalResult from './ClinicalResult.vue'
import { blankDraft, callVisit, canMiss, formatTime, missVisit, money, refreshSummary, saveClinicalDraft, statusText, submitClinical, useReceptionDemo, waitingQueue, sendVisitMessage, type ClinicalDraft, type DemoVisit } from './reception-demo'
import './reception-workspace.css'

const props = defineProps<{ patientId?: string }>()
const emit = defineEmits<{ patient: [id: string]; focus: [value: boolean] }>()
const { state, active, now, error, warning, busy } = useReceptionDemo()
const group = ref<'am' | 'pm'>(active.value?.group ?? (now.value >= Date.parse('2026-11-12T13:30:00+08:00') ? 'pm' : 'am'))
const search = ref(''), status = ref('待接诊'), focused = ref(false), aiCollapsed = ref(false), editorTab = ref<'record' | 'medicine'>('record')
const drawer = ref<HTMLDialogElement>(), historyDialog = ref<HTMLDialogElement>(), confirmDialog = ref<HTMLDialogElement>(), missedDialog = ref<HTMLDialogElement>()
const message = ref(''), medicineId = ref(''), saveMessage = ref('草稿尚未修改'), localError = ref(''), dirty = ref(false), saving = ref(false)
const localDraft = ref<ClinicalDraft>(blankDraft())
let baseVersion = 0, saveTask = Promise.resolve(true)
let dialogOpener: HTMLElement | null = null
const selected = computed(() => state.value.visits.find(v => v.id === props.patientId) ?? active.value ?? waitingQueue(group.value)[0] ?? state.value.visits.find(v => v.group === group.value)!)
const queue = computed(() => waitingQueue(group.value))
const filtered = computed(() => {
  const items = status.value === '待接诊' ? queue.value : state.value.visits.filter(v => v.group === group.value && (status.value === '就诊中' ? v.status === 'IN_PROGRESS' : status.value === '已完成' ? v.status === 'COMPLETED' : status.value === '过号' ? ['MISSED', 'EXPIRED'].includes(v.status) : v.status === 'PENDING_VISIT'))
  return items.filter(v => `${v.name}${v.number}`.includes(search.value.trim()))
})
const clinical = computed(() => selected.value)
const isActive = computed(() => clinical.value.status === 'IN_PROGRESS')
const summary = computed(() => clinical.value.summary)
const summaryStale = computed(() => !!summary.value && clinical.value.messages.some(m => !summary.value!.messageIds.includes(m.id)))
const total = computed(() => localDraft.value.medicines.reduce((sum, line) => sum + (state.value.medicines.find(m => m.id === line.medicineId)?.price ?? 0) * (Number(line.quantity) || 0), 0))
const availableMedicines = computed(() => state.value.medicines.filter(m => m.enabled && m.stock > m.held && !localDraft.value.medicines.some(l => l.medicineId === m.id)))
const draftFields = [ ['complaint', '主诉', true], ['present', '现病史', false], ['history', '既往史/过敏史', false], ['diagnosis', '诊断', true], ['advice', '处理建议', true] ] as const

function scrollToPatient() { void nextTick(() => window.scrollTo(0, 0)) }
function readDraft() { localDraft.value = JSON.parse(JSON.stringify(clinical.value.draft)); baseVersion = clinical.value.draftVersion; dirty.value = false; saveMessage.value = clinical.value.savedAt ? `已保存 ${formatTime(clinical.value.savedAt)}` : '草稿自动保存'; localError.value = '' }
watch(() => clinical.value.id, () => { readDraft(); focused.value = false; emit('focus', false); group.value = clinical.value.group }, { immediate: true })
watch(() => clinical.value.draftVersion, () => {
  if (saving.value) return
  if (!dirty.value) readDraft()
  else localError.value = '其他标签页更新了草稿。当前输入已保留，请复制需要的内容后重新载入。'
})
watch(() => clinical.value.status, value => { if (value !== 'IN_PROGRESS') confirmDialog.value?.close() })
function edit() { dirty.value = true; saveMessage.value = '正在保存…'; void persist() }
function persist(): Promise<boolean> {
  const id = clinical.value.id, snapshot: ClinicalDraft = JSON.parse(JSON.stringify(localDraft.value))
  saveTask = saveTask.then(async () => {
    if (!dirty.value || id !== clinical.value.id) return !dirty.value
    saving.value = true
    const success = await saveClinicalDraft(id, snapshot, baseVersion)
    if (success) { baseVersion = state.value.visits.find(v => v.id === id)!.draftVersion; dirty.value = JSON.stringify(localDraft.value) !== JSON.stringify(snapshot); saveMessage.value = dirty.value ? '正在保存…' : `已保存 ${formatTime(now.value)}`; localError.value = '' }
    else { saveMessage.value = '草稿未保存'; localError.value = error.value }
    saving.value = false
    return success
  })
  return saveTask
}
async function selectPatient(visit: Pick<DemoVisit, 'id' | 'group'>) {
  if (dirty.value && !await persist()) return
  emit('patient', visit.id); group.value = visit.group; focused.value = false; emit('focus', false); drawer.value?.close()
}
async function changeGroup() {
  if (dirty.value && !await persist()) { group.value = clinical.value.group; return }
  const first = waitingQueue(group.value)[0] ?? state.value.visits.find(v => v.group === group.value)!
  await selectPatient(first)
}
async function call() {
  if (active.value) { await selectPatient(active.value); focused.value = true; emit('focus', true); scrollToPatient(); return }
  if (await callVisit(clinical.value.id)) { emit('patient', clinical.value.id); focused.value = true; emit('focus', true); readDraft(); scrollToPatient() }
}
async function returnToQueue() { if (dirty.value && !await persist()) return; focused.value = false; emit('focus', false) }
function addMedicine() { if (!medicineId.value) return; localDraft.value.medicines.push({ medicineId: medicineId.value, quantity: 1, usage: '' }); medicineId.value = ''; edit() }
function openModal(element?: HTMLDialogElement) { dialogOpener = document.activeElement instanceof HTMLElement ? document.activeElement : null; element?.showModal() }
function restoreFocus() { dialogOpener?.focus() }
async function review() {
  localError.value = ''
  if (!localDraft.value.complaint.trim() || !localDraft.value.diagnosis.trim() || !localDraft.value.advice.trim()) { localError.value = '请填写主诉、诊断和处理建议。'; editorTab.value = 'record'; await nextTick(); document.querySelector<HTMLTextAreaElement>('.rd-editor textarea:invalid')?.focus(); return }
  if (localDraft.value.medicines.some(l => !Number.isInteger(l.quantity) || l.quantity < 1 || !l.usage.trim())) { localError.value = '请填写有效药品数量和用法用量。'; editorTab.value = 'medicine'; return }
  if (await persist()) openModal(confirmDialog.value)
}
async function finish() { if (dirty.value && !await persist()) return; if (await submitClinical(clinical.value.id, baseVersion)) { confirmDialog.value?.close(); dirty.value = false; localError.value = ''; scrollToPatient() } }
async function send() { if (await sendVisitMessage(clinical.value.id, 'doctor', message.value)) message.value = '' }
async function markMissed() { if (dirty.value && !await persist()) return; if (await missVisit(clinical.value.id)) { missedDialog.value?.close(); focused.value = false; emit('focus', false) } }
watch(() => props.patientId, () => { if (clinical.value.status === 'IN_PROGRESS' && active.value?.id === clinical.value.id) { focused.value = true; emit('focus', true) } }, { immediate: true })
onBeforeUnmount(() => { if (dirty.value) void persist(); emit('focus', false) })
</script>

<template>
  <section class="rd-workspace" aria-label="医生接诊工作台">
    <p v-if="warning || error || localError" class="rd-alert" role="alert">{{ localError || error || warning }} <button v-if="localError.includes('草稿')" class="dh-link" @click="readDraft">重新载入草稿</button></p>
    <template v-if="!focused">
      <div class="rd-overview"><div><span class="dh-eyebrow">PATIENT QUEUE</span><h2>每一位患者，都值得专注回应</h2><p>先了解就诊资料，再按队列顺序开始接诊。</p></div><span class="rd-live"><i></i>2026-11-12 · {{ formatTime(now) }}</span></div>
      <div class="rd-waiting-layout">
        <aside class="rd-queue-panel">
          <label class="rd-schedule-label">出诊安排<select v-model="group" aria-label="接诊出诊安排" @change="changeGroup"><option value="am">上午 08:00–12:00 · 线下面诊</option><option value="pm">下午 14:00–18:00 · 线上问诊</option></select></label>
          <label class="rd-search"><Icon name="search"/><input v-model="search" aria-label="搜索接诊患者" placeholder="搜索姓名 / 排队号"></label>
          <nav class="rd-queue-tabs" aria-label="接诊队列分类"><button v-for="label in ['待接诊', '就诊中', '已完成', '过号', '待签到']" :key="label" :aria-pressed="status === label" :class="{ active: status === label }" @click="status = label">{{ label }}</button></nav>
          <div class="rd-queue-list"><button v-for="visit in filtered" :key="visit.id" :class="['rd-queue-patient', { active: selected.id === visit.id }]" :aria-label="`预览${visit.name} ${visit.number}`" @click="selectPatient(visit)"><span class="rd-number">{{ visit.number }}</span><span><strong>{{ visit.name }}<small>{{ visit.sex }} · {{ visit.age }}岁</small></strong><span>{{ visit.symptom }}</span><small>{{ visit.source }} · {{ statusText[visit.status] }}</small></span><Icon name="chevron"/></button><div v-if="!filtered.length" class="rd-empty"><Icon name="user"/><p>当前暂无{{ status }}患者</p><small>患者完成缴费和签到后进入候诊。</small></div></div>
          <div class="rd-queue-foot"><Icon name="info"/>预约优先，同组按签到顺序；恢复排队进入原组末尾。</div>
        </aside>
        <section class="rd-preview">
          <div class="rd-preview-top"><span class="dh-eyebrow">PATIENT OVERVIEW</span><span class="rd-tag">{{ statusText[clinical.status] }}</span></div>
          <div class="rd-person"><span class="rd-avatar">{{ clinical.name[0] }}</span><div><h2>{{ clinical.name }} <small>{{ clinical.sex }} · {{ clinical.age }} 岁</small></h2><p>{{ clinical.number }} · {{ clinical.source }} · {{ clinical.group === 'am' ? '线下面诊' : '线上问诊' }}</p></div></div>
          <div class="rd-identity-grid"><div><span>本次出诊</span><strong>11月12日 · {{ clinical.group === 'am' ? '上午 08:00–12:00' : '下午 14:00–18:00' }}</strong></div><div><span>签到状态</span><strong>{{ clinical.checkedAt ? `已签到 ${formatTime(clinical.checkedAt)}` : '尚未签到' }}</strong></div><div><span>挂号费用</span><strong>已缴费 · {{ clinical.group === 'am' ? '¥12.00' : '¥18.00' }}</strong></div><div><span>接诊医生</span><strong>许知遥 · 内科</strong></div></div>
          <div class="rd-preview-block"><h3><Icon name="file"/>本次就诊描述</h3><p>{{ clinical.symptom }}</p><small>患者登记信息，需接诊时进一步核实。</small></div>
          <div v-if="clinical.group === 'pm'" class="rd-preview-block"><h3><Icon name="chat"/>提前留言</h3><p v-if="!clinical.messages.length">暂无留言。签到后患者可进入图文诊室补充症状。</p><p v-for="item in clinical.messages.filter(m => m.sender === 'patient')" :key="item.id">{{ item.text }} <small>{{ formatTime(item.at) }}</small></p></div>
          <div class="rd-history-strip"><span><Icon name="shield"/>{{ clinical.historyAuthorized ? '患者已授权本次查看一份历史病历' : '历史病历尚未授权，仅查看本次就诊信息' }}</span><button class="dh-link" :disabled="!clinical.historyAuthorized" @click="openModal(historyDialog)">查看历史病历<Icon name="chevron"/></button></div>
          <ClinicalResult v-if="clinical.result" :visit="clinical"/>
          <div class="rd-preview-actions"><p><Icon name="info"/>预览资料不会叫号，也不会结束当前接诊。</p><button v-if="active" class="dh-primary" @click="call">继续接诊<Icon name="arrow"/></button><button v-else-if="clinical.status === 'WAITING'" class="dh-primary" :disabled="queue[0]?.id !== clinical.id || busy" @click="call">{{ queue[0]?.id === clinical.id ? '叫号并开始接诊' : '请先接诊队首患者' }}<Icon name="arrow"/></button><button v-else-if="clinical.result" class="dh-soft" @click="focused = true; emit('focus', true)">查看完整结果</button></div>
        </section>
      </div>
    </template>
    <template v-else>
      <div class="rd-focus-header"><div class="rd-focus-left"><button class="rd-back" aria-label="返回候诊队列" @click="returnToQueue"><Icon name="chevron"/></button><span class="rd-avatar">{{ clinical.name[0] }}</span><div><h2>{{ clinical.name }} <small>{{ clinical.sex }} · {{ clinical.age }} 岁</small></h2><p>{{ clinical.number }} · {{ clinical.group === 'pm' ? '线上问诊' : '线下面诊' }} · {{ clinical.source }}</p></div><span class="rd-tag">{{ statusText[clinical.status] }}</span></div><div class="rd-focus-tools"><button class="dh-soft" @click="openModal(drawer)"><Icon name="user"/>候诊 {{ queue.length }}</button><button class="dh-soft" :disabled="!clinical.historyAuthorized" @click="openModal(historyDialog)"><Icon name="file"/>历史病历</button><button v-if="clinical.group === 'pm'" class="dh-soft" :aria-expanded="!aiCollapsed" @click="aiCollapsed = !aiCollapsed"><Icon name="sparkles"/>{{ aiCollapsed ? '展开摘要' : '收起摘要' }}</button></div></div>
      <div v-if="clinical.result" class="rd-completed"><div class="rd-completed-banner"><Icon name="scan"/><div><h2>本次接诊已完成</h2><p>病历已保存，患者可查看结果。返回候诊后手动叫下一位。</p></div><button class="dh-primary" @click="returnToQueue">返回候诊队列</button></div><ClinicalResult :visit="clinical"/></div>
      <div v-else :class="['rd-focus-layout', { offline: clinical.group === 'am', 'ai-collapsed': aiCollapsed }]">
        <aside v-if="clinical.group === 'pm' && !aiCollapsed" class="rd-ai-panel"><div class="rd-panel-title"><h3><Icon name="sparkles"/>症状摘要</h3><span class="rd-demo-badge">模拟演示</span></div><p class="rd-ai-boundary">仅供医生参考，不生成诊断或用药建议。</p><template v-if="clinical.authorizedIds.length"><div class="rd-ai-status"><i></i>已授权 {{ clinical.authorizedIds.length }} 条消息</div><p v-if="state.aiFailure || summary?.error" class="rd-ai-error">摘要生成失败，聊天和病历可正常使用。</p><p v-if="summaryStale" class="rd-ai-stale">有新消息未获授权，旧摘要可能过时。请患者补充授权后更新。</p><div v-if="summary" class="rd-summary"><span>授权对话整理</span><p>{{ summary.text }}</p><small>更新于 {{ formatTime(summary.at) }}</small></div><p v-else>暂未生成摘要。</p></template><div v-else class="rd-ai-unavailable"><Icon name="shield"/><strong>等待患者授权</strong><p>由患者在诊室确认提供方、用途及本次消息范围后生成。</p></div><button class="dh-soft" :disabled="!clinical.authorizedIds.length || busy" @click="refreshSummary(clinical.id)"><Icon name="sparkles"/>重新生成</button><div class="rd-ai-note"><Icon name="info"/>新增内容须重新授权；患者的独立 AI 会话不会被读取。</div></aside>
        <section v-if="clinical.group === 'pm'" class="rd-chat"><div class="rd-panel-title"><h3><Icon name="chat"/>医患交流</h3><span class="rd-live"><i></i>文字接诊</span></div><div class="rd-chat-notice"><Icon name="shield"/>本次挂号专属沟通，提交结果后转为只读。</div><div class="rd-chat-messages" aria-label="医患消息" aria-live="polite"><div class="rd-chat-system">已叫号 · {{ clinical.calledAt ? formatTime(clinical.calledAt) : '' }} · {{ clinical.answered ? '患者已应答' : '等待患者应答' }}</div><div v-for="item in clinical.messages" :key="item.id" :class="['rd-message', item.sender]"><span>{{ item.sender === 'doctor' ? '许知遥医生' : clinical.name }} · {{ formatTime(item.at) }}</span><p>{{ item.text }}</p></div><p v-if="!clinical.messages.length" class="rd-empty">暂无消息，可以先向患者问好。</p></div><form class="rd-composer" @submit.prevent="send"><span><Icon name="image"/>图片上传后续支持</span><textarea v-model="message" aria-label="医生输入消息" placeholder="询问症状、病史或补充本次交流…" maxlength="2000" :disabled="!isActive"></textarea><div><small>消息同步到患者的图文诊室</small><button class="dh-primary" :disabled="!message.trim() || !isActive || busy" type="submit">发送<Icon name="arrow"/></button></div></form></section>
        <section class="rd-editor"><div class="rd-editor-heading"><nav aria-label="病历与开药"><button :class="{ active: editorTab === 'record' }" :aria-pressed="editorTab === 'record'" @click="editorTab = 'record'"><Icon name="file"/>病历</button><button :class="{ active: editorTab === 'medicine' }" :aria-pressed="editorTab === 'medicine'" @click="editorTab = 'medicine'"><Icon name="wallet"/>开药 <small v-if="localDraft.medicines.length">{{ localDraft.medicines.length }}</small></button></nav><span>{{ clinical.group === 'am' ? '线下面诊记录' : '本次接诊记录' }}</span></div>
          <div v-if="editorTab === 'record'" class="rd-record-fields"><p class="rd-form-note"><Icon name="info"/>由医生独立填写，标 * 为提交必填。</p><label v-for="field in draftFields" :key="field[0]">{{ field[1] }} <b v-if="field[2]">*</b><textarea v-model="localDraft[field[0]]" :aria-label="field[1]" :required="field[2]" :rows="clinical.group === 'am' ? 3 : 2" maxlength="4000" :disabled="!isActive" :placeholder="`填写${field[1]}`" @input="edit"></textarea></label></div>
          <div v-else class="rd-medicine-editor"><p class="rd-form-note">开药可选，用法用量由医生填写。仅展示启用且有库存的药品。</p><label>选择药品<select v-model="medicineId" aria-label="选择药品"><option value="">搜索并选择演示药品</option><option v-for="medicine in availableMedicines" :key="medicine.id" :value="medicine.id">{{ medicine.name }} · {{ medicine.specification }} · {{ money(medicine.price) }} · 可用 {{ medicine.stock - medicine.held }}</option></select></label><button class="dh-soft" :disabled="!medicineId" @click="addMedicine"><Icon name="plus"/>添加药品</button><div v-for="(line, index) in localDraft.medicines" :key="line.medicineId" class="rd-medicine-line"><div><strong>{{ state.medicines.find(m => m.id === line.medicineId)?.name }}</strong><button class="dh-link" :aria-label="`移除药品${index + 1}`" @click="localDraft.medicines.splice(index, 1); edit()"><Icon name="close"/></button></div><p>{{ state.medicines.find(m => m.id === line.medicineId)?.specification }} · 单价 {{ money(state.medicines.find(m => m.id === line.medicineId)!.price) }}</p><label>数量<input v-model.number="line.quantity" :aria-label="`药品${index + 1}数量`" type="number" min="1" step="1" @input="edit"></label><label>用法用量<textarea v-model="line.usage" :aria-label="`药品${index + 1}用法用量`" rows="2" placeholder="由医生手动填写" maxlength="1000" @input="edit"></textarea></label></div><div v-if="!localDraft.medicines.length" class="rd-empty"><Icon name="wallet"/><p>本次暂未开药</p><small>可直接提交病历并完成接诊。</small></div><div class="rd-medicine-total">药费合计<strong>{{ money(total) }}</strong></div></div>
          <footer class="rd-editor-footer"><div role="status"><Icon name="scan"/>{{ saveMessage }} <button v-if="dirty && !saving" class="dh-link" @click="persist">重试保存</button></div><div><button class="rd-miss" :disabled="!canMiss(clinical, now) || busy" @click="openModal(missedDialog)">标记过号</button><button class="dh-primary" :disabled="!isActive || saving || busy" @click="review">核对并完成<Icon name="arrow"/></button></div><small v-if="clinical.group === 'pm'">{{ clinical.answered ? '患者已应答，不能标记过号' : '叫号后等待满 5 分钟且无应答，才可标记过号' }}</small></footer>
        </section>
      </div>
      <p class="rd-focus-footnote"><Icon name="shield"/>离开诊室或关闭工作区标签不会结束接诊；病历提交后才形成患者可见的医疗结果。</p>
    </template>
    <dialog ref="drawer" class="rd-dialog rd-drawer" aria-label="候诊队列抽屉" @close="restoreFocus"><div class="rd-panel-title"><h2>候诊队列</h2><button class="dh-link" aria-label="关闭队列抽屉" @click="drawer?.close()"><Icon name="close"/></button></div><p>当前接诊未结束，其他患者仅可预览。</p><button v-for="visit in queue" :key="visit.id" class="rd-drawer-patient" @click="selectPatient(visit)">{{ visit.number }} · {{ visit.name }}<Icon name="chevron"/></button></dialog>
    <dialog ref="historyDialog" class="rd-dialog" aria-label="授权历史病历" @close="restoreFocus"><button class="rd-dialog-close" aria-label="关闭历史病历" @click="historyDialog?.close()"><Icon name="close"/></button><h2>权限内历史病历</h2><template v-if="clinical.historyAuthorized"><p>2026-10-08 · 全科医学科 · 陈予安医生</p><h3>患者授权共享的演示记录</h3><p>既往就诊记录已由患者选择向本次接诊共享。演示材料：既往无已知药物过敏；接诊时仍须询问核实。</p></template><p v-else>患者尚未授权，无法查看历史医疗内容。</p></dialog>
    <dialog ref="missedDialog" class="rd-dialog" aria-label="确认标记过号" @close="restoreFocus"><h2>确认标记过号？</h2><p>{{ clinical.name }}将退出当前队列。{{ clinical.restored ? '本订单已恢复过一次，再次过号将作废。' : '患者可在本场结束前主动恢复一次。' }}</p><div class="rd-dialog-actions"><button class="dh-soft" @click="missedDialog?.close()">暂不标记</button><button class="dh-primary" @click="markMissed">确认过号</button></div></dialog>
    <dialog ref="confirmDialog" class="rd-dialog rd-confirm" aria-label="病历与药品核对" @close="restoreFocus"><button class="rd-dialog-close" aria-label="关闭提交核对" @click="confirmDialog?.close()"><Icon name="close"/></button><span class="dh-eyebrow">REVIEW & COMPLETE</span><h2>核对本次接诊</h2><p>{{ clinical.name }} · {{ clinical.number }} · {{ clinical.group === 'pm' ? '线上问诊' : '线下面诊' }}</p><dl><template v-for="field in draftFields" :key="field[0]"><dt>{{ field[1] }}</dt><dd>{{ localDraft[field[0]] || '未填写' }}</dd></template></dl><h3>药品 {{ localDraft.medicines.length }} 项 · {{ money(total) }}</h3><div v-for="line in localDraft.medicines" :key="line.medicineId" class="rd-review-line"><strong>{{ state.medicines.find(m => m.id === line.medicineId)?.name }} × {{ line.quantity }}</strong><p>{{ state.medicines.find(m => m.id === line.medicineId)?.specification }} · {{ line.usage }}</p></div><p v-if="!localDraft.medicines.length">本次未开药。</p><p v-if="error" role="alert" class="rd-alert">{{ error }}</p><p class="rd-review-note">确认后病历立即保存，诊室只读；药费单独流转，不自动叫下一位。</p><div class="rd-dialog-actions"><button class="dh-soft" @click="confirmDialog?.close()">返回修改</button><button class="dh-primary" :disabled="busy" @click="finish">确认提交并完成</button></div></dialog>
  </section>
</template>
