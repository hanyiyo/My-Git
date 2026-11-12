<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '../patient-home-prototype/PrototypeIcon.vue'
import ScheduleCard from './ScheduleCard.vue'
import { useReceptionDemo } from './reception-demo'
import { applicationLabels, dateLabel, demoDate, isPendingApplication, periodLabel, scheduleApplication, scheduleNumbers, scheduleStatus, type DemoSchedule, type ScheduleMode } from './schedule-data'
import './schedule-workspace.css'

const emit = defineEmits<{ apply: [id: string]; reception: [group: 'am' | 'pm'] }>()
const route = useRoute(), router = useRouter()
const { state, now, warning } = useReceptionDemo()
const validDate = (value: unknown) => typeof value === 'string' && /^20\d{2}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(`${value}T00:00:00Z`)) && new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value
const selectedDate = computed(() => validDate(route.query.date) ? String(route.query.date) : demoDate)
const month = computed(() => typeof route.query.month === 'string' && /^\d{4}-(0[1-9]|1[0-2])$/.test(route.query.month) ? route.query.month : selectedDate.value.slice(0, 7))
const filter = computed<ScheduleMode>(() => route.query.mode === 'online' || route.query.mode === 'offline' ? route.query.mode : 'all')
const monthLabel = computed(() => `${Number(month.value.slice(0, 4))} 年 ${Number(month.value.slice(5))} 月`)
const todaySchedules = computed(() => state.value.schedules.filter(s => s.date === demoDate))
const selectedSchedules = computed(() => state.value.schedules.filter(s => s.date === selectedDate.value && matches(s)))
const monthlySchedules = computed(() => state.value.schedules.filter(s => s.date.startsWith(month.value) && matches(s)))
const monthlyActive = computed(() => monthlySchedules.value.filter(s => s.status === 'PUBLISHED'))
const pendingCount = computed(() => state.value.applications.filter(a => isPendingApplication(a) && monthlySchedules.value.some(s => s.id === a.scheduleId)).length)
const days = computed(() => {
  const [year, mon] = month.value.split('-').map(Number) as [number, number]
  const offset = (new Date(Date.UTC(year, mon - 1, 1)).getUTCDay() + 6) % 7
  const last = new Date(Date.UTC(year, mon, 0)).getUTCDate()
  const cells: { date: string; day: number; schedules: DemoSchedule[] }[] = []
  for (let i = 0; i < Math.ceil((offset + last) / 7) * 7; i++) {
    const day = i - offset + 1, date = `${month.value}-${String(day).padStart(2, '0')}`
    cells.push({ date: day > 0 && day <= last ? date : '', day, schedules: monthlySchedules.value.filter(s => s.date === date) })
  }
  return cells
})
function matches(s: DemoSchedule) { return filter.value === 'all' || s.mode === filter.value }
function update(query: { month?: string; date?: string; mode?: ScheduleMode }) { void router.replace({ query: { ...route.query, month: month.value, date: selectedDate.value, mode: filter.value, ...query } }) }
function changeMonth(value: string) { if (/^\d{4}-(0[1-9]|1[0-2])$/.test(value)) update({ month: value, date: `${value}-01` }) }
function stepMonth(delta: number) {
  const [year, mon] = month.value.split('-').map(Number) as [number, number]
  const date = new Date(Date.UTC(year, mon - 1 + delta, 1))
  changeMonth(date.toISOString().slice(0, 7))
}
function selectDate(date: string) { update({ date }) }
function moveDate(event: KeyboardEvent, date: string) {
  const delta = ({ ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 } as Record<string, number>)[event.key]
  if (delta === undefined) return
  event.preventDefault()
  const next = new Date(`${date}T12:00:00Z`); next.setUTCDate(next.getUTCDate() + delta)
  const value = next.toISOString().slice(0, 10)
  void router.replace({ query: { ...route.query, month: value.slice(0, 7), date: value, mode: filter.value } }).then(() => document.querySelector<HTMLButtonElement>(`[data-calendar-date="${value}"]`)?.focus())
}
</script>

