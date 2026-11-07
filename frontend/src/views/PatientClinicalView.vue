<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, download, upload } from '../api'

interface Attachment { id: string; originalName: string; mediaType: string; sizeBytes: number }
interface Material { id: string; versionNo: number; symptomText: string; shared: boolean; attachments: Attachment[] }
interface AiResult { taskId: string; state: string; provider: string; purpose: string; attempts: number; errorCode: string | null; versionNo: number; stale: boolean; analysis: { summary: string; duration: string; departmentHint: string; followUpQuestions: string; riskAlert: string; safetyNotice: string; provider: string } | null; recommendations: { doctorId: string; doctorName: string; departmentName: string; scheduleId: string; clinicDate: string; session: string; serviceMode: string }[] }
interface Clinical { consultation: { state: string } | null; materials: Material[]; messages: { authorRole: string; body: string }[]; result: { body: string; authorId: string; versionNo: number; createdAt: number } | null; corrections: { reason: string; body: string }[] }

const route = useRoute(), id = String(route.params.id)
const clinical = ref<Clinical | null>(null), aiResults = ref<AiResult[]>([])
const drafts = ref<AiResult[]>([])
const symptoms = ref(''), files = ref<File[]>([]), message = ref(''), aiSymptoms = ref(''), aiConsent = ref(false)
const error = ref(''), busy = ref(false), aiBusy = ref(false)

async function load() {
  clinical.value = null; aiResults.value = []; drafts.value = []; error.value = ''
  try {
    clinical.value = await api<Clinical>(`/patient/appointments/${id}/clinical`)
    aiResults.value = await api<AiResult[]>(`/patient/appointments/${id}/ai/results`)
    drafts.value = (await api<AiResult[]>('/patient/ai/results')).filter(item => item.state === 'SUCCEEDED')
  } catch (failure) { error.value = failure instanceof Error ? failure.message : '问诊记录加载失败' }
}
async function submitMaterials() {
  busy.value = true; error.value = ''
  try { const form = new FormData(); form.append('symptoms', symptoms.value); form.append('shared', 'true'); files.value.forEach(file => form.append('files', file)); clinical.value = await upload<Clinical>(`/patient/appointments/${id}/clinical/materials`, form); symptoms.value = ''; files.value = []; await load() }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '材料提交失败' }
  finally { busy.value = false }
}
async function runPreconsultation() {
  if (!aiConsent.value) { error.value = '请先确认预问诊授权'; return }
  aiBusy.value = true; error.value = ''
  try {
    const consent = await api<{ id: string }>(`/patient/appointments/${id}/ai/consents`, 'POST', { accepted: true, provider: 'LOCAL_MOCK', purpose: 'PRECONSULTATION_SUMMARY', scope: 'SYMPTOM_TEXT', symptomText: aiSymptoms.value })
    const result = await api<AiResult>(`/patient/appointments/${id}/ai/preconsultations`, 'POST', { consentId: consent.id, symptomText: aiSymptoms.value, idempotencyKey: `web-${Date.now()}` })
    aiResults.value = [result, ...aiResults.value.filter(item => item.taskId !== result.taskId)]; aiSymptoms.value = ''; aiConsent.value = false; await load()
  } catch (failure) { error.value = failure instanceof Error ? failure.message : '预问诊暂不可用' }
  finally { aiBusy.value = false }
}
async function shareDraft(taskId: string) {
  busy.value = true; error.value = ''
  try { await api(`/patient/appointments/${id}/ai/drafts/${taskId}/share`, 'POST', { shared: true }); await load() }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '共享摘要失败' }
  finally { busy.value = false }
}
async function sendMessage() { busy.value = true; error.value = ''; try { clinical.value = await api<Clinical>(`/patient/appointments/${id}/clinical/messages`, 'POST', { body: message.value }); message.value = '' } catch (failure) { error.value = failure instanceof Error ? failure.message : '消息发送失败' } finally { busy.value = false } }
async function openAttachment(attachmentId: string) { try { const url = URL.createObjectURL(await download(`/patient/clinical/attachments/${attachmentId}`)); window.open(url, '_blank', 'noopener'); setTimeout(() => URL.revokeObjectURL(url), 60_000) } catch (failure) { error.value = failure instanceof Error ? failure.message : '附件下载失败' } }
onMounted(load)
</script>

