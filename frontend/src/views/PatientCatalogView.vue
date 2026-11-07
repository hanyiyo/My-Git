<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { api } from '../api'
import type { Hospital, Department, Doctor, Schedule } from '../catalog'
const hospital = ref<Hospital | null>(null), departments = ref<Department[]>([]), doctors = ref<Doctor[]>([]), schedules = ref<Schedule[]>([])
const departmentId = ref(''), doctorId = ref(''), date = ref(''), mode = ref(''), error = ref(''), status = ref(''), busy = ref(false), registering = ref('')
const availableDoctors = computed(() => doctors.value.filter(value => !departmentId.value || value.departmentId === departmentId.value))
watch(departmentId, () => { doctorId.value = '' })
async function search() {
  error.value = ''; status.value = ''; busy.value = true
  try {
    const query = new URLSearchParams()
    for (const [key, value] of Object.entries({ departmentId: departmentId.value, doctorId: doctorId.value, date: date.value, mode: mode.value })) if (value) query.set(key, value)
    const [information, units, people, sessions] = await Promise.all([
      api<Hospital>('/catalog/hospital'), api<Department[]>('/catalog/departments'),
      api<Doctor[]>('/catalog/doctors'), api<Schedule[]>('/catalog/schedules?' + query)
    ])
    hospital.value = information; departments.value = units; doctors.value = people; schedules.value = sessions
  } catch (failure) { schedules.value = []; error.value = failure instanceof Error ? failure.message : '查询失败，请重试' }
  finally { busy.value = false }
}
async function register(item: Schedule, source: 'ADVANCE' | 'WALK_IN') {
  error.value = ''; status.value = ''; registering.value = item.id
  try {
    await api(`/patient/appointments`, 'POST', { scheduleId: item.id, source, idempotencyKey: crypto.randomUUID() })
    status.value = source === 'WALK_IN' ? '现场挂号成功' : '预约登记成功'
  } catch (failure) { error.value = failure instanceof Error ? failure.message : '登记失败，请重试' }
  finally { registering.value = '' }
}
onMounted(search)
</script>
<template>
  <RouterLink to="/patient">返回工作台</RouterLink>
  <section class="card catalog"><h2>科室、医生与有效排班</h2><template v-if="hospital"><h3>{{ hospital.name }}</h3><p>{{ hospital.address }}</p><p class="hint">{{ hospital.description }}</p></template>
    <p v-if="error" role="alert" class="notice">{{ error }}</p><p v-if="status" role="status" class="notice">{{ status }}</p>
    <form @submit.prevent="search"><label for="query-department">查询科室</label><select id="query-department" v-model="departmentId"><option value="">全部有效科室</option><option v-for="item in departments" :key="item.id" :value="item.id">{{ item.name }}</option></select>
      <label for="query-doctor">查询医生</label><select id="query-doctor" v-model="doctorId"><option value="">全部已认证医生</option><option v-for="item in availableDoctors" :key="item.id" :value="item.id">{{ item.displayName }} · {{ item.title }}</option></select>
      <label for="query-date">查询日期</label><input id="query-date" v-model="date" type="date">
      <label for="query-mode">查询服务方式</label><select id="query-mode" v-model="mode"><option value="">线上及线下</option><option value="ONLINE">线上</option><option value="OFFLINE">线下</option></select>
      <button :disabled="busy">{{ busy ? '查询中…' : '查询排班' }}</button>
    </form>
    <p class="hint">医院时区：中国标准时间。默认展示未来7天，也包含当天仍开放的现场号源；可指定日期查询。提前预约在开诊时停止；现场挂号仅限线下门诊，截止到结束前15分钟。</p>
    <p v-if="!busy && !schedules.length">暂无符合条件的可预约排班。</p>
    <ul class="resource-list"><li v-for="item in schedules" :key="item.id"><strong>{{ item.doctorName }} · {{ item.departmentName }}</strong><p>{{ item.clinicDate }} {{ item.session === 'AM' ? '上午 08:00–12:00' : '下午 14:00–18:00' }} · {{ item.serviceMode === 'ONLINE' ? '线上' : '线下' }}</p><p>可用号源：{{ item.availableSlots }} · 费用：¥{{ (item.feeMinor / 100).toFixed(2) }}</p><p>{{ item.advanceAvailable ? '可提前预约' : item.walkInAvailable ? '可现场挂号' : '当前不可登记' }}</p><div class="catalog-links"><button v-if="item.advanceAvailable" type="button" :disabled="registering !== ''" @click="register(item, 'ADVANCE')">{{ registering === item.id ? '登记中…' : '提前预约' }}</button><button v-if="item.walkInAvailable" type="button" class="secondary" :disabled="registering !== ''" @click="register(item, 'WALK_IN')">现场挂号</button></div></li></ul>
  </section>
</template>
