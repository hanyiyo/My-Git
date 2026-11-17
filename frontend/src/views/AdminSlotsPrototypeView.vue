<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from './patient-home-prototype/PrototypeIcon.vue'
import './admin-home-prototype.css'
import './admin-resource-prototype.css'
import './admin-slots-prototype.css'

type SlotStatus = 'DRAFT' | 'PUBLISHED' | 'PAUSED' | 'SUSPENDED' | 'SUBSTITUTED' | 'COMPLETED'
type SlotMode = 'online' | 'offline'
type Period = 'am' | 'pm'
type SlotRecord = {
  id: string
  date: string
  period: Period
  mode: SlotMode
  doctor: string
  department: string
  title: string
  capacity: number
  registered: number
  held: number
  status: SlotStatus
  note: string
}

const route = useRoute(), router = useRouter()
const groups = [
  { name: '运营', items: [{ label: '运营总览', icon: 'grid' }, { label: '预约与订单', icon: 'calendar' }, { label: '退款 / 异常', icon: 'warning', badge: '3' }] },
  { name: '资源', items: [{ label: '患者', icon: 'user' }, { label: '医生', icon: 'doctor' }, { label: '科室', icon: 'hospital' }, { label: '排班', icon: 'calendar' }, { label: '号源', icon: 'grid' }] },
  { name: '药品', items: [{ label: '药品与库存', icon: 'heart', badge: '4' }, { label: '药费订单', icon: 'wallet' }] },
  { name: '治理', items: [{ label: '审批与替诊', icon: 'file', badge: '6' }, { label: '新闻通知', icon: 'bell' }, { label: '审计日志', icon: 'scan' }] },
  { name: '系统', items: [{ label: '全局规则', icon: 'settings' }, { label: 'AI 配置', icon: 'sparkles' }, { label: '管理员账户', icon: 'user' }] },
]
const slots = ref<SlotRecord[]>([
  { id: 'SLT-1101', date: '2026-11-13', period: 'am', mode: 'offline', doctor: '周宁', department: '内科', title: '副主任医师', capacity: 20, registered: 12, held: 1, status: 'PUBLISHED', note: '门诊二楼 · 205 诊室' },
  { id: 'SLT-1102', date: '2026-11-13', period: 'pm', mode: 'online', doctor: '林牧', department: '全科医学科', title: '住院医师', capacity: 16, registered: 7, held: 0, status: 'PUBLISHED', note: '线上图文诊室' },
  { id: 'SLT-1103', date: '2026-11-14', period: 'am', mode: 'online', doctor: '周宁', department: '内科', title: '副主任医师', capacity: 20, registered: 0, held: 0, status: 'DRAFT', note: '排班尚未发布，患者端不可见' },
  { id: 'SLT-1104', date: '2026-11-15', period: 'pm', mode: 'offline', doctor: '郭妤', department: '皮肤科', title: '主任医师', capacity: 12, registered: 0, held: 0, status: 'PUBLISHED', note: '门诊三楼 · 302 诊室' },
  { id: 'SLT-1105', date: '2026-11-17', period: 'am', mode: 'offline', doctor: '马凌', department: '内科', title: '主治医师', capacity: 16, registered: 3, held: 0, status: 'SUBSTITUTED', note: '已由陈予安替诊' },
  { id: 'SLT-1106', date: '2026-11-18', period: 'pm', mode: 'online', doctor: '林牧', department: '全科医学科', title: '住院医师', capacity: 16, registered: 0, held: 0, status: 'PAUSED', note: '暂停新增挂号，等待停诊 / 替诊处理' },
  { id: 'SLT-1107', date: '2026-11-12', period: 'pm', mode: 'offline', doctor: '郭妤', department: '皮肤科', title: '主任医师', capacity: 12, registered: 9, held: 0, status: 'COMPLETED', note: '门诊时段已结束' },
])
const doctors = ['全部医生', '周宁', '林牧', '郭妤', '马凌']
const departments = ['全部科室', '内科', '全科医学科', '皮肤科']
const statuses: Record<SlotStatus, string> = { DRAFT: '草稿', PUBLISHED: '已发布', PAUSED: '暂停新增', SUSPENDED: '已停诊', SUBSTITUTED: '已替诊', COMPLETED: '已结束' }
const statusReasons: Record<SlotStatus, string> = { DRAFT: '排班尚未发布，患者端不可见。', PUBLISHED: '当前场次可按剩余号源继续登记。', PAUSED: '已暂停新增挂号，已有预约仍保留。', SUSPENDED: '场次已停诊，号源不再重新售卖。', SUBSTITUTED: '场次已完成替诊复核，号源由替诊流程管理。', COMPLETED: '门诊时段已结束，号源不可再使用。' }
const routeDate = typeof route.query.date === 'string' && /^2026-\d{2}-\d{2}$/.test(route.query.date) ? route.query.date : '2026-11-13'
const dateFrom = ref(routeDate), dateTo = ref(routeDate), periodFilter = ref<'ALL' | Period>('ALL'), modeFilter = ref<'ALL' | SlotMode>('ALL'), doctorFilter = ref('全部医生'), departmentFilter = ref('全部科室'), statusFilter = ref<SlotStatus | 'ALL'>('PUBLISHED'), keyword = ref('')
const selectedSlot = ref<SlotRecord | null>(null), collapsed = ref(false), mobileOpen = ref(false), notice = ref(''), modal = ref<'capacity' | null>(null), formNotice = ref('')
const form = reactive({ capacity: 1 })

