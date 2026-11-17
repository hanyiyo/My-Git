<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import type { Department, Doctor, Hospital, Schedule } from '../catalog'

type Mode = 'ONLINE' | 'OFFLINE'
type Session = 'AM' | 'PM'
type Source = 'ADVANCE' | 'WALK_IN'
interface AppointmentHold { id: string; holdExpiresAt: number; state: string; serverNow: number }

const route = useRoute(), router = useRouter()
const hospital = ref<Hospital | null>(null)
const departments = ref<Department[]>([]), doctors = ref<Doctor[]>([]), schedules = ref<Schedule[]>([])
const departmentId = ref(''), doctorId = ref(''), date = ref(hospitalDate()), mode = ref<Mode>('OFFLINE'), session = ref<Session | ''>('')
const busy = ref(false), loadingMetadata = ref(true), error = ref(''), status = ref('')
const confirmationOpen = ref(false), selectedSchedule = ref<Schedule | null>(null), submitting = ref(false)
const hold = ref<AppointmentHold | null>(null), now = ref(Date.now())
const requestVersion = ref(0)
const filterNote = ref(''), filtersOpen = ref(false), drawer = ref<HTMLDialogElement>()
let submissionKey = '', serverNow = Date.now(), receivedAt = performance.now(), restoring = false
let returnFocus: HTMLElement | null = null
let refreshTimer: ReturnType<typeof setTimeout> | undefined
let clockTimer: ReturnType<typeof setInterval> | undefined
let initialized = false

