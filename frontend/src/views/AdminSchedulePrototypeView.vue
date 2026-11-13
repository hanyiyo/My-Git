<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from './patient-home-prototype/PrototypeIcon.vue'
import PrototypeDatePicker from './patient-home-prototype/PrototypeDatePicker.vue'
import './admin-home-prototype.css'
import './admin-schedule-prototype.css'

type ScheduleStatus = 'DRAFT' | 'PUBLISHED' | 'PAUSED' | 'SUSPENDED' | 'SUBSTITUTED' | 'COMPLETED'
type ScheduleMode = 'online' | 'offline'
type Period = 'am' | 'pm'
type Schedule = {
  id: string
  date: string
  period: Period
  mode: ScheduleMode
  doctor: string
  department: string
  title: string
  capacity: number
  registered: number
  held: number
  fee: number
  status: ScheduleStatus
  note: string
}

const route = useRoute(), router = useRouter()
const groups = [
  { name: '运营', items: [{ label: '运营总览', icon: 'grid' }, { label: '预约与订单', icon: 'calendar' }, { label: '退款 / 异常', icon: 'warning', badge: '3' }] },
  { name: '资源', items: [{ label: '患者', icon: 'user' }, { label: '医生', icon: 'doctor' }, { label: '科室', icon: 'hospital' }, { label: '排班', icon: 'calendar' }, { label: '号源', icon: 'grid' }] },
  { name: '药品', items: [{ label: '药品与库存', icon: 'heart', badge: '4' }, { label: '药费订单', icon: 'wallet' }] },
  { name: '治理', items: [{ label: '审批与替诊', icon: 'file', badge: '6' }, { label: '新闻通知', icon: 'bell' }, { label: '医疗访问审核', icon: 'lock', badge: '1' }, { label: '审计日志', icon: 'scan' }] },
  { name: '系统', items: [{ label: '全局规则', icon: 'settings' }, { label: 'AI 配置', icon: 'sparkles' }, { label: '管理员账户', icon: 'user' }] },
]
const schedules = ref<Schedule[]>([
  { id: 'SCH-1101', date: '2026-11-13', period: 'am', mode: 'offline', doctor: '周宁', department: '内科', title: '副主任医师', capacity: 20, registered: 12, held: 1, fee: 48, status: 'PUBLISHED', note: '门诊二楼 · 205 诊室' },
  { id: 'SCH-1102', date: '2026-11-13', period: 'pm', mode: 'online', doctor: '林牧', department: '全科医学科', title: '住院医师', capacity: 16, registered: 7, held: 0, fee: 30, status: 'PUBLISHED', note: '线上图文诊室' },
  { id: 'SCH-1103', date: '2026-11-14', period: 'am', mode: 'online', doctor: '周宁', department: '内科', title: '副主任医师', capacity: 20, registered: 0, held: 0, fee: 48, status: 'DRAFT', note: '待管理员发布' },
  { id: 'SCH-1104', date: '2026-11-15', period: 'pm', mode: 'offline', doctor: '郭妤', department: '皮肤科', title: '主任医师', capacity: 12, registered: 0, held: 0, fee: 68, status: 'PUBLISHED', note: '门诊三楼 · 302 诊室' },
  { id: 'SCH-1105', date: '2026-11-17', period: 'am', mode: 'offline', doctor: '马凌', department: '内科', title: '主治医师', capacity: 16, registered: 3, held: 0, fee: 36, status: 'SUBSTITUTED', note: '已由陈予安替诊' },
  { id: 'SCH-1106', date: '2026-11-18', period: 'pm', mode: 'online', doctor: '林牧', department: '全科医学科', title: '住院医师', capacity: 16, registered: 0, held: 0, fee: 30, status: 'PAUSED', note: '停诊申请等待复核' },
])
const doctors = [
  { name: '周宁', title: '副主任医师', department: '内科' },
  { name: '马凌', title: '主治医师', department: '内科' },
  { name: '林牧', title: '住院医师', department: '全科医学科' },
  { name: '郭妤', title: '主任医师', department: '皮肤科' },
]
const routeDate = typeof route.query.date === 'string' ? route.query.date : '2026-11-13'
const selectedDate = ref(/^2026-(10|11|12)-\d{2}$/.test(routeDate) ? routeDate : '2026-11-13')
const monthCursor = ref(selectedDate.value.slice(0, 7))
const dateFilter = ref(selectedDate.value)
const modeFilter = ref<'ALL' | ScheduleMode>('ALL')
const statusFilter = ref<'ALL' | ScheduleStatus>('ALL')
const keyword = ref('')
const selectedSchedule = ref<Schedule | null>(null)
const collapsed = ref(false), mobileOpen = ref(false)
const notice = ref('')
const modal = ref<'new' | 'delete' | 'pause' | null>(null)
const editingId = ref('')
const deleteTarget = ref<Schedule | null>(null)
const actionTarget = ref<Schedule | null>(null)
const form = reactive({ date: selectedDate.value, doctor: '', period: 'am' as Period, mode: 'offline' as ScheduleMode, capacity: 20, fee: 30 })
const statuses: Record<ScheduleStatus, string> = { DRAFT: '草稿', PUBLISHED: '已发布', PAUSED: '暂停新增', SUSPENDED: '已停诊', SUBSTITUTED: '已替诊', COMPLETED: '已结束' }

