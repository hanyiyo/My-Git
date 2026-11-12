<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '../patient-home-prototype/PrototypeIcon.vue'
import { submitSuspension, useReceptionDemo } from './reception-demo'
import { applicationLabels, dateLabel, dateTimeLabel, isPendingApplication, modeLabel, periodLabel, placeLabel, scheduleApplication, scheduleNumbers, scheduleStart, scheduleStatus, scheduleTime, suspensionBlock, type SuspensionApplication } from './schedule-data'
import type { DemoRole } from './data'
import './schedule-workspace.css'

const props = defineProps<{ role: DemoRole }>()
defineEmits<{ return: [] }>()
const route = useRoute(), router = useRouter()
const { state, now, error, warning, busy } = useReceptionDemo()
const schedule = computed(() => state.value.schedules.find(s => s.id === route.query.schedule))
const application = computed(() => schedule.value ? scheduleApplication(schedule.value, state.value.applications) : undefined)
const blocked = computed(() => schedule.value ? suspensionBlock(schedule.value, state.value.applications, now.value) : '')
const applications = computed(() => [...state.value.applications].sort((a, b) => b.submittedAt - a.submittedAt))
const reason = ref(''), localError = ref(''), confirmed = ref(false), confirmDialog = ref<HTMLDialogElement>()
let opener: HTMLElement | null = null
const deadline = computed(() => schedule.value ? Math.min(now.value + 7 * 24 * 3600000, scheduleStart(schedule.value) - 24 * 3600000) : 0)
const affected = computed(() => schedule.value ? scheduleNumbers(schedule.value, state.value.visits).registered : 0)
watch(() => schedule.value?.id, () => { reason.value = ''; localError.value = ''; confirmed.value = false })
function viewApplication(a: SuspensionApplication) { void router.push({ query: { ...route.query, schedule: a.scheduleId } }) }
async function review() {
  localError.value = ''
  if (!reason.value.trim() || reason.value.trim().length > 500) { localError.value = '请填写 1–500 字的停诊原因。'; return }
  if (blocked.value) { localError.value = blocked.value; return }
  opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
  confirmed.value = false; confirmDialog.value?.showModal(); await nextTick()
  confirmDialog.value?.querySelector<HTMLButtonElement>('button')?.focus()
}
async function submit() {
  if (!schedule.value || !confirmed.value) return
  if (await submitSuspension(schedule.value.id, reason.value, props.role)) { confirmDialog.value?.close(); reason.value = '' }
}
function closed() { opener?.focus() }
</script>

