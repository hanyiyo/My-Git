<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from './PrototypeIcon.vue'
import PrototypeDatePicker from './PrototypeDatePicker.vue'
import { departments } from './data'
import { prototypeToday } from './prototype-date-options'

type VisitMode = '线下面诊' | '线上问诊'
interface RegistrationSlot {
  id: string
  doctor: string
  title: string
  department: string
  specialty: string
  date: string
  dateLabel: string
  time: string
  mode: VisitMode
  room: string
  slots: number
  fee: string
  tag: string
}

const props = defineProps<{ initialMode: VisitMode; initialDepartment: string; initialDoctor: string }>()
const emit = defineEmits<{ back: []; orders: [] }>()
const mode = ref<VisitMode>(props.initialMode)
const department = ref(props.initialDepartment)
const doctor = ref(props.initialDoctor)
const date = ref(prototypeToday)
const search = ref('')
const selectedId = ref('general-am')
const submitted = ref(false)
const slots: RegistrationSlot[] = [
  { id: 'general-am', doctor: '陈予安', title: '主治医师', department: '全科医学科', specialty: '常见不适与健康咨询', date: '2026-11-10', dateLabel: '今天 · 11月10日', time: '08:00–12:00', mode: '线下面诊', room: '门诊楼 2F · 201室', slots: 8, fee: '¥12.00', tag: '今日出诊' },
  { id: 'general-pm', doctor: '陈予安', title: '主治医师', department: '全科医学科', specialty: '常见不适与健康咨询', date: '2026-11-10', dateLabel: '今天 · 11月10日', time: '14:00–18:00', mode: '线下面诊', room: '门诊楼 2F · 201室', slots: 5, fee: '¥12.00', tag: '余号充足' },
  { id: 'internal-online', doctor: '许知遥', title: '副主任医师', department: '内科', specialty: '呼吸消化 · 慢病管理', date: '2026-11-10', dateLabel: '今天 · 11月10日', time: '09:00–11:30', mode: '线上问诊', room: '线上图文诊室', slots: 12, fee: '¥18.00', tag: '线上问诊' },
  { id: 'dental-am', doctor: '林牧', title: '主治医师', department: '口腔科', specialty: '口腔检查 · 牙齿保健', date: '2026-11-10', dateLabel: '今天 · 11月10日', time: '13:30–16:30', mode: '线下面诊', room: '门诊楼 1F · 口腔诊室', slots: 3, fee: '¥20.00', tag: '余号不多' },
  { id: 'skin-pm', doctor: '周晚宁', title: '主治医师', department: '皮肤科', specialty: '皮肤问题 · 专业咨询', date: '2026-11-10', dateLabel: '今天 · 11月10日', time: '15:00–17:30', mode: '线上问诊', room: '线上图文诊室', slots: 7, fee: '¥16.00', tag: '线上问诊' },
]

const filteredSlots = computed(() => slots.filter(item => {
  const query = search.value.trim()
  return (!query || `${item.doctor}${item.department}${item.specialty}`.includes(query))
    && (!doctor.value || item.doctor === doctor.value)
    && (!department.value || department.value === '全部科室' || item.department === department.value)
    && item.mode === mode.value
    && item.date === date.value
}))
const selected = computed(() => filteredSlots.value.find(item => item.id === selectedId.value) ?? filteredSlots.value[0] ?? slots[0])
const departmentOptions = computed(() => ['全部科室', ...departments.map(item => item.name)])
const doctorOptions = computed(() => ['全部医生', ...Array.from(new Set(slots.map(item => item.doctor)))])

function choose(item: RegistrationSlot) {
  selectedId.value = item.id
  doctor.value = item.doctor
  submitted.value = false
}
function setMode(value: VisitMode) {
  mode.value = value
  const next = filteredSlots.value[0]
  if (next) choose(next)
}
function reset() {
  mode.value = props.initialMode
  department.value = props.initialDepartment || '全部科室'
  doctor.value = props.initialDoctor
  date.value = prototypeToday
  search.value = ''
  selectedId.value = 'general-am'
  submitted.value = false
}
function confirm() {
  submitted.value = true
}
</script>