const currentMonth = computed(() => {
  const [year, month] = monthCursor.value.split('-').map(Number)
  return { year, month }
})
const monthTitle = computed(() => `${currentMonth.value.year}年${currentMonth.value.month}月`)
const calendarDays = computed(() => {
  const { year, month } = currentMonth.value
  const total = new Date(year, month, 0).getDate()
  const firstDay = new Date(year, month - 1, 1).getDay()
  return [...Array.from({ length: firstDay }, () => null), ...Array.from({ length: total }, (_, index) => {
    const value = `${year}-${String(month).padStart(2, '0')}-${String(index + 1).padStart(2, '0')}`
    return { value, day: index + 1 }
  })]
})
const selectedDateSchedules = computed(() => schedules.value.filter(item => item.date === selectedDate.value))
const filteredSelectedSchedules = computed(() => selectedDateSchedules.value.filter(item => {
  const query = keyword.value.trim().toLowerCase()
  return (!query || `${item.doctor}${item.department}${item.id}`.toLowerCase().includes(query)) && (modeFilter.value === 'ALL' || item.mode === modeFilter.value) && (statusFilter.value === 'ALL' || item.status === statusFilter.value)
}))
const selectedMorningSchedules = computed(() => filteredSelectedSchedules.value.filter(item => item.period === 'am'))
const selectedAfternoonSchedules = computed(() => filteredSelectedSchedules.value.filter(item => item.period === 'pm'))
const summary = computed(() => [
  { label: '本月排班', value: schedules.value.filter(item => item.date.startsWith(monthCursor.value)).length, note: '含草稿和已发布场次' },
  { label: '已发布', value: schedules.value.filter(item => item.status === 'PUBLISHED').length, note: '患者端可见安排' },
  { label: '待处理', value: schedules.value.filter(item => item.status === 'DRAFT' || item.status === 'PAUSED').length, note: '需要管理员跟进' },
  { label: '可预约号源', value: schedules.value.reduce((sum, item) => sum + Math.max(0, item.capacity - item.registered - item.held), 0), note: '剩余名额合计' },
])
const selectedDateLabel = computed(() => {
  const date = new Date(`${selectedDate.value}T12:00:00`)
  return `${date.getMonth() + 1}月${date.getDate()}日 · ${['周日', '周一', '周二', '周三', '周四', '周五', '周六'][date.getDay()]}`
})
const formDoctor = computed(() => doctors.find(item => item.name === form.doctor))
const formTitle = computed(() => editingId.value ? '编辑排班' : '新增排班')

function monthShift(offset: number) {
  const { year, month } = currentMonth.value
  const date = new Date(year, month - 1 + offset, 1)
  monthCursor.value = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}
