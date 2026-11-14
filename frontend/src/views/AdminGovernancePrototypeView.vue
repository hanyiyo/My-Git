<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from './patient-home-prototype/PrototypeIcon.vue'
import AdminOperationsShell from '../components/AdminOperationsShell.vue'
import './admin-home-prototype.css'
import './admin-orders-prototype.css'
import './admin-governance-prototype.css'

type GovernanceState = 'HEAD_REVIEW' | 'SUBSTITUTE_REVIEW' | 'ADMIN_REVIEW' | 'HEAD_REJECTED' | 'APPROVED_STOP' | 'APPROVED_SUBSTITUTE' | 'EMERGENCY_STOP'
type RequestType = 'SUBSTITUTE' | 'STOP' | 'EMERGENCY_STOP'
type PatientChoice = 'ACCEPT' | 'REJECT' | ''
type PatientImpact = { id: string; name: string; appointmentId: string; state: 'CONFIRMED' | 'PAID' | 'CANCELLED'; choice?: PatientChoice }
type GovernanceTicket = {
  id: string; type: RequestType; doctor: string; department: string; date: string; period: '上午' | '下午'; time: string
  substitute: string | null; state: GovernanceState; submittedAt: number; reason: string; validation: string[]
  conflict: string | null; patients: PatientImpact[]; emergency?: boolean
}
type ModalAction = 'APPROVE_SUBSTITUTE' | 'APPROVE_STOP' | 'REJECT' | 'EMERGENCY'

const demoNow = Date.parse('2026-11-13T07:40:00+08:00')
const tickets = ref<GovernanceTicket[]>([
  { id: 'SUS-20261113-001', type: 'SUBSTITUTE', doctor: '周宁', department: '内科', date: '2026-11-15', period: '上午', time: '08:00–12:00', substitute: '林牧', state: 'ADMIN_REVIEW', submittedAt: Date.parse('2026-11-13T06:50:00+08:00'), reason: '原医生临时参加院内培训，申请由同科室医生接诊。', validation: ['替诊医生资质有效', '科室匹配', '时间无冲突'], conflict: null, patients: [{ id: 'P-1007', name: '林知夏', appointmentId: 'APT-20261115-021', state: 'CONFIRMED' }, { id: 'P-1021', name: '陈亦安', appointmentId: 'APT-20261115-026', state: 'PAID' }] },
  { id: 'SUS-20261113-002', type: 'STOP', doctor: '姜悦', department: '皮肤科', date: '2026-11-16', period: '下午', time: '14:00–18:00', substitute: null, state: 'ADMIN_REVIEW', submittedAt: Date.parse('2026-11-13T06:25:00+08:00'), reason: '设备维护安排与本次出诊时间冲突，申请停诊。', validation: ['已确认挂号 1 人', '退款路径可用'], conflict: null, patients: [{ id: 'P-1044', name: '沈嘉禾', appointmentId: 'APT-20261116-009', state: 'PAID' }] },
  { id: 'SUS-20261113-007', type: 'SUBSTITUTE', doctor: '许知遥', department: '内科', date: '2026-11-13', period: '上午', time: '08:30–10:30', substitute: '马凌', state: 'ADMIN_REVIEW', submittedAt: Date.parse('2026-11-13T06:55:00+08:00'), reason: '突发身体不适，需紧急安排同科室替诊。', validation: ['替诊医生资质有效', '科室匹配'], conflict: null, patients: [{ id: 'P-1098', name: '赵明远', appointmentId: 'APT-20261113-004', state: 'CONFIRMED' }, { id: 'P-1120', name: '苏晚晴', appointmentId: 'APT-20261113-006', state: 'PAID' }], emergency: true },
  { id: 'SUS-20261113-003', type: 'SUBSTITUTE', doctor: '许知遥', department: '内科', date: '2026-11-14', period: '上午', time: '08:00–12:00', substitute: '马凌', state: 'SUBSTITUTE_REVIEW', submittedAt: Date.parse('2026-11-12T15:10:00+08:00'), reason: '原医生临时外出，等待替诊医生确认。', validation: ['等待替诊医生确认'], conflict: null, patients: [{ id: 'P-1134', name: '顾言', appointmentId: 'APT-20261114-012', state: 'CONFIRMED' }] },
  { id: 'SUS-20261112-004', type: 'SUBSTITUTE', doctor: '宋欣', department: '口腔科', date: '2026-11-18', period: '上午', time: '08:00–12:00', substitute: '林牧', state: 'APPROVED_SUBSTITUTE', submittedAt: Date.parse('2026-11-12T10:20:00+08:00'), reason: '原医生参加学术会议。', validation: ['替诊已生效', '挂号已自动迁移'], conflict: null, patients: [{ id: 'P-1152', name: '谢宁', appointmentId: 'APT-20261118-003', state: 'CONFIRMED' }] },
  { id: 'SUS-20261110-005', type: 'STOP', doctor: '陈闻', department: '儿科', date: '2026-11-20', period: '下午', time: '14:00–18:00', substitute: null, state: 'APPROVED_STOP', submittedAt: Date.parse('2026-11-10T09:14:00+08:00'), reason: '医生参加外出培训。', validation: ['停诊已生效', '已完成退款预览'], conflict: null, patients: [{ id: 'P-1180', name: '方可', appointmentId: 'APT-20261120-018', state: 'CANCELLED' }] },
  { id: 'SUS-20261108-006', type: 'SUBSTITUTE', doctor: '赵明远', department: '内科', date: '2026-11-21', period: '上午', time: '08:00–12:00', substitute: '林牧', state: 'HEAD_REJECTED', submittedAt: Date.parse('2026-11-08T12:40:00+08:00'), reason: '替诊医生存在同时间段排班冲突。', validation: ['科室匹配'], conflict: '替诊医生林牧已有同时间段排班', patients: [] },
])

