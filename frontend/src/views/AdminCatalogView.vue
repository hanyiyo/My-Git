<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api } from '../api'
import type { Hospital, Department, Doctor, Schedule } from '../catalog'
const hospital = reactive<Hospital>({ name: '', address: '', description: '' })
const departments = ref<Department[]>([]), doctors = ref<Doctor[]>([]), schedules = ref<Schedule[]>([])
const department = reactive({ id: '', name: '', description: '', active: true })
const doctor = reactive({ id: '', displayName: '', title: '', biography: '', departmentId: '', credentialStatus: 'PENDING' })
const schedule = reactive({ doctorId: '', departmentId: '', clinicDate: '', session: 'AM', serviceMode: 'ONLINE', capacity: 20, feeMinor: 0 })
const message = ref(''), error = ref(''), busy = ref(false)
async function load() {
  const [information, units, people, sessions] = await Promise.all([
    api<Hospital>('/catalog/hospital'), api<Department[]>('/admin/catalog/departments'),
    api<Doctor[]>('/admin/catalog/doctors'), api<Schedule[]>('/admin/catalog/schedules')
  ])
  Object.assign(hospital, information); departments.value = units; doctors.value = people; schedules.value = sessions
}
function editDepartment() {
  const value = departments.value.find(value => value.id === department.id)
  if (value) Object.assign(department, value)
  else Object.assign(department, { id: '', name: '', description: '', active: true })
}
function editDoctor() {
  const value = doctors.value.find(value => value.id === doctor.id)
  if (value) Object.assign(doctor, { ...value, departmentId: value.departmentId ?? '' })
}
async function save(action: () => Promise<unknown>, success: string) {
  busy.value = true; error.value = ''; message.value = ''
  try { await action(); await load(); message.value = success }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '保存失败，请重试' }
  finally { busy.value = false }
}
function saveDepartment() {
  return save(async () => {
    const { id, ...body } = department
    const result = await api<Department>('/admin/catalog/departments' + (id ? '/' + id : ''), id ? 'PUT' : 'POST', body)
    Object.assign(department, result)
  }, '科室已保存')
}
function saveDoctor() {
  return save(async () => {
    const { id, displayName, title, biography, departmentId, credentialStatus } = doctor
    await api('/admin/catalog/doctors/' + id, 'PUT', { displayName, title, biography, departmentId, credentialStatus })
  }, '医生资料已保存')
}
onMounted(() => load().catch(failure => { error.value = failure instanceof Error ? failure.message : '目录暂不可用' }))
</script>
<template>
  <RouterLink to="/admin">返回工作台</RouterLink>
  <section class="card catalog"><h2>医院目录与排班管理</h2><p class="hint">仅维护虚构演示资料。排班发布后保留时间、费用和号源快照；线上线下不能占用医生的同一个半天。</p>
    <p v-if="message" role="status" class="notice">{{ message }}</p><p v-if="error" role="alert" class="notice">{{ error }}</p>
    <form @submit.prevent="save(() => api('/admin/catalog/hospital', 'PUT', hospital), '医院信息已保存')"><h3>医院信息</h3>
      <label for="hospital-name">医院名称</label><input id="hospital-name" v-model="hospital.name" required maxlength="120">
      <label for="hospital-address">医院地址</label><input id="hospital-address" v-model="hospital.address" required maxlength="300">
      <label for="hospital-description">医院服务说明</label><textarea id="hospital-description" v-model="hospital.description" maxlength="2000" />
      <button :disabled="busy">保存医院信息</button>
    </form>
    <form @submit.prevent="saveDepartment"><h3>科室</h3>
      <label for="department-edit">编辑已有科室</label><select id="department-edit" v-model="department.id" @change="editDepartment"><option value="">新建科室</option><option v-for="item in departments" :key="item.id" :value="item.id">{{ item.name }}</option></select>
      <label for="department-name">科室名称</label><input id="department-name" v-model="department.name" required maxlength="120">
      <label for="department-description">科室说明</label><textarea id="department-description" v-model="department.description" maxlength="2000" />
      <label for="department-active">科室状态</label><select id="department-active" v-model="department.active"><option :value="true">有效</option><option :value="false">停用</option></select>
      <button :disabled="busy">保存科室</button>
    </form>
    <form @submit.prevent="saveDoctor"><h3>医生资料与认证</h3><p class="hint">医生先通过注册入口创建账户。未认证或禁用的医生不能发布排班。</p>
      <label for="doctor-account">医生账户</label><select id="doctor-account" v-model="doctor.id" required @change="editDoctor"><option value="" disabled>请选择医生</option><option v-for="item in doctors" :key="item.id" :value="item.id">{{ item.displayName }}</option></select>
      <label for="doctor-name">医生姓名</label><input id="doctor-name" v-model="doctor.displayName" required maxlength="120">
      <label for="doctor-title">医生职称</label><input id="doctor-title" v-model="doctor.title" required maxlength="120">
      <label for="doctor-biography">医生简介</label><textarea id="doctor-biography" v-model="doctor.biography" maxlength="2000" />
      <label for="doctor-department">医生所属科室</label><select id="doctor-department" v-model="doctor.departmentId" required><option value="" disabled>请选择有效科室</option><option v-for="item in departments.filter(value => value.active)" :key="item.id" :value="item.id">{{ item.name }}</option></select>
      <label for="doctor-status">认证状态</label><select id="doctor-status" v-model="doctor.credentialStatus"><option value="PENDING">待审核</option><option value="CERTIFIED">已认证</option><option value="REJECTED">未通过</option></select>
      <button :disabled="busy || !doctor.id">保存医生资料</button>
    </form>
    <form @submit.prevent="save(() => api('/admin/catalog/schedules', 'POST', schedule), '排班已发布')"><h3>发布出诊安排</h3>
      <label for="schedule-doctor">排班医生</label><select id="schedule-doctor" v-model="schedule.doctorId" required @change="schedule.departmentId = doctors.find(value => value.id === schedule.doctorId)?.departmentId ?? ''"><option value="" disabled>请选择已认证医生</option><option v-for="item in doctors.filter(value => value.enabled && value.credentialStatus === 'CERTIFIED')" :key="item.id" :value="item.id">{{ item.displayName }}</option></select>
      <label for="schedule-department">排班科室</label><select id="schedule-department" v-model="schedule.departmentId" required><option value="" disabled>请选择有效科室</option><option v-for="item in departments.filter(value => value.active)" :key="item.id" :value="item.id">{{ item.name }}</option></select>
      <label for="schedule-date">出诊日期</label><input id="schedule-date" v-model="schedule.clinicDate" type="date" required>
      <label for="schedule-session">门诊时段</label><select id="schedule-session" v-model="schedule.session"><option value="AM">上午 08:00–12:00</option><option value="PM">下午 14:00–18:00</option></select>
      <label for="schedule-mode">服务方式</label><select id="schedule-mode" v-model="schedule.serviceMode"><option value="ONLINE">线上</option><option value="OFFLINE">线下</option></select>
      <label for="schedule-capacity">号源数量</label><input id="schedule-capacity" v-model.number="schedule.capacity" type="number" min="1" max="500" step="1" required>
      <label for="schedule-fee">挂号费用（分）</label><input id="schedule-fee" v-model.number="schedule.feeMinor" type="number" min="0" max="100000000" step="1" required>
      <button :disabled="busy">发布排班</button>
    </form>
    <h3>已发布排班</h3><p v-if="!schedules.length">暂无排班。</p>
    <ul class="resource-list"><li v-for="item in schedules" :key="item.id"><strong>{{ item.doctorName }} · {{ item.departmentName }}</strong><p>{{ item.clinicDate }} {{ item.session === 'AM' ? '上午' : '下午' }} · {{ item.serviceMode === 'ONLINE' ? '线上' : '线下' }}</p><p>号源：{{ item.availableSlots }}/{{ item.capacity }} · 费用：¥{{ (item.feeMinor / 100).toFixed(2) }}</p></li></ul>
  </section>
</template>