<template>
  <section class="ph-registration-page" data-testid="registration-page" aria-labelledby="prototype-registration-title">
    <header class="ph-registration-heading">
      <div>
        <span class="ph-eyebrow">PATIENT SERVICES · 门诊安排</span>
        <h1 id="prototype-registration-title">立即挂号</h1>
        <p>选择就诊方式、科室和出诊时段，完成一次清晰的就医安排。</p>
      </div>
      <div class="ph-registration-heading-actions">
        <span class="ph-service-open"><i></i> 门诊服务开放中</span>
        <button class="ph-soft-button" type="button" @click="emit('orders')"><Icon name="file" /> 我的挂号</button>
      </div>
    </header>

    <div v-if="submitted" class="ph-registration-success" role="status">
      <span class="ph-success-icon">✓</span><div><strong>演示挂号已提交，待付款</strong><small>号源暂时为你保留。进入“我的挂号”完成模拟支付后，预约才会确认。</small></div><button type="button" @click="emit('orders')">去我的挂号 <Icon name="arrow" /></button>
    </div>

    <section class="ph-registration-toolbar" aria-label="挂号筛选">
      <div class="ph-registration-tabs" role="tablist" aria-label="就诊方式"><button type="button" role="tab" :aria-selected="mode === '线下面诊'" :class="{ active: mode === '线下面诊' }" @click="setMode('线下面诊')"><Icon name="hospital" /> 线下面诊</button><button type="button" role="tab" :aria-selected="mode === '线上问诊'" :class="{ active: mode === '线上问诊' }" @click="setMode('线上问诊')"><Icon name="chat" /> 线上问诊</button></div>
      <PrototypeDatePicker v-model="date" label="就诊日期" />
      <label class="ph-registration-control">科室<select v-model="department" aria-label="挂号科室"><option v-for="item in departmentOptions" :key="item">{{ item }}</option></select></label><label class="ph-registration-control">医生<select v-model="doctor" aria-label="挂号医生"><option value="">全部医生</option><option v-for="item in doctorOptions.slice(1)" :key="item">{{ item }}</option></select></label>
      <label class="ph-registration-search"><Icon name="search" /><span class="sr-only">搜索医生</span><input v-model="search" aria-label="搜索医生" placeholder="搜索医生姓名或擅长方向" /></label>
      <button class="ph-reset-button" type="button" @click="reset"><Icon name="refresh" /> 重置</button>
    </section>

    <div class="ph-registration-layout">
      <section class="ph-registration-list" aria-labelledby="registration-list-title">
        <div class="ph-registration-list-heading"><div><span class="ph-eyebrow">AVAILABLE SLOTS</span><h2 id="registration-list-title">可挂号号源</h2></div><strong>{{ filteredSlots.length }} 条</strong></div>
        <div class="ph-registration-list-summary"><span>全部 {{ filteredSlots.length }}</span><span>余号充足 {{ filteredSlots.filter(item => item.slots > 5).length }}</span><span>今日出诊</span></div>
        <div v-if="!filteredSlots.length" class="ph-registration-empty"><Icon name="search" /><strong>没有匹配的号源</strong><small>可以更换日期、服务方式或清空搜索条件。</small><button class="ph-soft-button" type="button" @click="reset">恢复默认筛选</button></div>
        <button v-for="item in filteredSlots" v-else :key="item.id" type="button" :class="['ph-registration-item', { selected: selected.id === item.id }]" @click="choose(item)">
          <span class="ph-doctor-avatar">{{ item.doctor[0] }}</span><span class="ph-registration-item-main"><strong>{{ item.doctor }}</strong><small>{{ item.title }} · {{ item.department }}</small><small>{{ item.dateLabel }} · {{ item.time }}</small></span><span class="ph-registration-item-status"><b>{{ item.tag }}</b><small>剩余 {{ item.slots }} 号</small></span>
        </button>
      </section>

      <section class="ph-registration-detail" aria-labelledby="registration-detail-title">
        <div class="ph-registration-detail-top"><div class="ph-detail-doctor"><span class="ph-detail-avatar">{{ selected.doctor[0] }}</span><div><span class="ph-eyebrow">DOCTOR PROFILE</span><h2 id="registration-detail-title">{{ selected.doctor }} <small>{{ selected.title }}</small></h2><p>{{ selected.department }} · {{ selected.specialty }}</p></div></div><span class="ph-detail-tag">{{ selected.tag }}</span></div>
        <div class="ph-registration-detail-grid"><div><span>挂号日期</span><strong>{{ selected.dateLabel }}</strong></div><div><span>出诊时段</span><strong>{{ selected.time }}</strong></div><div><span>服务方式</span><strong>{{ selected.mode }}</strong></div><div><span>就诊地点</span><strong>{{ selected.room }}</strong></div><div><span>剩余号源</span><strong class="ph-slot-number">{{ selected.slots }} 个</strong></div><div><span>挂号费用</span><strong>{{ selected.fee }}</strong></div></div>
        <div class="ph-registration-note"><Icon name="shield" /><div><strong>挂号提示</strong><p>支付成功后才确认挂号。线下就诊请按时到院，线上问诊将在签到窗口开放后进入图文诊室。</p></div></div>
        <div class="ph-registration-patient"><div><span class="ph-eyebrow">PATIENT</span><h3>林同学</h3><p>当前使用患者账户 · 就诊信息将用于生成预约凭证</p></div><button type="button" class="ph-text-button" @click="emit('orders')">查看已有挂号 <Icon name="arrow" /></button></div>
        <div class="ph-registration-actions"><span>确认后将临时保留 {{ selected.slots }} 个号源中的 1 个</span><button class="ph-primary" type="button" @click="confirm">确认挂号 <Icon name="arrow" /></button></div>
      </section>
    </div>
    <p class="ph-registration-footnote"><Icon name="info" /> 这是 Web 端原型页面，当前使用虚构号源；实际号源、费用和可预约时间以系统发布为准。</p>
  </section>
</template>
