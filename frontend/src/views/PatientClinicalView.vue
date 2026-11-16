<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, download, upload } from '../api'
import { useNotificationRefresh } from '../useNotificationRefresh'

interface Attachment { id: string; originalName: string; mediaType: string; sizeBytes: number }
interface Material { id: string; versionNo: number; symptomText: string; shared: boolean; attachments: Attachment[] }
interface Clinical { consultation: { state: string } | null; serviceMode: 'ONLINE' | 'OFFLINE'; materials: Material[]; messages: { id: string; authorRole: string; body: string }[]; result: { body: string; chiefComplaint: string; presentIllness: string; historyAllergy: string; diagnosis: string; treatmentPlan: string; authorId: string; versionNo: number; createdAt: number } | null; corrections: { reason: string; body: string }[] }
interface PharmacyOrder { id: string; appointmentId: string; state: string; totalMinor: number; expiresAt: number; lines: { medicineId: string; name: string; spec: string; feeMinor: number; quantity: number; usage: string }[] }
interface SummaryConsent { id: string; provider: string; purpose: string; scope: string; accepted: boolean; messageIds: string[]; materialIds: string[]; createdAt: number; revokedAt: number | null }

const route = useRoute(), id = String(route.params.id)
const clinical = ref<Clinical | null>(null), orders = ref<PharmacyOrder[]>([]), summaryConsents = ref<SummaryConsent[]>([])
const symptoms = ref(''), files = ref<File[]>([]), message = ref('')
const selectedMessageIds = ref<string[]>([]), selectedMaterialIds = ref<string[]>([])
const error = ref(''), busy = ref(false)

async function load() {
  clinical.value = null; error.value = ''
  try {
    clinical.value = await api<Clinical>(`/patient/appointments/${id}/clinical`); orders.value = (await api<PharmacyOrder[]>('/patient/pharmacy/orders')).filter(order => order.appointmentId === id)
    summaryConsents.value = clinical.value.serviceMode === 'ONLINE' ? await api<SummaryConsent[]>(`/patient/appointments/${id}/ai/summary-consents`) : []
  } catch (failure) { error.value = failure instanceof Error ? failure.message : '问诊记录加载失败' }
}
async function submitMaterials() {
  busy.value = true; error.value = ''
  try { const form = new FormData(); form.append('symptoms', symptoms.value); form.append('shared', 'true'); files.value.forEach(file => form.append('files', file)); clinical.value = await upload<Clinical>(`/patient/appointments/${id}/clinical/materials`, form); symptoms.value = ''; files.value = []; await load() }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '材料提交失败' }
  finally { busy.value = false }
}
async function sendMessage() { busy.value = true; error.value = ''; try { clinical.value = await api<Clinical>(`/patient/appointments/${id}/clinical/messages`, 'POST', { body: message.value }); message.value = '' } catch (failure) { error.value = failure instanceof Error ? failure.message : '消息发送失败' } finally { busy.value = false } }
async function authorizeSummary() { busy.value = true; error.value = ''; try { await api<SummaryConsent>(`/patient/appointments/${id}/ai/summary-consents`, 'POST', { accepted: true, provider: 'LOCAL_MOCK', purpose: 'DOCTOR_SYMPTOM_SUMMARY', scope: 'EXACT_MESSAGES_AND_MATERIALS', messageIds: selectedMessageIds.value, materialIds: selectedMaterialIds.value }); await load() } catch (failure) { error.value = failure instanceof Error ? failure.message : '摘要授权失败' } finally { busy.value = false } }
async function revokeSummary() { const active = summaryConsents.value.find(item => item.accepted && !item.revokedAt); if (!active) return; busy.value = true; error.value = ''; try { await api<void>(`/patient/appointments/${id}/ai/summary-consents/${active.id}/revoke`, 'POST'); await load() } catch (failure) { error.value = failure instanceof Error ? failure.message : '摘要授权撤回失败' } finally { busy.value = false } }
async function pay(orderId: string) { busy.value = true; error.value = ''; try { await api<PharmacyOrder>(`/patient/pharmacy/orders/${orderId}/pay`, 'POST'); await load() } catch (failure) { error.value = failure instanceof Error ? failure.message : '药费支付失败' } finally { busy.value = false } }
async function openAttachment(attachmentId: string) { try { const url = URL.createObjectURL(await download(`/patient/clinical/attachments/${attachmentId}`)); window.open(url, '_blank', 'noopener'); setTimeout(() => URL.revokeObjectURL(url), 60_000) } catch (failure) { error.value = failure instanceof Error ? failure.message : '附件下载失败' } }
onMounted(load)
useNotificationRefresh(load, id)
</script>

