<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
import { useNotificationRefresh } from '../useNotificationRefresh'
interface Result { taskId: string; state: string; provider: string; versionNo: number; stale: boolean; errorCode: string | null; materialVersion: number; analysis: { summary: string; duration: string; departmentHint: string; followUpQuestions: string; riskAlert: string; safetyNotice: string } | null; recommendations: { doctorId: string; doctorName: string; departmentName: string; scheduleId: string; clinicDate: string; session: string }[] }
const text = ref(''), consent = ref(false), results = ref<Result[]>([]), error = ref(''), busy = ref(false)
async function load() { try { results.value = await api<Result[]>('/patient/ai/results') } catch (failure) { error.value = failure instanceof Error ? failure.message : '加载失败' } }
useNotificationRefresh(load)
async function analyze() {
  if (!consent.value) return
  busy.value = true; error.value = ''
  try {
    const grant = await api<{ id: string }>('/patient/ai/consents', 'POST', { accepted: true, provider: 'LOCAL_MOCK', purpose: 'PRECONSULTATION_SUMMARY', scope: 'SYMPTOM_TEXT', symptomText: text.value })
    await api('/patient/ai/preconsultations', 'POST', { consentId: grant.id, symptomText: text.value, idempotencyKey: crypto.randomUUID() })
    text.value = ''; consent.value = false; await load()
  } catch (failure) { error.value = failure instanceof Error ? failure.message : '预问诊暂不可用' }
  finally { busy.value = false }
}
onMounted(async () => { try { await load() } catch (failure) { error.value = failure instanceof Error ? failure.message : '加载失败' } })
</script>
<template>
  <RouterLink to="/patient">返回患者工作台</RouterLink>
  <section class="card catalog"><h2>预约前 AI 文本预问诊</h2>
    <p>可在选择医生和支付之前使用；不愿使用 AI 可直接自行挂号。</p>
    <RouterLink to="/patient/catalog">跳过 AI，自行挂号</RouterLink>
    <p v-if="error" role="alert">{{ error }}</p>
    <form @submit.prevent="analyze">
      <label for="preconsultation-text">症状文本</label><textarea id="preconsultation-text" v-model="text" required maxlength="10000"></textarea>
      <p>提供方：LOCAL_MOCK（本地模拟，不外发）。用途：辅助摘要、追问与科室建议。本次范围仅为上面的文本，不包含附件，不用于诊断或处方；修改文本或再次分析须重新授权。</p>
      <label><input v-model="consent" type="checkbox"> 同意本次文本预问诊授权</label>
      <button :disabled="busy || !consent">{{ busy ? '分析中…' : '确认授权并分析' }}</button>
    </form>
    <article v-for="item in results" :key="item.taskId" class="notice">
      <p>状态：{{ item.state }} · Provider：{{ item.provider }} · 材料版本：{{ item.materialVersion }}<span v-if="item.stale">（材料更新后已过时）</span></p>
      <template v-if="item.analysis"><p>{{ item.analysis.summary }}</p><p>持续时间：{{ item.analysis.duration }} · 建议科室：{{ item.analysis.departmentHint }}</p><p>追问：{{ item.analysis.followUpQuestions }}</p><p>风险提示：{{ item.analysis.riskAlert }}</p><p>{{ item.analysis.safetyNotice }}</p>
        <p v-for="recommendation in item.recommendations" :key="recommendation.scheduleId"><RouterLink :to="{ path: '/patient/catalog', query: { doctorId: recommendation.doctorId } }">查看 {{ recommendation.doctorName }} 的有效排班</RouterLink>（{{ recommendation.departmentName }}，{{ recommendation.clinicDate }} {{ recommendation.session }}）</p>
      </template>
      <p v-else-if="item.state === 'RUNNING' || item.state === 'PENDING'">AI 任务处理中，完成后自动更新；仍可自行挂号。</p>
      <p v-else>AI 暂不可用（{{ item.errorCode }}），仍可自行挂号或联系医生。</p>
    </article>
  </section>
</template>
