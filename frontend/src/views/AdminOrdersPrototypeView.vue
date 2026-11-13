<script setup lang="ts">
// Development prototype: administrator appointment operations workspace with fictional data.
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from './patient-home-prototype/PrototypeIcon.vue'
import PrototypeDatePicker from './patient-home-prototype/PrototypeDatePicker.vue'
import './admin-home-prototype.css'
import './admin-orders-prototype.css'
import './patient-home-prototype/patient-home-prototype.css'

type Tab = 'appointments' | 'payments' | 'refunds'
type AppointmentStatus = 'PENDING_PAYMENT' | 'CONFIRMED' | 'WAITING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | 'EXPIRED'
type PaymentState = 'PENDING' | 'SUCCEEDED' | 'CLOSED'
type RefundState = 'REFUND_PENDING' | 'REFUND_PROCESSING' | 'REFUND_FAILED' | 'REFUNDED'
type Appointment = {
  id: string; patient: string; patientId: string; doctor: string; department: string; mode: '线上问诊' | '线下面诊'
  source: '预约挂号' | '当日挂号'; date: string; time: string; status: AppointmentStatus; payment: PaymentState
  encounter: '未签到' | '候诊中' | '就诊中' | '已完成' | '不适用'; fee: number; checkedIn: boolean; checkinEligible: boolean; reason?: string
}
type Payment = { id: string; appointmentId: string; patient: string; doctor: string; amount: number; state: PaymentState; createdAt: string; paidAt?: string; deadline?: string }
type Refund = { id: string; paymentId: string; appointmentId: string; patient: string; amount: number; reason: string; state: RefundState; attempts: number; createdAt: string; nextAttemptAt?: string; lastError?: string; note?: string }

