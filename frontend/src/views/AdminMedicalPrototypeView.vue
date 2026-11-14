<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import Icon from './patient-home-prototype/PrototypeIcon.vue'
import { createMedicalDemo, displayTime, grantState, hospitalDate, kindLabels, readDemoContent, scopeLabels } from './admin-medical-prototype'
import type { AccessGrant, MedicalAppointment, MedicalEntry, Scope } from './admin-medical-prototype'
import './admin-medical-prototype.css'

const demo = reactive(createMedicalDemo(Date.now()))
const actorId = ref('ADMIN-01'), now = ref(Date.now()), offset = ref(0)
const actor = computed(() => demo.reviewers.find(item => item.id === actorId.value)!)
const tab = ref<'appointments' | 'records'>('appointments')
const keyword = ref(''), from = ref(hospitalDate(now.value - 29 * 86400000)), to = ref(hospitalDate(now.value)), stateFilter = ref('全部')
const notice = ref(''), formError = ref('')
const selected = ref<MedicalAppointment | null>(null), selectedGrantId = ref('')
const requestDialog = ref<HTMLDialogElement>(), contentDialog = ref<HTMLDialogElement>(), actionDialog = ref<HTMLDialogElement>(), permissionDialog = ref<HTMLDialogElement>()
const requestScope = ref<Scope>('RECORD'), requestReason = ref('')
const readingId = ref(''), loaded = ref<MedicalEntry[]>([]), preview = ref<{ url: string; name: string } | null>(null)
const operation = ref<null | { kind: 'flag' | 'end' | 'permission'; entry?: MedicalEntry; limited?: boolean; target?: string; enabled?: boolean }>(null)
const actionReason = ref(''), actionError = ref('')
const targetReviewer = ref('ADMIN-02'), permissionEnabled = ref(true), permissionReason = ref(''), permissionError = ref('')
let ticker: number | undefined
const ownGrants = computed(() => demo.grants.filter(g => g.actor === actorId.value).sort((a, b) => b.createdAt - a.createdAt))
const reading = computed(() => demo.grants.find(g => g.id === readingId.value))
const readingValid = computed(() => !!reading.value && reading.value.actor === actorId.value && actor.value.qualified && grantState(reading.value, now.value) === '有效')
const selectedGrant = computed(() => ownGrants.value.find(g => g.id === selectedGrantId.value))
const currentTarget = computed(() => demo.reviewers.find(r => r.id === targetReviewer.value)!)
const dateError = computed(() => from.value && to.value && from.value > to.value ? '开始日期不能晚于结束日期，请调整日期。' : '')
const currentAudits = computed(() => demo.audits.filter(a => a.actor === actorId.value && (selectedGrant.value ? a.grantId === selectedGrant.value.id : a.appointmentId === selected.value?.id)).slice().reverse())
const filteredAppointments = computed(() => demo.appointments.filter(a => !dateError.value && matches(a) && withinDate(a.date)))
const filteredGrants = computed(() => ownGrants.value.filter(g => {
  const a = appointment(g.appointmentId)
  return !dateError.value && a && matches(a, g.id) && withinDate(hospitalDate(g.createdAt)) && (stateFilter.value === '全部' || grantState(g, now.value) === stateFilter.value)
}))
const remaining = computed(() => {
  const seconds = Math.max(0, Math.ceil(((reading.value?.expiresAt || 0) - now.value) / 1000))
  return `${Math.floor(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}`
})
function appointment(id: string) { return demo.appointments.find(a => a.id === id) }
function actorName(id: string) { return demo.reviewers.find(r => r.id === id)?.name || id }
function matches(a: MedicalAppointment, extra = '') { return `${a.id} ${a.patient} ${a.patientId} ${a.doctor} ${extra}`.toLowerCase().includes(keyword.value.trim().toLowerCase()) }
function withinDate(date: string) { return (!from.value || date >= from.value) && (!to.value || date <= to.value) }
function resetFilters() { keyword.value = ''; stateFilter.value = '全部'; from.value = tab.value === 'appointments' ? hospitalDate(now.value - 29 * 86400000) : ''; to.value = tab.value === 'appointments' ? hospitalDate(now.value) : '' }
function changeTab(value: typeof tab.value) { tab.value = value; resetFilters(); selected.value = null; selectedGrantId.value = '' }
function openDetails(a: MedicalAppointment, grant?: AccessGrant) { selected.value = a; selectedGrantId.value = grant?.id || '' }
function log(action: string, target: string, grant?: AccessGrant, actorOverride?: string, reason = grant?.reason || '') {
  demo.audits.push({ id: crypto.randomUUID(), grantId: grant?.id || '', appointmentId: grant?.appointmentId || '', actor: actorOverride || actorId.value, action, target, reason, time: now.value })
}
function releasePreview() { if (preview.value) URL.revokeObjectURL(preview.value.url); preview.value = null }
function clearContent() { loaded.value = []; releasePreview(); if (operation.value?.kind === 'flag' || operation.value?.kind === 'end') { actionDialog.value?.close(); operation.value = null } }
function synchronize() {
  now.value = Date.now() + offset.value
  if (readingId.value && !readingValid.value) { clearContent(); notice.value = '本次访问已失效，已清空医疗内容和附件预览。' }
}
function checkGrant(grant: AccessGrant | undefined) {
  synchronize()
  return !!grant && grant.actor === actorId.value && actor.value.qualified && grantState(grant, now.value) === '有效'
}
function refresh() { synchronize(); notice.value = '演示状态已刷新，未自动创建或续期任何授权。' }
async function beginRequest() {
  synchronize()
  if (!selected.value || !actor.value.qualified) { notice.value = '请先由另一位管理员授予医疗审核资格。'; return }
  requestReason.value = ''; requestScope.value = 'RECORD'; formError.value = ''
  contentDialog.value?.close(); await nextTick(); requestDialog.value?.showModal()
}
async function submitRequest() {
  synchronize()
  if (!actor.value.qualified) { formError.value = '当前没有医疗审核资格，请由另一位管理员授权。'; return }
  if (!requestReason.value.trim() || requestReason.value.length > 1000) { formError.value = '请填写1至1000字的访问理由。'; return }
  if (!selected.value) return
  const grant: AccessGrant = { id: `REV-${crypto.randomUUID().slice(0, 8)}`, appointmentId: selected.value.id, actor: actorId.value, scope: requestScope.value, reason: requestReason.value.trim(), createdAt: now.value, expiresAt: now.value + 30 * 60000, revoked: false }
  demo.grants.unshift(grant); selectedGrantId.value = grant.id
  log('登记访问授权', scopeLabels[grant.scope], grant)
  requestDialog.value?.close(); notice.value = '已登记本次访问，授权有效期30分钟。'
  await nextTick(); openContent(grant)
}
function openContent(grant: AccessGrant) {
  if (!checkGrant(grant)) { notice.value = '授权已失效，请重新填写理由申请访问。'; return }
  clearContent(); readingId.value = grant.id
  loaded.value = readDemoContent(grant.appointmentId, grant.scope)
  log('读取医疗内容', scopeLabels[grant.scope], grant)
  contentDialog.value?.showModal()
}
function closeContent() { clearContent(); readingId.value = '' }
function isLimited(entry: MedicalEntry) { return demo.flags.find(f => f.appointmentId === reading.value?.appointmentId && f.targetId === entry.id)?.limited || false }
function prepareFlag(entry: MedicalEntry) {
  if (!checkGrant(reading.value) || !loaded.value.some(e => e.id === entry.id)) return
  operation.value = { kind: 'flag', entry, limited: !isLimited(entry) }; actionReason.value = ''; actionError.value = ''; actionDialog.value?.showModal()
}
function prepareEnd() {
  if (!checkGrant(reading.value)) return
  operation.value = { kind: 'end' }; actionReason.value = ''; actionError.value = ''; actionDialog.value?.showModal()
}
function submitAction() {
  const op = operation.value
  if (!op) return
  if (!actionReason.value.trim() || actionReason.value.length > 1000) { actionError.value = '请填写1至1000字的操作理由。'; return }
  if (op.kind === 'permission') {
    if (op.target === actorId.value) { actionError.value = '不能为自己管理审核资格。'; return }
    applyPermission(op.target!, false)
  } else {
    const grant = reading.value
    if (!checkGrant(grant) || !grant) return
    if (op.kind === 'end') { grant.revoked = true; log('结束本次访问', '主动撤回授权', grant, undefined, actionReason.value.trim()); notice.value = '已结束本次访问，原记录与审计保留。' }
    else if (op.entry && loaded.value.some(e => e.id === op.entry!.id)) {
      const flag = demo.flags.find(f => f.appointmentId === grant.appointmentId && f.targetId === op.entry!.id)
      if (flag) flag.limited = !!op.limited
      else demo.flags.push({ appointmentId: grant.appointmentId, targetId: op.entry.id, limited: !!op.limited })
      log(op.limited ? '限制内容展示' : '解除内容限制', `${kindLabels[op.entry.kind]} · ${op.entry.id}`, grant, undefined, actionReason.value.trim())
      notice.value = op.limited ? '已限制展示；原文及处理记录已保留（演示）。' : '已解除限制；原文及处理记录已保留（演示）。'
    }
  }
  actionDialog.value?.close(); operation.value = null; synchronize()
}
function openPermissions() { permissionError.value = ''; permissionReason.value = ''; targetReviewer.value = actorId.value === 'ADMIN-02' ? 'ADMIN-01' : 'ADMIN-02'; permissionEnabled.value = true; permissionDialog.value?.showModal() }
function applyPermission(targetId: string, enabled: boolean) {
  const target = demo.reviewers.find(r => r.id === targetId)!
  target.qualified = enabled; target.grantedBy = actor.value.name
  if (!enabled) demo.grants.filter(g => g.actor === targetId && !g.revoked).forEach(g => { g.revoked = true })
  log(enabled ? '授予审核资格' : '撤回审核资格', targetId, undefined, undefined, enabled ? permissionReason.value.trim() : actionReason.value.trim())
  permissionDialog.value?.close(); notice.value = enabled ? '已独立授予审核资格（演示）。' : '已撤回审核资格及该账户现有访问授权（演示）。'
}
function submitPermission() {
  if (targetReviewer.value === actorId.value) { permissionError.value = '不能为自己管理审核资格，请由另一位管理员操作。'; return }
  if (!permissionReason.value.trim() || permissionReason.value.length > 1000) { permissionError.value = '请填写1至1000字的资格变更理由。'; return }
  if (permissionEnabled.value) applyPermission(targetReviewer.value, true)
  else {
    operation.value = { kind: 'permission', target: targetReviewer.value, enabled: false }
    actionReason.value = permissionReason.value.trim(); actionError.value = ''; actionDialog.value?.showModal()
  }
}
function switchActor() {
  clearContent(); contentDialog.value?.close(); requestDialog.value?.close(); readingId.value = ''; selected.value = null; selectedGrantId.value = ''; resetFilters()
  notice.value = `已切换为${actor.value.name}；关闭先前账户的内容。`
}
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && ![requestDialog, contentDialog, actionDialog, permissionDialog].some(dialog => dialog.value?.open)) selected.value = null
}
function demoExpire() { offset.value += 31 * 60000; synchronize() }
function demoRevoke() {
  const other = demo.reviewers.find(r => r.id !== actorId.value)!
  actor.value.qualified = false
  demo.grants.filter(g => g.actor === actorId.value && !g.revoked).forEach(g => { g.revoked = true })
  log('外部撤回审核资格', actorId.value, reading.value, other.id); synchronize()
}
function attachmentBlob() {
  const canvas = document.createElement('canvas'); canvas.width = 720; canvas.height = 380
  const context = canvas.getContext('2d')!
  context.fillStyle = '#f3f8fd'; context.fillRect(0, 0, canvas.width, canvas.height)
  context.fillStyle = '#315d81'; context.font = '28px sans-serif'; context.fillText('虚构演示附件 · 非真实检查报告', 42, 80)
  context.font = '20px sans-serif'; context.fillText(reading.value?.appointmentId || '', 42, 140); context.fillText('仅用于医疗访问审核页面交互验收', 42, 195)
  return new Promise<Blob | null>(resolve => canvas.toBlob(resolve, 'image/png'))
}
async function openAttachment(entry: MedicalEntry, download = false) {
  const grant = reading.value
  if (!checkGrant(grant) || !loaded.value.some(e => e.id === entry.id && e.kind === 'ATTACHMENT')) return
  const blob = await attachmentBlob()
  if (!blob || !checkGrant(grant) || reading.value?.id !== grant?.id || !contentDialog.value?.open) return
  const url = URL.createObjectURL(blob)
  log(download ? '下载医疗附件' : '预览医疗附件', entry.id, grant)
  if (download) { const link = document.createElement('a'); link.href = url; link.download = entry.title; link.click(); window.setTimeout(() => URL.revokeObjectURL(url), 1000) }
  else { releasePreview(); preview.value = { url, name: entry.title } }
}
onMounted(() => { ticker = window.setInterval(synchronize, 1000); window.addEventListener('focus', synchronize); document.addEventListener('visibilitychange', synchronize); window.addEventListener('keydown', onKeydown) })
onBeforeUnmount(() => { if (ticker) window.clearInterval(ticker); releasePreview(); window.removeEventListener('focus', synchronize); document.removeEventListener('visibilitychange', synchronize); window.removeEventListener('keydown', onKeydown) })
</script>

