<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from './PrototypeIcon.vue'
import PrototypeDatePicker from './PrototypeDatePicker.vue'
import { departmentDoctors, departmentProfiles, type DepartmentMode, type PrototypeDepartmentDoctor } from './department-data'
import { prototypeToday } from './prototype-date-options'

type ServiceFilter = '全部' | DepartmentMode
type SessionFilter = '全部' | '上午' | '下午'
interface TeamSchedule {
  id: string
  date: string
  dateLabel: string
  session: '上午' | '下午'
  time: string
  mode: DepartmentMode
  place: string
  slots: number
  fee: string
}
interface DoctorRecord extends PrototypeDepartmentDoctor {
  department: string
  schedules: TeamSchedule[]
}

const emit = defineEmits<{ register: [doctor: string] }>()
const today = prototypeToday
const name = ref('')
const department = ref('全部科室')
const service = ref<ServiceFilter>('全部')
const date = ref(today)
const session = ref<SessionFilter>('全部')
const selectedDoctor = ref<DoctorRecord | null>(null)
const selectedScheduleId = ref('')

function schedule(id: string, dateValue: string, dateLabel: string, sessionValue: '上午' | '下午', time: string, mode: DepartmentMode, place: string, slots: number, fee = '¥12.00'): TeamSchedule {
  return { id, date: dateValue, dateLabel, session: sessionValue, time, mode, place, slots, fee }
}
function schedulesFor(doctor: PrototypeDepartmentDoctor, departmentName: string): TeamSchedule[] {
  const current = doctor.today && doctor.mode
    ? [schedule(`${doctor.id}-today`, today, '今天 · 11月10日', doctor.time.includes('14:') ? '下午' : '上午', doctor.time.replace(/^今天 /, ''), doctor.mode, doctor.place, doctor.slots ?? 0)]
    : []
  const future = [
    schedule(`${doctor.id}-next`, '2026-11-12', '周四 · 11月12日', '上午', '09:00–12:00', doctor.mode ?? 'OFFLINE', doctor.place, doctor.slots ?? 0),
    schedule(`${doctor.id}-later`, '2026-11-14', '周六 · 11月14日', '下午', '14:00–18:00', doctor.mode ?? 'ONLINE', doctor.place, doctor.slots ?? 0),
  ]
  return [...current, ...future].map(item => ({ ...item, id: `${departmentName}-${item.id}` }))
}
const doctors = computed<DoctorRecord[]>(() => Object.entries(departmentDoctors).flatMap(([departmentName, list]) => list.map(doctor => ({ ...doctor, department: departmentName, schedules: schedulesFor(doctor, departmentName) }))))
const departmentOptions = computed(() => ['全部科室', ...Object.keys(departmentDoctors)])
const sessionOptions: SessionFilter[] = ['全部', '上午', '下午']
const serviceOptions: { value: ServiceFilter; label: string }[] = [{ value: '全部', label: '全部类型' }, { value: 'ONLINE', label: '线上问诊' }, { value: 'OFFLINE', label: '线下面诊' }]
function matches(scheduleItem: TeamSchedule) {
  return (date.value === '全部日期' || scheduleItem.date === date.value)
    && (service.value === '全部' || scheduleItem.mode === service.value)
    && (session.value === '全部' || scheduleItem.session === session.value)
}
function matchingSchedules(doctor: DoctorRecord) {
  return doctor.schedules.filter(matches)
}
const visibleDoctors = computed(() => {
  const query = name.value.trim().toLowerCase()
  return doctors.value.filter(doctor => (!query || `${doctor.name}${doctor.department}${doctor.specialty}`.toLowerCase().includes(query)) && (department.value === '全部科室' || doctor.department === department.value))
    .sort((a, b) => Number(matchingSchedules(b).length > 0) - Number(matchingSchedules(a).length > 0) || a.name.localeCompare(b.name, 'zh-CN'))
})
const filteredScheduleCount = computed(() => visibleDoctors.value.reduce((count, doctor) => count + matchingSchedules(doctor).length, 0))
const hasCurrentSlots = computed(() => filteredScheduleCount.value > 0)
const modalSchedules = computed(() => selectedDoctor.value?.schedules.slice().sort((a, b) => a.date.localeCompare(b.date) || a.time.localeCompare(b.time)).slice(0, 3) ?? [])
const selectedSchedule = computed(() => modalSchedules.value.find(item => item.id === selectedScheduleId.value) ?? modalSchedules.value.find(item => item.slots > 0) ?? modalSchedules.value[0])
const modalProfile = computed(() => selectedDoctor.value ? departmentProfiles[selectedDoctor.value.department] : null)

function reset() {
  name.value = ''; department.value = '全部科室'; service.value = '全部'; date.value = today; session.value = '全部'
}
function openDoctor(doctor: DoctorRecord) {
  selectedDoctor.value = doctor
  selectedScheduleId.value = doctor.schedules.find(item => item.slots > 0)?.id ?? doctor.schedules[0]?.id ?? ''
}
function registerDoctor(doctor: DoctorRecord) {
  emit('register', doctor.name)
}
</script>