const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })
const router = useRouter()
type ProcessingFilter = 'ALL' | 'PENDING' | 'PROCESSED'
const processingFilter = ref<ProcessingFilter>('PENDING')
const startDate = ref(''), endDate = ref(''), keyword = ref('')
const selected = ref<GovernanceTicket | null>(null)
const modal = ref<ModalAction | null>(null), formReason = ref(''), formError = ref(''), notice = ref('')
const patientChoices = ref<Record<string, PatientChoice>>({})
const statusLabels: Record<GovernanceState, string> = { HEAD_REVIEW: '待科主任审核', SUBSTITUTE_REVIEW: '待替诊医生确认', ADMIN_REVIEW: '待管理员复核', HEAD_REJECTED: '已驳回', APPROVED_STOP: '已停诊', APPROVED_SUBSTITUTE: '已替诊', EMERGENCY_STOP: '紧急停诊' }
const typeLabels: Record<RequestType, string> = { SUBSTITUTE: '替诊', STOP: '直接停诊', EMERGENCY_STOP: '紧急停诊' }
const stateClass = (state: GovernanceState) => state.toLowerCase().replaceAll('_', '-')
const typeClass = (type: RequestType) => type.toLowerCase().replaceAll('_', '-')
const formatTime = (value: number) => new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
const isUrgent = (item: GovernanceTicket) => Boolean(item.emergency || Date.parse(`${item.date}T${item.time.slice(0, 5)}:00+08:00`) - demoNow < 2 * 60 * 60 * 1000)
const processedStates: GovernanceState[] = ['HEAD_REJECTED', 'APPROVED_STOP', 'APPROVED_SUBSTITUTE', 'EMERGENCY_STOP']
const isProcessed = (item: GovernanceTicket) => processedStates.includes(item.state)
const summary = computed(() => ({ admin: tickets.value.filter(item => item.state === 'ADMIN_REVIEW').length, emergency: tickets.value.filter(item => isUrgent(item) && item.state === 'ADMIN_REVIEW').length, substitute: tickets.value.filter(item => item.state === 'SUBSTITUTE_REVIEW').length, handled: tickets.value.filter(item => ['APPROVED_STOP', 'APPROVED_SUBSTITUTE', 'EMERGENCY_STOP'].includes(item.state) && item.submittedAt >= demoNow - 7 * 86400000).length }))
const dateError = computed(() => startDate.value && endDate.value && startDate.value > endDate.value ? '结束日期不能早于开始日期。' : '')
const filtered = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return tickets.value.filter(item => {
    const dateMatch = (!startDate.value || item.date >= startDate.value) && (!endDate.value || item.date <= endDate.value)
    const processingMatch = processingFilter.value === 'ALL' || (processingFilter.value === 'PROCESSED' ? isProcessed(item) : !isProcessed(item))
    const textMatch = !query || `${item.id}${item.doctor}${item.department}${item.substitute ?? ''}${item.reason}`.toLowerCase().includes(query)
    return dateMatch && processingMatch && textMatch
  }).sort((a, b) => b.submittedAt - a.submittedAt)
})
const patientSelectionComplete = computed(() => Boolean(selected.value?.patients.length && selected.value.patients.every(patient => patientChoices.value[patient.id])))

