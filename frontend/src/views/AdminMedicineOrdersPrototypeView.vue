<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from './patient-home-prototype/PrototypeIcon.vue'
import AdminOperationsShell from '../components/AdminOperationsShell.vue'
import './admin-home-prototype.css'
import './admin-medicine-orders-prototype.css'

type PaymentState = 'PENDING_PAYMENT' | 'PAID' | 'EXPIRED'
type AppointmentState = 'CONFIRMED' | 'WAITING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
type MedicineLine = { name: string; spec: string; unitMinor: number; quantity: number }
type MedicinePaymentOrder = {
  id: string; patient: string; patientId: string; doctor: string; department: string
  appointmentId: string; appointmentDate: string; appointmentPeriod: '上午' | '下午'
  mode: '线上问诊' | '线下面诊'; appointmentState: AppointmentState; state: PaymentState
  createdAt: number; expiresAt: number; paidAt: number | null; lines: MedicineLine[]
}

const demoNow = Date.parse('2026-11-13T07:40:00+08:00')
const orders = ref<MedicinePaymentOrder[]>([
  { id: 'MEDPAY-20261113-001', patient: '林知夏', patientId: 'P-1007', doctor: '许知遥', department: '内科', appointmentId: 'APT-20261113-001', appointmentDate: '2026-11-13', appointmentPeriod: '上午', mode: '线上问诊', appointmentState: 'WAITING', state: 'PAID', createdAt: Date.parse('2026-11-13T07:42:00+08:00'), expiresAt: Date.parse('2026-11-13T17:42:00+08:00'), paidAt: Date.parse('2026-11-13T07:45:00+08:00'), lines: [{ name: '对乙酰氨基酚片', spec: '0.5g × 12 片 / 盒', unitMinor: 1800, quantity: 2 }] },
  { id: 'MEDPAY-20261113-002', patient: '陈亦安', patientId: 'P-1021', doctor: '周宁', department: '内科', appointmentId: 'APT-20261113-002', appointmentDate: '2026-11-13', appointmentPeriod: '上午', mode: '线下面诊', appointmentState: 'CONFIRMED', state: 'PENDING_PAYMENT', createdAt: Date.parse('2026-11-13T07:56:00+08:00'), expiresAt: Date.parse('2026-11-13T08:06:00+08:00'), paidAt: null, lines: [{ name: '阿莫西林胶囊', spec: '0.25g × 24 粒 / 盒', unitMinor: 3200, quantity: 1 }, { name: '维生素 C 片', spec: '100mg × 100 片 / 瓶', unitMinor: 980, quantity: 1 }, { name: '氯雷他定片', spec: '10mg × 6 片 / 盒', unitMinor: 1500, quantity: 1 }] },
  { id: 'MEDPAY-20261113-003', patient: '沈嘉禾', patientId: 'P-1044', doctor: '姜悦', department: '皮肤科', appointmentId: 'APT-20261113-003', appointmentDate: '2026-11-13', appointmentPeriod: '下午', mode: '线上问诊', appointmentState: 'COMPLETED', state: 'PAID', createdAt: Date.parse('2026-11-13T08:18:00+08:00'), expiresAt: Date.parse('2026-11-13T18:18:00+08:00'), paidAt: Date.parse('2026-11-13T08:24:00+08:00'), lines: [{ name: '氯雷他定片', spec: '10mg × 6 片 / 盒', unitMinor: 1500, quantity: 2 }, { name: '维生素 C 片', spec: '100mg × 100 片 / 瓶', unitMinor: 980, quantity: 1 }] },
  { id: 'MEDPAY-20261113-004', patient: '赵明远', patientId: 'P-1098', doctor: '马凌', department: '内科', appointmentId: 'APT-20261113-004', appointmentDate: '2026-11-13', appointmentPeriod: '上午', mode: '线下面诊', appointmentState: 'IN_PROGRESS', state: 'PENDING_PAYMENT', createdAt: Date.parse('2026-11-13T08:37:00+08:00'), expiresAt: Date.parse('2026-11-13T18:37:00+08:00'), paidAt: null, lines: [{ name: '布洛芬缓释胶囊', spec: '0.3g × 20 粒 / 盒', unitMinor: 2600, quantity: 1 }] },
  { id: 'MEDPAY-20261112-005', patient: '苏晚晴', patientId: 'P-1120', doctor: '宋欣', department: '口腔科', appointmentId: 'APT-20261112-006', appointmentDate: '2026-11-12', appointmentPeriod: '上午', mode: '线下面诊', appointmentState: 'CANCELLED', state: 'EXPIRED', createdAt: Date.parse('2026-11-12T09:18:00+08:00'), expiresAt: Date.parse('2026-11-12T19:18:00+08:00'), paidAt: null, lines: [{ name: '对乙酰氨基酚片', spec: '0.5g × 12 片 / 盒', unitMinor: 1800, quantity: 1 }] },
])