<template>
  <RouterLink to="/patient/orders">返回预约订单</RouterLink>
  <section class="card catalog">
    <h2>问诊材料与结果</h2>
    <p v-if="error" role="alert" class="notice">{{ error }}</p>
    <p v-if="clinical?.consultation">问诊状态：{{ clinical.consultation.state === 'CLOSED' ? '已结束（只读）' : clinical.consultation.state }}</p>
    <section v-if="drafts.length && clinical?.consultation?.state !== 'CLOSED'"><h3>已有预约前摘要</h3>
      <article v-for="item in drafts" :key="item.taskId"><p>{{ item.analysis?.summary }}<span v-if="item.stale">（材料更新后已过时）</span></p>
        <button type="button" :disabled="busy || aiResults.some(result => result.taskId === item.taskId)" @click="shareDraft(item.taskId)">确认将此文本和摘要共享给当前医生</button>
      </article>
    </section>

    <form v-if="clinical?.consultation?.state !== 'CLOSED'" @submit.prevent="submitMaterials">
      <label for="symptoms">症状文本</label>
      <textarea id="symptoms" v-model="symptoms" maxlength="10000" required></textarea>
      <label for="attachments">共享医疗附件（JPEG、PNG、PDF，最多5份）</label>
      <input id="attachments" type="file" accept=".jpg,.jpeg,.png,.pdf" multiple @change="files = Array.from(($event.target as HTMLInputElement).files ?? [])">
      <button :disabled="busy">提交并确认共享</button>
    </form>

    <form v-if="clinical?.consultation?.state !== 'CLOSED'" @submit.prevent="runPreconsultation">
      <h3>AI 文本预问诊</h3>
      <p class="hint">提交前请确认：本次仅将症状文本交给已标识的本地模拟 Provider，用于生成辅助摘要、追问和科室建议；不用于诊断或处方。</p>
      <label for="aiSymptoms">预问诊症状文本</label>
      <textarea id="aiSymptoms" v-model="aiSymptoms" maxlength="10000" required></textarea>
      <label><input v-model="aiConsent" type="checkbox"> 我已阅读并同意本次预问诊授权范围</label>
      <button :disabled="aiBusy || !aiConsent">{{ aiBusy ? '分析中…' : '确认授权并分析' }}</button>
    </form>

    <section v-if="aiResults.length">
      <h3>AI 辅助结果</h3>
      <article v-for="item in aiResults" :key="item.taskId" class="notice">
        <p>状态：{{ item.state }} · Provider：{{ item.provider }} · 版本：{{ item.versionNo }}<span v-if="item.stale">（材料更新后已过时）</span></p>
        <template v-if="item.analysis">
          <p>{{ item.analysis.summary }}</p><p>持续时间：{{ item.analysis.duration }} · 建议科室：{{ item.analysis.departmentHint }}</p>
          <p>追问：{{ item.analysis.followUpQuestions }}</p><p>风险提示：{{ item.analysis.riskAlert }}</p><p>{{ item.analysis.safetyNotice }}</p>
          <p v-if="item.recommendations.length">可参考排班：<span v-for="recommendation in item.recommendations" :key="recommendation.scheduleId">{{ recommendation.doctorName }}（{{ recommendation.departmentName }}，{{ recommendation.clinicDate }} {{ recommendation.session }}） </span></p>
        </template>
        <p v-else>AI 暂不可用（{{ item.errorCode }}），不影响自行预约或医生接诊。</p>
      </article>
    </section>

    <section v-if="clinical?.materials.length"><h3>材料版本</h3><ul class="resource-list"><li v-for="material in clinical.materials" :key="material.id"><strong>版本 {{ material.versionNo }}</strong><p>{{ material.symptomText }}</p><p v-for="attachment in material.attachments" :key="attachment.id"><button type="button" class="secondary" @click="openAttachment(attachment.id)">{{ attachment.originalName }}</button></p></li></ul></section>
    <button type="button" class="secondary" @click="load">刷新问诊记录</button>
    <section v-if="clinical?.messages.length"><h3>图文消息</h3><p v-for="(item, index) in clinical.messages" :key="index">{{ item.authorRole === 'PATIENT' ? '患者' : '医生' }}：{{ item.body }}</p></section>
    <section v-if="clinical?.result"><h3>医生结果（只读）</h3><p>{{ clinical.result.body }}</p><p>作者：{{ clinical.result.authorId }} · 版本：{{ clinical.result.versionNo }} · 时间：{{ new Date(clinical.result.createdAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) }}</p></section>
    <section v-if="clinical?.corrections.length"><h3>追加更正</h3><p v-for="correction in clinical.corrections" :key="correction.reason + correction.body">{{ correction.reason }}：{{ correction.body }}</p></section>
    <form v-if="clinical?.consultation?.state !== 'CLOSED'" @submit.prevent="sendMessage"><label for="message">图文消息</label><textarea id="message" v-model="message" maxlength="10000" required></textarea><button :disabled="busy">发送消息</button></form>
  </section>
</template>
