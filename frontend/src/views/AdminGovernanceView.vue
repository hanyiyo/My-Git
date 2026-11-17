<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
import type { Schedule, Doctor, Department } from '../catalog'
interface Request { id: string; scheduleId: string; doctorId: string; departmentId: string; substituteDoctorId: string | null; state: string; reason: string }
const requests = ref<Request[]>([]), schedules = ref<Schedule[]>([]), doctors = ref<Doctor[]>([]), departments = ref<Department[]>([])
interface Impact { appointmentId: string; patientId: string; doctorId: string; state: string }
const impacts = ref<Record<string, Impact[]>>({}), emergencyChoices = ref<Record<string, string>>({})
async function loadImpacts(id: string) { try { impacts.value[id] = await api<Impact[]>(`/admin/catalog/suspensions/${id}/impacts`) } catch (failure) { error.value = failure instanceof Error ? failure.message : '影响清单加载失败' } }
async function emergencySubstitute(item: Request) {
  const patients = impacts.value[item.id]?.filter(patient => patient.state === 'CONFIRMED')
  if (!patients || patients.some(patient => !emergencyChoices.value[patient.appointmentId])) { error.value = '请逐一记录受影响患者的明确选择'; return }
  await action(`/admin/catalog/suspensions/${item.id}/emergency-substitution`, { reason: reasons.value[item.id], patientChoices: Object.fromEntries(patients.map(patient => [patient.appointmentId, emergencyChoices.value[patient.appointmentId] === 'ACCEPT'])) })
}
const reasons = ref<Record<string, string>>({}), substitutes = ref<Record<string, string>>({}), plans = ref<Record<string, string>>({}), departmentId = ref(''), headId = ref(''), emergencySchedule = ref(''), emergencyReason = ref(''), error = ref(''), status = ref(''), busy = ref(false)
async function load() { [requests.value, schedules.value, doctors.value, departments.value] = await Promise.all([api<Request[]>('/admin/catalog/suspensions'), api<Schedule[]>('/admin/catalog/schedules'), api<Doctor[]>('/admin/catalog/doctors'), api<Department[]>('/admin/catalog/departments')]) }
async function action(path: string, data: unknown, method = 'POST') { busy.value = true; error.value = ''; status.value = ''; try { await api(path, method, data); status.value = '处理成功'; await load() } catch (failure) { error.value = failure instanceof Error ? failure.message : '操作失败' } finally { busy.value = false } }
onMounted(async () => { try { await load() } catch (failure) { error.value = failure instanceof Error ? failure.message : '加载失败' } })
</script>
<template>
  <RouterLink to="/admin">返回管理员工作台</RouterLink>
  <section class="card catalog"><h2>停诊替诊审批与排班治理</h2><p v-if="error" role="alert">{{ error }}</p><p v-if="status" role="status">{{ status }}</p><button class="secondary" @click="load">刷新治理记录</button>
    <form @submit.prevent="action(`/admin/catalog/departments/${departmentId}/head/${headId}`, undefined, 'PUT')"><h3>任命科主任</h3>
      <label for="head-department">科室</label><select id="head-department" v-model="departmentId" required><option value="">请选择</option><option v-for="item in departments" :key="item.id" :value="item.id">{{ item.name }}</option></select>
      <label for="head-doctor">科主任医生</label><select id="head-doctor" v-model="headId" required><option value="">请选择</option><option v-for="item in doctors.filter(item => item.departmentId === departmentId && item.credentialStatus === 'CERTIFIED')" :key="item.id" :value="item.id">{{ item.displayName }} · {{ item.id }}</option></select><button :disabled="busy">任命科主任</button>
    </form>
    <form @submit.prevent="action(`/admin/catalog/schedules/${emergencySchedule}/emergency-stop`, { reason: emergencyReason })"><h3>紧急停诊</h3><p>无需医生提前申请；填写理由，取消受影响预约并生成退款。已进入现场候诊/接诊须先人工处置。</p>
      <label for="emergency-schedule">紧急停诊排班</label><select id="emergency-schedule" v-model="emergencySchedule" required><option value="">请选择</option><option v-for="item in schedules.filter(item => item.state !== 'CANCELLED')" :key="item.id" :value="item.id">{{ item.doctorName }} {{ item.clinicDate }} {{ item.session }}</option></select>
      <label for="emergency-reason">紧急处置理由</label><textarea id="emergency-reason" v-model="emergencyReason" required maxlength="1000"></textarea><button :disabled="busy">执行紧急停诊</button>
    </form>
    <h3>申请与影响记录</h3><article v-for="item in requests" :key="item.id" class="notice"><p>{{ item.id }} · {{ item.state }} · {{ item.reason }}</p><p>排班 {{ item.scheduleId }} · 原医生 {{ item.doctorId }} · 替诊 {{ item.substituteDoctorId || '无' }}</p>
      <button type="button" class="secondary" @click="loadImpacts(item.id)">查看受影响预约</button>
      <ul v-if="impacts[item.id]"><li v-for="patient in impacts[item.id]" :key="patient.appointmentId">预约 {{ patient.appointmentId }} · {{ patient.state }}
        <template v-if="item.state === 'ADMIN_REVIEW' && item.substituteDoctorId && patient.state === 'CONFIRMED'"><label :for="'patient-choice-' + patient.appointmentId">人工确认患者选择</label><select :id="'patient-choice-' + patient.appointmentId" v-model="emergencyChoices[patient.appointmentId]"><option value="">尚未取得患者选择</option><option value="ACCEPT">患者明确接受</option><option value="REJECT">患者明确拒绝，取消退款</option></select></template>
      </li></ul>
      <template v-if="item.state === 'ADMIN_REVIEW'">
        <label :for="'admin-reason-' + item.id">复核理由</label><textarea :id="'admin-reason-' + item.id" v-model="reasons[item.id]" required maxlength="1000"></textarea>
        <button :disabled="busy || !reasons[item.id]?.trim()" @click="action(`/admin/catalog/suspensions/${item.id}/review`, { approve: true, reason: reasons[item.id] })">批准生效</button>
        <button :disabled="busy || !reasons[item.id]?.trim()" class="secondary" @click="action(`/admin/catalog/suspensions/${item.id}/review`, { approve: false, reason: reasons[item.id] })">驳回申请</button>
        <button v-if="item.substituteDoctorId" :disabled="busy || !reasons[item.id]?.trim() || !impacts[item.id]" @click="emergencySubstitute(item)">记录患者选择并紧急办理替诊</button>
        <template v-if="!item.substituteDoctorId"><p>科主任本人申请回避时，管理员可组织替诊方案：</p>
          <label :for="'admin-plan-' + item.id">回避处置方案</label><select :id="'admin-plan-' + item.id" v-model="plans[item.id]"><option value="SUBSTITUTE">安排替诊</option><option value="STOP">直接停诊</option></select>
          <label :for="'admin-substitute-' + item.id">回避替诊医生</label><select :id="'admin-substitute-' + item.id" v-model="substitutes[item.id]"><option v-for="doctor in doctors.filter(doctor => doctor.departmentId === item.departmentId && doctor.id !== item.doctorId && doctor.credentialStatus === 'CERTIFIED')" :key="doctor.id" :value="doctor.id">{{ doctor.displayName }} · {{ doctor.id }}</option></select>
          <button :disabled="busy || !reasons[item.id]?.trim()" @click="action(`/admin/catalog/suspensions/${item.id}/plan`, { plan: plans[item.id] || 'SUBSTITUTE', substituteDoctorId: substitutes[item.id], reason: reasons[item.id] })">提交回避方案</button>
        </template>
      </template>
    </article>
  </section>
</template>
