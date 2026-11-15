<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { useSession, type Profile } from '../session'
import { useNotificationRefresh } from '../useNotificationRefresh'
import type { Department, Doctor, Hospital } from '../catalog'

interface AppointmentSummary {
  id: string
  doctorName: string
  departmentName: string
  clinicDate: string
  session: string
  serviceMode: string
  state: string
  startsAt: number
  endsAt: number
  serverNow: number
  credential: string | null
  payment: { state: string } | null
  reception: { state: string; calledAt: number | null; restorationCount: number } | null
}

const session = useSession()
const router = useRouter()
const profile = ref<Profile | null>(null)
const hospital = ref<Hospital | null>(null)
const departments = ref<Department[]>([])
const doctors = ref<Doctor[]>([])
const appointments = ref<AppointmentSummary[]>([])
const loading = ref(true)
const error = ref('')
const appointmentsError = ref('')
const appointmentsLoading = ref(false)
let appointmentsLoadVersion = 0
const notificationUnread = ref<number | null>(null)
const notificationError = ref('')

const displayName = computed(() => profile.value?.displayName || profile.value?.identifier || session.account?.displayName || session.account?.identifier || '患者')
const currentAppointment = computed(() => appointments.value[0] ?? null)
const todayParts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date())
const today = `${todayParts.find(part => part.type === 'year')?.value}-${todayParts.find(part => part.type === 'month')?.value}-${todayParts.find(part => part.type === 'day')?.value}`
const catalogQuery = (key: string, value: string) => ({ path: '/patient/catalog', query: { [key]: value, date: today, mode: 'OFFLINE' } })
const appointmentNow = computed(() => currentAppointment.value?.serverNow || Date.now())
const checkInAvailable = computed(() => {
  const item = currentAppointment.value
  if (!item || item.state !== 'CONFIRMED' || item.reception) return false
  return appointmentNow.value >= item.startsAt - 30 * 60 * 1000 && appointmentNow.value < item.endsAt - 15 * 60 * 1000
})
const appointmentStatus = computed(() => {
  const item = currentAppointment.value
  if (!item) return ''
  if (item.state === 'PENDING_PAYMENT') return '待支付'
  if (item.state === 'CANCELLED') return '已取消'
  if (item.state === 'EXPIRED') return '已过期'
  if (item.state === 'COMPLETED') return '已完成'
  if (item.reception?.state === 'WAITING') return '候诊中'
  if (item.reception?.state === 'CALLED') return '已叫号'
  if (item.reception?.state === 'IN_CONSULTATION') return '接诊中'
  if (item.reception?.state === 'MISSED') return '已过号'
  if (item.reception?.state === 'COMPLETED') return '已完成'
  return item.state === 'CONFIRMED' ? '待签到' : item.state
})
const appointmentAction = computed(() => {
  const item = currentAppointment.value
  if (!item) return null
  if (item.state === 'PENDING_PAYMENT') return { label: '去我的挂号完成支付', to: '/patient/orders?state=PENDING_PAYMENT' }
  if (item.state === 'COMPLETED') return { label: '查看问诊材料与结果', to: `/patient/orders/${item.id}/clinical` }
  if (item.reception?.state === 'MISSED' && item.reception.calledAt != null && item.reception.restorationCount === 0 && appointmentNow.value >= item.startsAt && appointmentNow.value < item.endsAt)
    return { label: '进入我的挂号恢复排队', to: '/patient/orders' }
  if (checkInAvailable.value) return { label: '进入我的挂号签到', to: '/patient/orders' }
  if (item.reception?.state === 'CALLED' || item.reception?.state === 'IN_CONSULTATION') return { label: '查看当前接诊状态', to: '/patient/orders' }
  return { label: '进入我的挂号', to: '/patient/orders' }
})
const appointmentHint = computed(() => {
  if (!currentAppointment.value) return '从真实号源中选择合适的医生和时段。'
  if (appointmentStatus.value === '待支付') return '号源已暂时保留，请尽快完成支付。'
  if (appointmentStatus.value === '待签到') return '进入签到窗口后，可从我的挂号完成签到。'
  if (appointmentStatus.value === '候诊中') return '你已进入候诊队列，请留意叫号提醒。'
  return '进入我的挂号查看完整业务时间线。'
})