function setToday() { monthCursor.value = '2026-11'; openDate('2026-11-13') }
function dateSchedules(date: string) { return schedules.value.filter(item => item.date === date) }
function deletableSchedule(date: string) { return schedules.value.find(item => item.date === date && item.registered === 0 && item.status !== 'COMPLETED') }
function openDate(date: string) { selectedDate.value = date; dateFilter.value = date; selectedSchedule.value = null; notice.value = '' }
function selectSchedule(item: Schedule) { selectedDate.value = item.date; dateFilter.value = item.date; selectedSchedule.value = item; notice.value = '' }
function scheduleTime(period: Period) { return period === 'am' ? '08:00–12:00' : '14:00–18:00' }
function modeLabel(mode: ScheduleMode) { return mode === 'online' ? '线上问诊' : '线下面诊' }
function statusClass(status: ScheduleStatus) { return `schedule-status-${status.toLowerCase()}` }
function money(value: number) { return `¥${value}` }
function navigate(module: string) {
  mobileOpen.value = false
  if (module === '运营总览') return void router.push({ path: '/prototype/admin-home', query: { variant: 'A' } })
  if (module === '预约与订单' || module === '退款 / 异常') return void router.push({ path: '/prototype/admin-orders', query: { tab: module === '退款 / 异常' ? 'refunds' : 'appointments' } })
  if (module === '患者') return void router.push({ path: '/prototype/admin-patients' })
  if (module === '医生') return void router.push({ path: '/prototype/admin-doctors' })
  if (module === '科室') return void router.push({ path: '/prototype/admin-departments' })
  if (module === '排班') return void router.push({ path: '/prototype/admin-schedules', query: { date: selectedDate.value } })
  void router.push({ path: '/prototype/admin-home', query: { module } })
}
function openNew(date = selectedDate.value) {
  editingId.value = ''; Object.assign(form, { date, doctor: '', period: 'am', mode: 'offline', capacity: 20, fee: 30 }); modal.value = 'new'; notice.value = ''
}
function editSchedule(item: Schedule) {
  if (item.status !== 'DRAFT' || item.registered > 0) { notice.value = '已发布排班只能通过停诊或替诊流程变更，不能直接覆盖患者承诺。'; return }
  editingId.value = item.id; Object.assign(form, { date: item.date, doctor: item.doctor, period: item.period, mode: item.mode, capacity: item.capacity, fee: item.fee }); modal.value = 'new'; notice.value = ''
}
function saveSchedule() {
  if (!form.doctor) { notice.value = '请选择医生。'; return }
  const conflict = schedules.value.find(item => item.id !== editingId.value && item.date === form.date && item.doctor === form.doctor && item.period === form.period && item.status !== 'COMPLETED')
  if (conflict) { notice.value = '同一医生同一时段已有排班，线上和线下不能重复。'; return }
  if (form.capacity < 1) { notice.value = '号源数量至少为 1。'; return }
  const doctor = formDoctor.value!
  if (editingId.value) {
    const item = schedules.value.find(value => value.id === editingId.value)!
    Object.assign(item, { date: form.date, doctor: form.doctor, department: doctor.department, title: doctor.title, period: form.period, mode: form.mode, capacity: form.capacity, fee: form.fee, note: '草稿排班，待管理员发布' })
    notice.value = '排班草稿已更新。'
  } else {
    const item: Schedule = { id: `SCH-${Date.now().toString().slice(-4)}`, date: form.date, period: form.period, mode: form.mode, doctor: form.doctor, department: doctor.department, title: doctor.title, capacity: form.capacity, registered: 0, held: 0, fee: form.fee, status: 'DRAFT', note: '待管理员发布' }
    schedules.value.push(item); notice.value = '排班草稿已创建。'
  }
  openDate(form.date); modal.value = null
}
function requestDelete(date: string) {
  const item = deletableSchedule(date)
  if (!item) { openDate(date); notice.value = '该日期排班已有患者挂号或已结束，不能删除。'; return }
  openDate(date); deleteTarget.value = item; modal.value = 'delete'
}
function confirmDelete() {
  if (!deleteTarget.value) return
  const id = deleteTarget.value.id; schedules.value = schedules.value.filter(item => item.id !== id); selectedSchedule.value = null; modal.value = null; notice.value = '排班已删除。'; deleteTarget.value = null
}
function publishSchedule(item: Schedule) { item.status = 'PUBLISHED'; item.note = '已开放患者预约'; notice.value = '排班已发布，号源已对患者端开放。' }
function requestPause(item: Schedule) { actionTarget.value = item; modal.value = 'pause' }
function confirmPause() { if (!actionTarget.value) return; actionTarget.value.status = 'PAUSED'; actionTarget.value.note = '暂停新增挂号，等待停诊 / 替诊处理'; notice.value = '已暂停新增挂号。'; modal.value = null; actionTarget.value = null }
function syncDate(value: string) { if (value === 'ALL') return; openDate(value); monthCursor.value = value.slice(0, 7) }
function onKeydown(event: KeyboardEvent) { if (event.key === 'Escape') { modal.value = null; selectedSchedule.value = null } }
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="admin-prototype" :class="{ 'admin-collapsed': collapsed }">
    <aside class="admin-sidebar" :class="{ 'admin-mobile-open': mobileOpen }" aria-label="管理员端导航"><div class="admin-profile"><span class="admin-avatar">管</span><div class="admin-profile-copy"><strong>运营管理员</strong><span>平台治理与运营</span><em>管理员</em></div></div><nav class="admin-nav" aria-label="管理员功能导航"><template v-for="group in groups" :key="group.name"><p class="admin-nav-caption">{{ group.name }}</p><button v-for="item in group.items" :key="item.label" type="button" :class="{ active: item.label === '排班' }" :aria-label="item.label" @click="navigate(item.label)"><Icon :name="item.icon" /><span>{{ item.label }}</span><b v-if="item.badge">{{ item.badge }}</b></button></template></nav><div class="admin-sidebar-bottom"><div class="admin-permission"><Icon name="lock" /><div><strong>医疗内容权限</strong><span>默认未开启</span></div></div><button type="button" class="admin-collapse" :aria-label="collapsed ? '展开侧栏' : '收起侧栏'" :aria-expanded="!collapsed" @click="collapsed = !collapsed"><Icon name="chevron" /><span>{{ collapsed ? '展开侧栏' : '收起侧栏' }}</span></button></div></aside>
    <button v-if="mobileOpen" type="button" class="admin-backdrop" aria-label="关闭侧栏" @click="mobileOpen = false"></button>
    <section class="admin-workspace"><header class="admin-topbar"><div class="admin-topbar-left"><button type="button" class="admin-mobile-toggle" aria-label="打开侧栏" @click="mobileOpen = true"><Icon name="menu" /></button><div class="admin-brand"><span><Icon name="hospital" /></span><div><strong>智能医疗问诊预约系统</strong><small>ADMIN OPERATIONS</small></div></div><div class="admin-hospital"><i></i>市一医院 · 管理员工作台</div></div><div class="admin-topbar-right"><div class="admin-updated"><span>实时演示数据</span><Icon name="refresh" /></div><button type="button" class="admin-bell" aria-label="查看通知"><Icon name="bell" /><i></i></button><div class="admin-user"><span>管</span><strong>运营管理员</strong><Icon name="chevron" /></div></div></header>
      <main class="admin-content schedule-content"><div class="schedule-heading"><div><span class="admin-eyebrow">资源 / 排班</span><h1>排班管理工作台</h1><p>按日期管理医生出诊安排，发布后号源才会对患者端开放。</p></div><div class="schedule-heading-actions"><span class="resource-demo-chip"><i></i>虚构演示数据</span><button type="button" class="resource-add-button" @click="openNew()">＋新增排班</button></div></div>
        <section class="resource-summary schedule-summary" aria-label="排班摘要"><article v-for="item in summary" :key="item.label"><span>{{ item.label }}</span><strong>{{ item.value }}</strong><small>{{ item.note }}</small></article></section>
        <section class="schedule-toolbar" aria-label="排班筛选"><label class="schedule-search"><span>关键词</span><div><Icon name="search" /><input v-model="keyword" aria-label="搜索排班" placeholder="医生、科室或排班编号"></div></label><label><span>服务方式</span><select v-model="modeFilter" aria-label="排班服务方式"><option value="ALL">全部方式</option><option value="online">线上问诊</option><option value="offline">线下面诊</option></select></label><label><span>排班状态</span><select v-model="statusFilter" aria-label="排班状态"><option value="ALL">全部状态</option><option v-for="(label, value) in statuses" :key="value" :value="value">{{ label }}</option></select></label><PrototypeDatePicker v-model="dateFilter" label="跳转日期" min-date="2026-10-01" max-date="2026-12-31" @update:model-value="syncDate" /><button type="button" class="admin-soft schedule-clear" @click="keyword = ''; modeFilter = 'ALL'; statusFilter = 'ALL'; openDate('2026-11-13'); monthCursor = '2026-11'">重置筛选</button></section>
        <section class="schedule-layout"><div class="schedule-calendar-panel" aria-label="排班月历"><div class="schedule-panel-heading"><div><h2>{{ monthTitle }}排班月历</h2><p>点击日期卡片查看详情，使用卡片下方加减按钮新增或删除排班</p></div><div class="schedule-month-actions"><button type="button" aria-label="上个月" @click="monthShift(-1)">‹</button><button type="button" @click="setToday">回到今天</button><button type="button" aria-label="下个月" @click="monthShift(1)">›</button></div></div><div class="schedule-weekdays" aria-hidden="true"><span v-for="day in ['日', '一', '二', '三', '四', '五', '六']" :key="day">{{ day }}</span></div><div class="schedule-calendar-grid"><span v-for="(day, index) in calendarDays" :key="day?.value ?? `blank-${index}`" class="schedule-calendar-cell" :class="{ blank: !day }"><article v-if="day" class="schedule-day-card" :class="{ selected: day.value === selectedDate, today: day.value === '2026-11-13' }" :data-calendar-date="day.value" @click="openDate(day.value)"><header><strong>{{ day.day }}</strong><em v-if="day.value === '2026-11-13'">今天</em></header><div class="schedule-day-items"><span v-for="item in dateSchedules(day.value).slice(0, 2)" :key="item.id" :class="statusClass(item.status)">{{ item.doctor }} · {{ item.period === 'am' ? '上午' : '下午' }}</span><small v-if="dateSchedules(day.value).length > 2">+{{ dateSchedules(day.value).length - 2 }} 场</small><small v-if="!dateSchedules(day.value).length" class="schedule-day-empty">暂无排班</small></div><div class="schedule-day-actions" @click.stop><button type="button" :aria-label="`新增排班 ${day.value}`" @click="openNew(day.value)">＋</button><button type="button" :aria-label="`删除排班 ${day.value}`" :disabled="!deletableSchedule(day.value)" @click="requestDelete(day.value)">−</button></div></article></span></div></div>
          <aside class="schedule-day-overview" aria-label="所选日期详情"><div class="schedule-day-overview-header"><div><span class="schedule-detail-kicker">所选日期</span><h2>{{ selectedDateLabel }}</h2><p class="schedule-detail-subtitle">{{ selectedDateSchedules.length }} 场排班 · {{ selectedDateSchedules.reduce((sum, item) => sum + item.capacity, 0) }} 个总号源</p></div><button type="button" class="admin-soft" @click="openNew()">＋新增本日排班</button></div><div class="schedule-period-columns"><section class="schedule-period-group" aria-label="上午排班"><header><div><span>上午</span><small>08:00–12:00</small></div><b>{{ selectedMorningSchedules.length }} 场</b></header><div class="schedule-card-list"><button v-for="item in selectedMorningSchedules" :key="item.id" type="button" class="schedule-card" @click="selectSchedule(item)"><span class="schedule-card-time">08:00<strong>12:00</strong></span><span class="schedule-card-main"><strong>{{ item.doctor }}</strong><small>{{ item.title }} · {{ item.department }}</small><em>{{ modeLabel(item.mode) }} · {{ item.id }}</em></span><span class="schedule-card-meta"><b :class="statusClass(item.status)">{{ statuses[item.status] }}</b><strong>{{ Math.max(0, item.capacity - item.registered - item.held) }}<small>/ {{ item.capacity }} 个剩余</small></strong><em>已挂号 {{ item.registered }} 人</em></span><Icon name="chevron" /></button><p v-if="!selectedMorningSchedules.length" class="schedule-detail-empty">上午暂无排班</p></div></section><section class="schedule-period-group" aria-label="下午排班"><header><div><span>下午</span><small>14:00–18:00</small></div><b>{{ selectedAfternoonSchedules.length }} 场</b></header><div class="schedule-card-list"><button v-for="item in selectedAfternoonSchedules" :key="item.id" type="button" class="schedule-card" @click="selectSchedule(item)"><span class="schedule-card-time">14:00<strong>18:00</strong></span><span class="schedule-card-main"><strong>{{ item.doctor }}</strong><small>{{ item.title }} · {{ item.department }}</small><em>{{ modeLabel(item.mode) }} · {{ item.id }}</em></span><span class="schedule-card-meta"><b :class="statusClass(item.status)">{{ statuses[item.status] }}</b><strong>{{ Math.max(0, item.capacity - item.registered - item.held) }}<small>/ {{ item.capacity }} 个剩余</small></strong><em>已挂号 {{ item.registered }} 人</em></span><Icon name="chevron" /></button><p v-if="!selectedAfternoonSchedules.length" class="schedule-detail-empty">下午暂无排班</p></div></section></div><p v-if="notice" class="schedule-feedback" role="status">{{ notice }}</p><p v-else class="schedule-detail-note">点击排班卡片查看完整业务详情；排班发布后才会生成患者可预约号源。</p></aside>
        </section>
        <div v-if="selectedSchedule" class="schedule-drawer-backdrop" @click.self="selectedSchedule = null"><aside class="schedule-drawer" aria-label="排班详情" @click.stop><button type="button" class="schedule-drawer-close" aria-label="关闭排班详情" @click="selectedSchedule = null">×</button><span class="schedule-detail-kicker">{{ selectedSchedule.date }} · {{ selectedSchedule.period === 'am' ? '上午' : '下午' }}排班</span><div class="schedule-drawer-title"><div><span :class="statusClass(selectedSchedule.status)">{{ statuses[selectedSchedule.status] }}</span><h2>{{ selectedSchedule.doctor }}</h2><p>{{ selectedSchedule.title }} · {{ selectedSchedule.department }}</p></div><strong>{{ selectedSchedule.id }}</strong></div><div class="schedule-drawer-grid"><span>出诊时段<strong>{{ selectedSchedule.period === 'am' ? '上午' : '下午' }} · {{ scheduleTime(selectedSchedule.period) }}</strong></span><span>服务方式<strong>{{ modeLabel(selectedSchedule.mode) }}</strong></span><span>号源使用<strong>{{ selectedSchedule.registered }} 已挂号 / {{ selectedSchedule.capacity }} 总号源</strong></span><span>剩余号源<strong>{{ Math.max(0, selectedSchedule.capacity - selectedSchedule.registered - selectedSchedule.held) }} 个</strong></span><span>挂号费<strong>{{ money(selectedSchedule.fee) }}</strong></span><span>关联状态<strong>{{ selectedSchedule.note }}</strong></span></div><div class="schedule-drawer-section"><h3>业务影响</h3><p>已挂号患者 {{ selectedSchedule.registered }} 人，临时占号 {{ selectedSchedule.held }} 个；现有预约、支付和退款记录请到预约运营工作台处理。</p></div><p v-if="notice" class="schedule-feedback" role="status">{{ notice }}</p><div class="schedule-detail-actions"><button type="button" class="admin-soft" :disabled="selectedSchedule.status !== 'DRAFT'" @click="editSchedule(selectedSchedule)">编辑草稿</button><button v-if="selectedSchedule.status === 'DRAFT'" type="button" class="admin-primary" @click="publishSchedule(selectedSchedule)">发布排班</button><button v-if="selectedSchedule.status === 'PUBLISHED'" type="button" class="admin-soft" @click="requestPause(selectedSchedule)">暂停新增</button><button type="button" class="admin-outline" @click="router.push({ path: '/prototype/admin-home', query: { module: '审批与替诊' } })">进入停诊 / 替诊复核</button></div><p class="schedule-detail-note">已有患者挂号后不能删除排班，也不能修改日期、时段或服务方式。</p></aside></div>
        <p class="schedule-footnote"><Icon name="shield" /> 排班页只管理出诊安排和号源摘要，不直接修改预约、支付、退款或医疗内容。</p>
      </main>
    </section>
    <div v-if="modal === 'new'" class="schedule-modal-backdrop"><section class="schedule-modal" role="dialog" aria-modal="true" :aria-label="formTitle"><button type="button" class="schedule-modal-close" aria-label="关闭排班表单" @click="modal = null">×</button><span class="schedule-detail-kicker">排班配置</span><h2>{{ formTitle }}</h2><label>出诊日期<PrototypeDatePicker v-model="form.date" label="出诊日期" min-date="2026-10-01" max-date="2026-12-31" /></label><label>医生<select v-model="form.doctor" aria-label="医生"><option value="" disabled>请选择已认证医生</option><option v-for="doctor in doctors" :key="doctor.name" :value="doctor.name">{{ doctor.name }} · {{ doctor.title }} · {{ doctor.department }}</option></select></label><div class="schedule-form-two"><label>门诊时段<select v-model="form.period" aria-label="门诊时段"><option value="am">上午 · 08:00–12:00</option><option value="pm">下午 · 14:00–18:00</option></select></label><label>服务方式<select v-model="form.mode" aria-label="服务方式"><option value="online">线上问诊</option><option value="offline">线下面诊</option></select></label></div><div class="schedule-form-two"><label>总号源<input v-model.number="form.capacity" aria-label="总号源" type="number" min="1" max="99"></label><label>挂号费<input v-model.number="form.fee" aria-label="挂号费" type="number" min="0" max="9999"><small>默认价格可在全局规则中维护</small></label></div><p class="schedule-form-note">同一医生同一日期同一时段不能重复排班，线上和线下不可混合。</p><button type="button" class="admin-primary schedule-submit" @click="saveSchedule">保存为草稿</button></section></div>
    <div v-if="modal === 'delete'" class="schedule-modal-backdrop"><section class="schedule-confirm" role="dialog" aria-modal="true" aria-label="确认删除排班"><span class="schedule-confirm-icon">!</span><h2>确认删除排班</h2><p>{{ deleteTarget?.date }} {{ deleteTarget?.doctor }} · {{ deleteTarget?.period === 'am' ? '上午' : '下午' }}</p><div class="schedule-confirm-impact">该场次没有已挂号患者，可以删除。已有患者挂号的排班必须保留，并通过停诊或替诊流程处理。</div><div class="schedule-confirm-actions"><button type="button" class="admin-soft" @click="modal = null">取消</button><button type="button" class="schedule-danger" @click="confirmDelete">确认删除</button></div></section></div>
    <div v-if="modal === 'pause'" class="schedule-modal-backdrop"><section class="schedule-confirm" role="dialog" aria-modal="true" aria-label="确认暂停新增"><span class="schedule-confirm-icon">!</span><h2>确认暂停新增</h2><p>{{ actionTarget?.doctor }} · {{ actionTarget?.date }}</p><div class="schedule-confirm-impact">暂停后患者不能继续新挂号，已有支付预约保留；后续停诊或替诊结果进入审批与替诊复核。</div><div class="schedule-confirm-actions"><button type="button" class="admin-soft" @click="modal = null">取消</button><button type="button" class="schedule-danger" @click="confirmPause">确认暂停新增</button></div></section></div>
  </div>
</template>
