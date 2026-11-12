<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '../patient-home-prototype/PrototypeIcon.vue'
import { acceptSubstitution, rejectSubstitution, submitSuspension, useReceptionDemo } from './reception-demo'
import { applicationLabels, dateLabel, dateTimeLabel, isPendingApplication, modeLabel, periodLabel, placeLabel, scheduleApplication, scheduleNumbers, scheduleStart, scheduleStatus, scheduleTime, suspensionBlock, type SuspensionApplication } from './schedule-data'
import type { DemoRole } from './data'
import './schedule-workspace.css'

const props = defineProps<{ role: DemoRole }>()
defineEmits<{ return: [] }>()
const route = useRoute(), router = useRouter()
const { state, now, error, warning, busy } = useReceptionDemo()
const schedule = computed(() => state.value.schedules.find(s => s.id === route.query.schedule))
const invitation = computed(() => state.value.invitations.find(i => i.id === route.query.invitation))
const application = computed(() => schedule.value ? scheduleApplication(schedule.value, state.value.applications) : undefined)
const blocked = computed(() => schedule.value ? suspensionBlock(schedule.value, state.value.applications, now.value) : '')
const applications = computed(() => [...state.value.applications].sort((a, b) => b.submittedAt - a.submittedAt))
const reason = ref(''), rejectReason = ref(''), localError = ref(''), confirmed = ref(false), applicationTab = ref<'suspension' | 'invitation' | 'department'>('suspension'), confirmDialog = ref<HTMLDialogElement>(), inviteConfirm = ref<HTMLDialogElement>(), rejectDialog = ref<HTMLDialogElement>()
let opener: HTMLElement | null = null
const deadline = computed(() => schedule.value ? Math.min(now.value + 7 * 24 * 3600000, scheduleStart(schedule.value) - 24 * 3600000) : 0)
const affected = computed(() => schedule.value ? scheduleNumbers(schedule.value, state.value.visits).registered : 0)
watch(() => schedule.value?.id, () => { reason.value = ''; localError.value = ''; confirmed.value = false })
watch(() => invitation.value?.id, () => { rejectReason.value = ''; localError.value = ''; confirmed.value = false })
function backToList() { const { schedule: _schedule, ...query } = route.query; void router.push({ query }) }
function viewApplication(a: SuspensionApplication) { void router.push({ query: { ...route.query, schedule: a.scheduleId } }) }
function viewInvitation(id: string) { void router.push({ query: { ...route.query, invitation: id, schedule: undefined } }) }
function backToApplicationList() { const { schedule: _schedule, invitation: _invitation, ...query } = route.query; void router.push({ query }) }
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
function acceptReview() { if (invitation.value?.status === 'PENDING' && !invitation.value.conflict) { confirmed.value = false; inviteConfirm.value?.showModal() } }
function rejectReview() { rejectReason.value = ''; localError.value = ''; rejectDialog.value?.showModal() }
async function accept() { if (!invitation.value || !confirmed.value) return; if (await acceptSubstitution(invitation.value.id)) inviteConfirm.value?.close() }
async function reject() { if (!invitation.value) return; if (await rejectSubstitution(invitation.value.id, rejectReason.value)) rejectDialog.value?.close() }
</script>