<template>
  <section class="ph-doctor-team-page" data-testid="doctor-team-page" aria-labelledby="doctor-team-title">
    <header class="ph-doctor-team-heading"><div><span class="ph-eyebrow">MEDICAL TEAM · DOCTOR DIRECTORY</span><h1 id="doctor-team-title">医生列表</h1><p>了解医生擅长方向与出诊安排，选择适合你的服务方式。</p></div><div class="ph-doctor-team-summary"><strong>{{ doctors.length }}</strong><span>位医生</span><small>今日门诊服务开放中</small></div></header>

    <section class="ph-doctor-query" aria-label="医生查询"><label class="ph-doctor-query-search"><Icon name="search" /><span class="sr-only">按姓名搜索</span><input v-model="name" aria-label="按姓名搜索" placeholder="搜索医生姓名、擅长方向" /></label><label>科室<select v-model="department" aria-label="医生科室"><option v-for="item in departmentOptions" :key="item">{{ item }}</option></select></label><label>出诊类型<select v-model="service" aria-label="出诊类型"><option v-for="item in serviceOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label><PrototypeDatePicker v-model="date" label="出诊日期" /><label>门诊时段<select v-model="session" aria-label="门诊时段"><option v-for="item in sessionOptions" :key="item">{{ item }}</option></select></label><button class="ph-reset-button" type="button" @click="reset"><Icon name="refresh" /> 重置</button></section>

    <div v-if="!hasCurrentSlots" class="ph-doctor-empty-notice" role="status"><Icon name="info" /><div><strong>当前条件暂无号源</strong><small>下面展示默认医生列表，你可以打开医生详情查看最近三次排班，或直接进入挂号页查询更远日期。</small></div></div>
    <div class="ph-doctor-list-heading"><div><span class="ph-eyebrow">DOCTOR TEAM</span><h2>{{ hasCurrentSlots ? '医生列表' : '默认医生列表' }}</h2></div><span>{{ visibleDoctors.length }} 位医生</span></div>

    <div class="ph-doctor-grid">
      <article v-for="doctor in visibleDoctors" :key="doctor.id" class="ph-doctor-card" @click="openDoctor(doctor)">
        <div class="ph-doctor-card-top"><span class="ph-doctor-card-avatar">{{ doctor.name[0] }}</span><span v-if="matchingSchedules(doctor).length" class="ph-doctor-card-status">有号源</span><span v-else class="ph-doctor-card-status muted">暂无号源</span></div>
        <div class="ph-doctor-card-identity"><h3>{{ doctor.name }} <small>{{ doctor.title }}</small></h3><p>{{ doctor.department }}</p><p class="ph-doctor-specialty">擅长：{{ doctor.specialty }}</p></div>
        <div v-if="matchingSchedules(doctor).length" class="ph-doctor-card-schedules"><div v-for="item in matchingSchedules(doctor).slice(0, 2)" :key="item.id"><span><b :class="['ph-mode-dot', item.mode.toLowerCase()]"></b>{{ item.mode === 'ONLINE' ? '线上' : '线下' }} · {{ item.dateLabel }} {{ item.session }}</span><strong>余 {{ item.slots }} 号</strong></div></div><div v-else class="ph-doctor-card-no-slot">当前日期或出诊方式下暂无匹配号源</div>
        <div class="ph-doctor-card-footer"><span>点击卡片查看详情</span><a href="#register" @click.stop.prevent="registerDoctor(doctor)">立即挂号 <Icon name="arrow" /></a></div>
      </article>
    </div>

    <p v-if="!visibleDoctors.length" class="ph-doctor-no-result"><Icon name="search" /><strong>没有找到匹配的医生</strong><small>请修改姓名、科室或出诊条件后重试。</small></p>

    <Teleport to="body"><dialog v-if="selectedDoctor" open class="ph-doctor-dialog" aria-labelledby="doctor-dialog-title" @click="event => { if (event.target === event.currentTarget) selectedDoctor = null }"><button class="ph-icon-button ph-doctor-dialog-close" type="button" aria-label="关闭医生详情" @click="selectedDoctor = null"><Icon name="close" /></button><div class="ph-doctor-dialog-profile"><span class="ph-detail-avatar">{{ selectedDoctor.name[0] }}</span><div><span class="ph-eyebrow">DOCTOR PROFILE</span><h2 id="doctor-dialog-title">{{ selectedDoctor.name }} <small>{{ selectedDoctor.title }}</small></h2><p>{{ selectedDoctor.department }} · {{ selectedDoctor.specialty }}</p></div></div><div class="ph-doctor-dialog-info"><div><span>擅长方向</span><strong>{{ selectedDoctor.specialty }}</strong></div><div><span>服务方式</span><strong>线上问诊 / 线下面诊</strong></div><div><span>就诊提示</span><strong>{{ modalProfile?.hint }}</strong></div></div><div class="ph-doctor-dialog-schedules"><div class="ph-doctor-dialog-section-heading"><div><span class="ph-eyebrow">UPCOMING SCHEDULES</span><h3>最近三次排班</h3></div><small>选择排班后再挂号</small></div><button v-for="item in modalSchedules" :key="item.id" type="button" :class="['ph-doctor-schedule-row', { selected: selectedSchedule?.id === item.id }]" @click="selectedScheduleId = item.id"><span class="ph-doctor-schedule-date">{{ item.dateLabel }}</span><span><strong>{{ item.session }} · {{ item.time }}</strong><small>{{ item.mode === 'ONLINE' ? '线上问诊' : '线下面诊' }} · {{ item.place }}</small></span><b :class="{ empty: item.slots === 0 }">{{ item.slots > 0 ? `余 ${item.slots} 号` : '暂无号源' }}</b></button></div><div class="ph-doctor-dialog-footer"><span>挂号后只会把医生姓名带入立即挂号页</span><button class="ph-primary" type="button" @click="emit('register', selectedDoctor.name)">立即挂号 <Icon name="arrow" /></button></div></dialog></Teleport>
  </section>
</template>
