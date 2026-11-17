<script setup lang="ts">
// Development prototype: doctor shell and shared reception workflow, using fictional data.
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from './patient-home-prototype/PrototypeIcon.vue'
import ReceptionWorkspace from './doctor-home-prototype/ReceptionWorkspace.vue'
import ReceptionDemoControls from './doctor-home-prototype/ReceptionDemoControls.vue'
import ScheduleWorkspace from './doctor-home-prototype/ScheduleWorkspace.vue'
import ScheduleApplicationWorkspace from './doctor-home-prototype/ScheduleApplicationWorkspace.vue'
import DoctorNotificationsWorkspace from './doctor-home-prototype/DoctorNotificationsWorkspace.vue'
import DoctorProfileWorkspace from './doctor-home-prototype/DoctorProfileWorkspace.vue'
import { applicationLabels, demoDate, scheduleNumbers } from './doctor-home-prototype/schedule-data'
import { callVisit, clearReceptionError, formatTime, resetReception, setDemoTime, useReceptionDemo, waitingQueue } from './doctor-home-prototype/reception-demo'
import { notices } from './patient-home-prototype/data'
import { applications, navigation, patients, scenarios, type DemoRole, type QueuePatient, type Scenario, type Section } from './doctor-home-prototype/data'
import { demoSettingsKey, saveDemoSettings } from './doctor-home-prototype/demo-settings'
import './doctor-home-prototype/doctor-home.css'

const route = useRoute(), router = useRouter()
const { state: receptionState, active: activeVisit, now: receptionNow, error: receptionError } = useReceptionDemo()
const receptionFocused = ref(false)
const doctorProfile = ref({ name: '许知遥', avatar: '' })
const scenario = ref<Scenario>('current'), role = ref<DemoRole>('doctor')
const storageWarning = ref(''), collapsed = ref(false), mobileOpen = ref(false), search = ref('')
const openTabs = ref<Section[]>(['home'])
const receptionPatient = ref(patients.find(patient => patient.id === route.query.patient))
const current = computed<Section>(() => navigation.find(item => item.key === route.params.section)?.key ?? 'home')
const currentName = computed(() => navigation.find(item => item.key === current.value)!.name)
watch(current, clearReceptionError, { immediate: true })
const scheduleContext = ref<Record<string, string>>({}), applicationContext = ref<Record<string, string>>({})
watch(() => route.fullPath, () => {
  const context = Object.fromEntries(['month', 'date', 'mode'].flatMap(key => typeof route.query[key] === 'string' ? [[key, String(route.query[key])]] : []))
  if (current.value === 'schedules' || current.value === 'applications') scheduleContext.value = context
  if (current.value === 'applications') applicationContext.value = { ...context, ...(typeof route.query.tab === 'string' ? { tab: route.query.tab } : {}) }
}, { immediate: true })
const schedules = computed(() => receptionState.value.schedules.filter(s => s.date === demoDate).map(s => ({ id: s.period, period: s.period === 'am' ? '上午' : '下午', time: s.period === 'am' ? '08:00–12:00' : '14:00–18:00', mode: s.mode === 'online' ? '线上问诊' : '线下面诊', place: s.mode === 'online' ? '线上图文诊室' : '门诊二楼 · 205 诊室', capacity: s.capacity, registered: scheduleNumbers(s, receptionState.value.visits).registered })))
const demoTime = computed(() => formatTime(receptionNow.value))
const finished = computed(() => !activeVisit.value && demoTime.value >= '18:00')
const upcoming = computed(() => !activeVisit.value && demoTime.value >= '12:00' && demoTime.value < '14:00')
const ongoing = computed(() => !!activeVisit.value)
const currentSchedule = computed(() => schedules.value[activeVisit.value ? activeVisit.value.group === 'pm' ? 1 : 0 : demoTime.value >= '12:00' ? 1 : 0]!)
const visibleApplications = computed(() => [
  ...applications.filter(item => item.id === 'department' && role.value === 'head').map(item => ({ ...item, scheduleId: '', invitationId: undefined })),
  ...receptionState.value.invitations.filter(invitation => invitation.status === 'PENDING').slice(0, 1).map(invitation => ({ id: invitation.id, kind: '替诊邀请', title: '陈予安医生邀请你替诊', detail: `${invitation.date.slice(5)} · ${invitation.period === 'am' ? '上午' : '下午'} · ${invitation.mode === 'online' ? '线上问诊' : '线下面诊'}`, status: '待你确认', actionable: true, headOnly: false, icon: 'doctor', scheduleId: '', invitationId: invitation.id })),
  ...receptionState.value.applications.map(a => ({ id: a.id, kind: '我的申请', title: `${a.scheduleId.slice(5, 7)}月${a.scheduleId.slice(8, 10)}日${a.scheduleId.endsWith('am') ? '上午' : '下午'}停诊申请`, detail: a.reason, status: applicationLabels[a.status], actionable: false, headOnly: false, icon: 'file', scheduleId: a.scheduleId, invitationId: undefined })),
])
const pendingCount = computed(() => visibleApplications.value.filter(item => item.actionable).length)
const doctorUnread = computed(() => receptionState.value.notifications.filter(item => !item.deleted && !item.read).length)
const waiting = computed(() => finished.value || upcoming.value ? [] : waitingQueue(currentSchedule.value.id as 'am' | 'pm'))
const filteredPatients = computed(() => waiting.value.filter(patient => `${patient.name}${patient.number}`.includes(search.value.trim())))
const selectedPatient = ref<QueuePatient | null>(null)
const selectedNotice = ref<typeof notices[number] | null>(null)
const dialog = ref<HTMLDialogElement>(), dialogKind = ref<'patient' | 'notice' | 'controls'>('controls')
const resetRequested = ref(false)
const mobileToggle = ref<HTMLButtonElement>(), resetCancel = ref<HTMLButtonElement>()
let returnFocus: HTMLElement | null = null

