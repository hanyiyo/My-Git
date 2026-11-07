<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
import { credentialLabels, type Doctor, type Schedule } from '../catalog'
const profile = ref<Doctor | null>(null), schedules = ref<Schedule[]>([]), error = ref('')
onMounted(async () => {
  try { [profile.value, schedules.value] = await Promise.all([api<Doctor>('/doctor/catalog/profile'), api<Schedule[]>('/doctor/catalog/schedules')]) }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '资料暂不可用' }
})
</script>
<template>
  <RouterLink to="/doctor">返回工作台</RouterLink>
  <section class="card catalog"><h2>我的认证与排班</h2><p v-if="error" role="alert">{{ error }}</p>
    <template v-if="profile"><h3>{{ profile.displayName }}</h3><p>认证状态：{{ credentialLabels[profile.credentialStatus] ?? profile.credentialStatus }}</p><p>{{ profile.title }}</p><p>所属科室：{{ profile.departmentName ?? '尚未分配科室' }}</p><p class="hint">{{ profile.biography }}</p></template>
    <h3>我的出诊安排</h3><p class="hint">医院时区：中国标准时间。包含已结束或当前不可预约的已发布排班。</p><p v-if="!schedules.length">暂无已发布排班。</p>
    <ul class="resource-list"><li v-for="item in schedules" :key="item.id"><strong>{{ item.departmentName }} · {{ item.clinicDate }}</strong><p>{{ item.session === 'AM' ? '上午 08:00–12:00' : '下午 14:00–18:00' }} · {{ item.serviceMode === 'ONLINE' ? '线上' : '线下' }}</p><p>可用号源：{{ item.availableSlots }} / 总号源：{{ item.capacity }} · 费用：¥{{ (item.feeMinor / 100).toFixed(2) }}</p><p>{{ item.advanceAvailable || item.walkInAvailable ? '当前开放' : '当前不可预约' }}</p></li></ul>
  </section>
</template>