const route = useRoute(), router = useRouter()
const tabs: { key: Tab; label: string }[] = [{ key: 'appointments', label: '预约登记' }, { key: 'payments', label: '支付订单' }, { key: 'refunds', label: '退款异常' }]
const groups = [
  { name: '运营', items: [{ label: '运营总览', icon: 'grid' }, { label: '预约与订单', icon: 'calendar', active: true }, { label: '退款 / 异常', icon: 'warning', badge: '3' }] },
  { name: '资源', items: [{ label: '患者', icon: 'user' }, { label: '医生', icon: 'doctor' }, { label: '科室', icon: 'hospital' }, { label: '排班', icon: 'calendar' }, { label: '号源', icon: 'grid' }, { label: '账户管理', icon: 'scan' }] },
  { name: '药品', items: [{ label: '药品与库存', icon: 'heart', badge: '4' }, { label: '药费订单', icon: 'wallet' }] },
  { name: '治理', items: [{ label: '审批与替诊', icon: 'file', badge: '6' }, { label: '新闻通知', icon: 'bell' }, { label: '医疗访问审核', icon: 'lock', badge: '1' }, { label: '审计日志', icon: 'scan' }] },
  { name: '系统', items: [{ label: '全局规则', icon: 'settings' }, { label: 'AI 配置', icon: 'sparkles' }, { label: '管理员账户', icon: 'user' }] },
]
const appointments = ref<Appointment[]>([
  { id: 'APT-20261113-001', patient: '林知夏', patientId: 'P-1007', doctor: '许知遥', department: '内科', mode: '线上问诊', source: '预约挂号', date: '2026-11-13', time: '08:00–12:00', status: 'WAITING', payment: 'SUCCEEDED', encounter: '候诊中', fee: 1800, checkedIn: true, checkinEligible: false },
  { id: 'APT-20261113-002', patient: '陈亦安', patientId: 'P-1021', doctor: '周宁', department: '内科', mode: '线下面诊', source: '当日挂号', date: '2026-11-13', time: '08:00–12:00', status: 'CONFIRMED', payment: 'SUCCEEDED', encounter: '未签到', fee: 1200, checkedIn: false, checkinEligible: true },
  { id: 'APT-20261113-003', patient: '沈嘉禾', patientId: 'P-1044', doctor: '姜悦', department: '皮肤科', mode: '线上问诊', source: '预约挂号', date: '2026-11-13', time: '14:00–18:00', status: 'PENDING_PAYMENT', payment: 'PENDING', encounter: '不适用', fee: 1600, checkedIn: false, checkinEligible: false },
  { id: 'APT-20261113-004', patient: '赵明远', patientId: 'P-1098', doctor: '马凌', department: '内科', mode: '线下面诊', source: '预约挂号', date: '2026-11-13', time: '08:00–12:00', status: 'IN_PROGRESS', payment: 'SUCCEEDED', encounter: '就诊中', fee: 1200, checkedIn: true, checkinEligible: false },
  { id: 'APT-20261113-005', patient: '顾思齐', patientId: 'P-1106', doctor: '林牧', department: '全科医学科', mode: '线下面诊', source: '预约挂号', date: '2026-11-13', time: '14:00–18:00', status: 'CONFIRMED', payment: 'SUCCEEDED', encounter: '未签到', fee: 1200, checkedIn: false, checkinEligible: false, reason: '签到窗口尚未开放' },
  { id: 'APT-20261112-006', patient: '苏晚晴', patientId: 'P-1120', doctor: '宋欣', department: '口腔科', mode: '线下面诊', source: '预约挂号', date: '2026-11-12', time: '09:00–12:00', status: 'COMPLETED', payment: 'SUCCEEDED', encounter: '已完成', fee: 2000, checkedIn: true, checkinEligible: false },
  { id: 'APT-20261112-007', patient: '唐可心', patientId: 'P-1138', doctor: '郭妤', department: '内科', mode: '线上问诊', source: '预约挂号', date: '2026-11-12', time: '14:00–18:00', status: 'CANCELLED', payment: 'SUCCEEDED', encounter: '不适用', fee: 1800, checkedIn: false, checkinEligible: false, reason: '医院停诊，已发起全额退款' },
  { id: 'APT-20261111-008', patient: '王子墨', patientId: 'P-1152', doctor: '白璐', department: '儿科', mode: '线下面诊', source: '当日挂号', date: '2026-11-11', time: '09:00–12:00', status: 'EXPIRED', payment: 'SUCCEEDED', encounter: '不适用', fee: 1500, checkedIn: false, checkinEligible: false, reason: '签到截止仍未签到，记为爽约' },
])
const payments = ref<Payment[]>([
  { id: 'PAY-8801', appointmentId: 'APT-20261113-001', patient: '林知夏', doctor: '许知遥', amount: 1800, state: 'SUCCEEDED', createdAt: '11月13日 07:42', paidAt: '11月13日 07:45' },
  { id: 'PAY-8802', appointmentId: 'APT-20261113-002', patient: '陈亦安', doctor: '周宁', amount: 1200, state: 'SUCCEEDED', createdAt: '11月13日 07:50', paidAt: '11月13日 07:51' },
  { id: 'PAY-8803', appointmentId: 'APT-20261113-003', patient: '沈嘉禾', doctor: '姜悦', amount: 1600, state: 'PENDING', createdAt: '11月13日 07:56', deadline: '11月13日 08:06' },
  { id: 'PAY-8790', appointmentId: 'APT-20261112-007', patient: '唐可心', doctor: '郭妤', amount: 1800, state: 'SUCCEEDED', createdAt: '11月12日 10:21', paidAt: '11月12日 10:25' },
])
const refunds = ref<Refund[]>([
  { id: 'REF-3001', paymentId: 'PAY-8790', appointmentId: 'APT-20261112-007', patient: '唐可心', amount: 1800, reason: '医院停诊', state: 'REFUND_FAILED', attempts: 2, createdAt: '11月12日 11:08', nextAttemptAt: '立即可重试', lastError: '模拟退款渠道超时' },
  { id: 'REF-3002', paymentId: 'PAY-8781', appointmentId: 'APT-20261111-008', patient: '王子墨', amount: 1500, reason: '迟到付款', state: 'REFUND_PENDING', attempts: 0, createdAt: '11月11日 12:02', nextAttemptAt: '等待处理' },
  { id: 'REF-3003', paymentId: 'PAY-8750', appointmentId: 'APT-20261108-015', patient: '陆清河', amount: 1200, reason: '患者预约前取消', state: 'REFUND_PROCESSING', attempts: 1, createdAt: '11月8日 09:18', nextAttemptAt: '处理中' },
  { id: 'REF-2994', paymentId: 'PAY-8722', appointmentId: 'APT-20261105-021', patient: '叶青', amount: 1800, reason: '替诊拒绝', state: 'REFUNDED', attempts: 1, createdAt: '11月5日 16:20' },
])

