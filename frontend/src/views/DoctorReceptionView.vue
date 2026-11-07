<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
import { useNotificationRefresh } from '../useNotificationRefresh'
interface QueueEntry { appointmentId: string; patientIdentifier: string; clinicDate: string; session: string; serviceMode: string; source: string; queueGroup: number; queuedAt: number; state: string; restorationCount: number }
const queue = ref<QueueEntry[]>([]), error = ref(''), busy = ref(false)
const stateLabels: Record<string, string> = { WAITING: '候诊中', CALLED: '已叫号', IN_CONSULTATION: '接诊中', MISSED: '已过号', COMPLETED: '已结束' }
const groupLabels: Record<number, string> = { 1: '预约优先', 2: '现场挂号', 3: '恢复排队' }
async function load() { busy.value = true; error.value = ''; try { queue.value = await api<QueueEntry[]>('/doctor/reception') } catch (failure) { error.value = failure instanceof Error ? failure.message : '候诊队列加载失败，请重试' } finally { busy.value = false } }
async function action(id: string, operation: 'call' | 'start' | 'miss') { busy.value = true; error.value = ''; try { await api(`/doctor/reception/${id}/${operation}`, 'POST'); await load() } catch (failure) { error.value = failure instanceof Error ? failure.message : '操作失败，请重试'; busy.value = false } }
onMounted(load)
useNotificationRefresh(load)
</script>
<template>
  <RouterLink to="/doctor">返回工作台</RouterLink>
  <section class="card catalog"><h2>候诊与接诊</h2><p v-if="error" role="alert" class="notice">{{ error }}</p><p class="hint">队列顺序：预约优先、现场挂号、恢复排队；每组按签到或恢复时间排列。</p><p v-if="busy && !queue.length" class="hint">队列加载中…</p><p v-if="!busy && !queue.length" class="hint">暂无候诊记录。</p>
    <ul class="resource-list"><li v-for="entry in queue" :key="entry.appointmentId"><strong>{{ entry.patientIdentifier }}</strong><p>{{ entry.clinicDate }} {{ entry.session === 'AM' ? '上午' : '下午' }} · {{ entry.serviceMode === 'ONLINE' ? '线上' : '线下' }}</p><p>候诊组：{{ groupLabels[entry.queueGroup] ?? entry.queueGroup }} · 状态：{{ stateLabels[entry.state] ?? entry.state }}</p><p>预约登记：{{ entry.appointmentId }} · 恢复次数：{{ entry.restorationCount }}</p><div class="catalog-links"><RouterLink v-if="entry.state === 'IN_CONSULTATION' || entry.state === 'COMPLETED'" :to="`/doctor/clinical/${entry.appointmentId}`">打开问诊记录</RouterLink><button v-if="entry.state === 'WAITING'" type="button" :disabled="busy" @click="action(entry.appointmentId, 'call')">叫号</button><button v-if="entry.state === 'CALLED'" type="button" :disabled="busy" @click="action(entry.appointmentId, 'start')">开始接诊</button><button v-if="entry.state === 'CALLED'" type="button" class="secondary" :disabled="busy" @click="action(entry.appointmentId, 'miss')">标记过号</button></div></li></ul>
  </section>
</template>