async function load() {
  loading.value = true
  error.value = ''
  appointmentsError.value = ''
  try {
    const [home, information, units, people] = await Promise.all([
      api<Profile>('/patient/home'),
      api<Hospital>('/catalog/hospital'),
      api<Department[]>('/catalog/departments'),
      api<Doctor[]>('/catalog/doctors'),
    ])
    profile.value = home
    hospital.value = information
    departments.value = units
    doctors.value = people
    try {
      await loadAppointments()
    } catch (failure) {
      appointments.value = []
      appointmentsError.value = failure instanceof Error ? failure.message : '当前预约暂不可用'
    }
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : '患者首页暂不可用，请重试'
  } finally {
    loading.value = false
  }
}

async function loadAppointments() {
  const version = ++appointmentsLoadVersion
  appointmentsLoading.value = true
  appointmentsError.value = ''
  try {
    appointments.value = await api<AppointmentSummary[]>('/patient/appointments')
  } catch (failure) {
    if (version === appointmentsLoadVersion) {
      appointments.value = []
      appointmentsError.value = failure instanceof Error ? failure.message : '当前预约暂不可用'
    }
  } finally {
    if (version === appointmentsLoadVersion) appointmentsLoading.value = false
  }
}

async function loadNotifications() {
  try {
    notificationUnread.value = await api<number>('/notifications/unread')
    notificationError.value = ''
  } catch (failure) {
    notificationError.value = failure instanceof Error ? failure.message : '通知暂时不可用'
  }
}

useNotificationRefresh(loadNotifications)
onMounted(() => { void load(); void loadNotifications() })
async function logout() {
  await session.logout()
  await router.push('/login')
}
</script>

