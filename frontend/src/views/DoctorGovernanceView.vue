<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
import type { Schedule, Doctor } from '../catalog'
interface Request { id: string; scheduleId: string; doctorId: string; departmentId: string; substituteDoctorId: string | null; state: string; reason: string }
const schedules = ref<Schedule[]>([]), doctors = ref<Doctor[]>([]), own = ref<Request[]>([]), inbox = ref<Request[]>([]), invitations = ref<Request[]>([])
const scheduleId = ref(''), submitReason = ref(''), reasons = ref<Record<string, string>>({}), plans = ref<Record<string, string>>({}), substitutes = ref<Record<string, string>>({}), error = ref(''), busy = ref(false)
async function load() { [schedules.value, doctors.value, own.value, inbox.value, invitations.value] = await Promise.all([api<Schedule[]>('/doctor/catalog/schedules'), api<Doctor[]>('/catalog/doctors'), api<Request[]>('/doctor/catalog/suspensions'), api<Request[]>('/doctor/catalog/head/suspensions'), api<Request[]>('/doctor/catalog/substitutions')]) }
async function action(path: string, data: unknown) { busy.value = true; error.value = ''; try { await api(path, 'POST', data); await load() } catch (failure) { error.value = failure instanceof Error ? failure.message : '操作失败' } finally { busy.value = false } }
onMounted(async () => { try { await load() } catch (failure) { error.value = failure instanceof Error ? failure.message : '加载失败' } })
</script>
<template>
  <RouterLink to="/doctor">返回医生工作台</RouterLink>
  <section class="card catalog"><h2>停诊与替诊工作台</h2><p v-if="error" role="alert">{{ error }}</p>
    <button type="button" class="secondary" @click="load">刷新治理记录</button>
    <form @submit.prevent="action('/doctor/catalog/suspensions', { scheduleId, reason: submitReason })">
      <h3>申请停诊</h3><p>须提前至少24小时；不足24小时请联系管理员紧急办理。</p>
      <label for="suspension-schedule">出诊安排</label><select id="suspension-schedule" v-model="scheduleId" required><option value="">请选择</option><option v-for="item in schedules.filter(item => item.state === 'PUBLISHED')" :key="item.id" :value="item.id">{{ item.clinicDate }} {{ item.session }} {{ item.serviceMode }}</option></select>
      <label for="suspension-reason">申请理由</label><textarea id="suspension-reason" v-model="submitReason" required maxlength="1000"></textarea><button :disabled="busy">提交停诊申请</button>
    </form>
    <h3>我的申请</h3><p v-for="item in own" :key="item.id">{{ item.id }} · {{ item.state }} · {{ item.reason }}</p>
    <h3>科主任审核</h3><article v-for="item in inbox" :key="item.id" class="notice">
      <p>申请 {{ item.id }} · {{ item.reason }} · {{ item.state }}</p>
      <form @submit.prevent="action(`/doctor/catalog/head/suspensions/${item.id}/review`, { plan: plans[item.id] || 'STOP', substituteDoctorId: substitutes[item.id], reason: reasons[item.id] })">
        <label :for="'plan-' + item.id">处置方案</label><select :id="'plan-' + item.id" v-model="plans[item.id]"><option value="STOP">直接停诊</option><option value="SUBSTITUTE">安排替诊</option><option value="REJECT">驳回</option></select>
        <label :for="'substitute-' + item.id">替诊医生</label><select :id="'substitute-' + item.id" v-model="substitutes[item.id]"><option value="">请选择</option><option v-for="doctor in doctors.filter(doctor => doctor.departmentId === item.departmentId && doctor.id !== item.doctorId)" :key="doctor.id" :value="doctor.id">{{ doctor.displayName }} · {{ doctor.id }}</option></select>
        <label :for="'review-' + item.id">审核理由</label><textarea :id="'review-' + item.id" v-model="reasons[item.id]" required maxlength="1000"></textarea><button :disabled="busy">提交科主任方案</button>
      </form>
    </article>
    <h3>替诊邀请</h3><article v-for="item in invitations" :key="item.id" class="notice"><p>{{ item.id }} · {{ item.reason }}</p>
      <label :for="'respond-' + item.id">回应理由</label><textarea :id="'respond-' + item.id" v-model="reasons[item.id]" maxlength="500" required></textarea>
      <button :disabled="busy || !reasons[item.id]?.trim()" @click="action(`/doctor/catalog/substitutions/${item.id}/respond`, { accepted: true, reason: reasons[item.id] })">同意替诊</button>
      <button :disabled="busy || !reasons[item.id]?.trim()" class="secondary" @click="action(`/doctor/catalog/substitutions/${item.id}/respond`, { accepted: false, reason: reasons[item.id] })">拒绝替诊</button>
    </article>
  </section>
</template>
