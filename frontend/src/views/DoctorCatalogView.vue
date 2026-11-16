<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '../api'
import { credentialLabels, type Doctor, type Schedule } from '../catalog'

const profile = ref<Doctor | null>(null)
const schedules = ref<Schedule[]>([])
const error = ref('')
const month = ref(new Date(new Date().toLocaleString('en-US', { timeZone: 'Asia/Shanghai' })))
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
const monthKey = computed(() => `${month.value.getFullYear()}-${String(month.value.getMonth() + 1).padStart(2, '0')}`)
const days = computed(() => {
  const first = new Date(month.value.getFullYear(), month.value.getMonth(), 1)
  const count = new Date(month.value.getFullYear(), month.value.getMonth() + 1, 0).getDate()
  const leading = (first.getDay() + 6) % 7
  return [...Array(leading).fill(null), ...Array.from({ length: count }, (_, index) => {
    const date = `${monthKey.value}-${String(index + 1).padStart(2, '0')}`
    return { date, number: index + 1, items: schedules.value.filter(item => item.clinicDate === date) }
  })]
})
const todaySchedules = computed(() => schedules.value.filter(item => item.clinicDate === today))
const historySchedules = computed(() => schedules.value.filter(item => item.clinicDate < today))
const futureSchedules = computed(() => schedules.value.filter(item => item.clinicDate > today))
const selectedSchedule = ref<Schedule | null>(null)
const timeLabel = (item: Schedule) => `${new Intl.DateTimeFormat('zh-CN', { timeZone: item.zoneId, hour: '2-digit', minute: '2-digit', hour12: false }).format(item.startsAt)}–${new Intl.DateTimeFormat('zh-CN', { timeZone: item.zoneId, hour: '2-digit', minute: '2-digit', hour12: false }).format(item.endsAt)}`
const statusLabel = (item: Schedule) => item.state === 'PUBLISHED' ? (item.advanceAvailable || item.walkInAvailable ? '当前开放预约' : '当前不可预约') : item.state === 'SUSPENSION_PENDING' ? '停诊申请处理中' : '已取消'
function shiftMonth(offset: number) { month.value = new Date(month.value.getFullYear(), month.value.getMonth() + offset, 1) }
function choose(item: Schedule) { selectedSchedule.value = selectedSchedule.value?.id === item.id ? null : item }
async function load() {
  try { [profile.value, schedules.value] = await Promise.all([api<Doctor>('/doctor/catalog/profile'), api<Schedule[]>('/doctor/catalog/schedules')]) }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '资料暂不可用' }
}
onMounted(load)
</script>
<template>
  <RouterLink to="/doctor">返回工作台</RouterLink>
  <section class="card catalog"><h2>我的认证与排班</h2><p v-if="error" role="alert">{{ error }}</p>
    <template v-if="profile"><h3>{{ profile.displayName }}</h3><p>认证状态：{{ credentialLabels[profile.credentialStatus] ?? profile.credentialStatus }}</p><p>{{ profile.title }}</p><p>所属科室：{{ profile.departmentName ?? '尚未分配科室' }}</p><p class="hint">{{ profile.biography }}</p></template>
    <div class="catalog-toolbar"><div><h3>真实排班月历</h3><p class="hint">医院时区：中国标准时间。点击场次查看号源、接诊进度和申请入口。</p></div><div class="calendar-nav"><button type="button" class="secondary" aria-label="上个月" @click="shiftMonth(-1)">‹</button><strong>{{ monthKey }}</strong><button type="button" class="secondary" aria-label="下个月" @click="shiftMonth(1)">›</button><button type="button" class="secondary" @click="month = new Date(new Date().getFullYear(), new Date().getMonth(), 1)">回到本月</button></div></div>
    <div class="schedule-calendar" aria-label="排班月历"><div v-for="label in ['一','二','三','四','五','六','日']" :key="label" class="calendar-weekday">{{ label }}</div><div v-for="(day, index) in days" :key="day?.date ?? `blank-${index}`" :class="['calendar-day', { blank: !day, today: day?.date === today }]" :aria-label="day?.date"><template v-if="day"><strong>{{ day.number }}</strong><button v-for="item in day.items" :key="item.id" type="button" class="calendar-slot" :class="{ selected: selectedSchedule?.id === item.id }" @click="choose(item)">{{ item.session === 'AM' ? '上午' : '下午' }} · {{ item.serviceMode === 'ONLINE' ? '线上' : '线下' }}</button></template></div></div>
    <article v-if="selectedSchedule" class="notice schedule-detail"><div class="catalog-toolbar"><div><h3>{{ selectedSchedule.clinicDate }} · {{ selectedSchedule.session === 'AM' ? '上午' : '下午' }} · {{ selectedSchedule.serviceMode === 'ONLINE' ? '线上' : '线下' }}</h3><p>{{ timeLabel(selectedSchedule) }} · {{ statusLabel(selectedSchedule) }}</p></div><RouterLink class="button" :to="`/doctor/governance?schedule=${selectedSchedule.id}`">申请停诊</RouterLink></div><div class="schedule-metrics"><span>总号源 <strong>{{ selectedSchedule.capacity }}</strong></span><span>已确认占用 <strong>{{ selectedSchedule.confirmedAppointments }}</strong></span><span>临时占号 <strong>{{ selectedSchedule.pendingHolds }}</strong></span><span>可用数 <strong>{{ selectedSchedule.availableSlots }}</strong></span><span>候诊 <strong>{{ selectedSchedule.waitingPatients }}</strong></span><span>已叫号 <strong>{{ selectedSchedule.calledPatients }}</strong></span><span>接诊中 <strong>{{ selectedSchedule.inConsultationPatients }}</strong></span><span>已完成 <strong>{{ selectedSchedule.completedPatients }}</strong></span></div></article>
    <section v-if="todaySchedules.length" class="schedule-section"><h3>今日场次</h3><ul class="resource-list"><li v-for="item in todaySchedules" :key="item.id"><strong>{{ item.session === 'AM' ? '上午' : '下午' }} · {{ item.serviceMode === 'ONLINE' ? '线上' : '线下' }} · {{ timeLabel(item) }}</strong><p>总号源：{{ item.capacity }} · 已确认占用：{{ item.confirmedAppointments }} · 临时占号：{{ item.pendingHolds }} · 可用号源：{{ item.availableSlots }}</p><p>候诊 {{ item.waitingPatients }} · 已叫号 {{ item.calledPatients }} · 接诊中 {{ item.inConsultationPatients }} · 已完成 {{ item.completedPatients }}</p></li></ul></section>
    <section class="schedule-section"><h3>历史与未来</h3><p class="hint">历史排班仅供只读核对；未来排班可从月历进入停诊申请。</p><ul class="resource-list"><li v-for="item in [...historySchedules, ...futureSchedules].slice(0, 12)" :key="item.id"><strong>{{ item.clinicDate }} · {{ item.session === 'AM' ? '上午' : '下午' }} · {{ item.serviceMode === 'ONLINE' ? '线上' : '线下' }}</strong><p>{{ timeLabel(item) }} · 总号源：{{ item.capacity }} · 已确认占用：{{ item.confirmedAppointments }} · 临时占号：{{ item.pendingHolds }} · 可用号源：{{ item.availableSlots }}</p><p>{{ statusLabel(item) }} <RouterLink v-if="item.clinicDate >= today && item.state === 'PUBLISHED'" :to="`/doctor/governance?schedule=${item.id}`">申请停诊</RouterLink></p></li><li v-if="!historySchedules.length && !futureSchedules.length">暂无历史或未来排班。</li></ul></section>
  </section>
</template>