<template>
  <div class="medical-workspace">
    <header class="medical-heading"><div><span class="admin-eyebrow">治理 / 医疗访问审核</span><h1>医疗访问审核</h1><p>按预约登记访问范围，审核内容展示，保留每一步处理记录。</p></div><div class="medical-heading-actions"><span class="medical-demo">虚构演示数据</span><RouterLink class="medical-formal" to="/admin/medical">正式接口页 <Icon name="arrow" /></RouterLink><button class="admin-soft" type="button" @click="refresh"><Icon name="refresh" />刷新</button></div></header>
    <section class="medical-qualification" aria-label="当前审核资格"><span class="medical-shield"><Icon name="shield" /></span><div><h2>{{ actor.name }} <span :class="['medical-tag', actor.qualified ? 'valid' : 'ended']">{{ actor.qualified ? '已获审核资格' : '未获审核资格' }}</span></h2><p>{{ actor.qualified ? `由${actor.grantedBy}独立授权；查看每次预约仍需填写理由并选择范围。` : '请由另一位管理员授予资格；当前只能查看业务信息和访问记录。' }}</p></div><button type="button" class="admin-soft" @click="openPermissions"><Icon name="lock" />资格管理</button></section>
    <div class="medical-stats" aria-label="我的访问概况"><article><span>今日访问登记</span><strong>{{ ownGrants.filter(g => hospitalDate(g.createdAt) === hospitalDate(now)).length }}</strong><small>按医院业务日统计</small></article><article><span>当前有效授权</span><strong>{{ ownGrants.filter(g => grantState(g, now) === '有效').length }}</strong><small>仅限本人 · 单次30分钟</small></article><article><span>已结束访问</span><strong>{{ ownGrants.filter(g => grantState(g, now) !== '有效').length }}</strong><small>已撤回和已过期 · 历史保留</small></article></div>
    <p v-if="notice" class="medical-notice" role="status">{{ notice }}</p>
    <div class="medical-tabs" role="tablist" aria-label="医疗访问工作区"><button type="button" role="tab" :aria-selected="tab === 'appointments'" @click="changeTab('appointments')">预约目录</button><button type="button" role="tab" :aria-selected="tab === 'records'" @click="changeTab('records')">我的访问记录</button></div>
    <form class="medical-filters" aria-label="医疗访问筛选" @submit.prevent><label class="medical-search">关键词<input v-model="keyword" aria-label="医疗访问关键词" placeholder="预约号、患者姓名 / 编号、医生"></label><label>{{ tab === 'appointments' ? '就诊开始日期' : '申请开始日期' }}<input v-model="from" type="date" aria-label="开始日期"></label><label>结束日期<input v-model="to" type="date" aria-label="结束日期"></label><label v-if="tab === 'records'">授权状态<select v-model="stateFilter" aria-label="授权状态"><option>全部</option><option>有效</option><option>已撤回</option><option>已过期</option></select></label><button type="button" class="admin-soft" @click="resetFilters">重置</button><p v-if="dateError" class="medical-error" role="alert">{{ dateError }}</p></form>
    <section class="medical-table-panel" :aria-label="tab === 'appointments' ? '预约目录列表' : '我的访问记录列表'"><div class="medical-section-head"><div><h2>{{ tab === 'appointments' ? '预约目录' : '我的访问记录' }} <small>{{ tab === 'appointments' ? filteredAppointments.length : filteredGrants.length }} 条</small></h2><p>{{ tab === 'appointments' ? '仅展示业务信息，医疗内容需单次授权后查看。' : '访问记录不包含医疗正文；已结束的授权不会自动续期。' }}</p></div><Icon name="lock" /></div>
      <div class="medical-table-scroll"><table v-if="tab === 'appointments'"><caption class="visually-hidden">预约目录</caption><thead><tr><th>预约号 / 患者</th><th>医生 / 科室</th><th>就诊日期 / 时段</th><th>服务方式</th><th>预约状态</th><th>操作</th></tr></thead><tbody><tr v-for="a in filteredAppointments" :key="a.id" tabindex="0" @click="openDetails(a)" @keydown.enter.self="openDetails(a)"><td><strong>{{ a.id }}</strong><span>{{ a.patient }} · {{ a.patientId }}</span></td><td><strong>{{ a.doctor }}</strong><span>{{ a.department }}</span></td><td>{{ a.date }}<span>{{ a.period }}</span></td><td>{{ a.mode }}</td><td><span class="medical-tag neutral">{{ a.state }}</span></td><td><button type="button" class="medical-link" @click.stop="openDetails(a)">查看详情 <Icon name="chevron" /></button></td></tr></tbody></table>
      <table v-else><caption class="visually-hidden">我的访问记录</caption><thead><tr><th>访问编号 / 关联预约</th><th>患者 / 医生</th><th>访问范围</th><th>申请 / 截止时间</th><th>授权状态</th><th>操作</th></tr></thead><tbody><tr v-for="g in filteredGrants" :key="g.id" tabindex="0" @click="openDetails(appointment(g.appointmentId)!, g)" @keydown.enter.self="openDetails(appointment(g.appointmentId)!, g)"><td><strong>{{ g.id }}</strong><span>{{ g.appointmentId }} · {{ actorName(g.actor) }}</span></td><td><strong>{{ appointment(g.appointmentId)?.patient }} · {{ appointment(g.appointmentId)?.patientId }}</strong><span>{{ appointment(g.appointmentId)?.doctor }} / {{ appointment(g.appointmentId)?.department }}</span></td><td>{{ scopeLabels[g.scope] }}</td><td>{{ displayTime(g.createdAt) }}<span>{{ displayTime(g.expiresAt) }}</span></td><td><span :class="['medical-tag', grantState(g, now) === '有效' ? 'valid' : 'ended']">{{ grantState(g, now) }}</span></td><td><button type="button" class="medical-link" @click.stop="openDetails(appointment(g.appointmentId)!, g)">查看记录 <Icon name="chevron" /></button></td></tr></tbody></table></div>
      <div v-if="tab === 'appointments' ? !filteredAppointments.length : !filteredGrants.length" class="medical-empty"><Icon name="search" /><h3>暂无符合条件的{{ tab === 'appointments' ? '预约' : '访问记录' }}</h3><p>调整日期或关键词后再试，也可以重置筛选。</p><button type="button" class="admin-soft" @click="resetFilters">重置筛选</button></div>
    </section>
    <details class="medical-demo-tools"><summary>演示场景 · 切换管理员</summary><label>当前演示管理员<select v-model="actorId" aria-label="当前演示管理员" @change="switchActor"><option v-for="r in demo.reviewers" :key="r.id" :value="r.id">{{ r.name }} · {{ r.id }}</option></select></label><p>仅切换本页虚构身份；刷新页面重置演示。正式环境由登录身份与后端权限决定。</p></details>
    <p class="medical-footnote"><Icon name="shield" />访问资格、单次授权与内容限制分别管理；所有时间按 Asia/Shanghai 展示。</p>

    <div v-if="selected" class="medical-drawer-backdrop" @click.self="selected = null"><aside class="medical-drawer" aria-label="医疗访问详情"><button type="button" class="medical-close" aria-label="关闭医疗访问详情" @click="selected = null"><Icon name="close" /></button><span class="admin-eyebrow">{{ selectedGrant ? '我的访问记录' : '预约业务信息' }}</span><h2>{{ selected.patient }} <small>{{ selected.patientId }}</small></h2><p>{{ selected.id }}</p><dl class="medical-info"><div><dt>负责医生 / 科室</dt><dd>{{ selected.doctor }} / {{ selected.department }}</dd></div><div><dt>就诊日期 / 时段</dt><dd>{{ selected.date }} {{ selected.period }}</dd></div><div><dt>服务方式</dt><dd>{{ selected.mode }}</dd></div><div><dt>预约状态</dt><dd>{{ selected.state }}</dd></div></dl>
      <section v-if="selectedGrant" class="medical-grant-info"><h3>本次访问授权</h3><p>{{ selectedGrant.id }} · {{ grantState(selectedGrant, now) }}</p><dl class="medical-info"><div><dt>范围 / 操作者</dt><dd>{{ scopeLabels[selectedGrant.scope] }} / {{ actorName(selectedGrant.actor) }}</dd></div><div><dt>申请时间</dt><dd>{{ displayTime(selectedGrant.createdAt) }}</dd></div><div><dt>截止时间</dt><dd>{{ displayTime(selectedGrant.expiresAt) }}</dd></div></dl></section>
      <p class="medical-boundary"><Icon name="lock" />此处仅显示业务信息；访问理由和范围确认后，医疗内容在独立弹窗内打开。</p><div class="medical-actions"><button v-if="selectedGrant && grantState(selectedGrant, now) === '有效'" class="admin-primary" type="button" :disabled="!actor.qualified" @click="openContent(selectedGrant)">查看授权内容</button><button class="admin-primary" type="button" :disabled="!actor.qualified" @click="beginRequest">{{ selectedGrant ? '重新申请访问' : '申请访问' }}</button></div><p v-if="!actor.qualified" class="medical-error">未获审核资格，请由另一位管理员独立授权。</p>
      <section class="medical-history" aria-label="访问操作记录"><h3>访问与处理记录</h3><p>仅展示范围、操作者、时间和动作，不保留医疗正文快照。</p><ol v-if="currentAudits.length"><li v-for="a in currentAudits" :key="a.id"><strong>{{ a.action }}</strong><span>{{ a.target }}</span><small>{{ actorName(a.actor) }} · {{ displayTime(a.time) }}</small></li></ol><p v-else class="medical-empty-note">尚无本人访问记录</p></section>
    </aside></div>

    <dialog ref="requestDialog" class="medical-dialog" aria-label="申请医疗访问" @close="requestReason = ''; formError = ''"><form @submit.prevent="submitRequest" novalidate><header><div><span class="admin-eyebrow">单次访问 · 有效30分钟</span><h2>申请医疗访问</h2></div><button type="button" class="medical-close" aria-label="关闭访问申请" @click="requestDialog?.close()"><Icon name="close" /></button></header><p>{{ selected?.patient }} · {{ selected?.id }}</p><label>本次访问范围<select v-model="requestScope" aria-label="本次访问范围"><option value="RECORD">医疗正文</option><option value="ATTACHMENTS">医疗附件</option><option value="ALL">正文和附件</option></select></label><label>访问理由<textarea v-model="requestReason" aria-label="访问理由" maxlength="1000" rows="4" placeholder="请说明本次审核目的及必要性"></textarea></label><p class="medical-boundary">有审核资格的管理员登记后立即获准本次访问；每次读取及附件预览/下载均留审计。</p><p v-if="formError" class="medical-error" role="alert">{{ formError }}</p><footer><button type="button" class="admin-soft" @click="requestDialog?.close()">取消</button><button type="submit" class="admin-primary">确认申请并查看</button></footer></form></dialog>

    <dialog ref="contentDialog" class="medical-dialog medical-content-dialog" aria-label="授权医疗内容" @close="closeContent"><header><div><span class="admin-eyebrow">授权范围内原始内容 · 演示</span><h2>医疗内容审核</h2></div><button type="button" class="medical-close" aria-label="关闭授权内容" @click="contentDialog?.close()"><Icon name="close" /></button></header>
      <template v-if="readingValid && reading"><div class="medical-session"><div><strong>{{ scopeLabels[reading.scope] }}</strong><span>{{ reading.appointmentId }} · {{ actorName(reading.actor) }}</span></div><span class="medical-countdown">剩余 {{ remaining }}</span><button class="admin-soft medical-danger" type="button" @click="prepareEnd">结束本次访问</button></div><p class="medical-boundary"><Icon name="shield" />仅供本次审核取证。限制展示保留原文，不改写医疗记录；当前读取已记录演示审计。</p><p v-if="notice" class="medical-inline-feedback">{{ notice }}</p>
      <div class="medical-entry-list"><article v-for="entry in loaded" :key="entry.id" class="medical-entry"><header><span>{{ kindLabels[entry.kind] }}</span><span :class="['medical-tag', isLimited(entry) ? 'limited' : 'valid']">{{ isLimited(entry) ? '已限制展示' : '正常展示' }}</span></header><h3>{{ entry.title }}</h3><p>{{ entry.body }}</p><small v-if="isLimited(entry)">医患页面将显示限制提示；授权审核仍可查看原文（演示）。</small><div class="medical-actions"><template v-if="entry.kind === 'ATTACHMENT'"><button type="button" class="admin-soft" @click="openAttachment(entry)"><Icon name="image" />预览附件</button><button type="button" class="admin-soft" @click="openAttachment(entry, true)"><Icon name="file" />下载附件</button></template><button type="button" class="admin-soft" @click="prepareFlag(entry)">{{ isLimited(entry) ? '解除限制' : '限制展示' }}</button></div></article><p v-if="!loaded.length" class="medical-empty-note">本次授权范围内暂无医疗内容，预约业务状态不代表存在医疗记录。</p></div>
      <section v-if="preview" class="medical-preview" aria-label="医疗附件预览"><div><h3>{{ preview.name }}</h3><button type="button" class="admin-soft" @click="releasePreview">关闭预览</button></div><img :src="preview.url" :alt="preview.name"></section>
      <details class="medical-demo-tools"><summary>演示失效场景</summary><div class="medical-actions"><button type="button" class="admin-soft" @click="demoExpire">模拟授权到期</button><button type="button" class="admin-soft" @click="demoRevoke">模拟外部撤权</button></div><p>模拟时间推进31分钟，或由另一管理员撤回当前资格；应立即停止查看。</p></details></template>
      <div v-else class="medical-expired" role="alert"><Icon name="lock" /><h3>本次访问已失效</h3><p>医疗内容和附件预览已清空。{{ actor.qualified ? '继续查看需重新填写理由并申请授权。' : '当前审核资格已撤回，请先由另一位管理员授权。' }}</p><button type="button" class="admin-primary" :disabled="!actor.qualified" @click="beginRequest">重新申请访问</button></div>
    </dialog>

    <dialog ref="actionDialog" class="medical-dialog medical-confirm" :aria-label="operation?.kind === 'permission' ? '确认撤回审核资格' : operation?.kind === 'end' ? '结束本次访问' : '确认内容处理'" @close="operation = null"><form @submit.prevent="submitAction" novalidate><header><h2>{{ operation?.kind === 'permission' ? '确认撤回审核资格' : operation?.kind === 'end' ? '结束本次访问' : operation?.limited ? '限制展示' : '解除限制' }}</h2><button type="button" class="medical-close" aria-label="关闭操作确认" @click="actionDialog?.close()"><Icon name="close" /></button></header><p v-if="operation?.kind === 'permission'">{{ actorName(operation.target!) }}的现有访问授权将同时失效。此操作不会删除医疗记录和审计。</p><p v-else-if="operation?.kind === 'end'">结束后立即清空医疗内容与附件预览，继续查看需要重新登记。</p><p v-else>{{ operation?.entry?.title }}；原始内容和历史处理记录将保留。</p><label>操作理由<textarea v-model="actionReason" aria-label="操作理由" maxlength="1000" rows="3"></textarea></label><p v-if="actionError" class="medical-error" role="alert">{{ actionError }}</p><footer><button type="button" class="admin-soft" @click="actionDialog?.close()">取消</button><button type="submit" class="admin-primary">确认{{ operation?.kind === 'permission' ? '撤回资格' : operation?.kind === 'end' ? '结束访问' : operation?.limited ? '限制展示' : '解除限制' }}</button></footer></form></dialog>

    <dialog ref="permissionDialog" class="medical-dialog" aria-label="医疗审核资格管理"><form @submit.prevent="submitPermission" novalidate><header><div><span class="admin-eyebrow">独立授权 · 已有管理员</span><h2>审核资格管理</h2></div><button type="button" class="medical-close" aria-label="关闭资格管理" @click="permissionDialog?.close()"><Icon name="close" /></button></header><p>当前操作者：{{ actor.name }}。不能自行授予或调整自己的资格。</p><label>目标管理员<select v-model="targetReviewer" aria-label="目标管理员"><option v-for="r in demo.reviewers" :key="r.id" :value="r.id">{{ r.name }} · {{ r.id }}</option></select></label><p>当前资格：{{ currentTarget.qualified ? '已获审核资格' : '未获审核资格' }}</p><label>资格操作<select v-model="permissionEnabled" aria-label="资格操作"><option :value="true">授予审核资格</option><option :value="false">撤回审核资格</option></select></label><label>资格变更理由<textarea v-model="permissionReason" aria-label="资格变更理由" rows="3" maxlength="1000"></textarea></label><p v-if="targetReviewer === actorId" class="medical-error">不能为自己管理审核资格，请由另一位管理员操作。</p><p v-if="permissionError" class="medical-error" role="alert">{{ permissionError }}</p><footer><button type="button" class="admin-soft" @click="permissionDialog?.close()">取消</button><button type="submit" class="admin-primary" :disabled="targetReviewer === actorId">{{ permissionEnabled ? '保存审核资格' : '继续确认撤权' }}</button></footer></form></dialog>
  </div>
</template>
