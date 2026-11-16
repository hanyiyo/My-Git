<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import type { Doctor } from '../catalog'
import type { Profile } from '../session'

interface Schedule { id: string; clinicDate: string; session: string; serviceMode: string; startsAt: number; endsAt: number; state: string }
interface Home { account: Profile; doctor: Doctor; hospitalDate: string; hospitalZoneId: string; currentSchedule: Schedule | null; nextSchedule: Schedule | null; waitingCount: number; inConsultationCount: number; completedCount: number; pendingApplicationCount: number; activeAppointmentId: string | null }
const router = useRouter(), home = ref<Home | null>(null), error = ref(''), loading = ref(false)
const name = computed(() => home.value?.doctor.displayName || home.value?.account.displayName || home.value?.account.identifier || '医生')
const salutation = computed(() => name.value.endsWith('医生') ? name.value : `${name.value}医生`)
const schedule = computed(() => home.value?.currentSchedule || home.value?.nextSchedule)
const scheduleLabel = computed(() => schedule.value ? `${schedule.value.clinicDate} · ${schedule.value.session === 'AM' ? '上午' : '下午'} · ${schedule.value.serviceMode === 'ONLINE' ? '线上问诊' : '线下面诊'}` : '暂无近期出诊')
const statusLabel = computed(() => home.value?.currentSchedule ? '当前出诊中' : home.value?.nextSchedule ? '下一场出诊' : '暂无近期出诊')
function formatTime(value?: number) { return value ? new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: home.value?.hospitalZoneId || 'Asia/Shanghai' }).format(value) : '' }
async function load() { loading.value = true; error.value = ''; try { home.value = await api<Home>('/doctor/home') } catch (failure) { error.value = failure instanceof Error ? failure.message : '医生首页暂时无法加载，请重试' } finally { loading.value = false } }
function openReception() { void router.push(home.value?.activeAppointmentId ? `/doctor/clinical/${home.value.activeAppointmentId}` : '/doctor/reception') }
onMounted(load)
</script>

<template>
  <section class="doctor-home" aria-labelledby="doctor-home-title">
    <div v-if="loading && !home" class="doctor-home-state" role="status">正在加载今日工作概况…</div>
    <div v-else-if="error" class="doctor-home-state doctor-home-error" role="alert"><p>{{ error }}</p><button type="button" @click="load">重试加载</button></div>
    <template v-else-if="home">
      <header class="doctor-home-welcome"><div><p class="doctor-home-eyebrow">TODAY'S PRACTICE</p><h1 id="doctor-home-title">{{ statusLabel === '下一场出诊' ? '下午好' : '您好' }}，{{ salutation }}。</h1><p>这里显示本人真实账户、出诊和候诊状态。</p><p class="doctor-home-credential">医生资质：{{ home.doctor.credentialStatus === 'PENDING' ? '待审核' : home.doctor.credentialStatus === 'CERTIFIED' ? '已认证' : '已驳回' }} · {{ home.doctor.departmentName || '尚未分配科室' }}</p></div><time :datetime="home.hospitalDate">{{ home.hospitalDate }}<small>医院时区：{{ home.hospitalZoneId }}</small></time></header>
      <section class="doctor-home-hero" aria-labelledby="doctor-session-title"><div><span class="doctor-home-status">{{ statusLabel }}</span><h2 id="doctor-session-title">{{ home.activeAppointmentId ? '继续当前接诊' : schedule ? '准备下一次接诊' : '等待新的出诊安排' }}</h2><p>{{ scheduleLabel }}</p><p v-if="schedule">{{ formatTime(schedule.startsAt) }}–{{ formatTime(schedule.endsAt) }} · {{ schedule.state === 'SUSPENSION_PENDING' ? '停诊申请处理中' : '已发布' }}</p><button v-if="home.activeAppointmentId || schedule" type="button" @click="openReception">{{ home.activeAppointmentId ? '继续接诊' : '进入接诊工作台' }}</button></div></section>
      <section class="doctor-home-stats" aria-label="今日工作概况"><article><strong>{{ home.waitingCount }}</strong><span>待接诊</span><small>已缴费、已签到、等待叫号</small></article><article><strong>{{ home.inConsultationCount }}</strong><span>接诊中</span><small>当前未结束接诊</small></article><article><strong>{{ home.completedCount }}</strong><span>今日已完成</span><small>已提交医疗结果</small></article><article><strong>{{ home.pendingApplicationCount }}</strong><span>待处理申请</span><small>本人申请、科室审核或替诊邀请</small></article></section>
      <section class="doctor-home-links" aria-label="工作区入口"><RouterLink to="/doctor/reception">查看候诊队列</RouterLink><RouterLink to="/doctor/catalog">我的认证与排班</RouterLink><RouterLink to="/doctor/governance">查看申请处理</RouterLink></section>
    </template>
  </section>
</template>

<style scoped>
.doctor-home { display: grid; gap: 22px; }.doctor-home-welcome { display: flex; justify-content: space-between; gap: 18px; align-items: end; }.doctor-home-eyebrow { margin: 0; color: #2563eb; letter-spacing: .14em; font-size: .76rem; font-weight: 700; }.doctor-home h1, .doctor-home h2, .doctor-home p { margin-top: 0; }.doctor-home h1 { margin-bottom: 8px; font-size: clamp(1.65rem, 3vw, 2.25rem); }.doctor-home-welcome p:not(.doctor-home-eyebrow) { margin-bottom: 0; color: #64748b; }.doctor-home-credential { font-size: .9rem; color: #334155 !important; }.doctor-home-welcome time { display: grid; gap: 4px; color: #334155; font-weight: 600; text-align: right; }.doctor-home-welcome time small { color: #64748b; font-weight: 400; }.doctor-home-hero { padding: 28px; border-radius: 16px; color: #fff; background: linear-gradient(120deg, #145da0, #2f80ed); }.doctor-home-status { display: inline-block; padding: 5px 10px; border-radius: 999px; background: #ffffff2e; font-size: .82rem; }.doctor-home-hero h2 { margin: 18px 0 10px; }.doctor-home-hero p { color: #e0efff; }.doctor-home-hero button, .doctor-home-error button { border: 0; border-radius: 8px; padding: 10px 16px; color: #124e78; background: #fff; font-weight: 700; cursor: pointer; }.doctor-home-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }.doctor-home-stats article { display: grid; gap: 5px; padding: 18px; border: 1px solid #e2e8f0; border-radius: 12px; background: #fff; }.doctor-home-stats strong { color: #1d4ed8; font-size: 2rem; }.doctor-home-stats span { font-weight: 700; }.doctor-home-stats small { color: #64748b; line-height: 1.4; }.doctor-home-links { display: flex; flex-wrap: wrap; gap: 12px; }.doctor-home-links a { padding: 11px 14px; border-radius: 8px; color: #1d4ed8; background: #eff6ff; text-decoration: none; }.doctor-home-state { padding: 40px 20px; border-radius: 12px; background: #fff; text-align: center; }.doctor-home-error { color: #b42318; }.doctor-home-error button { color: #1d4ed8; background: #eff6ff; }
@media (max-width: 700px) { .doctor-home-welcome { align-items: start; flex-direction: column; }.doctor-home-welcome time { text-align: left; }.doctor-home-hero { padding: 22px; }.doctor-home-stats { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 380px) { .doctor-home-stats { grid-template-columns: 1fr; } }
</style>