const filteredSlots = computed(() => slots.value.filter(item => {
  const query = keyword.value.trim().toLowerCase()
  return item.date >= dateFrom.value && item.date <= dateTo.value && (periodFilter.value === 'ALL' || item.period === periodFilter.value) && (modeFilter.value === 'ALL' || item.mode === modeFilter.value) && (doctorFilter.value === '全部医生' || item.doctor === doctorFilter.value) && (departmentFilter.value === '全部科室' || item.department === departmentFilter.value) && (statusFilter.value === 'ALL' || item.status === statusFilter.value) && (!query || `${item.id}${item.doctor}${item.department}`.toLowerCase().includes(query))
}).sort((a, b) => `${a.date}-${a.period}`.localeCompare(`${b.date}-${b.period}`)))
const summary = computed(() => [
  { label: '展示场次', value: filteredSlots.value.length, note: statusFilter.value === 'PUBLISHED' ? '默认显示已发布' : '当前筛选范围' },
  { label: '总号源', value: filteredSlots.value.reduce((sum, item) => sum + item.capacity, 0), note: '场次库存总量' },
  { label: '已占用', value: filteredSlots.value.reduce((sum, item) => sum + item.registered + item.held, 0), note: '已挂号 + 临时占号' },
  { label: '剩余号源', value: filteredSlots.value.reduce((sum, item) => sum + available(item), 0), note: '可继续登记名额' },
])

