<script setup lang="ts">
import { computed } from 'vue'
import Icon from '../patient-home-prototype/PrototypeIcon.vue'
import { useReceptionDemo } from './reception-demo'
import { applicationLabels, demoDate, isPendingApplication, modeLabel, periodLabel, placeLabel, scheduleApplication, scheduleEnd, scheduleNumbers, scheduleStart, scheduleStatus, scheduleTime, suspensionBlock, type DemoSchedule } from './schedule-data'

const props = defineProps<{ schedule: DemoSchedule; compact?: boolean }>()
defineEmits<{ apply: [id: string]; reception: [group: 'am' | 'pm'] }>()
const { state, now, active } = useReceptionDemo()
const numbers = computed(() => scheduleNumbers(props.schedule, state.value.visits))
const application = computed(() => scheduleApplication(props.schedule, state.value.applications))
const pending = computed(() => application.value && isPendingApplication(application.value))
const blocked = computed(() => suspensionBlock(props.schedule, state.value.applications, now.value))
const status = computed(() => scheduleStatus(props.schedule, state.value.applications, now.value))
const today = computed(() => props.schedule.date === demoDate)
const canReception = computed(() => today.value && props.schedule.status === 'PUBLISHED' && (now.value >= scheduleStart(props.schedule) || !!active.value))
const showReception = computed(() => canReception.value && (now.value < scheduleEnd(props.schedule) || numbers.value.waiting > 0 || !!active.value))
</script>

<template>
  <article :class="['ds-session', schedule.mode, { 'ds-session-compact': compact, 'ds-session-closed': schedule.status !== 'PUBLISHED' }]" :aria-label="`${schedule.date} ${periodLabel(schedule)}出诊详情`">
    <header class="ds-session-heading">
      <span class="ds-mode-icon"><Icon :name="schedule.mode === 'online' ? 'chat' : 'hospital'" /></span>
      <div><h3>{{ periodLabel(schedule) }} · {{ modeLabel(schedule) }}</h3><p>{{ scheduleTime(schedule) }}</p></div>
      <span :class="['ds-status', { live: status === '出诊中', warn: pending }]">{{ status }}</span>
    </header>
    <p class="ds-place"><Icon name="pin" />{{ placeLabel(schedule) }}<span v-if="schedule.substituteFor" class="ds-substitute">替 {{ schedule.substituteFor }} 医生出诊</span></p>
    <p v-if="schedule.replacement" class="ds-notice">本场由 {{ schedule.replacement }} 医生接诊，原预约已迁移；不再占用你的出诊时间。</p>
    <div class="ds-source-numbers" aria-label="号源情况">
      <div><span>总号源</span><strong>{{ schedule.capacity }}</strong></div>
      <div><span>已挂号</span><strong>{{ numbers.registered }}</strong></div>
      <div><span>临时占号</span><strong>{{ numbers.held }}</strong></div>
      <div><span>剩余号源</span><strong>{{ numbers.remaining }}</strong></div>
    </div>
    <div class="ds-source-bar" aria-hidden="true"><i :style="{ width: `${numbers.registered / schedule.capacity * 100}%` }"></i><b :style="{ width: `${numbers.held / schedule.capacity * 100}%` }"></b></div>
    <p class="ds-source-caption">{{ schedule.status !== 'PUBLISHED' || pending || now >= scheduleEnd(schedule) - 15 * 60000 || !numbers.remaining ? '当前不可新增挂号' : '剩余名额可用于挂号' }}<span>蓝色已挂号 · 浅橙临时占号</span></p>
    <div v-if="today" class="ds-reception-numbers" aria-label="接诊进度"><span>候诊 <b>{{ numbers.waiting }}</b></span><span>就诊中 <b>{{ numbers.active }}</b></span><span>已完成 <b>{{ numbers.completed }}</b></span></div>
    <p v-if="pending" class="ds-notice"><Icon name="info" />{{ applicationLabels[application!.status] }} · 已付款预约保留，仍须按原安排接诊。</p>
    <p v-else-if="application && !compact" class="ds-notice">最近申请：{{ applicationLabels[application.status] }}</p>
    <footer class="ds-session-actions">
      <button v-if="showReception" type="button" class="dh-primary" @click="$emit('reception', schedule.period)">{{ active ? '继续接诊' : '进入接诊工作台' }}<Icon name="arrow" /></button>
      <button v-if="application" class="ds-apply-link" type="button" @click="$emit('apply', schedule.id)">查看申请<Icon name="chevron" /></button>
      <button v-if="!pending && application?.status !== 'APPROVED'" type="button" class="ds-apply-link" :disabled="!!blocked" :title="blocked" @click="$emit('apply', schedule.id)"><Icon name="file" />申请停诊</button>
    </footer>
    <p v-if="blocked && !pending && application?.status !== 'APPROVED'" class="ds-disabled-reason">{{ blocked }}</p>
  </article>
</template>