const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })
const router = useRouter()
const startDate = ref('2026-11-13'), endDate = ref('2026-11-13')
const statusFilter = ref<'ALL' | PaymentState>('ALL'), doctorFilter = ref('ALL'), departmentFilter = ref('ALL'), keyword = ref('')
const selected = ref<MedicinePaymentOrder | null>(null), notice = ref('')
const departments = computed(() => [...new Set(orders.value.map(item => item.department))])
const doctors = computed(() => [...new Set(orders.value.map(item => item.doctor))])
const statusLabels: Record<PaymentState, string> = { PENDING_PAYMENT: '待缴费', PAID: '已支付', EXPIRED: '已失效' }
const appointmentLabels: Record<AppointmentState, string> = { CONFIRMED: '待签到', WAITING: '候诊中', IN_PROGRESS: '就诊中', COMPLETED: '已完成', CANCELLED: '已取消' }
const money = (minor: number) => `¥${(minor / 100).toFixed(2)}`
const total = (item: MedicinePaymentOrder) => item.lines.reduce((sum, line) => sum + line.unitMinor * line.quantity, 0)
const formatTime = (value: number) => new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
const isExpiringSoon = (item: MedicinePaymentOrder) => item.state === 'PENDING_PAYMENT' && item.expiresAt > demoNow && item.expiresAt - demoNow <= 30 * 60 * 1000
const summaryOrders = computed(() => orders.value.filter(item => item.appointmentDate === '2026-11-13'))
const summary = computed(() => ({
  total: summaryOrders.value.length,
  pending: summaryOrders.value.filter(item => item.state === 'PENDING_PAYMENT').length,
  paid: summaryOrders.value.filter(item => item.state === 'PAID'),
  expired: summaryOrders.value.filter(item => item.state === 'EXPIRED').length,
}))
const filtered = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return orders.value.filter(item => {
    const matchesDate = item.appointmentDate >= startDate.value && item.appointmentDate <= endDate.value
    const matchesStatus = statusFilter.value === 'ALL' || item.state === statusFilter.value
    const matchesDoctor = doctorFilter.value === 'ALL' || item.doctor === doctorFilter.value
    const matchesDepartment = departmentFilter.value === 'ALL' || item.department === departmentFilter.value
    const matchesKeyword = !query || `${item.id}${item.patient}${item.patientId}${item.doctor}${item.department}${item.lines.map(line => line.name).join('')}`.toLowerCase().includes(query)
    return matchesDate && matchesStatus && matchesDoctor && matchesDepartment && matchesKeyword
  }).sort((a, b) => b.createdAt - a.createdAt)
})
const dateError = computed(() => startDate.value > endDate.value ? '结束日期不能早于开始日期。' : '')
const statusClass = (state: PaymentState) => state.toLowerCase().replace('_', '-')
const appointmentClass = (state: AppointmentState) => state.toLowerCase().replace('_', '-')
const shortLines = (item: MedicinePaymentOrder) => item.lines.length <= 2 ? item.lines.map(line => `${line.name} × ${line.quantity}`).join('、') : `${item.lines.slice(0, 2).map(line => `${line.name} × ${line.quantity}`).join('、')} 等 ${item.lines.length} 项`