function available(item: SlotRecord) { return Math.max(0, item.capacity - item.registered - item.held) }
function periodLabel(period: Period) { return period === 'am' ? '上午' : '下午' }
function modeLabel(mode: SlotMode) { return mode === 'online' ? '线上问诊' : '线下面诊' }
function statusClass(status: SlotStatus) { return `slot-status-${status.toLowerCase()}` }
function dateLabel(value: string) { const date = new Date(`${value}T12:00:00`); return `${date.getMonth() + 1}月${date.getDate()}日 ${['周日', '周一', '周二', '周三', '周四', '周五', '周六'][date.getDay()]}` }
function resetFilters() { dateFrom.value = '2026-11-13'; dateTo.value = '2026-11-13'; periodFilter.value = 'ALL'; modeFilter.value = 'ALL'; doctorFilter.value = '全部医生'; departmentFilter.value = '全部科室'; statusFilter.value = 'PUBLISHED'; keyword.value = '' }
function navigate(module: string) {
  mobileOpen.value = false
  if (module === '运营总览') return void router.push({ path: '/prototype/admin-home', query: { variant: 'A' } })
  if (module === '预约与订单' || module === '退款 / 异常') return void router.push({ path: '/prototype/admin-orders', query: { tab: module === '退款 / 异常' ? 'refunds' : 'appointments' } })
  if (module === '患者') return void router.push({ path: '/prototype/admin-patients' })
  if (module === '医生') return void router.push({ path: '/prototype/admin-doctors' })
  if (module === '科室') return void router.push({ path: '/prototype/admin-departments' })
  if (module === '排班') return void router.push({ path: '/prototype/admin-schedules', query: { date: selectedSlot.value?.date || dateFrom.value } })
  if (module === '号源') return
  void router.push({ path: '/prototype/admin-home', query: { module } })
}
function selectSlot(item: SlotRecord) { selectedSlot.value = item; notice.value = '' }
function canAdjust(item: SlotRecord) { return item.status === 'PUBLISHED' && item.registered === 0 && item.held === 0 }
function adjustmentReason(item: SlotRecord) { return canAdjust(item) ? '无患者承诺，可调整总号源。' : item.registered || item.held ? '已有患者承诺，必须保留当前总号源。' : statusReasons[item.status] }
function openCapacityEdit() { if (!selectedSlot.value || !canAdjust(selectedSlot.value)) return; form.capacity = selectedSlot.value.capacity; formNotice.value = ''; modal.value = 'capacity' }
function saveCapacity() {
  formNotice.value = ''
  if (!selectedSlot.value || !canAdjust(selectedSlot.value)) { formNotice.value = '当前场次已有患者承诺或不可预约，不能调整号源。'; return }
  if (!Number.isInteger(form.capacity) || form.capacity < 1) { formNotice.value = '总号源必须是大于或等于 1 的整数。'; return }
  const previous = selectedSlot.value.capacity
  selectedSlot.value.capacity = form.capacity
  notice.value = `总号源已从 ${previous} 调整为 ${form.capacity}。`
  modal.value = null
}
function viewSchedule() { if (selectedSlot.value) router.push({ path: '/prototype/admin-schedules', query: { date: selectedSlot.value.date } }) }
</script>