function hospitalDate(value = new Date()) {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(value)
  return `${parts.find(part => part.type === 'year')?.value}-${parts.find(part => part.type === 'month')?.value}-${parts.find(part => part.type === 'day')?.value}`
}
function dateFromOffset(offset: number) {
  // Use a UTC date-only value so Shanghai midnight cannot shift the shortcut back a day.
  const [year, month, day] = hospitalDate(new Date(now.value)).split('-').map(Number)
  const value = new Date(Date.UTC(year, month - 1, day + offset))
  return value.toISOString().slice(0, 10)
}
function formatDate(value: string) {
  const parsed = new Date(`${value}T12:00:00+08:00`)
  if (Number.isNaN(parsed.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', month: 'numeric', day: 'numeric', weekday: 'short' }).format(parsed)
}
function scheduleTime(item: Schedule) {
  const format = (value: number) => new Intl.DateTimeFormat('zh-CN', { timeZone: item.zoneId, hour: '2-digit', minute: '2-digit', hour12: false }).format(value)
  return `${format(item.startsAt)}–${format(item.endsAt)}`
}
async function retryLoad() {
  await loadMetadata()
  if (error.value) return
  if (!initialized) { hydrateFromUrl(); initialized = true; await syncUrl() }
  await loadSchedules()
}
function sessionLabel(value: string) { return value === 'AM' ? '上午' : '下午' }
function modeLabel(value: string) { return value === 'ONLINE' ? '线上' : '线下' }
function money(value: number) { return `¥${(value / 100).toFixed(2)}` }
function actionLabel(item: Schedule) { return item.clinicDate === today.value ? '当日挂号' : '预约挂号' }
function sourceFor(item: Schedule): Source { return item.clinicDate === today.value ? 'WALK_IN' : 'ADVANCE' }
function errorMessage(failure: unknown) {
  if (failure instanceof ApiError && failure.status >= 500) return '号源加载失败，请重试'
  if (failure instanceof TypeError) return '号源加载失败，请重试'
  return failure instanceof Error ? failure.message : '号源加载失败，请重试'
}

const today = computed(() => hospitalDate(new Date(now.value)))
const quickDates = computed(() => [
  { value: today.value, label: '今天' },
  { value: dateFromOffset(1), label: '明天' },
  { value: dateFromOffset(2), label: '后天' },
])
const availableDoctors = computed(() => doctors.value.filter(item => !departmentId.value || item.departmentId === departmentId.value))
const sortedSchedules = computed(() => [...schedules.value].sort((a, b) => a.clinicDate.localeCompare(b.clinicDate) || (a.session === b.session ? 0 : a.session === 'AM' ? -1 : 1) || b.availableSlots - a.availableSlots || a.doctorName.localeCompare(b.doctorName)))
const holdSeconds = computed(() => hold.value ? Math.max(0, Math.ceil((hold.value.holdExpiresAt - now.value) / 1000)) : 0)
const holdMinutes = computed(() => `${String(Math.floor(holdSeconds.value / 60)).padStart(2, '0')}:${String(holdSeconds.value % 60).padStart(2, '0')}`)
const currentTitle = computed(() => selectedSchedule.value ? `${selectedSchedule.value.doctorName} · ${selectedSchedule.value.departmentName}` : '')

function queryForSchedules() {
  const query = new URLSearchParams({ date: date.value, mode: mode.value })
  if (departmentId.value) query.set('departmentId', departmentId.value)
  if (doctorId.value) query.set('doctorId', doctorId.value)
  if (session.value) query.set('session', session.value)
  return query
}
async function syncUrl() {
  const query: Record<string, string> = { mode: mode.value, date: date.value }
  if (departmentId.value) query.departmentId = departmentId.value
  if (doctorId.value) query.doctorId = doctorId.value
  if (session.value) query.session = session.value
  if (JSON.stringify(route.query) !== JSON.stringify(query)) await router.push({ query })
}
function hydrateFromUrl() {
  const value = (key: string) => typeof route.query[key] === 'string' ? String(route.query[key]) : ''
  const requestedDate = value('date')
  date.value = /^\d{4}-\d{2}-\d{2}$/.test(requestedDate) && !Number.isNaN(Date.parse(requestedDate)) && new Date(requestedDate).toISOString().slice(0, 10) === requestedDate && requestedDate >= today.value ? requestedDate : today.value
  mode.value = value('mode') === 'ONLINE' ? 'ONLINE' : 'OFFLINE'
  session.value = value('session') === 'AM' || value('session') === 'PM' ? value('session') as Session : ''
  departmentId.value = departments.value.some(item => item.id === value('departmentId')) ? value('departmentId') : ''
  doctorId.value = doctors.value.some(item => item.id === value('doctorId')) ? value('doctorId') : ''
  if (doctorId.value) departmentId.value = doctors.value.find(item => item.id === doctorId.value)?.departmentId ?? departmentId.value
  if (doctorId.value && departmentId.value && !availableDoctors.value.some(item => item.id === doctorId.value)) doctorId.value = ''
}
async function loadMetadata() {
  loadingMetadata.value = true; error.value = ''
  try {
    const [information, units, people] = await Promise.all([
      api<Hospital>('/catalog/hospital'), api<Department[]>('/catalog/departments'), api<Doctor[]>('/catalog/doctors')
    ])
    hospital.value = information; departments.value = units; doctors.value = people
  } catch (failure) { error.value = errorMessage(failure) }
  finally { loadingMetadata.value = false }
}
async function loadSchedules(clearUnavailableDoctor = true) {
  const version = ++requestVersion.value
  busy.value = true; error.value = ''; status.value = ''
  try {
    const result = await api<Schedule[]>('/catalog/schedules?' + queryForSchedules().toString())
    if (version === requestVersion.value) {
      schedules.value = result
      if (clearUnavailableDoctor && doctorId.value && !result.some(item => item.doctorId === doctorId.value)) {
        doctorId.value = ''; filterNote.value = '该医生在所选日期或方式下暂无可用排班，已清除医生条件。'
      }
    }
  } catch (failure) {
    if (version === requestVersion.value) { schedules.value = []; error.value = errorMessage(failure) }
  } finally { if (version === requestVersion.value) busy.value = false }
}
function queueRefresh() {
  if (!initialized || restoring) return
  if (!date.value || date.value < today.value) date.value = today.value
  ++requestVersion.value
  if (doctorId.value && departmentId.value && !availableDoctors.value.some(item => item.id === doctorId.value)) doctorId.value = ''
  clearTimeout(refreshTimer)
  refreshTimer = setTimeout(async () => { await syncUrl(); await loadSchedules() }, 0)
}
function resetFilters() { mode.value = 'OFFLINE'; date.value = today.value; departmentId.value = ''; doctorId.value = ''; session.value = '' }
function toggleMode() { mode.value = mode.value === 'OFFLINE' ? 'ONLINE' : 'OFFLINE' }
function shiftDate() { date.value = date.value === today.value ? dateFromOffset(1) : today.value }
function doctorProfile(item: Schedule) { return doctors.value.find(doctor => doctor.id === item.doctorId) }
async function openConfirmation(item: Schedule) {
  selectedSchedule.value = item; hold.value = null; status.value = ''; error.value = ''; submissionKey = crypto.randomUUID()
  returnFocus = document.activeElement as HTMLElement; confirmationOpen.value = true
  await nextTick(); drawer.value?.showModal()
}
function closeConfirmation() { if (!submitting.value) { drawer.value?.close(); confirmationOpen.value = false; selectedSchedule.value = null; hold.value = null; returnFocus?.focus() } }
async function submitRegistration() {
  if (!selectedSchedule.value) return
  submitting.value = true; error.value = ''; status.value = ''
  const item = selectedSchedule.value, source = sourceFor(item)
  try {
    const created = await api<AppointmentHold>('/patient/appointments', 'POST', { scheduleId: item.id, source, idempotencyKey: submissionKey })
    if (created.state !== 'PENDING_PAYMENT') {
      await loadSchedules(false)
      error.value = '该登记状态已变化，请前往“我的挂号”查看。'
      return
    }
    hold.value = created; serverNow = created.serverNow; receivedAt = performance.now(); now.value = serverNow
    await loadSchedules(false)
  } catch (failure) {
    const message = errorMessage(failure)
    await loadSchedules(false)
    error.value = message === '号源已用完' ? '该排班刚刚售罄，请选择其他号源' : message
  } finally { submitting.value = false }
}
function goOrders() { closeConfirmation(); router.push({ path: '/patient/orders', query: { state: 'PENDING_PAYMENT' } }) }

watch([mode, date, departmentId, doctorId, session], queueRefresh)
watch(doctorId, value => { const doctor = doctors.value.find(item => item.id === value); if (doctor?.departmentId) departmentId.value = doctor.departmentId })
watch(departmentId, value => { if (value && doctorId.value && !availableDoctors.value.some(item => item.id === doctorId.value)) doctorId.value = '' })
watch(() => route.query, async () => {
  if (!initialized) return
  restoring = true; hydrateFromUrl(); await nextTick(); restoring = false
  clearTimeout(refreshTimer); await loadSchedules()
})
onMounted(async () => {
  clockTimer = setInterval(() => { now.value = serverNow + performance.now() - receivedAt }, 1000)
  await loadMetadata()
  if (error.value) return
  hydrateFromUrl(); initialized = true; await syncUrl(); await loadSchedules()
})
onUnmounted(() => { clearTimeout(refreshTimer); if (clockTimer) clearInterval(clockTimer) })
</script>

<template>
  <section class="registration-page" aria-labelledby="registration-title">
    <header class="registration-heading">
      <div><p class="eyebrow">患者服务 / 门诊安排</p><h2 id="registration-title">立即挂号</h2><p class="hint">选择科室、医生和出诊时段，确认后临时保留号源。</p></div>
      <div class="registration-summary" aria-label="号源概览"><strong>{{ sortedSchedules.length }}</strong><span>个可登记号源</span><small>{{ hospital?.name || '校医院' }} · 医院时间（北京时间）</small></div>
    </header>
    <p v-if="error" role="alert" class="notice notice-error">{{ error }} <button v-if="!loadingMetadata" type="button" class="text" @click="retryLoad">重试</button></p>
    <p v-if="status && !confirmationOpen" role="status" class="notice">{{ status }}</p>
    <div class="registration-workspace">
      <div class="mobile-filter-bar" aria-label="当前筛选"><span>{{ modeLabel(mode) }}</span><span>{{ formatDate(date) }}</span><span>{{ session ? sessionLabel(session) : '全部时段' }}</span><button type="button" :aria-expanded="filtersOpen" aria-controls="registration-filters" @click="filtersOpen = !filtersOpen">{{ filtersOpen ? '收起筛选' : '展开筛选' }}</button></div>
      <aside id="registration-filters" :class="['registration-filters', { 'filters-open': filtersOpen }]" aria-label="挂号筛选">
        <div class="filter-heading"><div><span class="section-kicker">筛选工作台</span><h3>找到合适的号源</h3></div><button type="button" class="reset-button" @click="resetFilters">重置</button></div>
        <fieldset class="filter-group"><legend>就诊类型</legend><div class="segmented-control" role="group" aria-label="就诊类型"><button type="button" :class="{ active: mode === 'OFFLINE' }" @click="mode = 'OFFLINE'">线下挂号</button><button type="button" :class="{ active: mode === 'ONLINE' }" @click="mode = 'ONLINE'">线上问诊</button></div></fieldset>
        <fieldset class="filter-group"><legend>就诊日期</legend><div class="date-shortcuts"><button v-for="item in quickDates" :key="item.value" type="button" :class="{ active: date === item.value }" @click="date = item.value"><strong>{{ item.label }}</strong><small>{{ formatDate(item.value).split(' ')[0] }}</small></button></div><label class="visually-hidden" for="query-date">选择日期</label><input id="query-date" v-model="date" type="date" :min="today"></fieldset>
        <label for="query-department">科室</label><select id="query-department" v-model="departmentId"><option value="">全部科室</option><option v-for="item in departments" :key="item.id" :value="item.id">{{ item.name }}</option></select>
        <label for="query-doctor">医生</label><select id="query-doctor" v-model="doctorId"><option value="">全部医生</option><option v-for="item in availableDoctors" :key="item.id" :value="item.id">{{ item.displayName }} · {{ item.title }}</option></select>
        <fieldset class="filter-group"><legend>门诊时段</legend><div class="session-options"><button type="button" :class="{ active: session === '' }" @click="session = ''">全部</button><button type="button" :class="{ active: session === 'AM' }" @click="session = 'AM'">上午</button><button type="button" :class="{ active: session === 'PM' }" @click="session = 'PM'">下午</button></div></fieldset>
        <p v-if="filterNote" role="status" class="filter-note">{{ filterNote }}</p><p class="filter-note">筛选条件会自动刷新。过去日期不可选，同一日期同一半天只能有一条有效登记。</p>
      </aside>
      <section class="registration-results" aria-labelledby="result-title" aria-live="polite">
        <div class="results-heading"><div><span class="section-kicker">号源结果</span><h3 id="result-title">{{ date === today ? '今天可登记' : `${formatDate(date)} 可预约` }}</h3></div><div class="result-tools"><button type="button" class="tool-button" :class="{ active: mode === 'OFFLINE' }" @click="mode = 'OFFLINE'">线下</button><button type="button" class="tool-button" :class="{ active: mode === 'ONLINE' }" @click="mode = 'ONLINE'">线上</button></div></div>
        <p v-if="busy || loadingMetadata" class="loading-state">正在加载可用号源…</p>
        <div v-else-if="!sortedSchedules.length && !error" class="empty-state"><div class="empty-icon">⌁</div><h4>当前筛选暂无可用号源</h4><p>可以换一个日期或服务方式继续查看。</p><div class="catalog-links"><button type="button" @click="shiftDate">切换日期</button><button type="button" class="secondary" @click="toggleMode">切换线上/线下</button><button type="button" class="secondary" @click="resetFilters">重置</button></div></div>
        <ul v-else class="schedule-results" aria-label="号源列表"><li v-for="item in sortedSchedules" :key="item.id" class="schedule-card"><div class="doctor-summary"><div class="doctor-avatar" aria-hidden="true">{{ item.doctorName.slice(-1) }}</div><div><h4>{{ item.doctorName }}</h4><p>{{ doctorProfile(item)?.title || '认证医生' }} · {{ item.departmentName }}</p><p class="doctor-specialty">{{ doctorProfile(item)?.biography || '提供专业门诊服务' }}</p></div></div><div class="schedule-details"><div><span>出诊日期</span><strong>{{ formatDate(item.clinicDate) }}</strong></div><div><span>门诊时段</span><strong>{{ sessionLabel(item.session) }} <small>{{ scheduleTime(item) }}</small></strong></div><div><span>服务方式</span><strong><b class="mode-tag" :class="item.serviceMode.toLowerCase()">{{ modeLabel(item.serviceMode) }}</b></strong></div><div><span>剩余号源</span><strong :class="{ 'slots-low': item.availableSlots <= 2 }">{{ item.availableSlots }} 个</strong></div><div><span>费用</span><strong>{{ money(item.feeMinor) }}</strong></div></div><div class="schedule-action"><span v-if="item.availableSlots <= 2" class="slots-hint">余号不多</span><button type="button" :disabled="!item.advanceAvailable && !item.walkInAvailable" @click="openConfirmation(item)">{{ actionLabel(item) }}</button></div></li></ul>
      </section>
    </div>
    <footer class="appointment-flow"><span class="section-kicker">就诊流程</span><div><strong>1 选择号源</strong><i>→</i><strong>2 确认挂号</strong><i>→</i><strong>3 临时占号</strong><i>→</i><strong>4 去我的挂号付款</strong></div><small>支付成功后才确认挂号；取消和支付截止时间以系统提示为准。</small></footer>
  </section>
  <Teleport to="body"><dialog v-if="confirmationOpen" ref="drawer" class="registration-drawer" aria-labelledby="confirm-title" @cancel.prevent="closeConfirmation" @click="event => { if (event.target === drawer) closeConfirmation() }"><button type="button" class="drawer-close" aria-label="关闭确认窗口" @click="closeConfirmation">×</button><template v-if="!hold"><span class="section-kicker">确认挂号</span><h3 id="confirm-title">确认你的就诊安排</h3><p class="drawer-doctor">{{ currentTitle }}</p><div v-if="selectedSchedule" class="confirm-details"><p><span>日期</span><strong>{{ formatDate(selectedSchedule.clinicDate) }}</strong></p><p><span>时段</span><strong>{{ sessionLabel(selectedSchedule.session) }} · {{ scheduleTime(selectedSchedule) }}</strong></p><p><span>服务方式</span><strong>{{ modeLabel(selectedSchedule.serviceMode) }}</strong></p><p><span>费用</span><strong>{{ money(selectedSchedule.feeMinor) }}</strong></p></div><ul class="confirm-rules"><li>支付成功后才确认挂号。</li><li>同一日期同一半天只能有一条有效登记。</li><li>{{ selectedSchedule && selectedSchedule.clinicDate === today ? '本次为当日挂号。' : '本次为预约挂号。' }}类别按提交时医院日期确定。</li><li>取消和支付截止时间以系统提示为准。</li></ul><p v-if="error" role="alert" class="notice notice-error">{{ error }} <RouterLink to="/patient/orders">查看我的挂号</RouterLink></p><button type="button" class="drawer-primary" :disabled="submitting" @click="submitRegistration">{{ submitting ? '提交中…' : '确认挂号' }}</button></template><template v-else><span class="success-mark">✓</span><h3 id="confirm-title">已提交，待付款</h3><p class="hint">号源已临时保留，请在倒计时结束前前往“我的挂号”完成付款。</p><div class="hold-countdown"><span>临时占号剩余</span><strong>{{ holdMinutes }}</strong></div><p v-if="holdSeconds === 0" role="alert" class="notice notice-error">占号已到期，请重新选择号源。</p><div class="catalog-links drawer-actions"><button type="button" @click="goOrders">去我的挂号</button><button type="button" class="secondary" @click="closeConfirmation">继续浏览</button></div></template></dialog></Teleport>
</template>