function resetFilters() { startDate.value = '2026-11-13'; endDate.value = '2026-11-13'; statusFilter.value = 'ALL'; doctorFilter.value = 'ALL'; departmentFilter.value = 'ALL'; keyword.value = '' }
function refresh() { notice.value = `已刷新，当前显示 ${filtered.value.length} 条药费订单。` }
function openDetails(item: MedicinePaymentOrder) { selected.value = item; notice.value = '' }
function closeDetails() { selected.value = null }
async function copyValue(value: string, label: string) {
  try { await navigator.clipboard.writeText(value); notice.value = `${label}已复制。` }
  catch { notice.value = `请手动记录${label}：${value}` }
}
function openAppointment() {
  if (!selected.value) return
  router.push({ path: '/prototype/admin-orders', query: { tab: 'appointments', date: 'ALL', keyword: selected.value.appointmentId } })
}
function onKeydown(event: KeyboardEvent) { if (event.key === 'Escape') closeDetails() }
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <AdminOperationsShell :embedded="props.embedded" active="药费订单">
    <main class="medicine-orders-content"><div class="medicine-orders-heading"><div><span class="admin-eyebrow">药品 / 药费订单</span><h1>药费订单工作台</h1><p>查看医生开药后的费用请求和库存支付结果，订单状态与挂号履约状态分别记录。</p></div><div class="medicine-orders-heading-actions"><span class="orders-demo-chip"><i></i>虚构演示数据</span><button type="button" class="admin-soft" @click="resetFilters"><Icon name="refresh" />重置筛选</button></div></div>
        <section class="orders-summary medicine-orders-summary" aria-label="药费订单摘要"><article><span>今日订单总数</span><strong>{{ summary.total }}</strong><small>按医院业务日统计</small></article><article class="warning"><span>待缴费</span><strong>{{ summary.pending }}</strong><small>其中临近截止 {{ summaryOrders.filter(item => isExpiringSoon(item)).length }} 笔</small></article><article><span>已支付</span><strong>{{ summary.paid.length }}</strong><small>支付金额 {{ money(summary.paid.reduce((sum, item) => sum + total(item), 0)) }}</small></article><article><span>已失效</span><strong>{{ summary.expired }}</strong><small>超时后库存已释放</small></article></section>
        <section class="medicine-orders-filters" aria-label="药费订单筛选"><div class="medicine-orders-date"><label>开始日期<input v-model="startDate" type="date" min="2025-11-13" max="2026-11-13" aria-label="开始日期"></label><span>至</span><label>结束日期<input v-model="endDate" type="date" min="2025-11-13" max="2026-11-13" aria-label="结束日期"></label></div><label>订单状态<select v-model="statusFilter" aria-label="药费订单状态"><option value="ALL">全部状态</option><option value="PENDING_PAYMENT">待缴费</option><option value="PAID">已支付</option><option value="EXPIRED">已失效</option></select></label><label>医生<select v-model="doctorFilter" aria-label="药费订单医生"><option value="ALL">全部医生</option><option v-for="doctor in doctors" :key="doctor" :value="doctor">{{ doctor }}</option></select></label><label>科室<select v-model="departmentFilter" aria-label="药费订单科室"><option value="ALL">全部科室</option><option v-for="department in departments" :key="department" :value="department">{{ department }}</option></select></label><label class="medicine-orders-search">关键词<input v-model="keyword" aria-label="搜索药费订单" placeholder="患者、订单号或药品名称"></label><button type="button" class="medicine-orders-reset" @click="resetFilters">清除条件</button><p v-if="dateError" class="medicine-orders-filter-error" role="alert">{{ dateError }}</p><span v-else class="medicine-orders-filter-note">默认今天 · 最多查询近一年订单</span></section>
        <p v-if="notice" class="medicine-orders-feedback" role="status">{{ notice }}</p>
        <section class="orders-table-panel medicine-orders-panel" aria-label="药费订单列表"><div class="orders-table-head"><div><h2>药费订单 <span>{{ filtered.length }} 条</span></h2><p>只读运营视图 · 药费状态和关联挂号状态独立展示</p></div><button type="button" class="admin-link" @click="refresh">刷新数据 <Icon name="refresh" /></button></div><div class="orders-table-wrap"><table class="orders-table medicine-orders-table"><caption class="visually-hidden">药费订单列表</caption><thead><tr><th>药费订单</th><th>患者</th><th>医生 / 科室</th><th>药品摘要</th><th>订单金额</th><th>状态</th><th>创建 / 截止</th><th>操作</th></tr></thead><tbody><tr v-for="item in filtered" :key="item.id" tabindex="0" @click="openDetails(item)" @keydown.enter="openDetails(item)"><td><strong>{{ item.id }}</strong><span>{{ item.appointmentId }}</span></td><td><strong>{{ item.patient }}</strong><span>{{ item.patientId }}</span></td><td><strong>{{ item.doctor }}</strong><span>{{ item.department }}</span></td><td><strong class="medicine-orders-lines">{{ shortLines(item) }}</strong></td><td><strong>{{ money(total(item)) }}</strong><span>金额快照</span></td><td><div class="orders-badges"><span :class="['orders-badge', `medicine-${statusClass(item.state)}`]">{{ statusLabels[item.state] }}</span><em v-if="isExpiringSoon(item)" class="medicine-orders-expiring">即将到期</em></div></td><td><strong>{{ formatTime(item.createdAt) }}</strong><span v-if="item.state === 'PENDING_PAYMENT'">截止 {{ formatTime(item.expiresAt) }}</span><span v-else>{{ item.paidAt ? `支付 ${formatTime(item.paidAt)}` : '库存已释放' }}</span></td><td><button type="button" class="orders-row-action" :aria-label="`查看${item.id}详情`" @click.stop="openDetails(item)">查看详情 <Icon name="chevron" /></button></td></tr><tr v-if="!dateError && !filtered.length"><td colspan="8" class="orders-empty"><Icon name="wallet" /><strong>当前筛选暂无药费订单</strong><span>可以清除条件或扩大日期范围后继续查看。</span></td></tr><tr v-if="dateError"><td colspan="8" class="orders-empty"><Icon name="calendar" /><strong>日期范围无效</strong><span>请先修正开始日期和结束日期。</span></td></tr></tbody></table></div></section><p class="orders-footnote"><Icon name="shield" /> 管理员只查看药费支付事实，不代付、不改价、不强制改变状态；医疗正文和用法用量需要独立权限。</p></main>
    <div v-if="selected" class="orders-drawer-backdrop" @click.self="closeDetails"><aside class="orders-drawer medicine-orders-drawer" aria-label="药费订单详情" @click.stop><button type="button" class="orders-drawer-close" aria-label="关闭药费订单详情" @click="closeDetails"><Icon name="close" /></button><span class="orders-drawer-kicker">药费订单 · {{ statusLabels[selected.state] }}</span><h2>{{ selected.id }}</h2><p>{{ selected.patient }} · {{ selected.patientId }} · {{ selected.doctor }} · {{ selected.department }}</p><div class="orders-detail-grid"><span>订单状态<strong>{{ statusLabels[selected.state] }}<em v-if="isExpiringSoon(selected)">即将到期</em></strong></span><span>订单金额<strong>{{ money(total(selected)) }}</strong></span><span>创建时间<strong>{{ formatTime(selected.createdAt) }}</strong></span><span>截止 / 支付<strong>{{ selected.paidAt ? formatTime(selected.paidAt) : formatTime(selected.expiresAt) }}</strong></span></div><section class="medicine-orders-detail-section"><div class="medicine-orders-section-heading"><h3>药品明细</h3><span>{{ selected.lines.length }} 项</span></div><table><thead><tr><th>药品 / 规格</th><th>单价</th><th>数量</th><th>小计</th></tr></thead><tbody><tr v-for="line in selected.lines" :key="line.name"><td><strong>{{ line.name }}</strong><small>{{ line.spec }}</small></td><td>{{ money(line.unitMinor) }}</td><td>× {{ line.quantity }}</td><td>{{ money(line.unitMinor * line.quantity) }}</td></tr></tbody><tfoot><tr><th colspan="3">订单金额快照</th><th>{{ money(total(selected)) }}</th></tr></tfoot></table></section><section class="medicine-orders-detail-section"><div class="medicine-orders-section-heading"><h3>关联挂号摘要</h3><span :class="['orders-badge', `appointment-${appointmentClass(selected.appointmentState)}`]">{{ appointmentLabels[selected.appointmentState] }}</span></div><div class="medicine-orders-appointment"><span>挂号号<strong>{{ selected.appointmentId }}</strong></span><span>医生 / 科室<strong>{{ selected.doctor }} · {{ selected.department }}</strong></span><span>出诊安排<strong>{{ selected.appointmentDate }} · {{ selected.appointmentPeriod }} · {{ selected.mode }}</strong></span></div><button type="button" class="medicine-orders-link" @click="openAppointment">查看预约与订单 <Icon name="arrow" /></button></section><section class="medicine-orders-timeline"><h3>订单时间</h3><div class="done"><i></i><span>开药并创建订单<strong>{{ formatTime(selected.createdAt) }}</strong></span></div><div :class="{ done: selected.state === 'PAID' }"><i></i><span>药费支付<strong>{{ selected.paidAt ? `已支付 · ${formatTime(selected.paidAt)}` : '尚未支付' }}</strong></span></div><div :class="{ done: selected.state === 'EXPIRED' }"><i></i><span>订单结束<strong>{{ selected.state === 'EXPIRED' ? '已失效，库存已释放' : selected.state === 'PAID' ? '支付成功，库存已消耗' : `待缴费，截止 ${formatTime(selected.expiresAt)}` }}</strong></span></div></section><p class="orders-drawer-note">此处只展示药费订单和挂号摘要，不展示病历、症状、处方用法用量或其他医疗正文。</p><div class="orders-drawer-actions medicine-orders-drawer-actions"><button type="button" class="admin-soft" @click="copyValue(selected.id, '药费订单号')">复制订单号</button><button type="button" class="admin-soft" @click="copyValue(selected.appointmentId, '关联挂号号')">复制挂号号</button></div></aside></div>
  </AdminOperationsShell>
</template>
