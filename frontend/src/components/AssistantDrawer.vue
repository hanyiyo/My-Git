<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { api, upload } from '../api'

interface BusinessAnswer { mode: string; answer: string; sources: string[] }
interface Recommendation {
  id?: string
  doctorId: string
  doctorName: string
  departmentId: string
  departmentName: string
  scheduleId: string
  clinicDate: string
  session: string
  serviceMode: string
}
interface DepartmentOption { id: string; name: string }
interface TriageAnswer { mode: string; answer: string; departmentHint: string; imageReceived: boolean; sources: string[] }

const open = ref(false)
const dialog = ref<HTMLDialogElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const question = ref('')
const answer = ref<BusinessAnswer | null>(null)
const result = ref<TriageAnswer | null>(null)
const recommendations = ref<Recommendation[]>([])
const error = ref('')
const busy = ref(false)
const consentRequired = ref(false)
const consentAccepted = ref(false)
const consentRefused = ref(false)

function resetResult() {
  answer.value = null
  result.value = null
  recommendations.value = []
  error.value = ''
  consentRequired.value = false
  consentAccepted.value = false
  consentRefused.value = false
}
async function show() {
  open.value = true
  await nextTick()
  dialog.value?.showModal()
}
async function close() {
  dialog.value?.close()
  open.value = false
  resetResult()
  await nextTick()
  trigger.value?.focus()
}
async function ask() {
  busy.value = true; resetResult()
  try {
    answer.value = await api<BusinessAnswer>('/assistant/business?question=' + encodeURIComponent(question.value.trim()))
    consentRequired.value = answer.value.mode === 'TRIAGE_ASSISTANCE'
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : '导诊助手暂不可用，请稍后重试'
  } finally { busy.value = false }
}
function refuseConsent() {
  consentRequired.value = false
  consentRefused.value = true
  consentAccepted.value = false
}
async function analyzeSymptoms() {
  if (!consentAccepted.value) return
  busy.value = true; error.value = ''; result.value = null
  try {
    const form = new FormData(); form.append('question', question.value.trim())
    result.value = await upload<TriageAnswer>('/assistant/triage', form)
    if (result.value.departmentHint) {
      const departments = await api<DepartmentOption[]>('/catalog/departments')
      const department = departments.find(item => item.id === result.value?.departmentHint || item.name === result.value?.departmentHint)
      if (!department) return
      const schedules = await api<Array<Recommendation & { advanceAvailable: boolean; walkInAvailable: boolean }>>('/catalog/schedules?departmentId=' + encodeURIComponent(department.id))
      recommendations.value = schedules.filter(item => item.advanceAvailable || item.walkInAvailable).slice(0, 5).map(item => ({ ...item, scheduleId: item.scheduleId || item.id || '' }))
    }
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : 'AI 导诊暂不可用，请自行查询科室和排班。'
  } finally { busy.value = false }
}
function recommendationPath(item: Recommendation) {
  const query = new URLSearchParams({ departmentId: item.departmentId, doctorId: item.doctorId, date: item.clinicDate, mode: item.serviceMode, session: item.session })
  return `/patient/catalog?${query.toString()}`
}
</script>

<template>
  <button v-show="!open" ref="trigger" class="assistant-fab" type="button" aria-label="打开导诊助手" @click="show">AI 导诊</button>
  <dialog v-if="open" ref="dialog" class="assistant-drawer" aria-label="导诊助手" @cancel.prevent="close">
    <button class="secondary" type="button" @click="close">关闭</button>
    <h2>导诊助手</h2>
    <p class="hint">可咨询医院流程和科室，也可描述症状获得辅助导诊；不提供诊断或处方。</p>
    <form @submit.prevent="ask">
      <label for="assistant-question">描述问题或症状</label>
      <textarea id="assistant-question" v-model="question" maxlength="500" required autofocus placeholder="例如：医院地址在哪里？最近咳嗽挂什么科？"></textarea>
      <button :disabled="busy">{{ busy ? '查询中…' : '获取辅助建议' }}</button>
    </form>
    <p v-if="error" role="alert" class="notice notice-error">{{ error }}</p>
    <section v-if="answer" class="notice assistant-answer" aria-live="polite">
      <h3>{{ answer.mode === 'LOCAL_FACTS' ? '业务事实' : '服务指引' }}</h3>
      <p>{{ answer.answer }}</p>
      <small v-if="answer.sources.length">来源：{{ answer.sources.join('、') }}</small>
    </section>
    <section v-if="consentRequired" class="assistant-consent" aria-label="症状授权边界">
      <strong>提交症状前请确认授权范围</strong>
      <p>提供方：医院配置的 AI 导诊服务；用途：根据本次症状文字辅助推荐科室与排班；范围：仅本次症状文字。本次不生成诊断、处方或预约前病历。你可以拒绝授权，继续自行查询科室和挂号。</p>
      <label><input v-model="consentAccepted" type="checkbox"> 我已阅读并同意本次症状文字用于辅助导诊</label>
      <div class="assistant-consent-actions"><button type="button" :disabled="busy || !consentAccepted" @click="analyzeSymptoms">同意授权并获取建议</button><button type="button" class="secondary" @click="refuseConsent">拒绝授权，继续自行查询</button></div>
    </section>
    <p v-if="consentRefused" class="notice">已拒绝本次授权，不影响你自行查询科室和挂号。<RouterLink to="/patient/catalog" @click="close">查看真实科室与排班</RouterLink></p>
    <section v-if="result && !error" class="assistant-result" aria-label="AI辅助导诊结果">
      <h3>AI 辅助导诊结果</h3>
      <p>{{ result.answer }}</p>
      <p v-if="result.departmentHint"><strong>建议了解：</strong>{{ result.departmentHint }}</p>
      <div v-if="recommendations.length" class="assistant-recommendations">
        <h4>真实可用号源</h4>
        <RouterLink v-for="item in recommendations" :key="item.scheduleId" :to="recommendationPath(item)" class="assistant-recommendation" @click="close">
          <span><strong>{{ item.doctorName }}</strong><small>{{ item.departmentName }} · {{ item.clinicDate }} · {{ item.session === 'AM' ? '上午' : '下午' }} · {{ item.serviceMode === 'ONLINE' ? '线上' : '线下' }}</small></span><b aria-hidden="true">查看号源 →</b>
        </RouterLink>
      </div>
      <p v-else class="hint">暂无符合条件的有效号源，可以进入目录调整日期或服务方式。</p>
    </section>
  </dialog>
</template>