<template>
  <div class="admin-prototype" :class="{ 'admin-collapsed': collapsed }">
    <aside class="admin-sidebar" :class="{ 'admin-mobile-open': mobileOpen }" aria-label="管理员端导航"><div class="admin-profile"><span class="admin-avatar">管</span><div class="admin-profile-copy"><strong>运营管理员</strong><span>平台治理与运营</span><em>管理员</em></div></div><nav class="admin-nav" aria-label="管理员功能导航"><template v-for="group in groups" :key="group.name"><p class="admin-nav-caption">{{ group.name }}</p><button v-for="item in group.items" :key="item.label" type="button" :class="{ active: item.label === '号源' }" :aria-label="item.label" @click="navigate(item.label)"><Icon :name="item.icon" /><span>{{ item.label }}</span><b v-if="item.badge">{{ item.badge }}</b></button></template></nav><div class="admin-sidebar-bottom"><div class="admin-permission"><Icon name="lock" /><div><strong>医疗内容权限</strong><span>默认未开启</span></div></div><button type="button" class="admin-collapse" :aria-label="collapsed ? '展开侧栏' : '收起侧栏'" :aria-expanded="!collapsed" @click="collapsed = !collapsed"><Icon name="chevron" /><span>{{ collapsed ? '展开侧栏' : '收起侧栏' }}</span></button></div></aside>
    <button v-if="mobileOpen" type="button" class="admin-backdrop" aria-label="关闭侧栏" @click="mobileOpen = false"></button>
    <section class="admin-workspace"><header class="admin-topbar"><div class="admin-topbar-left"><button type="button" class="admin-mobile-toggle" aria-label="打开侧栏" @click="mobileOpen = true"><Icon name="menu" /></button><div class="admin-brand"><span><Icon name="hospital" /></span><div><strong>智能医疗问诊预约系统</strong><small>ADMIN OPERATIONS</small></div></div><div class="admin-hospital"><i></i>市一医院 · 管理员工作台</div></div><div class="admin-topbar-right"><div class="admin-updated"><span>实时演示数据</span><Icon name="refresh" /></div><button type="button" class="admin-bell" aria-label="查看通知"><Icon name="bell" /><i></i></button><div class="admin-user"><span>管</span><strong>运营管理员</strong><Icon name="chevron" /></div></div></header>
      <main class="admin-content slot-content"><div class="slot-heading"><div><span class="admin-eyebrow">资源 / 号源</span><h1>号源管理工作台</h1><p>按日期和门诊时段查看库存，跟踪患者占用并控制可登记名额。</p></div><span class="resource-demo-chip"><i></i>虚构演示数据</span></div>
        <section class="resource-summary slot-summary" aria-label="号源摘要"><article v-for="item in summary" :key="item.label"><span>{{ item.label }}</span><strong>{{ item.value }}</strong><small>{{ item.note }}</small></article></section>
        <section class="slot-toolbar" aria-label="号源筛选"><label class="slot-date"><span>开始日期</span><input v-model="dateFrom" aria-label="开始日期" type="date" min="2026-10-01" max="2026-12-31"></label><label class="slot-date"><span>结束日期</span><input v-model="dateTo" aria-label="结束日期" type="date" min="2026-10-01" max="2026-12-31"></label><label><span>门诊时段</span><select v-model="periodFilter" aria-label="号源门诊时段"><option value="ALL">全天</option><option value="am">上午</option><option value="pm">下午</option></select></label><label><span>服务方式</span><select v-model="modeFilter" aria-label="号源服务方式"><option value="ALL">全部方式</option><option value="online">线上问诊</option><option value="offline">线下面诊</option></select></label><label><span>医生</span><select v-model="doctorFilter" aria-label="号源医生"><option v-for="doctor in doctors" :key="doctor" :value="doctor">{{ doctor }}</option></select></label><label><span>科室</span><select v-model="departmentFilter" aria-label="号源科室"><option v-for="department in departments" :key="department" :value="department">{{ department }}</option></select></label><label><span>状态</span><select v-model="statusFilter" aria-label="号源状态"><option value="ALL">全部状态</option><option v-for="(label, value) in statuses" :key="value" :value="value">{{ label }}</option></select></label><label class="slot-search"><span>关键词</span><input v-model="keyword" aria-label="搜索号源" placeholder="医生、科室或号源编号"></label><button type="button" class="admin-soft slot-reset" @click="resetFilters">重置筛选</button></section>
        <section class="resource-panel slot-panel" aria-label="号源库存表"><div class="resource-panel-heading"><div><h2>号源库存 <span>{{ filteredSlots.length }} 场</span></h2><p>按门诊时段展示总号源、已挂号、临时占号和剩余号源</p></div><span class="slot-legend"><i></i>可继续登记</span></div><div class="resource-table-wrap"><table class="resource-table slot-table"><caption class="visually-hidden">号源库存列表</caption><thead><tr><th>日期 / 时段</th><th>医生与科室</th><th>服务方式</th><th>状态</th><th>总号源</th><th>已挂号</th><th>临时占号</th><th>剩余号源</th><th><span class="visually-hidden">操作</span></th></tr></thead><tbody><tr v-for="item in filteredSlots" :key="item.id" tabindex="0" @click="selectSlot(item)" @keydown.enter="selectSlot(item)"><td><strong class="slot-date-cell">{{ dateLabel(item.date) }}</strong><small>{{ periodLabel(item.period) }} · {{ item.period === 'am' ? '08:00–12:00' : '14:00–18:00' }}</small></td><td><div class="resource-person"><span class="doctor-avatar">{{ item.doctor.slice(-1) }}</span><div><strong>{{ item.doctor }}</strong><small>{{ item.title }} · {{ item.department }}</small></div></div></td><td><span class="slot-mode" :class="item.mode">{{ modeLabel(item.mode) }}</span></td><td><span class="resource-status slot-status" :class="statusClass(item.status)"><i></i>{{ statuses[item.status] }}</span></td><td><strong class="slot-number">{{ item.capacity }}</strong></td><td><strong class="slot-number">{{ item.registered }}</strong></td><td><strong class="slot-number">{{ item.held }}</strong></td><td><strong class="slot-number" :class="{ 'slot-low': available(item) <= 2 }">{{ available(item) }}</strong></td><td><button type="button" class="resource-row-action" :aria-label="`查看${item.doctor}${periodLabel(item.period)}号源详情`" @click.stop="selectSlot(item)">查看详情 <Icon name="chevron" /></button></td></tr><tr v-if="!filteredSlots.length"><td colspan="9" class="resource-empty"><Icon name="search" /><strong>当前筛选暂无号源</strong><span>可以调整日期、状态或服务方式后继续查看。</span></td></tr></tbody></table></div></section>
        <p class="resource-footnote"><Icon name="shield" /> 号源页只管理可登记名额；预约、支付、退款和停诊替诊仍在对应工作台处理。</p>
      </main>
    </section>
    <div v-if="selectedSlot" class="slot-drawer-backdrop" @click.self="selectedSlot = null"><aside class="slot-drawer" aria-label="号源详情" @click.stop><button type="button" class="slot-drawer-close" aria-label="关闭号源详情" @click="selectedSlot = null">×</button><span class="slot-kicker">{{ dateLabel(selectedSlot.date) }} · {{ periodLabel(selectedSlot.period) }}号源</span><div class="slot-drawer-title"><div><span class="resource-status slot-status" :class="statusClass(selectedSlot.status)"><i></i>{{ statuses[selectedSlot.status] }}</span><h2>{{ selectedSlot.doctor }}</h2><p>{{ selectedSlot.title }} · {{ selectedSlot.department }}</p></div><strong>{{ selectedSlot.id }}</strong></div><div class="slot-drawer-grid"><span>门诊时段<strong>{{ periodLabel(selectedSlot.period) }} · {{ selectedSlot.period === 'am' ? '08:00–12:00' : '14:00–18:00' }}</strong></span><span>服务方式<strong>{{ modeLabel(selectedSlot.mode) }}</strong></span><span>总号源<strong>{{ selectedSlot.capacity }} 个</strong></span><span>已挂号<strong>{{ selectedSlot.registered }} 人</strong></span><span>临时占号<strong>{{ selectedSlot.held }} 个</strong></span><span>剩余号源<strong>{{ available(selectedSlot) }} 个</strong></span></div><section class="slot-drawer-section"><h3>库存状态</h3><p>{{ statusReasons[selectedSlot.status] }}{{ selectedSlot.note }}</p></section><p v-if="notice" class="schedule-feedback" role="status">{{ notice }}</p><div class="slot-detail-actions"><button type="button" class="admin-primary" :disabled="!canAdjust(selectedSlot)" @click="openCapacityEdit">调整号源</button><button type="button" class="admin-outline" @click="viewSchedule">查看排班</button></div><p class="slot-adjust-note">{{ adjustmentReason(selectedSlot) }}</p></aside></div>
    <div v-if="modal === 'capacity' && selectedSlot" class="slot-modal-backdrop"><section class="slot-modal" role="dialog" aria-modal="true" aria-label="调整号源"><button type="button" class="slot-modal-close" aria-label="关闭调整号源" @click="modal = null">×</button><span class="slot-kicker">容量调整</span><h2>调整总号源</h2><p>{{ selectedSlot.doctor }} · {{ dateLabel(selectedSlot.date) }} · {{ periodLabel(selectedSlot.period) }}</p><div class="slot-capacity-compare"><span>当前总号源<strong>{{ selectedSlot.capacity }}</strong></span><b>→</b><span>新总号源<strong>{{ form.capacity }}</strong></span></div><label>新总号源<input v-model.number="form.capacity" aria-label="新总号源" type="number" min="1" max="99"></label><p class="slot-form-note">此场次没有已挂号或临时占号，调整后剩余号源将同步更新。</p><p v-if="formNotice" class="schedule-form-error" role="alert">{{ formNotice }}</p><div class="slot-modal-actions"><button type="button" class="admin-soft" @click="modal = null">取消</button><button type="button" class="admin-primary" @click="saveCapacity">确认调整</button></div></section></div>
  </div>
</template>