<template>
  <section class="ds-applications" aria-label="停诊申请处理">
    <div class="ds-application-banner"><span class="ds-mode-icon"><Icon name="file" /></span><div><h2>让每一次变动，都有清晰的交接</h2><p>按场次提出停诊申请，在这里查看处理进度。</p></div><button class="ds-return" @click="$emit('return')"><Icon name="calendar" />返回我的排班</button></div>
    <p v-if="warning || error || localError" class="ds-alert" role="alert">{{ localError || error || warning }}</p>
    <p v-if="route.query.schedule && !schedule" role="alert" class="ds-alert">未找到对应出诊安排，请返回我的排班重新选择。</p>
    <div v-if="schedule" class="ds-application-layout">
      <section class="ds-application-form" aria-label="本场停诊申请">
        <div class="ds-section-heading"><div><span class="dh-eyebrow">SUSPENSION REQUEST</span><h2>{{ application ? '申请记录与出诊安排' : '发起停诊申请' }}</h2></div><span class="ds-status">{{ scheduleStatus(schedule, state.applications, now) }}</span></div>
        <dl class="ds-prefill"><div><dt>出诊医生</dt><dd>许知遥 · 内科</dd></div><div><dt>排班日期</dt><dd>{{ dateLabel(schedule.date) }}</dd></div><div><dt>出诊时段</dt><dd>{{ periodLabel(schedule) }} {{ scheduleTime(schedule) }}</dd></div><div><dt>服务类型</dt><dd>{{ modeLabel(schedule) }}</dd></div><div><dt>出诊地点</dt><dd>{{ placeLabel(schedule) }}</dd></div><div><dt>号源情况</dt><dd>总 {{ schedule.capacity }} · 已挂号 {{ affected }} · 临时占号 {{ scheduleNumbers(schedule, state.visits).held }}</dd></div></dl>
        <template v-if="application">
          <div class="ds-submitted"><Icon name="shield" /><div><strong>{{ applicationLabels[application.status] }}</strong><p>{{ isPendingApplication(application) ? '申请已保存，新增挂号已暂停，已付款患者预约仍保留。审批完成前，请按原安排接诊。' : application.note || '请查看下方处理进度。' }}</p></div></div>
          <dl class="ds-request-meta"><div><dt>申请编号</dt><dd>{{ application.id }}</dd></div><div><dt>提交时间</dt><dd>{{ dateTimeLabel(application.submittedAt) }}</dd></div><div><dt>审批截止</dt><dd>{{ dateTimeLabel(application.deadline) }}</dd></div><div><dt>受影响预约</dt><dd>{{ application.affected }} 人（提交时快照）</dd></div><div><dt>停诊原因</dt><dd class="ds-reason-text">{{ application.reason }}</dd></div></dl>
        </template>
        <form v-if="!application || !isPendingApplication(application) && application.status !== 'APPROVED'" @submit.prevent="review">
          <div v-if="blocked" class="ds-notice ds-blocked"><Icon name="info" />{{ blocked }}</div>
          <template v-else><label for="ds-reason">停诊原因 <span class="ds-required">*</span></label><textarea id="ds-reason" v-model="reason" :disabled="busy" maxlength="500" rows="5" placeholder="请说明本场无法出诊的具体原因，便于安排后续处理。" aria-required="true"></textarea><p class="ds-text-count">{{ reason.length }} / 500</p><p class="ds-notice">本次审批最晚须于 {{ dateTimeLabel(deadline) }} 完成。提交不代表停诊已经生效。</p><button type="submit" class="dh-primary" :disabled="busy || !!warning">核对并提交申请<Icon name="arrow" /></button></template>
        </form>
      </section>
      <aside class="ds-progress-panel" aria-label="申请进度">
        <h2>{{ application ? '本次处理进度' : '申请会怎样处理' }}</h2>
        <ol class="ds-progress"><li :class="{ done: application }"><span>1</span><div><strong>医生提交申请</strong><p>{{ application ? dateTimeLabel(application.submittedAt) : '填写原因并核对受影响预约' }}</p></div></li><li :class="{ current: application?.status === 'PENDING_HEAD', done: application?.status === 'PENDING_ADMIN' }"><span>2</span><div><strong>{{ (application?.applicantRole ?? role) === 'head' ? '本人申请回避科主任审核' : '科主任审核方案' }}</strong><p>{{ (application?.applicantRole ?? role) === 'head' ? '交管理员处理本人申请' : '选择直接停诊，或安排替诊' }}</p></div></li><li :class="{ current: application?.status === 'PENDING_ADMIN' }"><span>3</span><div><strong>管理员最终复核</strong><p>替诊方案须先由替诊医生确认同意</p></div></li><li :class="{ current: application?.status === 'EXPIRED' || application?.status === 'REJECTED', done: application?.status === 'APPROVED' }"><span>4</span><div><strong>{{ application && !isPendingApplication(application) ? applicationLabels[application.status] : '结果生效并通知' }}</strong><p>{{ application?.status === 'EXPIRED' ? '申请过期，原出诊安排保留' : '最终通过后才停诊或迁移预约' }}</p></div></li></ol>
        <div class="ds-help"><Icon name="info" /><p>本轮演示支持申请提交和进度查看。科主任审批、替诊确认、管理员复核及患者退款迁移将在后续阶段完善。</p></div>
      </aside>
    </div>
    <section class="ds-application-list" aria-label="我的停诊申请"><div class="ds-section-heading"><div><span class="dh-eyebrow">MY REQUESTS</span><h2>我的停诊申请</h2></div><span>{{ applications.length }} 项</span></div><button v-for="a in applications" :key="a.id" class="ds-request-row" @click="viewApplication(a)"><span class="ds-request-icon"><Icon name="file" /></span><span><strong>{{ state.schedules.find(s => s.id === a.scheduleId)?.date }} · {{ state.schedules.find(s => s.id === a.scheduleId)?.period === 'am' ? '上午' : '下午' }}停诊申请</strong><small>{{ a.reason }}</small></span><span class="ds-request-row-status"><span :class="['ds-status', { warn: isPendingApplication(a) }]">{{ applicationLabels[a.status] }}</span><small>截止 {{ dateTimeLabel(a.deadline) }}</small></span><Icon name="chevron" /></button></section>
    <dialog ref="confirmDialog" class="ds-confirm-dialog" aria-label="核对停诊申请" @close="closed">
      <span class="dh-eyebrow">CHECK BEFORE SUBMITTING</span><h2>确认本场停诊申请</h2><p v-if="schedule">{{ schedule.date }} · {{ periodLabel(schedule) }} {{ scheduleTime(schedule) }} · {{ modeLabel(schedule) }}</p><div class="ds-confirm-impact"><strong>将影响 {{ affected }} 位已挂号患者</strong><p>提交后暂停新增挂号并解除未付款临时占号，已付款预约保留。最终审批通过前仍需按原安排接诊。</p></div><p class="ds-reason-text">申请原因：{{ reason }}</p><p>审批截止：{{ dateTimeLabel(deadline) }}</p><label class="ds-confirm-checkbox"><input v-model="confirmed" type="checkbox">我已核对场次和影响，知晓提交不代表停诊生效</label><p v-if="error" role="alert" class="ds-alert">{{ error }} 原因已保留，可重试。</p><div class="ds-confirm-actions"><button class="ds-secondary" @click="confirmDialog?.close()">返回修改</button><button class="dh-primary" :disabled="!confirmed || busy" @click="submit">{{ busy ? '正在保存…' : '确认提交' }}</button></div>
    </dialog>
  </section>
</template>