function loadDemo(value: string | null) {
  scenario.value = 'current'; role.value = 'doctor'
  if (!value) return
  try {
    const parsed = JSON.parse(value)
    if (parsed && scenarios.some(item => item.key === parsed.scenario)) scenario.value = parsed.scenario
    if (parsed?.role === 'head') role.value = 'head'
  } catch { storageWarning.value = '演示设置无法读取，已恢复默认场景。' }
}
async function saveDemo(changeTime = false) {
  try { saveDemoSettings({ scenario: scenario.value, role: role.value }); storageWarning.value = ''; if (changeTime) await setDemoTime(scenarios.find(item => item.key === scenario.value)!.time); if (scenario.value === 'ongoing' && !activeVisit.value) { const first = waitingQueue('am')[0]; if (first) await callVisit(first.id) } }
  catch { storageWarning.value = '浏览器暂时无法保留演示设置，当前页面仍可预览。' }
}
function syncDemo(event: StorageEvent) { if (event.key === demoSettingsKey || event.key === null) loadDemo(event.newValue) }
function syncDoctorProfile(event?: StorageEvent | { name: string; avatar: string }) { if (event && 'name' in event) { doctorProfile.value = event; return } try { const value = JSON.parse(event?.newValue || localStorage.getItem('medical-doctor-profile-v1') || 'null'); if (value && typeof value.name === 'string') doctorProfile.value = { name: value.name, avatar: typeof value.avatar === 'string' ? value.avatar : '' } } catch { doctorProfile.value = { name: '许知遥', avatar: '' } } }
const syncDoctorStorage = (event: StorageEvent) => syncDoctorProfile(event)
const resetDoctorProfile = () => syncDoctorProfile()
function navigate(section: Section, patient?: QueuePatient, application?: Record<string, string>) {
  if (!openTabs.value.includes(section)) openTabs.value.push(section)
  if (section === 'reception' && patient) receptionPatient.value = patient
  mobileOpen.value = false
  void router.push({ path: section === 'home' ? '/prototype/doctor-home' : `/prototype/doctor-home/${section}`, query: section === 'reception' && receptionPatient.value ? { patient: receptionPatient.value.id } : section === 'schedules' ? scheduleContext.value : section === 'applications' ? { ...applicationContext.value, ...application } : {} })
}
function openScheduleApplication(id: string) { navigate('applications', undefined, { ...scheduleContext.value, tab: 'suspension', schedule: id }) }
function openInvitation(id: string) { navigate('applications', undefined, { ...scheduleContext.value, tab: 'invitation', invitation: id }) }
function openScheduleReception(group: 'am' | 'pm') { openReception(activeVisit.value ?? receptionState.value.visits.find(v => v.group === group && v.status === 'WAITING') ?? receptionState.value.visits.find(v => v.group === group)) }
function closeTab(section: Section) {
  openTabs.value = openTabs.value.filter(tab => tab !== section)
  if (section === 'reception') receptionPatient.value = undefined
  if (current.value === section) navigate('home')
}
function openDialog(kind: typeof dialogKind.value) {
  dialogKind.value = kind; resetRequested.value = false
  returnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  dialog.value?.showModal()
}
function closeDialog() { dialog.value?.close(); resetRequested.value = false }
function restoreFocus() { if (dialogKind.value === 'controls') clearReceptionError(); returnFocus?.focus() }
function viewPatient(patient: QueuePatient) { selectedPatient.value = patient; openDialog('patient') }
function viewNotice(notice: typeof notices[number]) { selectedNotice.value = notice; openDialog('notice') }
function openReception(patient?: QueuePatient) { closeDialog(); navigate('reception', patient ?? activeVisit.value) }
async function resetDemo() { if (!await resetReception()) return; window.dispatchEvent(new CustomEvent('doctor-profile-reset')); scenario.value = 'current'; role.value = 'doctor'; search.value = ''; receptionPatient.value = undefined; openTabs.value = ['home']; await saveDemo(true); closeDialog(); navigate('home') }
function logoutDoctor() { void router.push('/prototype/auth?role=doctor') }
async function requestReset() { resetRequested.value = true; await nextTick(); resetCancel.value?.focus() }
function closeMobileNavigation() { mobileOpen.value = false; mobileToggle.value?.focus() }
function keydown(event: KeyboardEvent) { if (event.key === 'Escape' && mobileOpen.value && !dialog.value?.open) closeMobileNavigation() }
function selectReceptionPatient(id: string) { const patient = receptionState.value.visits.find(v => v.id === id); if (patient) navigate('reception', patient) }
const statistics = computed(() => [
  { name: '待接诊', value: waiting.value.length, unit: '人', detail: upcoming.value ? '下午尚未开放签到' : finished.value ? '今日队列已结束' : '已缴费并完成签到', icon: 'doctor', color: 'blue' },
  { name: '就诊中', value: ongoing.value ? 1 : 0, unit: '人', detail: activeVisit.value ? `${activeVisit.value.name} · ${activeVisit.value.group === 'am' ? '线下面诊' : '线上问诊'}` : '按队列顺序手动叫号', icon: 'chat', color: 'teal' },
  { name: '今日已完成', value: receptionState.value.visits.filter(v => v.status === 'COMPLETED').length, unit: '人', detail: '医疗结果已提交', icon: 'scan', color: 'purple' },
  { name: '待处理申请', value: pendingCount.value, unit: '项', detail: role.value === 'head' ? '替诊确认与科室审批' : '有一份替诊邀请待确认', icon: 'file', color: 'amber' },
])
onMounted(() => {
  try { loadDemo(localStorage.getItem(demoSettingsKey)) } catch { storageWarning.value = '浏览器暂时无法读取演示设置。' }
  syncDoctorProfile()
  if (!openTabs.value.includes(current.value)) openTabs.value.push(current.value)
  window.addEventListener('storage', syncDemo)
  window.addEventListener('storage', syncDoctorStorage)
  window.addEventListener('doctor-profile-reset', resetDoctorProfile)
  window.addEventListener('keydown', keydown)
})
onUnmounted(() => { window.removeEventListener('storage', syncDemo); window.removeEventListener('storage', syncDoctorStorage); window.removeEventListener('doctor-profile-reset', resetDoctorProfile); window.removeEventListener('keydown', keydown) })
</script>