function resetFilters() { startDate.value = ''; endDate.value = ''; processingFilter.value = 'PENDING'; keyword.value = '' }
function openDetails(item: GovernanceTicket) { selected.value = item; notice.value = ''; formError.value = '' }
function closeDetails() { selected.value = null }
function canReview(item: GovernanceTicket | null) { return Boolean(item && item.state === 'ADMIN_REVIEW') }
function openAction(action: ModalAction) { if (!selected.value || !canReview(selected.value)) return; modal.value = action; formReason.value = ''; formError.value = ''; patientChoices.value = Object.fromEntries(selected.value.patients.map(patient => [patient.id, ''])) }
function closeModal() { modal.value = null; formError.value = '' }
function submitAction() {
  if (!selected.value || !modal.value) return
  if ((modal.value === 'REJECT' || modal.value === 'EMERGENCY') && !formReason.value.trim()) { formError.value = modal.value === 'REJECT' ? '请填写驳回理由。' : '请填写紧急处理理由。'; return }
  if (modal.value === 'EMERGENCY' && !patientSelectionComplete.value) { formError.value = '请逐一选择所有患者的处理方式。'; return }
  const item = selected.value
  if (modal.value === 'APPROVE_SUBSTITUTE') item.state = 'APPROVED_SUBSTITUTE'
  if (modal.value === 'APPROVE_STOP') item.state = 'APPROVED_STOP'
  if (modal.value === 'REJECT') item.state = 'HEAD_REJECTED'
  if (modal.value === 'EMERGENCY') item.state = 'EMERGENCY_STOP'
  notice.value = `${item.id} 已${modal.value === 'REJECT' ? '驳回申请' : modal.value === 'APPROVE_SUBSTITUTE' ? '通过替诊' : modal.value === 'APPROVE_STOP' ? '确认停诊' : '完成紧急处理'}。`
  closeModal()
}
function openSchedule() { if (selected.value) router.push({ path: '/prototype/admin-schedules', query: { date: selected.value.date } }) }
function openAppointments() { if (selected.value) router.push({ path: '/prototype/admin-orders', query: { tab: 'appointments', date: selected.value.date, keyword: selected.value.id } }) }
function onKeydown(event: KeyboardEvent) { if (event.key === 'Escape') { if (modal.value) closeModal(); else closeDetails() } }
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <AdminOperationsShell :embedded="props.embedded" active="审批与替诊">
    <div class="governance-content">
      <div class="governance-heading"><div><span class="admin-eyebrow">治理 / 停诊与替诊</span><h1>审批与替诊工作台</h1><p>处理排班变更的管理员终审，患者影响与退款预览仅展示运营必要信息。</p></div><div class="governance-heading-actions"><span class="orders-demo-chip"><i></i>虚构演示数据</span><button type="button" class="admin-soft" @click="resetFilters"><Icon name="refresh" />重置筛选</button></div></div>
      <section class="orders-summary governance-summary" aria-label="审批与替诊摘要"><article class="warning"><span>待管理员复核</span><strong>{{ summary.admin }}</strong><small>需要人工终审</small></article><article><span>紧急处理</span><strong>{{ summary.emergency }}</strong><small>距出诊不足 2 小时</small></article><article><span>替诊待确认</span><strong>{{ summary.substitute }}</strong><small>等待替诊医生确认</small></article><article><span>近 7 日已处理</span><strong>{{ summary.handled }}</strong><small>停诊 / 替诊已完成</small></article></section>
      <section class="governance-filters" aria-label="审批与替诊筛选"><label>处理状态<select v-model="processingFilter" aria-label="处理状态"><option value="PENDING">待处理</option><option value="PROCESSED">已处理</option><option value="ALL">全部工单</option></select></label><div class="governance-date"><label>提交日期<input v-model="startDate" type="date" aria-label="提交开始日期"></label><span>至</span><label><span class="visually-hidden">提交结束日期</span><input v-model="endDate" type="date" aria-label="提交结束日期"></label></div><label class="governance-search">关键词<input v-model="keyword" aria-label="搜索审批工单" placeholder="工单号、医生或理由"></label><button type="button" class="governance-reset" @click="resetFilters">重置筛选</button><p v-if="dateError" class="governance-filter-error" role="alert">{{ dateError }}</p><span v-else class="governance-filter-note">待处理包含科主任审核、替诊确认和管理员复核 · 日期最多查询近一年</span></section>
      <p v-if="notice" class="governance-feedback" role="status">{{ notice }}</p>
      <section class="orders-table-panel governance-panel" aria-label="审批工单列表"><div class="orders-table-head"><div><h2>审批工单 <span>{{ filtered.length }} 条</span></h2><p>列表包含全部状态；只有“待管理员复核”允许执行终审动作</p></div><button type="button" class="admin-link" @click="notice = `已刷新，当前显示 ${filtered.length} 条审批工单。`">刷新数据 <Icon name="refresh" /></button></div><div class="orders-table-wrap"><table class="orders-table governance-table"><caption class="visually-hidden">审批与替诊工单列表</caption><thead><tr><th>工单编号</th><th>申请类型</th><th>原医生 / 科室</th><th>出诊安排</th><th>替诊医生</th><th>当前状态</th><th>提交时间</th><th>截止提醒</th><th>操作</th></tr></thead><tbody><tr v-for="item in filtered" :key="item.id" tabindex="0" @click="openDetails(item)" @keydown.enter="openDetails(item)"><td><strong>{{ item.id }}</strong><span>{{ item.patients.length }} 位患者受影响</span></td><td><span :class="['governance-type', `type-${typeClass(item.type)}`]">{{ item.emergency ? '紧急替诊' : typeLabels[item.type] }}</span></td><td><strong>{{ item.doctor }}</strong><span>{{ item.department }}</span></td><td><strong>{{ item.date }} · {{ item.period }}</strong><span>{{ item.time }}</span></td><td><strong>{{ item.substitute || '直接停诊' }}</strong><span v-if="item.conflict" class="governance-danger">存在冲突</span></td><td><span :class="['governance-state', `state-${stateClass(item.state)}`]">{{ statusLabels[item.state] }}</span></td><td><strong>{{ formatTime(item.submittedAt) }}</strong></td><td><span v-if="isUrgent(item)" class="governance-urgent">紧急处理</span><span v-else class="governance-muted">按流程处理</span></td><td><button type="button" class="orders-row-action" :aria-label="`查看${item.id}详情`" @click.stop="openDetails(item)">查看详情 <Icon name="chevron" /></button></td></tr><tr v-if="!dateError && !filtered.length"><td colspan="9" class="orders-empty"><Icon name="file" /><strong>当前筛选暂无审批工单</strong><span>可以清除条件后继续查看。</span></td></tr><tr v-if="dateError"><td colspan="9" class="orders-empty"><Icon name="calendar" /><strong>日期范围无效</strong><span>请先修正提交日期范围。</span></td></tr></tbody></table></div></section>
      <p class="orders-footnote"><Icon name="shield" /> 管理员只处理停诊与替诊终审，不查看病历、症状、处方或其他医疗正文。</p>
    </div>
    <div v-if="selected" class="orders-drawer-backdrop" @click.self="closeDetails"><aside class="orders-drawer governance-drawer" aria-label="审批工单详情" @click.stop><button type="button" class="orders-drawer-close" aria-label="关闭审批工单详情" @click="closeDetails"><Icon name="close" /></button><span class="orders-drawer-kicker">{{ selected.emergency ? '紧急处理' : typeLabels[selected.type] }} · {{ statusLabels[selected.state] }}</span><h2>{{ selected.id }}</h2><p>{{ selected.doctor }} · {{ selected.department }} · {{ selected.date }} · {{ selected.period }}</p><section class="governance-detail-card"><div><span>原排班</span><strong>{{ selected.doctor }} · {{ selected.time }}</strong></div><div><span>替诊医生</span><strong>{{ selected.substitute || '直接停诊' }}</strong></div><div><span>提交时间</span><strong>{{ formatTime(selected.submittedAt) }}</strong></div><div><span>患者影响</span><strong>{{ selected.patients.length }} 人</strong></div></section><section class="governance-detail-section"><h3>申请理由</h3><p>{{ selected.reason }}</p></section><section class="governance-detail-section"><h3>校验结果</h3><ul><li v-for="item in selected.validation" :key="item" :class="{ danger: selected.conflict && item.includes('冲突') }">{{ item }}</li></ul><p v-if="selected.conflict" class="governance-danger">{{ selected.conflict }}</p></section><section class="governance-detail-section"><div class="governance-section-heading"><h3>患者影响与处理预览</h3><span>{{ selected.patients.length }} 人</span></div><div v-for="patient in selected.patients" :key="patient.id" class="governance-patient"><div><strong>{{ patient.name }}</strong><span>{{ patient.id }} · {{ patient.appointmentId }}</span></div><span :class="['governance-patient-state', patient.state.toLowerCase()]">{{ patient.state === 'CANCELLED' ? '已取消' : patient.state === 'PAID' ? '已付款' : '已确认' }}</span><span v-if="selected.type === 'STOP' || selected.state === 'APPROVED_STOP' || selected.state === 'EMERGENCY_STOP'" class="governance-refund">取消并退款</span><span v-else-if="selected.type === 'SUBSTITUTE'" class="governance-migrate">自动迁移</span></div></section><p v-if="!canReview(selected)" class="governance-readonly"><Icon name="info" /> 当前由{{ selected.state === 'SUBSTITUTE_REVIEW' ? '替诊医生' : selected.state === 'HEAD_REVIEW' ? '科主任' : '流程记录' }}负责，管理员只能查看。</p><div class="governance-drawer-links"><button type="button" class="admin-soft" @click="openSchedule">查看排班 <Icon name="arrow" /></button><button type="button" class="admin-soft" @click="openAppointments">查看预约与订单 <Icon name="arrow" /></button></div><div class="orders-drawer-actions governance-actions"><template v-if="canReview(selected)"><button type="button" class="admin-soft" @click="openAction('REJECT')">驳回申请</button><button v-if="selected.type === 'STOP'" type="button" class="admin-soft" @click="openAction('APPROVE_STOP')">确认停诊</button><button v-else type="button" class="admin-primary" @click="openAction(selected.emergency ? 'EMERGENCY' : 'APPROVE_SUBSTITUTE')">{{ selected.emergency ? '进入紧急处理' : '通过替诊' }}</button></template><span v-else class="governance-readonly-label">此工单已进入只读状态</span></div></aside></div>
    <div v-if="modal && selected" class="governance-modal-backdrop" @click.self="closeModal"><section class="governance-modal" role="dialog" aria-modal="true" aria-label="审批确认"><button type="button" class="orders-drawer-close" aria-label="关闭审批确认" @click="closeModal"><Icon name="close" /></button><span class="orders-drawer-kicker">审批确认</span><h2>{{ modal === 'REJECT' ? '驳回申请' : modal === 'APPROVE_SUBSTITUTE' ? '确认通过替诊' : modal === 'APPROVE_STOP' ? '确认直接停诊' : '紧急处理替诊' }}</h2><p v-if="modal === 'APPROVE_SUBSTITUTE'">通过后将关闭原排班、创建替诊排班，并自动迁移 {{ selected.patients.length }} 条有效挂号。</p><p v-else-if="modal === 'APPROVE_STOP'">确认后将取消 {{ selected.patients.length }} 条受影响挂号，已付款挂号进入退款流程。</p><p v-else-if="modal === 'REJECT'">驳回后工单退回申请链路，申请人可根据理由重新提交。</p><p v-else>距离出诊不足 2 小时，请逐一确认患者接受替诊或拒绝并退款。</p><div v-if="modal === 'EMERGENCY'" class="governance-choice-list"><label v-for="patient in selected.patients" :key="patient.id"><span>{{ patient.name }} · {{ patient.id }}</span><select v-model="patientChoices[patient.id]" :aria-label="`${patient.name}处理方式`"><option value="">请选择</option><option value="ACCEPT">接受替诊</option><option value="REJECT">拒绝并退款</option></select></label></div><label v-if="modal === 'REJECT' || modal === 'EMERGENCY'" class="governance-reason">{{ modal === 'REJECT' ? '驳回理由' : '紧急处理理由' }}<textarea v-model="formReason" :aria-label="modal === 'REJECT' ? '驳回理由' : '紧急处理理由'" placeholder="请填写处理理由"></textarea></label><p v-if="formError" class="governance-form-error" role="alert">{{ formError }}</p><div class="governance-modal-actions"><button type="button" class="admin-soft" @click="closeModal">取消</button><button type="button" class="admin-primary" @click="submitAction">确认提交</button></div></section></div>
  </AdminOperationsShell>
</template>