const validTab = (value: unknown): Tab => tabs.some(tab => tab.key === value) ? value as Tab : 'appointments'
type DateFilter = '2026-11-13' | '2026-11-12' | '2026-11-11' | 'ALL'
const validDate = (value: unknown): DateFilter => ['2026-11-13', '2026-11-12', '2026-11-11', 'ALL'].includes(value as string) ? value as DateFilter : '2026-11-13'
const tab = ref<Tab>(validTab(route.query.tab))
const dateFilter = ref<DateFilter>(validDate(route.query.date))
const statusFilter = ref(typeof route.query.status === 'string' ? route.query.status : 'ALL')
const modeFilter = ref(typeof route.query.mode === 'string' ? route.query.mode : 'ALL')
const departmentFilter = ref(typeof route.query.department === 'string' ? route.query.department : 'ALL')
const keyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')
const selectedAppointment = ref<Appointment | null>(null), selectedPayment = ref<Payment | null>(null), selectedRefund = ref<Refund | null>(null)
const notice = ref(''), manualNote = ref(''), collapsed = ref(false), mobileOpen = ref(false)
const departments = ['内科', '儿科', '皮肤科', '全科医学科', '口腔科']
const statusLabels: Record<AppointmentStatus, string> = { PENDING_PAYMENT: '待付款', CONFIRMED: '待签到', WAITING: '候诊中', IN_PROGRESS: '就诊中', COMPLETED: '已完成', CANCELLED: '已取消', EXPIRED: '已过期' }
const paymentLabels: Record<PaymentState, string> = { PENDING: '待付款', SUCCEEDED: '已付款', CLOSED: '已关闭' }
const refundLabels: Record<RefundState, string> = { REFUND_PENDING: '待处理', REFUND_PROCESSING: '处理中', REFUND_FAILED: '处理失败', REFUNDED: '已退款' }
const money = (minor: number) => `¥${(minor / 100).toFixed(2)}`
const filteredAppointments = computed(() => appointments.value.filter(item => {
  const matchesDate = dateFilter.value === 'ALL' || item.date === dateFilter.value
  const matchesStatus = statusFilter.value === 'ALL' || item.status === statusFilter.value
  const matchesMode = modeFilter.value === 'ALL' || item.mode === modeFilter.value
  const matchesDepartment = departmentFilter.value === 'ALL' || item.department === departmentFilter.value
  const query = keyword.value.trim()
  const matchesKeyword = !query || `${item.id}${item.patient}${item.patientId}${item.doctor}`.includes(query)
  return matchesDate && matchesStatus && matchesMode && matchesDepartment && matchesKeyword
}).sort((a, b) => `${a.status === 'COMPLETED' || a.status === 'CANCELLED' || a.status === 'EXPIRED' ? 1 : 0}${a.date}${a.time}`.localeCompare(`${b.status === 'COMPLETED' || b.status === 'CANCELLED' || b.status === 'EXPIRED' ? 1 : 0}${b.date}${b.time}`)))
const appointmentCount = computed(() => appointments.value.filter(item => item.date === '2026-11-13').length)
const pendingPaymentCount = computed(() => appointments.value.filter(item => item.payment === 'PENDING').length)
const activeEncounterCount = computed(() => appointments.value.filter(item => ['WAITING', 'IN_PROGRESS'].includes(item.status)).length)
const refundIssueCount = computed(() => refunds.value.filter(item => ['REFUND_PENDING', 'REFUND_FAILED'].includes(item.state)).length)
const currentRows = computed(() => tab.value === 'appointments' ? filteredAppointments.value : tab.value === 'payments' ? payments.value : refunds.value)