<template>
  <div class="ds-workspace">
    <p v-if="warning" class="ds-alert" role="alert">{{ warning }}</p>
    <section aria-label="今日排班" class="ds-today">
      <div class="ds-section-heading"><div><span class="dh-eyebrow">TODAY’S SCHEDULE</span><h2>把今天的安排，放在眼前</h2><p>{{ dateLabel(demoDate) }} · 许知遥医生 · 内科</p></div><span class="ds-date-badge"><Icon name="calendar" />今日 {{ todaySchedules.length }} 场</span></div>
      <div class="ds-today-grid"><ScheduleCard v-for="s in todaySchedules" :key="s.id" :schedule="s" compact @apply="emit('apply', $event)" @reception="emit('reception', $event)" /></div>
    </section>
    <div class="ds-month-overview"><div><h2>我的出诊日历</h2><p>看清整月安排，提前准备每一次出诊。</p></div><div><span>本月出诊 <b>{{ monthlyActive.length }}</b> 场</span><span>线上 <b>{{ monthlyActive.filter(s => s.mode === 'online').length }}</b></span><span>线下 <b>{{ monthlyActive.filter(s => s.mode === 'offline').length }}</b></span><span v-if="pendingCount" class="ds-pending-total">申请中 <b>{{ pendingCount }}</b></span></div></div>
    <div class="ds-calendar-layout">
      <section class="ds-calendar" aria-label="月排班日历">
        <div class="ds-calendar-toolbar">
          <div class="ds-month-picker"><button aria-label="上个月" @click="stepMonth(-1)"><Icon name="chevron" class="ds-prev" /></button><label><span>{{ monthLabel }}</span><input type="month" aria-label="选择排班年月" :value="month" min="2000-01" max="2100-12" @change="changeMonth(($event.target as HTMLInputElement).value)"></label><button aria-label="下个月" @click="stepMonth(1)"><Icon name="chevron" /></button></div>
          <button class="ds-today-button" @click="update({ month: demoDate.slice(0, 7), date: demoDate })">回到今天</button>
        </div>
        <div class="ds-calendar-options"><nav aria-label="排班类型筛选"><button v-for="item in [{ value: 'all', name: '全部' }, { value: 'online', name: '线上' }, { value: 'offline', name: '线下' }]" :key="item.value" :aria-pressed="filter === item.value" :class="{ active: filter === item.value }" @click="update({ mode: item.value as ScheduleMode })">{{ item.name }}</button></nav><span><i class="ds-dot online"></i>线上<i class="ds-dot offline"></i>线下</span></div>
        <div class="ds-weekdays" aria-hidden="true"><span v-for="day in ['一', '二', '三', '四', '五', '六', '日']" :key="day">周{{ day }}</span></div>
        <div class="ds-calendar-grid">
          <template v-for="(cell, index) in days" :key="cell.date || index">
            <button v-if="cell.date" type="button" :data-calendar-date="cell.date" :class="['ds-day', { selected: selectedDate === cell.date, today: cell.date === demoDate, weekend: index % 7 > 4 }]" :aria-label="`${cell.date} ${cell.schedules.length ? `${cell.schedules.length}场出诊` : filter === 'all' ? '未排班' : '无匹配排班'}`" :aria-pressed="selectedDate === cell.date" :aria-current="cell.date === demoDate ? 'date' : undefined" @click="selectDate(cell.date)" @keydown="moveDate($event, cell.date)">
              <span class="ds-day-number">{{ cell.day }}<small v-if="cell.date === demoDate">今</small></span>
              <span v-for="s in cell.schedules" :key="s.id" :class="['ds-calendar-entry', s.mode, { closed: s.status !== 'PUBLISHED' }]"><span>{{ periodLabel(s) }} <b>{{ s.mode === 'online' ? '线上' : '线下' }}</b></span><strong>{{ scheduleNumbers(s, state.visits).registered }}<small>/{{ s.capacity }}</small></strong><em v-if="s.status !== 'PUBLISHED' || scheduleApplication(s, state.applications)">{{ s.status !== 'PUBLISHED' ? scheduleStatus(s, state.applications, now) : isPendingApplication(scheduleApplication(s, state.applications)!) ? '申请中' : applicationLabels[scheduleApplication(s, state.applications)!.status] }}</em><em v-else-if="s.substituteFor">替诊</em></span>
              <small v-if="!cell.schedules.length" class="ds-no-schedule">{{ filter === 'all' ? '未排班' : '无匹配' }}</small>
            </button>
            <div v-else class="ds-day ds-day-placeholder" aria-hidden="true"></div>
          </template>
        </div>
        <p class="ds-calendar-foot"><Icon name="info" />每格数字为已挂号 / 总号源；选中日期查看详细情况。未排班不代表已批准休假。</p>
      </section>
      <aside class="ds-date-detail" aria-label="所选日期详情" aria-live="polite">
        <header><div><span class="dh-eyebrow">DAILY DETAILS</span><h2>{{ dateLabel(selectedDate) }}</h2></div><span class="ds-selected-count">{{ selectedSchedules.length }} 场出诊</span></header>
        <ScheduleCard v-for="s in selectedSchedules" :key="s.id" :schedule="s" @apply="emit('apply', $event)" @reception="emit('reception', $event)" />
        <div v-if="!selectedSchedules.length" class="ds-empty"><span><Icon name="calendar" /></span><h3>{{ state.schedules.some(s => s.date === selectedDate) ? '没有符合筛选的安排' : '当天没有已发布排班' }}</h3><p>{{ state.schedules.some(s => s.date === selectedDate) ? '可切换类型筛选，查看其他出诊安排。' : '可选择其他日期查看。出诊安排由管理员统一配置。' }}</p><button v-if="filter !== 'all'" class="dh-link" @click="update({ mode: 'all' })">查看全部类型</button></div>
        <div class="ds-help"><Icon name="shield" /><div><strong>提前安排，从容出诊</strong><p>普通停诊申请须至少提前 24 小时提交，并在审批截止前完成全部审批；紧急停诊请联系管理员。</p></div></div>
      </aside>
    </div>
    <p class="ds-page-foot">虚构演示安排 · 已挂号与临时占号分别统计；今日接诊进度与工作台同步。其他日期为演示安排。</p>
  </div>
</template>