<template>
  <section class="ds-applications" aria-label="申请处理">
    <div class="ds-application-banner"><span class="ds-mode-icon"><Icon :name="invitation ? 'doctor' : 'file'" /></span><span class="ds-banner-title">{{ invitation ? '替诊邀请' : '申请处理' }}</span><div class="ds-application-nav"><button v-if="schedule || invitation" class="ds-return" @click="backToApplicationList"><Icon name="chevron" class="ds-prev" />返回申请列表</button><button class="ds-return" @click="$emit('return')"><Icon name="calendar" />返回我的排班</button></div></div>
    <p v-if="!confirmDialog?.open && (warning || error || localError)" class="ds-alert" role="alert">{{ localError || error || warning }}</p>
    <p v-if="route.query.schedule && !schedule" role="alert" class="ds-alert">未找到对应出诊安排，请返回我的排班重新选择。</p>
    <div v-if="invitation" class="ds-application-layout ds-substitution-layout">
      <section class="ds-application-form" aria-label="替诊邀请详情">
        <div class="ds-section-heading"><div><h2>替诊邀请详情</h2><p>请核对场次和本人排班后处理</p></div><span :class="['ds-status', { warn: invitation.status === 'PENDING' }]">{{ invitation.status === 'PENDING' ? '待你确认' : invitation.status === 'ACCEPTED' ? '待管理员复核' : invitation.status === 'REJECTED' ? '已拒绝' : invitation.status === 'EXPIRED' ? '已过期' : '已撤回' }}</span></div>
        <dl class="ds-prefill"><div><dt>原出诊医生</dt><dd>{{ invitation.originalDoctor }} · 内科</dd></div><div><dt>安排替诊</dt><dd>{{ invitation.arrangedBy }}（科主任）</dd></div><div><dt>科室</dt><dd>{{ invitation.department }}</dd></div><div><dt>日期</dt><dd>{{ dateLabel(invitation.date) }}</dd></div><div><dt>出诊时段</dt><dd>{{ invitation.period === 'am' ? '上午' : '下午' }} {{ invitation.period === 'am' ? '08:00–12:00' : '14:00–18:00' }}</dd></div><div><dt>服务类型</dt><dd>{{ invitation.mode === 'online' ? '线上问诊' : '线下面诊' }}</dd></div><div><dt>出诊地点</dt><dd>{{ invitation.place }}</dd></div><div><dt>受影响预约</dt><dd>{{ invitation.affected }} 人</dd></div></dl>
        <div class="ds-substitution-reason"><strong>替诊原因</strong><p>{{ invitation.reason }}</p></div>
        <div :class="['ds-notice', invitation.conflict ? 'ds-blocked' : '']"><Icon name="info"/><span>{{ invitation.conflict ? '你在该时段已有出诊安排，无法接受此邀请。' : invitation.status === 'PENDING' ? `确认截止：${dateTimeLabel(invitation.deadline)}。接受后仍需等待管理员复核，尚未正式生效。` : invitation.status === 'ACCEPTED' ? '你已同意替诊，当前等待管理员复核，尚未正式生效。' : invitation.status === 'REJECTED' ? `已退回科主任重新安排。拒绝原因：${invitation.rejectionReason}` : '该邀请已失效，原出诊安排保留。' }}</span></div>
        <div v-if="invitation.status === 'PENDING'" class="ds-invitation-actions"><button class="ds-secondary" @click="rejectReview">拒绝替诊</button><button class="dh-primary" :disabled="invitation.conflict || busy" @click="acceptReview">接受替诊<Icon name="arrow"/></button></div>
        <div v-else class="ds-submitted"><Icon name="shield"/><div><strong>本次邀请已处理</strong><p>结果已同步到站内通知和首页待办。</p></div></div>
      </section>
      <aside class="ds-progress-panel" aria-label="替诊处理进度"><h2>处理进度</h2><ol class="ds-progress"><li class="done"><span>1</span><div><strong>科主任安排替诊</strong><p>{{ dateTimeLabel(invitation.createdAt) }}</p></div></li><li :class="{ current: invitation.status === 'PENDING', done: invitation.status !== 'PENDING' }"><span>2</span><div><strong>替诊医生确认</strong><p>{{ invitation.status === 'PENDING' ? '请在截止时间前选择接受或拒绝' : invitation.status === 'REJECTED' ? '已拒绝，退回科主任重新安排' : invitation.status === 'EXPIRED' ? '已过期，原出诊安排保留' : '已接受，等待管理员复核' }}</p></div></li><li :class="{ current: invitation.status === 'ACCEPTED' }"><span>3</span><div><strong>管理员最终复核</strong><p>通过后才会创建替诊排班并迁移有效预约</p></div></li></ol></aside>
    </div>
    <div v-else-if="schedule" class="ds-application-layout">
      <section class="ds-application-form" aria-label="本场停诊申请">
        <div class="ds-section-heading"><div><h2>{{ application ? '申请详情' : '发起停诊申请' }}</h2></div><span class="ds-status">{{ scheduleStatus(schedule, state.applications, now) }}</span></div>
        <dl class="ds-prefill"><div><dt>出诊医生</dt><dd>许知遥 · 内科</dd></div><div><dt>排班日期</dt><dd>{{ dateLabel(schedule.date) }}</dd></div><div><dt>出诊时段</dt><dd>{{ periodLabel(schedule) }} {{ scheduleTime(schedule) }}</dd></div><div><dt>服务类型</dt><dd>{{ modeLabel(schedule) }}</dd></div><div><dt>出诊地点</dt><dd>{{ placeLabel(schedule) }}</dd></div><div><dt>号源情况</dt><dd>总 {{ schedule.capacity }} · 已挂号 {{ affected }} · 临时占号 {{ scheduleNumbers(schedule, state.visits).held }}</dd></div></dl>
        <template v-if="application">
          <div class="ds-submitted"><Icon name="shield" /><div><strong>{{ applicationLabels[application.status] }}</strong><p>{{ isPendingApplication(application) ? '申请已保存，新增挂号已暂停，已付款患者预约仍保留。审批完成前，请按原安排接诊。' : application.note || '请查看下方处理进度。' }}</p></div></div>
          <dl class="ds-request-meta"><div><dt>申请编号</dt><dd>{{ application.id }}</dd></div><div><dt>提交时间</dt><dd>{{ dateTimeLabel(application.submittedAt) }}</dd></div><div><dt>审批截止</dt><dd>{{ dateTimeLabel(application.deadline) }}</dd></div><div><dt>受影响预约</dt><dd>{{ application.affected }} 人（提交时）</dd></div><div><dt>停诊原因</dt><dd class="ds-reason-text">{{ application.reason }}</dd></div></dl>
        </template>
        <form v-if="!application || !isPendingApplication(application) && application.status !== 'APPROVED'" @submit.prevent="review">
          <div v-if="blocked" class="ds-notice ds-blocked"><Icon name="info" />{{ blocked }}</div>
          <template v-else><label for="ds-reason">停诊原因 <span class="ds-required">*</span></label><textarea id="ds-reason" v-model="reason" :disabled="busy" maxlength="500" rows="5" placeholder="请说明本场无法出诊的具体原因，便于安排后续处理。" aria-required="true"></textarea><p class="ds-text-count">{{ reason.length }} / 500</p><p class="ds-notice">本次审批最晚须于 {{ dateTimeLabel(deadline) }} 完成。提交不代表停诊已经生效。</p><button type="submit" class="dh-primary" :disabled="busy || !!warning">核对并提交申请<Icon name="arrow" /></button></template>
        </form>
      </section>
      <aside class="ds-progress-panel" aria-label="申请进度">
        <h2>{{ application ? '本次处理进度' : '申请会怎样处理' }}</h2>
        <ol class="ds-progress"><li :class="{ done: application }"><span>1</span><div><strong>医生提交申请</strong><p>{{ application ? dateTimeLabel(application.submittedAt) : '填写原因并核对受影响预约' }}</p></div></li><li :class="{ current: application?.status === 'PENDING_HEAD', done: application?.status === 'PENDING_ADMIN' }"><span>2</span><div><strong>{{ (application?.applicantRole ?? role) === 'head' ? '本人申请回避科主任审核' : '科主任审核方案' }}</strong><p>{{ (application?.applicantRole ?? role) === 'head' ? '交管理员处理本人申请' : '选择直接停诊，或安排替诊' }}</p></div></li><li :class="{ current: application?.status === 'PENDING_ADMIN' }"><span>3</span><div><strong>管理员最终复核</strong><p>替诊方案须先由替诊医生确认同意</p></div></li><li :class="{ current: application?.status === 'EXPIRED' || application?.status === 'REJECTED', done: application?.status === 'APPROVED' }"><span>4</span><div><strong>{{ application && !isPendingApplication(application) ? applicationLabels[application.status] : '结果生效并通知' }}</strong><p>{{ application?.status === 'EXPIRED' ? '申请过期，原出诊安排保留' : '最终通过后才停诊或迁移预约' }}</p></div></li></ol>

      </aside>
    </div>
    <section v-if="!schedule && !invitation" class="ds-application-list" aria-label="我的停诊申请">
      <div class="ds-application-tabs" role="tablist"><span role="tab" tabindex="0" :aria-selected="applicationTab === 'suspension'" :class="{ active: applicationTab === 'suspension' }" @click="applicationTab = 'suspension'" @keydown.enter="applicationTab = 'suspension'">我的停诊申请 <b>{{ applications.length }}</b></span><span role="tab" tabindex="0" :aria-selected="applicationTab === 'invitation'" :class="{ active: applicationTab === 'invitation' }" @click="applicationTab = 'invitation'" @keydown.enter="applicationTab = 'invitation'">替诊邀请 <b>{{ state.invitations.filter(i => i.status === 'PENDING').length }}</b></span><span v-if="role === 'head'" role="tab" tabindex="0" :aria-selected="applicationTab === 'department'" :class="{ active: applicationTab === 'department' }" @click="applicationTab = 'department'" @keydown.enter="applicationTab = 'department'">科室审批</span></div>
      <template v-if="applicationTab === 'suspension'"><div class="ds-section-heading"><div><h2>我的停诊申请</h2></div><span>{{ applications.length }} 项</span></div><button v-for="a in applications" :key="a.id" class="ds-request-row" @click="viewApplication(a)"><span class="ds-request-icon"><Icon name="file" /></span><span><strong>{{ state.schedules.find(s => s.id === a.scheduleId)?.date }} · {{ state.schedules.find(s => s.id === a.scheduleId)?.period === 'am' ? '上午' : '下午' }}停诊申请</strong><small>{{ a.reason }}</small></span><span class="ds-request-row-status"><span :class="['ds-status', { warn: isPendingApplication(a) }]">{{ applicationLabels[a.status] }}</span><small>截止 {{ dateTimeLabel(a.deadline) }}</small></span><Icon name="chevron" /></button></template>
      <template v-else-if="applicationTab === 'invitation'"><div class="ds-section-heading"><h2>替诊邀请</h2><span>{{ state.invitations.length }} 项</span></div><button v-for="item in state.invitations" :key="item.id" class="ds-request-row" @click="viewInvitation(item.id)"><span class="ds-request-icon ds-doctor-icon"><Icon name="doctor" /></span><span><strong>{{ item.date }} · {{ item.period === 'am' ? '上午' : '下午' }}替诊邀请</strong><small>{{ item.arrangedBy }}（科主任） · {{ item.reason }}</small></span><span class="ds-request-row-status"><span :class="['ds-status', { warn: item.status === 'PENDING' }]">{{ item.status === 'PENDING' ? '待你确认' : item.status === 'ACCEPTED' ? '待管理员复核' : item.status === 'REJECTED' ? '已拒绝' : item.status === 'EXPIRED' ? '已过期' : '已撤回' }}</span><small>截止 {{ dateTimeLabel(item.deadline) }}</small></span><Icon name="chevron" /></button></template>
      <template v-else><div class="ds-section-heading"><h2>科室审批</h2></div><div class="ds-empty"><span><Icon name="shield" /></span><h3>科室审批将在下一阶段处理</h3><p>当前身份可以查看待办，但本轮只完成替诊邀请确认。</p></div></template>
    </section>
    <dialog ref="confirmDialog" class="ds-confirm-dialog" aria-label="核对停诊申请" @close="closed">
      <h2>确认本场停诊申请</h2><p v-if="schedule">{{ schedule.date }} · {{ periodLabel(schedule) }} {{ scheduleTime(schedule) }} · {{ modeLabel(schedule) }}</p><div class="ds-confirm-impact"><strong>将影响 {{ affected }} 位已挂号患者</strong><p>提交后暂停新增挂号并解除未付款临时占号，已付款预约保留。最终审批通过前仍需按原安排接诊。</p></div><p class="ds-reason-text">申请原因：{{ reason }}</p><p>审批截止：{{ dateTimeLabel(deadline) }}</p><label class="ds-confirm-checkbox"><input v-model="confirmed" type="checkbox">我已核对场次和影响，知晓提交不代表停诊生效</label><p v-if="error" role="alert" class="ds-alert">{{ error }} 原因已保留，可重试。</p><div class="ds-confirm-actions"><button class="ds-secondary" @click="confirmDialog?.close()">返回修改</button><button class="dh-primary" :disabled="!confirmed || busy" @click="submit">{{ busy ? '正在保存…' : '确认提交' }}</button></div>
    </dialog>
    <dialog ref="inviteConfirm" class="ds-confirm-dialog" aria-label="确认接受替诊"><h2>确认接受替诊？</h2><p v-if="invitation">{{ invitation.date }} · {{ invitation.period === 'am' ? '上午' : '下午' }} · {{ invitation.mode === 'online' ? '线上问诊' : '线下面诊' }}</p><div class="ds-confirm-impact"><strong>接受后等待管理员复核</strong><p>当前不会立即变更排班、迁移预约或开放患者医疗材料。</p></div><label class="ds-confirm-checkbox"><input v-model="confirmed" type="checkbox">我已核对场次和本人排班，知晓替诊尚未正式生效</label><p v-if="error" role="alert" class="ds-alert">{{ error }}</p><div class="ds-confirm-actions"><button class="ds-secondary" @click="inviteConfirm?.close()">返回</button><button class="dh-primary" :disabled="!confirmed || busy" @click="accept">确认接受</button></div></dialog>
    <dialog ref="rejectDialog" class="ds-confirm-dialog" aria-label="拒绝替诊"><h2>拒绝替诊</h2><p>拒绝后邀请将退回科主任重新安排，请填写原因。</p><label for="reject-reason">拒绝原因 <span class="ds-required">*</span></label><textarea id="reject-reason" v-model="rejectReason" maxlength="500" rows="4" placeholder="请说明无法替诊的原因" aria-required="true"></textarea><p v-if="error" role="alert" class="ds-alert">{{ error }}</p><div class="ds-confirm-actions"><button class="ds-secondary" @click="rejectDialog?.close()">返回</button><button class="dn-danger" :disabled="!rejectReason.trim() || busy" @click="reject">确认拒绝</button></div></dialog>
  </section>
</template>