<template>
  <section class="patient-home-page" aria-labelledby="patient-home-title">
    <div v-if="loading" class="patient-home-state" role="status">正在加载你的患者服务…</div>
    <div v-else-if="error" class="patient-home-state patient-home-error" role="alert">
      <strong>{{ error }}</strong>
      <button type="button" @click="load">重试加载</button>
    </div>
    <template v-else>
      <header class="patient-home-heading">
        <div>
          <p class="patient-home-eyebrow">患者服务 / 今日概览</p>
          <h2 id="patient-home-title">欢迎回来，{{ displayName }}</h2>
          <p>从这里开始安排今天的就诊计划。</p>
        </div>
        <div class="patient-home-heading-actions">
          <RouterLink class="patient-home-primary" to="/patient/catalog">立即挂号</RouterLink>
          <button class="patient-home-logout" type="button" @click="logout">退出登录</button>
        </div>
      </header>

      <section class="patient-home-hero" aria-label="医院信息">
        <div>
          <p class="patient-home-eyebrow">你的就诊服务台</p>
          <h3>{{ hospital?.name || '医院信息暂缺' }}</h3>
          <p>{{ hospital?.description || '医院服务说明暂缺，请稍后重试。' }}</p>
          <small v-if="hospital?.address">{{ hospital.address }}</small>
        </div>
        <div class="patient-home-hero-actions">
          <RouterLink class="patient-home-soft" to="/assistant/business">打开 AI 助手</RouterLink>
          <RouterLink class="patient-home-soft" to="/patient/orders">查看我的预约</RouterLink>
        </div>
      </section>

      <section class="patient-home-section" aria-labelledby="patient-home-services-title">
        <div class="patient-home-section-heading">
          <div>
            <p class="patient-home-eyebrow">快捷入口</p>
            <h3 id="patient-home-services-title">快捷服务</h3>
          </div>
          <span>选择你现在需要的服务</span>
        </div>
        <div class="patient-home-service-grid">
          <RouterLink to="/patient/catalog"><strong>查询科室、医生与排班</strong><small>查看有效排班和剩余号源</small></RouterLink>
          <RouterLink to="/patient/orders"><strong>我的挂号</strong><small>支付、签到和候诊状态</small></RouterLink>
          <RouterLink to="/assistant/business"><strong>AI 导诊助手</strong><small>咨询医院流程和科室信息</small></RouterLink>
          <RouterLink to="/notifications"><strong>站内通知</strong><small v-if="notificationError">通知暂时不可用，不影响预约</small><small v-else>未读提醒：{{ notificationUnread === null ? '加载中…' : notificationUnread }} · 查看预约和就诊提醒</small></RouterLink>
        </div>
      </section>

      <section class="patient-home-section" aria-labelledby="patient-home-catalog-title">
        <div class="patient-home-section-heading">
          <div>
            <p class="patient-home-eyebrow">真实目录</p>
            <h3 id="patient-home-catalog-title">科室与医生</h3>
          </div>
          <RouterLink to="/patient/catalog">查看完整排班</RouterLink>
        </div>
        <div class="patient-home-directory-grid">
          <div class="patient-home-directory-block">
            <h4>热门科室</h4>
            <p v-if="!departments.length" class="patient-home-directory-empty">暂无可用科室，请稍后再试。</p>
            <RouterLink v-for="item in departments.slice(0, 4)" :key="item.id" :to="catalogQuery('departmentId', item.id)" class="patient-home-directory-link">
              <span><strong>{{ item.name }}</strong><small>{{ item.description || '查看该科室的有效排班' }}</small></span><b aria-hidden="true">→</b>
            </RouterLink>
          </div>
          <div class="patient-home-directory-block">
            <h4>认证医生</h4>
            <p v-if="!doctors.length" class="patient-home-directory-empty">暂无可见医生，请稍后再试。</p>
            <RouterLink v-for="item in doctors.slice(0, 4)" :key="item.id" :to="catalogQuery('doctorId', item.id)" class="patient-home-directory-link">
              <span><strong>{{ item.displayName }}</strong><small>{{ item.title }} · {{ item.departmentName || '查看医生排班' }}</small></span><b aria-hidden="true">→</b>
            </RouterLink>
          </div>
        </div>
      </section>

      <section class="patient-home-section patient-home-appointment" aria-labelledby="patient-home-appointment-title">
        <div class="patient-home-section-heading">
          <div>
            <p class="patient-home-eyebrow">我的就诊</p>
            <h3 id="patient-home-appointment-title">当前预约</h3>
          </div>
          <RouterLink to="/patient/orders">查看全部</RouterLink>
        </div>
        <div v-if="appointmentsError" class="patient-home-inline-error" role="alert"><span>{{ appointmentsError }}</span><button type="button" :disabled="appointmentsLoading" @click="loadAppointments">{{ appointmentsLoading ? '重试中…' : '重试加载预约' }}</button></div>
        <div v-else-if="!currentAppointment" class="patient-home-empty">
          <strong>暂无当前预约</strong>
          <p>{{ appointmentHint }}</p>
          <RouterLink class="patient-home-soft" to="/patient/catalog">去查询号源</RouterLink>
        </div>
        <div v-else class="patient-home-appointment-card">
          <div>
            <span class="patient-home-status">{{ appointmentStatus }}</span>
            <h4>{{ currentAppointment.doctorName }} · {{ currentAppointment.departmentName }}</h4>
            <p>{{ currentAppointment.clinicDate }} · {{ currentAppointment.session === 'AM' ? '上午' : '下午' }} · {{ currentAppointment.serviceMode === 'ONLINE' ? '线上问诊' : '线下面诊' }}</p>
            <small>{{ appointmentHint }}</small>
          </div>
          <RouterLink v-if="appointmentAction" class="patient-home-primary" :to="appointmentAction.to">{{ appointmentAction.label }}</RouterLink>
        </div>
      </section>

      <section class="patient-home-process" aria-label="就诊流程">
        <div>
          <p class="patient-home-eyebrow">就诊流程</p>
          <h3>一看就懂</h3>
        </div>
        <ol>
          <li><strong>1</strong><span>选择号源</span></li>
          <li><strong>2</strong><span>确认挂号</span></li>
          <li><strong>3</strong><span>完成支付</span></li>
          <li><strong>4</strong><span>签到候诊</span></li>
          <li><strong>5</strong><span>进入接诊</span></li>
        </ol>
      </section>
    </template>
  </section>
</template>
