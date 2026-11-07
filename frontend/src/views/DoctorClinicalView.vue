<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, download } from '../api'
interface AiResult { taskId: string; state: string; provider: string; versionNo: number; stale: boolean; errorCode: string | null; analysis: { summary: string; duration: string; departmentHint: string; followUpQuestions: string; riskAlert: string; safetyNotice: string } | null }
interface Clinical { consultation: { state: string } | null; materials: { id: string; versionNo: number; symptomText: string; attachments: { id: string; originalName: string }[] }[]; messages: { authorRole: string; body: string }[]; result: { body: string; authorId: string; versionNo: number; createdAt: number } | null; corrections: { reason: string; body: string }[] }
const route = useRoute(), id = String(route.params.id), clinical = ref<Clinical | null>(null), aiResults = ref<AiResult[]>([]), result = ref(''), reason = ref(''), correction = ref(''), error = ref(''), busy = ref(false)
const message = ref('')
async function sendMessage() { busy.value = true; error.value = ''; try { clinical.value = await api<Clinical>(`/doctor/clinical/${id}/messages`, 'POST', { body: message.value }); message.value = '' } catch (failure) { error.value = failure instanceof Error ? failure.message : '消息发送失败' } finally { busy.value = false } }
async function load() { try { clinical.value = await api<Clinical>(`/doctor/clinical/${id}`); aiResults.value = await api<AiResult[]>(`/doctor/clinical/${id}/ai`) } catch (failure) { error.value = failure instanceof Error ? failure.message : '问诊记录加载失败' } }
async function submitResult() { busy.value = true; error.value = ''; try { clinical.value = await api<Clinical>(`/doctor/clinical/${id}/result`, 'POST', { body: result.value }); result.value = '' } catch (failure) { error.value = failure instanceof Error ? failure.message : '结果提交失败' } finally { busy.value = false } }
async function submitCorrection() { busy.value = true; error.value = ''; try { clinical.value = await api<Clinical>(`/doctor/clinical/${id}/corrections`, 'POST', { reason: reason.value, body: correction.value }); reason.value = ''; correction.value = '' } catch (failure) { error.value = failure instanceof Error ? failure.message : '更正提交失败' } finally { busy.value = false } }
async function openAttachment(attachmentId: string) { try { const url = URL.createObjectURL(await download(`/doctor/clinical/attachments/${attachmentId}`)); window.open(url, '_blank', 'noopener'); setTimeout(() => URL.revokeObjectURL(url), 60_000) } catch (failure) { error.value = failure instanceof Error ? failure.message : '附件下载失败' } }
onMounted(load)
</script>
<template>
  <RouterLink to="/doctor/reception">返回候诊队列</RouterLink>
  <section class="card catalog">
    <h2>患者问诊记录</h2>
    <p v-if="error" role="alert" class="notice">{{ error }}</p>
    <p v-if="clinical?.consultation">问诊状态：{{ clinical.consultation.state }}</p>
    <button type="button" class="secondary" @click="load">刷新问诊记录</button>
    <form v-if="clinical?.consultation?.state !== 'CLOSED'" @submit.prevent="sendMessage"><label for="doctor-message">回复患者</label><textarea id="doctor-message" v-model="message" maxlength="10000" required></textarea><button :disabled="busy">发送回复</button></form>

    <section v-for="material in clinical?.materials" :key="material.id">
      <h3>材料版本 {{ material.versionNo }}</h3>
      <p>{{ material.symptomText }}</p>
      <p v-for="attachment in material.attachments" :key="attachment.id">
        <button type="button" class="secondary" @click="openAttachment(attachment.id)">
          {{ attachment.originalName }}
        </button>
      </p>
    </section>

    <section v-if="clinical?.messages.length">
      <h3>图文消息</h3>
      <p v-for="item in clinical.messages" :key="item.authorRole + item.body">
        {{ item.authorRole === 'PATIENT' ? '患者' : '医生' }}：{{ item.body }}
      </p>
    </section>

    <section v-if="aiResults.length">
      <h3>AI 预问诊辅助摘要</h3>
      <article v-for="item in aiResults" :key="item.taskId" class="notice">
        <p>状态：{{ item.state }} · Provider：{{ item.provider }} · 版本：{{ item.versionNo }}<span v-if="item.stale">（材料更新后已过时）</span></p>
        <template v-if="item.analysis"><p>{{ item.analysis.summary }}</p><p>持续时间：{{ item.analysis.duration }} · 建议科室：{{ item.analysis.departmentHint }}</p><p>追问：{{ item.analysis.followUpQuestions }}</p><p>风险提示：{{ item.analysis.riskAlert }}</p><p>{{ item.analysis.safetyNotice }}</p></template>
        <p v-else>AI 暂不可用（{{ item.errorCode }}），不影响医生继续接诊。</p>
      </article>
    </section>

    <form v-if="clinical?.consultation?.state !== 'CLOSED'" @submit.prevent="submitResult">
      <label for="result">问诊结果/面诊登记</label>
      <textarea id="result" v-model="result" maxlength="20000" required></textarea>
      <button :disabled="busy">提交结果并结束接诊</button>
    </form>

    <section v-if="clinical?.consultation?.state === 'CLOSED'">
      <h3>追加更正</h3>
      <form @submit.prevent="submitCorrection">
        <label for="reason">更正原因</label>
        <input id="reason" v-model="reason" maxlength="500" required>
        <label for="correction">更正内容</label>
        <textarea id="correction" v-model="correction" maxlength="20000" required></textarea>
        <button :disabled="busy">追加更正</button>
      </form>
    </section>

    <p v-if="clinical?.result">当前结果：{{ clinical.result.body }}</p>
    <section v-if="clinical?.corrections.length"><h3>更正记录</h3><p v-for="(item, index) in clinical.corrections" :key="index">{{ item.reason }}：{{ item.body }}</p></section>
    <p v-if="clinical?.result">作者：{{ clinical.result.authorId }} · 版本：{{ clinical.result.versionNo }} · 时间：{{ new Date(clinical.result.createdAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) }}</p>
  </section>
</template>
