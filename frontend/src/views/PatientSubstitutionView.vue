<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
interface Choice { id: string; appointmentId: string; substituteDoctorId: string; state: string; deadlineAt: number; reason: string | null }
const choices = ref<Choice[]>([]), error = ref(''), busy = ref(false)
async function load() { try { choices.value = await api<Choice[]>('/patient/substitution-choices') } catch (failure) { error.value = failure instanceof Error ? failure.message : '替诊选择加载失败' } }
async function choose(choice: Choice, accepted: boolean) { busy.value = true; error.value = ''; try { await api(`/patient/substitution-choices/${choice.id}`, 'POST', { accepted, reason: accepted ? '患者接受替诊' : '患者拒绝替诊' }); await load() } catch (failure) { error.value = failure instanceof Error ? failure.message : '替诊选择提交失败' } finally { busy.value = false } }
onMounted(load)
</script>
<template>
  <RouterLink to="/patient">返回工作台</RouterLink>
  <section class="card catalog"><h2>替诊确认</h2><p v-if="error" role="alert" class="notice">{{ error }}</p><p v-if="!choices.length" class="hint">暂无待确认替诊。</p><ul class="resource-list"><li v-for="choice in choices" :key="choice.id"><p>预约：{{ choice.appointmentId }} · 替诊医生：{{ choice.substituteDoctorId }}</p><p>确认截止：{{ new Date(choice.deadlineAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) }}</p><div class="catalog-links"><button :disabled="busy" @click="choose(choice, true)">接受替诊</button><button class="secondary" :disabled="busy" @click="choose(choice, false)">拒绝并退款</button></div></li></ul></section>
</template>