function syncUrl() {
  void router.replace({ query: { tab: tab.value, ...(dateFilter.value !== '2026-11-13' ? { date: dateFilter.value } : {}), ...(statusFilter.value !== 'ALL' ? { status: statusFilter.value } : {}), ...(modeFilter.value !== 'ALL' ? { mode: modeFilter.value } : {}), ...(departmentFilter.value !== 'ALL' ? { department: departmentFilter.value } : {}), ...(keyword.value ? { keyword: keyword.value } : {}) } })
}
function setTab(next: Tab) { tab.value = next; selectedAppointment.value = null; selectedPayment.value = null; selectedRefund.value = null; syncUrl() }
function resetFilters() { dateFilter.value = '2026-11-13'; statusFilter.value = 'ALL'; modeFilter.value = 'ALL'; departmentFilter.value = 'ALL'; keyword.value = ''; syncUrl() }
function openAppointment(item: Appointment) { selectedAppointment.value = item; notice.value = ''; manualNote.value = '' }
function openPayment(item: Payment) { selectedPayment.value = item }
function openRefund(item: Refund) { selectedRefund.value = item; notice.value = ''; manualNote.value = item.note ?? '' }
function adminCheckIn() {
  const item = selectedAppointment.value
  if (!item || !item.checkinEligible) return
  item.checkedIn = true; item.checkinEligible = false; item.status = 'WAITING'; item.encounter = '候诊中'; notice.value = '已记录管理员代签到，患者已进入候诊队列。'
}
function retryRefund() {
  const item = selectedRefund.value
  if (!item || !['REFUND_PENDING', 'REFUND_FAILED'].includes(item.state)) return
  item.state = 'REFUND_PROCESSING'; item.attempts += 1; item.nextAttemptAt = '演示处理中'; item.lastError = undefined; notice.value = '已提交退款重试，当前状态为处理中。'
}
function saveManualNote() {
  const item = selectedRefund.value
  if (!item || !manualNote.value.trim()) return
  item.note = manualNote.value.trim(); item.state = 'REFUND_PROCESSING'; item.nextAttemptAt = '等待人工跟进'; notice.value = '已保存人工处理备注，原退款记录保留。'
}
function navigate(module: string) {
  mobileOpen.value = false
  if (module === '运营总览') return void router.push({ path: '/prototype/admin-home', query: { variant: 'A' } })
  if (module === '预约与订单') return void router.push({ path: '/prototype/admin-orders', query: { tab: 'appointments' } })
  if (module === '退款 / 异常') return void router.push({ path: '/prototype/admin-orders', query: { tab: 'refunds' } })
  if (module === '账户管理') return void router.push({ path: '/prototype/admin-accounts' })
  void router.push({ path: '/prototype/admin-home', query: { module } })
}
function closeDetails() { selectedAppointment.value = null; selectedPayment.value = null; selectedRefund.value = null }
function onKeydown(event: KeyboardEvent) { if (event.key === 'Escape') closeDetails() }
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
watch(() => route.query.tab, value => { const next = validTab(value); if (next !== tab.value) tab.value = next })
watch(() => route.query.date, value => { const next = validDate(value); if (next !== dateFilter.value) dateFilter.value = next })
</script>