<template>
  <RouterLink to="/patient/orders">返回预约订单</RouterLink>
  <section class="card catalog">
    <h2>问诊材料与结果</h2>
    <p v-if="error" role="alert" class="notice">{{ error }}</p>
    <p v-if="clinical?.consultation">问诊状态：{{ clinical.consultation.state === 'CLOSED' ? '已结束（只读）' : clinical.consultation.state }}</p>
    <form v-if="clinical?.consultation?.state !== 'CLOSED'" @submit.prevent="submitMaterials">
      <label for="symptoms">症状文本</label>
      <textarea id="symptoms" v-model="symptoms" maxlength="10000" required></textarea>
      <label for="attachments">共享医疗附件（JPEG、PNG、PDF，最多5份）</label>
      <input id="attachments" type="file" accept=".jpg,.jpeg,.png,.pdf" multiple @change="files = Array.from(($event.target as HTMLInputElement).files ?? [])">
      <button :disabled="busy">提交并确认共享</button>
    </form>

    <section v-if="clinical?.materials.length"><h3>材料版本</h3><ul class="resource-list"><li v-for="material in clinical.materials" :key="material.id"><strong>版本 {{ material.versionNo }}</strong><p>{{ material.symptomText }}</p><p v-for="attachment in material.attachments" :key="attachment.id"><button type="button" class="secondary" @click="openAttachment(attachment.id)">{{ attachment.originalName }}</button></p></li></ul></section>
    <button type="button" class="secondary" @click="load">刷新问诊记录</button>
    <section v-if="clinical?.messages.length"><h3>图文消息</h3><p v-for="item in clinical.messages" :key="item.id">{{ item.authorRole === 'PATIENT' ? '患者' : '医生' }}：{{ item.body }}</p></section>
    <section v-if="clinical?.serviceMode === 'ONLINE' && clinical.consultation?.state !== 'CLOSED'" class="summary-consent" aria-label="医生症状摘要授权"><h3>医生症状摘要授权</h3><p>只授权你勾选的本次消息和已共享材料；之后新增内容不会自动加入。</p><fieldset v-if="clinical.messages.length"><legend>选择图文消息</legend><label v-for="item in clinical.messages" :key="item.id"><input v-model="selectedMessageIds" type="checkbox" :value="item.id">{{ item.authorRole === 'PATIENT' ? '患者' : '医生' }}：{{ item.body }}</label></fieldset><fieldset v-if="clinical.materials.length"><legend>选择已共享材料</legend><label v-for="item in clinical.materials" :key="item.id"><input v-model="selectedMaterialIds" type="checkbox" :value="item.id">材料版本 {{ item.versionNo }}：{{ item.symptomText }}</label></fieldset><p v-if="summaryConsents.some(item => item.accepted && !item.revokedAt)" role="status">已授权本次范围，提供方：LOCAL_MOCK <button type="button" class="secondary" :disabled="busy" @click="revokeSummary">撤回摘要授权</button></p><button v-else type="button" :disabled="busy || (!selectedMessageIds.length && !selectedMaterialIds.length)" @click="authorizeSummary">授权勾选范围生成摘要</button></section>
    <section v-if="clinical?.result"><h3>医生结果（只读）</h3><dl><dt>主诉</dt><dd>{{ clinical.result.chiefComplaint }}</dd><dt>现病史</dt><dd>{{ clinical.result.presentIllness }}</dd><dt>既往史/过敏史</dt><dd>{{ clinical.result.historyAllergy }}</dd><dt>诊断</dt><dd>{{ clinical.result.diagnosis }}</dd><dt>处理建议</dt><dd>{{ clinical.result.treatmentPlan }}</dd></dl><p v-if="clinical.result.body">补充说明：{{ clinical.result.body }}</p><p>作者：{{ clinical.result.authorId }} · 版本：{{ clinical.result.versionNo }} · 时间：{{ new Date(clinical.result.createdAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) }}</p></section>
    <section v-for="order in orders" :key="order.id"><h3>药费订单：{{ order.state === 'PENDING_PAYMENT' ? '待缴费' : order.state === 'PAID' ? '已支付' : '已超时' }}</h3><p>合计：{{ (order.totalMinor / 100).toFixed(2) }} 元</p><p v-for="line in order.lines" :key="line.medicineId">{{ line.name }} · {{ line.spec }} × {{ line.quantity }} · {{ line.usage }}</p><button v-if="order.state === 'PENDING_PAYMENT'" :disabled="busy" @click="pay(order.id)">模拟支付药费</button></section>
    <section v-if="clinical?.corrections.length"><h3>追加更正</h3><p v-for="correction in clinical.corrections" :key="correction.reason + correction.body">{{ correction.reason }}：{{ correction.body }}</p></section>
    <form v-if="clinical?.consultation?.state !== 'CLOSED'" @submit.prevent="sendMessage"><label for="message">图文消息</label><textarea id="message" v-model="message" maxlength="10000" required></textarea><button :disabled="busy">发送消息</button></form>
  </section>
</template>
