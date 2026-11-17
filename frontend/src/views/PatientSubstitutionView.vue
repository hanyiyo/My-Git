<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
interface Choice { id: string; appointmentId: string; substituteDoctorId: string; state: string; deadlineAt: number; reason: string | null }
const choices = ref<Choice[]>([]), error = ref('')
async function load() { try { choices.value = await api<Choice[]>('/patient/substitution-choices') } catch (failure) { error.value = failure instanceof Error ? failure.message : '替诊通知加载失败' } }
onMounted(load)
</script>
<template>
  <RouterLink to="/patient">返回工作台</RouterLink>
  <section class="card catalog"><h2>替诊通知</h2><p v-if="error" role="alert" class="notice">{{ error }}</p><p class="hint">替诊审批通过后，预约会自动转至替诊医生。系统只发送通知，不要求患者确认；如需取消，请回到“我的挂号”主动取消。</p><ul v-if="choices.length" class="resource-list"><li v-for="choice in choices" :key="choice.id"><p>预约：{{ choice.appointmentId }} · 替诊医生：{{ choice.substituteDoctorId }}</p><p>状态：已自动迁移</p><RouterLink to="/patient/orders">前往我的挂号</RouterLink></li></ul><p v-else class="hint">暂无替诊通知。</p></section>
</template>