<template>
  <div :class="['doctor-prototype', { 'dh-collapsed': collapsed || receptionFocused }]">
    <button v-if="mobileOpen" class="dh-backdrop" aria-label="关闭导航遮罩" @click="closeMobileNavigation"></button>
    <aside :class="['dh-sidebar', { 'dh-mobile-open': mobileOpen }]" aria-label="医生端侧栏">
      <button class="dh-profile" aria-label="打开医生个人中心" @click="navigate('profile')"><span class="dh-avatar"><img v-if="doctorProfile.avatar" :src="doctorProfile.avatar" alt="医生头像"><template v-else>{{ doctorProfile.name.slice(0, 1) }}</template><span></span></span><span class="dh-profile-copy"><strong>{{ doctorProfile.name }}<small>医生</small></strong><span>内科 · 副主任医师</span><em>{{ role === 'head' ? '科主任' : '已认证医生' }}</em></span></button>
      <span class="dh-nav-caption">医 生 工 作 区</span>
      <nav aria-label="医生端导航"><button v-for="item in navigation" :key="item.key" :class="{ active: current === item.key }" :aria-current="current === item.key ? 'page' : undefined" :aria-label="item.name" :title="collapsed ? item.name : undefined" @click="navigate(item.key)"><Icon :name="item.icon"/><span>{{ item.name }}</span><b v-if="item.key === 'applications'">{{ pendingCount }}</b><b v-if="item.key === 'notifications' && doctorUnread">{{ doctorUnread }}</b></button></nav>
      <div class="dh-sidebar-bottom"><div class="dh-care"><Icon name="heart"/><strong>专注每一次诊疗</strong><span>让关怀，始终在线</span></div><button class="dh-sidebar-primary" aria-label="快捷进入接诊工作台" @click="openReception()"><Icon name="doctor"/><span>进入接诊工作台</span><Icon name="arrow"/></button><button class="dh-collapse" :aria-label="collapsed ? '展开侧栏' : '收起侧栏'" :aria-expanded="!collapsed" @click="collapsed = !collapsed"><Icon name="menu"/><span>收起侧栏</span></button></div>
    </aside>

    <div class="dh-workspace">
      <header class="dh-topbar"><div class="dh-topbar-left"><button ref="mobileToggle" class="dh-mobile-toggle" aria-label="打开医生导航" :aria-expanded="mobileOpen" @click="mobileOpen = !mobileOpen"><Icon name="menu"/></button><a href="/prototype/doctor-home" class="dh-brand" @click.prevent="navigate('home')"><span><Icon name="cross"/></span><div><strong>智能医疗问诊预约系统</strong><small>SMART HEALTHCARE · 医生工作区</small></div></a></div><div class="dh-topbar-right"><span class="dh-hospital"><i></i> 校医院 · 内科</span><button class="dh-bell" aria-label="查看站内通知" @click="navigate('notifications')"><Icon name="bell"/><i v-if="doctorUnread"></i></button><button class="dh-user" aria-label="个人资料" @click="navigate('profile')"><span><img v-if="doctorProfile.avatar" :src="doctorProfile.avatar" alt="医生头像"><template v-else>{{ doctorProfile.name.slice(0, 1) }}</template></span><strong>{{ doctorProfile.name }}</strong><Icon name="chevron"/></button></div></header>
      <nav class="dh-tabs" aria-label="医生临时工作区标签"><div v-for="tab in openTabs" :key="tab" :class="{ active: current === tab }"><button :aria-current="current === tab ? 'page' : undefined" @click="navigate(tab)"><Icon :name="navigation.find(item => item.key === tab)!.icon"/>{{ navigation.find(item => item.key === tab)!.name }}</button><button v-if="tab !== 'home'" :aria-label="`关闭${navigation.find(item => item.key === tab)!.name}标签`" @click="closeTab(tab)"><Icon name="close"/></button></div></nav>

      <main class="dh-content" aria-label="医生端原型"><p v-if="receptionError && current !== 'reception' && current !== 'applications' && !dialog?.open" role="alert" class="rd-alert">{{ receptionError }}</p>
        <template v-if="current === 'home'">
          <section class="dh-welcome"><div><span class="dh-eyebrow">YOUR DAILY PRACTICE</span><h1>{{ upcoming || finished ? '下午好' : '上午好' }}，许知遥医生<span>。</span></h1><p>每一份信任，都值得认真回应。今天也一起守护校园健康。</p></div><div class="dh-date"><strong>12</strong><span>2026 年 11 月<small>星期四 · 演示时间 {{ demoTime }}</small></span></div></section>

          <section class="dh-hero" aria-labelledby="dh-hero-title"><div class="dh-hero-copy"><span class="dh-status" :class="{ muted: finished }"><i></i>{{ finished ? '今日排班时段结束' : upcoming ? '下一场出诊' : '当前出诊中' }}</span><h2 id="dh-hero-title">{{ finished ? '今天的诊疗，辛苦了。' : upcoming ? '下一场问诊，准备就绪。' : ongoing ? '继续这一份，专注的关怀。' : '今日诊疗，从容有序。' }}</h2><p>{{ finished ? '今日排班时段已结束，可查看接诊记录与后续安排；已签到候诊仍可继续处理。' : upcoming ? '下午线上问诊将于 14:00 开始，签到将在 13:30 开放。' : ongoing ? `${activeVisit?.name}正在接诊中，返回工作台继续完成本次诊疗。` : '候诊患者已准备就绪，进入工作台，开始今天的接诊。' }}</p><div class="dh-hero-actions"><button v-if="!finished" class="dh-primary" @click="openReception()">{{ ongoing ? '继续接诊' : '进入接诊工作台' }}<Icon name="arrow"/></button><button class="dh-hero-secondary" @click="navigate('schedules')">查看我的排班<Icon name="chevron"/></button></div></div><div v-if="!finished" class="dh-session-card"><span class="dh-session-kicker">{{ upcoming ? 'UP NEXT' : 'TODAY’S SESSION' }}</span><div class="dh-session-heading"><span><Icon :name="currentSchedule.id === 'pm' ? 'chat' : 'hospital'"/></span><div><strong>{{ currentSchedule.mode }}</strong><small>{{ currentSchedule.period }}门诊 · 内科</small></div><span class="dh-session-period">{{ currentSchedule.period }}</span></div><div class="dh-session-time">{{ currentSchedule.time }}</div><p><Icon name="pin"/>{{ currentSchedule.place }}</p><div class="dh-session-footer"><span>已挂号 <strong>{{ currentSchedule.registered }}</strong> / {{ currentSchedule.capacity }}</span><span>{{ upcoming ? '尚未开诊' : '接诊进行中' }}</span></div></div><div v-else class="dh-finished-art"><Icon name="shield"/><span>用专业守护健康</span><small>CARE IN EVERY DETAIL</small></div></section>

          <section class="dh-stats" aria-label="今日工作概况"><button v-for="stat in statistics" :key="stat.name" class="dh-stat" @click="navigate(stat.name === '待处理申请' ? 'applications' : 'reception')"><span :class="['dh-icon-tile', stat.color]"><Icon :name="stat.icon"/></span><div><span>{{ stat.name }}</span><strong>{{ stat.value }}<small>{{ stat.unit }}</small></strong><p>{{ stat.detail }}</p></div><Icon name="chevron"/></button></section>

          <div class="dh-dashboard"><div class="dh-main-column">
            <section class="dh-panel dh-queue" aria-labelledby="queue-title"><div class="dh-section-heading"><div><h2 id="queue-title">候诊预览 <span>{{ waiting.length }} 人</span></h2><p>{{ currentSchedule.period }} · {{ currentSchedule.mode }}<span class="dh-separator">/</span> 已缴费 · 已签到</p></div><button class="dh-link" @click="navigate('reception')">完整队列<Icon name="arrow"/></button></div><div v-if="ongoing" class="dh-ongoing"><span class="dh-pulse"></span><span>正在接诊<strong>{{ activeVisit?.name }} · {{ activeVisit?.number }}</strong></span><button @click="openReception()">继续接诊<Icon name="arrow"/></button></div><div class="dh-queue-tools"><span><i></i>预约优先，同组按签到顺序</span><div class="dh-search"><Icon name="search"/><input v-model="search" aria-label="搜索候诊患者" placeholder="搜索姓名 / 排队号"></div></div><div class="dh-queue-head"><span>排队号 / 患者</span><span>就诊信息</span><span>签到时间</span><span>操作</span></div><div v-for="patient in filteredPatients" :key="patient.id" class="dh-patient-row"><div class="dh-patient-person"><span class="dh-queue-number" :class="{ walkin: patient.source === '当日挂号' }">{{ patient.number }}</span><span><strong>{{ patient.name }}</strong><small>{{ patient.sex }} · {{ patient.age }} 岁</small></span></div><div class="dh-patient-info"><span :class="['dh-source', { walkin: patient.source === '当日挂号' }]">{{ patient.source }}</span><p>{{ patient.symptom }}</p></div><time>{{ patient.checkin }}<small>已签到</small></time><button class="dh-link" :aria-label="`查看${patient.name}详情`" @click="viewPatient(patient)">查看<Icon name="chevron"/></button></div><div v-if="!filteredPatients.length" class="dh-empty"><span><Icon :name="search ? 'search' : 'calendar'"/></span><strong>{{ search ? '未找到匹配的患者' : upcoming ? '下一场尚未开放签到' : finished ? '今日候诊已结束' : '暂无候诊患者' }}</strong><p>{{ search ? '试试其他姓名或排队号。' : upcoming ? '13:30 开放签到后，患者将按规则进入候诊队列。' : '可在接诊工作台查看相关就诊记录。' }}</p><button v-if="search" class="dh-link" @click="search = ''">清空搜索</button></div><div class="dh-queue-note"><Icon name="info"/>仅预览已签到患者；叫号与诊疗操作在接诊工作台完成。</div></section>
            <section class="dh-news dh-panel"><div class="dh-section-heading"><div><span class="dh-eyebrow">HOSPITAL UPDATES</span><h2>医院动态</h2></div><button class="dh-link" @click="navigate('notifications')">全部动态<Icon name="arrow"/></button></div><button v-for="notice in notices.slice(0, 2)" :key="notice.title" class="dh-news-row" @click="viewNotice(notice)"><span>{{ notice.tag }}</span><strong>{{ notice.title }}</strong><time>{{ notice.date }}</time><Icon name="chevron"/></button></section>
          </div><div class="dh-side-column">
            <section class="dh-panel dh-schedule-panel"><div class="dh-section-heading"><h2>今日出诊</h2><button class="dh-link" @click="navigate('schedules')">全部排班<Icon name="chevron"/></button></div><div class="dh-timeline"><button v-for="schedule in schedules" :key="schedule.id" :class="['dh-schedule-row', { active: !finished && currentSchedule.id === schedule.id }]" @click="navigate('schedules')"><span class="dh-timeline-dot"></span><div class="dh-schedule-top"><strong>{{ schedule.period }} {{ schedule.time }}</strong><small>{{ finished || (schedule.id === 'am' && upcoming) ? '已结束' : schedule.id === 'pm' || upcoming ? '待出诊' : '出诊中' }}</small></div><p><Icon :name="schedule.id === 'am' ? 'hospital' : 'chat'"/>{{ schedule.mode }}</p><span>{{ schedule.place }}</span><div class="dh-capacity"><i :style="{ width: `${schedule.registered / schedule.capacity * 100}%` }"></i></div><span class="dh-capacity-label">已挂号 {{ schedule.registered }} / {{ schedule.capacity }} 人</span></button></div></section>
            <section class="dh-panel dh-todo"><div class="dh-section-heading"><h2>申请待办 <span class="dh-count">{{ pendingCount }}</span></h2><button class="dh-link" @click="navigate('applications')">申请处理<Icon name="chevron"/></button></div><button v-for="application in visibleApplications" :key="application.id" class="dh-application" @click="application.scheduleId ? openScheduleApplication(application.scheduleId) : application.invitationId ? openInvitation(application.invitationId) : navigate('applications')"><span :class="['dh-application-icon', application.headOnly ? 'purple' : application.actionable ? 'amber' : 'blue']"><Icon :name="application.icon"/></span><div><span>{{ application.kind }}<small :class="{ actionable: application.actionable }">{{ application.status }}</small></span><strong>{{ application.title }}</strong><p>{{ application.detail }}</p></div></button><p class="dh-todo-note"><Icon name="shield"/>审批结果以最终复核为准</p></section>
          </div></div>
        </template>

        <template v-else>
          <div :class="['dh-page-heading', { 'ds-page-heading': current === 'schedules' }]"><div><span v-if="current !== 'applications' && current !== 'notifications'" class="dh-eyebrow">DOCTOR WORKSPACE</span><h1>{{ currentName }}</h1><p v-if="current !== 'applications' && current !== 'notifications'">{{ navigation.find(item => item.key === current)!.caption }}</p></div><div v-if="current === 'schedules'" class="ds-page-date" aria-label="今日日期"><Icon name="calendar" /><div><span>今天</span><time :datetime="demoDate">{{ demoDate.replaceAll('-', ' / ') }}</time><strong>{{ new Date(`${demoDate}T12:00:00+08:00`).toLocaleDateString('zh-CN', { timeZone: 'Asia/Shanghai', weekday: 'long' }) }}</strong></div></div></div>
          <ReceptionWorkspace v-if="current === 'reception'" :patient-id="String(route.query.patient || '')" @patient="selectReceptionPatient" @focus="receptionFocused = $event" />
          <ScheduleWorkspace v-else-if="current === 'schedules'" @apply="openScheduleApplication" @reception="openScheduleReception" />
          <template v-else-if="current === 'applications'">
            <ScheduleApplicationWorkspace :role="role" @return="navigate('schedules')" />
          </template>
          <DoctorNotificationsWorkspace v-else-if="current === 'notifications'" @navigate="(section, id) => section === 'applications' ? (id?.startsWith('substitution-') ? openInvitation(id) : id ? openScheduleApplication(id) : navigate('applications')) : navigate(section)" />
          <DoctorProfileWorkspace v-else :role="role" :has-active-visit="!!activeVisit" @navigate="section => navigate(section, undefined, { tab: 'department' })" @profile-change="syncDoctorProfile" @logout="logoutDoctor" />
          <button class="dh-back-home dh-link" @click="navigate('home')">返回首页<Icon name="arrow"/></button>
        </template>
        <footer class="dh-footer"><span>智能医疗问诊预约系统 · 医生工作区</span><span><Icon name="shield"/>以专业守护健康，以关怀回应信任</span></footer>
      </main>
    </div>

    <aside class="dh-demo-toolbar" aria-label="独立演示控制区"><span>PROTOTYPE <i></i> 医患联动</span><button @click="openDialog('controls')"><Icon name="menu"/>演示设置</button><a href="/prototype/patient-home/orders?status=全部" target="_blank" rel="noopener">患者端<Icon name="arrow"/></a></aside>
    <dialog ref="dialog" class="dh-dialog" @close="restoreFocus" @cancel="resetRequested = false" @click="event => { if (event.target === dialog) closeDialog() }" :aria-label="dialogKind === 'patient' ? '患者详情' : dialogKind === 'notice' ? '医院动态详情' : '原型演示设置'">
      <button class="dh-dialog-close" aria-label="关闭弹窗" @click="closeDialog()"><Icon name="close"/></button>
      <template v-if="dialogKind === 'patient' && selectedPatient"><span class="dh-eyebrow">PATIENT PREVIEW</span><h2>{{ selectedPatient.name }}</h2><p>{{ selectedPatient.sex }} · {{ selectedPatient.age }} 岁 · {{ selectedPatient.number }}</p><div class="dh-detail"><span>{{ selectedPatient.source }} · {{ selectedPatient.group === 'am' ? '线下面诊' : '线上问诊' }}</span><strong>{{ selectedPatient.symptom }}</strong><p>签到时间 {{ selectedPatient.checkin }} · 已缴费并签到</p></div><p class="dh-dialog-note">当前为候诊资料预览，进入工作台不会自动叫号。</p><button class="dh-primary" @click="openReception(selectedPatient)">进入接诊工作台<Icon name="arrow"/></button></template>
      <template v-else-if="dialogKind === 'notice' && selectedNotice"><span class="dh-eyebrow">HOSPITAL UPDATES</span><h2>{{ selectedNotice.title }}</h2><p>{{ selectedNotice.tag }} · {{ selectedNotice.date }}</p><p class="dh-notice-body">{{ selectedNotice.body }}</p></template>
      <template v-else><span class="dh-eyebrow">PROTOTYPE CONTROLS</span><h2>原型演示设置</h2><p>仅用于预览界面，不改变真实账号或业务。</p><label for="dh-scenario">演示场景</label><select id="dh-scenario" v-model="scenario" @change="saveDemo(true)"><option v-for="item in scenarios" :key="item.key" :value="item.key">{{ item.name }}</option></select><label for="dh-role">演示身份</label><select id="dh-role" v-model="role" @change="saveDemo()"><option value="doctor">普通医生</option><option value="head">科主任</option></select><p class="dh-dialog-note">医患联动仅在同一地址的浏览器标签页同步。更改时间不会结束当前接诊；完整审批演示后续制作。</p><ReceptionDemoControls/><p v-if="storageWarning" role="alert">{{ storageWarning }}</p><div class="dh-controls-actions"><button class="dh-soft" @click="closeDialog()">完成设置</button><button class="dh-reset" @click="requestReset">重置演示</button></div><div v-if="resetRequested" class="dh-reset-confirm" role="group" aria-label="确认重置演示"><p>恢复默认场景与医生身份，并清空医患联动演示的消息、草稿、结果及药费进度？其他患者页面示例不受影响。</p><button ref="resetCancel" class="dh-soft" @click="resetRequested = false">取消</button><button class="dh-primary" @click="resetDemo">确认重置</button></div></template>
    </dialog>
  </div>
</template>