<template>
  <div class="admin-prototype" :class="{ 'admin-collapsed': collapsed }" @keydown.window="onKeydown">
    <aside class="admin-sidebar" :class="{ 'admin-mobile-open': mobileOpen }" aria-label="管理员端导航">
      <div class="admin-profile"><span class="admin-avatar">管</span><div class="admin-profile-copy"><strong>运营管理员</strong><span>平台治理与运营</span><em>管理员</em></div></div>
      <nav class="admin-nav" aria-label="管理员功能导航"><template v-for="group in groups" :key="group.name"><p class="admin-nav-caption">{{ group.name }}</p><button v-for="item in group.items" :key="item.label" type="button" :class="{ active: item.active && item.label === '预约与订单' }" :aria-label="item.label" @click="navigate(item.label)"><Icon :name="item.icon" /><span>{{ item.label }}</span><b v-if="item.badge">{{ item.badge }}</b></button></template></nav>
      <div class="admin-sidebar-bottom"><div class="admin-permission"><Icon name="lock" /><div><strong>医疗内容权限</strong><span>默认未开启</span></div></div><button type="button" class="admin-collapse" :aria-label="collapsed ? '展开侧栏' : '收起侧栏'" :aria-expanded="!collapsed" @click="collapsed = !collapsed"><Icon name="chevron" /><span>{{ collapsed ? '展开侧栏' : '收起侧栏' }}</span></button></div>
    </aside>
    <button v-if="mobileOpen" type="button" class="admin-backdrop" aria-label="关闭侧栏" @click="mobileOpen = false"></button>
    <section class="admin-workspace">
      <header class="admin-topbar"><div class="admin-topbar-left"><button type="button" class="admin-mobile-toggle" aria-label="打开侧栏" @click="mobileOpen = true"><Icon name="menu" /></button><div class="admin-brand"><span><Icon name="hospital" /></span><div><strong>智能医疗问诊预约系统</strong><small>ADMIN OPERATIONS</small></div></div><div class="admin-hospital"><i></i>市一医院 · 管理员工作台</div></div><div class="admin-topbar-right"><div class="admin-updated"><span>实时演示数据</span><Icon name="refresh" /></div><button type="button" class="admin-bell" aria-label="查看通知"><Icon name="bell" /><i></i></button><div class="admin-user"><span>管</span><strong>运营管理员</strong><Icon name="chevron" /></div></div></header>
      <main class="admin-content orders-content">
        <div class="orders-heading"><div><span class="admin-eyebrow">运营 / 预约与订单</span><h1>预约运营工作台</h1><p>分开查看预约履约、支付事实和退款处理，快速定位当天需要人工介入的记录。</p></div><div class="orders-heading-actions"><span class="orders-demo-chip"><i></i>虚构演示数据</span><button type="button" class="admin-primary" @click="resetFilters"><Icon name="refresh" />重置筛选</button></div></div>
        <section class="orders-summary" aria-label="预约运营摘要"><article><span>今日预约总量</span><strong>{{ appointmentCount }}</strong><small>线上 104 · 线下 182</small></article><article><span>待付款</span><strong>{{ pendingPaymentCount }}</strong><small>临时占号仍在有效期内</small></article><article><span>候诊 / 就诊中</span><strong>{{ activeEncounterCount }}</strong><small>需要关注接诊进度</small></article><article class="warning"><span>退款异常</span><strong>{{ refundIssueCount }}</strong><small>待处理或最近一次失败</small></article></section>
        <nav class="orders-tabs" role="tablist" aria-label="预约订单类型"><button v-for="item in tabs" :key="item.key" type="button" role="tab" :aria-selected="tab === item.key" :class="{ active: tab === item.key }" @click="setTab(item.key)">{{ item.label }}<b>{{ item.key === 'appointments' ? filteredAppointments.length : item.key === 'payments' ? payments.length : refunds.length }}</b></button></nav>
        <template v-if="tab === 'appointments'">
          <section class="orders-filters" aria-label="预约筛选"><PrototypeDatePicker v-model="dateFilter" label="预约日期" min-date="2026-11-11" max-date="2026-11-13" allow-all @update:model-value="syncUrl" /><label>状态<select v-model="statusFilter" aria-label="预约状态" @change="syncUrl"><option value="ALL">全部状态</option><option value="PENDING_PAYMENT">待付款</option><option value="CONFIRMED">待签到</option><option value="WAITING">候诊中</option><option value="IN_PROGRESS">就诊中</option><option value="COMPLETED">已完成</option><option value="CANCELLED">已取消</option><option value="EXPIRED">已过期</option></select></label><label>服务方式<select v-model="modeFilter" aria-label="服务方式" @change="syncUrl"><option value="ALL">线上 + 线下</option><option value="线上问诊">线上问诊</option><option value="线下面诊">线下面诊</option></select></label><label>科室<select v-model="departmentFilter" aria-label="预约科室" @change="syncUrl"><option value="ALL">全部科室</option><option v-for="item in departments" :key="item">{{ item }}</option></select></label><label class="orders-search">关键词<input v-model="keyword" aria-label="搜索预约" placeholder="预约号、患者或医生" @input="syncUrl"></label><span class="orders-filter-note">{{ dateFilter === '2026-11-13' ? '默认查看今日运营记录' : '按日期查看历史演示记录' }}</span></section>
          <section class="orders-table-panel" aria-label="预约登记列表"><div class="orders-table-head"><div><h2>预约登记 <span>{{ filteredAppointments.length }} 条</span></h2><p>预约状态、支付状态和接诊状态独立展示</p></div><button type="button" class="admin-link" @click="resetFilters">清除条件 <Icon name="close" /></button></div><div class="orders-table-wrap"><table class="orders-table"><thead><tr><th>预约登记</th><th>患者 / 医生</th><th>出诊安排</th><th>状态</th><th>金额</th><th>操作</th></tr></thead><tbody><tr v-for="item in filteredAppointments" :key="item.id"><td><strong>{{ item.id }}</strong><span>{{ item.source }} · {{ item.mode }}</span></td><td><strong>{{ item.patient }}</strong><span>{{ item.doctor }} · {{ item.department }}</span></td><td><strong>{{ item.date === '2026-11-13' ? '今天' : item.date }}</strong><span>{{ item.time }}</span></td><td><div class="orders-badges"><span :class="['orders-badge', `status-${item.status.toLowerCase()}`]">{{ statusLabels[item.status] }}</span><span :class="['orders-badge', `payment-${item.payment.toLowerCase()}`]">{{ paymentLabels[item.payment] }}</span><span class="orders-encounter">{{ item.encounter }}</span></div></td><td><strong>{{ money(item.fee) }}</strong><span>下单金额快照</span></td><td><button type="button" class="orders-row-action" :aria-label="`查看${item.id}详情`" @click="openAppointment(item)">查看详情 <Icon name="chevron" /></button></td></tr><tr v-if="!filteredAppointments.length"><td colspan="6" class="orders-empty"><Icon name="calendar" /><strong>没有匹配的预约记录</strong><span>可以清除筛选后查看全部今日演示数据。</span></td></tr></tbody></table></div></section>
        </template>
         <template v-else-if="tab === 'payments'"><section class="orders-table-panel" aria-label="支付订单列表"><div class="orders-table-head"><div><h2>支付订单 <span>{{ payments.length }} 条</span></h2><p>支付记录与预约履约状态独立保存</p></div><span class="orders-filter-note">金额均为下单时快照</span></div><div class="orders-table-wrap"><table class="orders-table"><thead><tr><th>支付订单</th><th>关联预约</th><th>患者 / 医生</th><th>支付状态</th><th>金额</th><th>时间</th></tr></thead><tbody><tr v-for="item in payments" :key="item.id" tabindex="0" @click="openPayment(item)"><td><strong>{{ item.id }}</strong><span>模拟支付 · 不代表真实转账</span></td><td><strong>{{ item.appointmentId }}</strong><span>可回看预约时间线</span></td><td><strong>{{ item.patient }}</strong><span>{{ item.doctor }}</span></td><td><span :class="['orders-badge', `payment-${item.state.toLowerCase()}`]">{{ paymentLabels[item.state] }}</span></td><td><strong>{{ money(item.amount) }}</strong></td><td><strong>{{ item.paidAt || item.createdAt }}</strong><span v-if="item.deadline">截止 {{ item.deadline }}</span></td></tr></tbody></table></div></section></template>
         <template v-else><section class="orders-table-panel" aria-label="退款异常列表"><div class="orders-table-head"><div><h2>退款异常 <span>{{ refunds.length }} 条</span></h2><p>退款独立记录状态，失败可重试；不会恢复已取消预约</p></div><span class="orders-filter-note">{{ refundIssueCount }} 条需要关注</span></div><div class="orders-table-wrap"><table class="orders-table"><thead><tr><th>退款记录</th><th>关联预约 / 患者</th><th>退款原因</th><th>状态</th><th>尝试次数</th><th>操作</th></tr></thead><tbody><tr v-for="item in refunds" :key="item.id" @click="openRefund(item)"><td><strong>{{ item.id }}</strong><span>{{ item.createdAt }}</span></td><td><strong>{{ item.appointmentId }}</strong><span>{{ item.patient }} · {{ money(item.amount) }}</span></td><td><strong>{{ item.reason }}</strong><span>{{ item.lastError || item.note || '等待退款处理器' }}</span></td><td><span :class="['orders-badge', `refund-${item.state.toLowerCase().replace('_', '-')}`]">{{ refundLabels[item.state] }}</span></td><td><strong>第 {{ item.attempts }} 次</strong><span>{{ item.nextAttemptAt || '已完成' }}</span></td><td><button type="button" class="orders-row-action" :aria-label="`查看${item.id}详情`" @click.stop="openRefund(item)">查看处理 <Icon name="chevron" /></button></td></tr></tbody></table></div></section></template>
        <p class="orders-footnote"><Icon name="shield" /> 管理员不能修改医生医疗结果；医疗正文和附件需要独立审核权限。所有状态均为虚构演示，不连接真实订单接口。</p>
      </main>
    </section>
    <div v-if="selectedAppointment" class="orders-drawer-backdrop" @click="closeDetails"><aside class="orders-drawer" aria-label="预约详情" @click.stop><button type="button" class="orders-drawer-close" aria-label="关闭预约详情" @click="closeDetails"><Icon name="close" /></button><span class="orders-drawer-kicker">预约登记 · {{ statusLabels[selectedAppointment.status] }}</span><h2>{{ selectedAppointment.patient }} · {{ selectedAppointment.id }}</h2><p>{{ selectedAppointment.doctor }} · {{ selectedAppointment.department }} · {{ selectedAppointment.mode }}</p><div class="orders-detail-grid"><span>出诊安排<strong>{{ selectedAppointment.date }} {{ selectedAppointment.time }}</strong></span><span>预约来源<strong>{{ selectedAppointment.source }}</strong></span><span>支付状态<strong>{{ paymentLabels[selectedAppointment.payment] }} · {{ money(selectedAppointment.fee) }}</strong></span><span>接诊状态<strong>{{ selectedAppointment.encounter }}</strong></span></div><div class="orders-timeline"><h3>业务时间线</h3><div class="done"><i></i><span>预约创建<strong>11月13日 07:42 · 临时占号已建立</strong></span></div><div :class="{ done: selectedAppointment.payment === 'SUCCEEDED' }"><i></i><span>支付<strong>{{ selectedAppointment.payment === 'SUCCEEDED' ? '模拟支付成功，预约已确认' : '尚未完成支付' }}</strong></span></div><div :class="{ done: selectedAppointment.checkedIn }"><i></i><span>签到<strong>{{ selectedAppointment.checkedIn ? '已签到' : selectedAppointment.reason || '尚未签到' }}</strong></span></div><div :class="{ done: ['WAITING', 'IN_PROGRESS', 'COMPLETED'].includes(selectedAppointment.status) }"><i></i><span>候诊 / 接诊<strong>{{ selectedAppointment.encounter }}</strong></span></div><div :class="{ done: selectedAppointment.status === 'COMPLETED' || selectedAppointment.status === 'CANCELLED' || selectedAppointment.status === 'EXPIRED' }"><i></i><span>完成 / 取消 / 过期<strong>{{ selectedAppointment.reason || (selectedAppointment.status === 'COMPLETED' ? '医生已提交接诊结果' : '尚未发生') }}</strong></span></div></div><p v-if="notice" class="orders-feedback" role="status">{{ notice }}</p><div class="orders-drawer-actions"><button type="button" class="admin-soft" @click="closeDetails">关闭</button><button type="button" class="admin-primary" :disabled="!selectedAppointment.checkinEligible" @click="adminCheckIn">{{ selectedAppointment.checkinEligible ? '管理员代签到' : selectedAppointment.reason || '当前不可代签到' }}</button></div><p class="orders-drawer-note">代签到仅在后端允许的签到窗口内可用，并会记录实际操作者。</p></aside></div>
    <div v-else-if="selectedPayment" class="orders-drawer-backdrop" @click="closeDetails"><aside class="orders-drawer" aria-label="支付订单详情" @click.stop><button type="button" class="orders-drawer-close" aria-label="关闭支付详情" @click="closeDetails"><Icon name="close" /></button><span class="orders-drawer-kicker">支付订单</span><h2>{{ selectedPayment.id }}</h2><p>{{ selectedPayment.patient }} · {{ selectedPayment.doctor }} · 关联 {{ selectedPayment.appointmentId }}</p><div class="orders-detail-grid"><span>支付状态<strong>{{ paymentLabels[selectedPayment.state] }}</strong></span><span>金额<strong>{{ money(selectedPayment.amount) }}</strong></span><span>创建时间<strong>{{ selectedPayment.createdAt }}</strong></span><span>支付时间<strong>{{ selectedPayment.paidAt || '尚未支付' }}</strong></span></div><p class="orders-drawer-note">支付记录只表达资金事实，不直接代表已经签到、候诊或完成接诊。</p><div class="orders-drawer-actions"><button type="button" class="admin-primary" @click="setTab('appointments'); closeDetails()">查看关联预约</button></div></aside></div>
    <div v-else-if="selectedRefund" class="orders-drawer-backdrop" @click="closeDetails"><aside class="orders-drawer" aria-label="退款详情" @click.stop><button type="button" class="orders-drawer-close" aria-label="关闭退款详情" @click="closeDetails"><Icon name="close" /></button><span class="orders-drawer-kicker">退款异常 · {{ refundLabels[selectedRefund.state] }}</span><h2>{{ selectedRefund.id }}</h2><p>{{ selectedRefund.patient }} · {{ selectedRefund.appointmentId }} · {{ money(selectedRefund.amount) }}</p><div class="orders-detail-grid"><span>退款原因<strong>{{ selectedRefund.reason }}</strong></span><span>支付订单<strong>{{ selectedRefund.paymentId }}</strong></span><span>尝试次数<strong>{{ selectedRefund.attempts }} 次</strong></span><span>最近状态<strong>{{ selectedRefund.lastError || selectedRefund.nextAttemptAt || '已退款' }}</strong></span></div><div class="orders-timeline"><h3>退款时间线</h3><div class="done"><i></i><span>退款记录创建<strong>{{ selectedRefund.createdAt }}</strong></span></div><div :class="{ done: selectedRefund.attempts > 0 }"><i></i><span>退款处理<strong>{{ selectedRefund.attempts ? `已尝试 ${selectedRefund.attempts} 次` : '等待首次处理' }}</strong></span></div><div :class="{ done: selectedRefund.state === 'REFUNDED' }"><i></i><span>退款结果<strong>{{ refundLabels[selectedRefund.state] }}</strong></span></div></div><p v-if="notice" class="orders-feedback" role="status">{{ notice }}</p><label class="orders-note-field">人工处理备注<textarea v-model="manualNote" maxlength="500" placeholder="仅在需要人工跟进时填写"></textarea></label><div class="orders-drawer-actions"><button type="button" class="admin-soft" @click="saveManualNote">记录人工处理</button><button type="button" class="admin-primary" :disabled="!['REFUND_PENDING', 'REFUND_FAILED'].includes(selectedRefund.state)" @click="retryRefund">{{ ['REFUND_PENDING', 'REFUND_FAILED'].includes(selectedRefund.state) ? '重试退款' : refundLabels[selectedRefund.state] }}</button></div><p class="orders-drawer-note">重试只更新演示状态；不会重复创建退款，也不会恢复已取消预约。</p></aside></div>
  </div>
</template>
