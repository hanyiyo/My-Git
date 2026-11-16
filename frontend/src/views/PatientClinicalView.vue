<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, download, upload } from '../api'
import { useNotificationRefresh } from '../useNotificationRefresh'

interface Attachment { id: string; originalName: string; mediaType: string; sizeBytes: number }
interface Material { id: string; versionNo: number; symptomText: string; shared: boolean; attachments: Attachment[] }
interface Clinical { consultation: { state: string } | null; materials: Material[]; messages: { authorRole: string; body: string }[]; result: { body: string; chiefComplaint: string; presentIllness: string; historyAllergy: string; diagnosis: string; treatmentPlan: string; authorId: string; versionNo: number; createdAt: number } | null; corrections: { reason: string; body: string }[] }
interface PharmacyOrder { id: string; appointmentId: string; state: string; totalMinor: number; expiresAt: number; lines: { medicineId: string; name: string; spec: string; feeMinor: number; quantity: number; usage: string }[] }

const route = useRoute(), id = String(route.params.id)
const clinical = ref<Clinical | null>(null), orders = ref<PharmacyOrder[]>([])
const symptoms = ref(''), files = ref<File[]>([]), message = ref('')
const error = ref(''), busy = ref(false)

async function load() {
  clinical.value = null; error.value = ''
  try {
    clinical.value = await api<Clinical>(`/patient/appointments/${id}/clinical`); orders.value = (await api<PharmacyOrder[]>('/patient/pharmacy/orders')).filter(order => order.appointmentId === id)
  } catch (failure) { error.value = failure instanceof Error ? failure.message : '问诊记录加载失败' }
}
async function submitMaterials() {
  busy.value = true; error.value = ''
  try { const form = new FormData(); form.append('symptoms', symptoms.value); form.append('shared', 'true'); files.value.forEach(file => form.append('files', file)); clinical.value = await upload<Clinical>(`/patient/appointments/${id}/clinical/materials`, form); symptoms.value = ''; files.value = []; await load() }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '材料提交失败' }
  finally { busy.value = false }
}
async function sendMessage() { busy.value = true; error.value = ''; try { clinical.value = await api<Clinical>(`/patient/appointments/${id}/clinical/messages`, 'POST', { body: message.value }); message.value = '' } catch (failure) { error.value = failure instanceof Error ? failure.message : '消息发送失败' } finally { busy.value = false } }
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
    <section v-if="clinical?.messages.length"><h3>图文消息</h3><p v-for="(item, index) in clinical.messages" :key="index">{{ item.authorRole === 'PATIENT' ? '患者' : '医生' }}：{{ item.body }}</p></section>
    <section v-if="clinical?.result"><h3>医生结果（只读）</h3><dl><dt>主诉</dt><dd>{{ clinical.result.chiefComplaint }}</dd><dt>现病史</dt><dd>{{ clinical.result.presentIllness }}</dd><dt>既往史/过敏史</dt><dd>{{ clinical.result.historyAllergy }}</dd><dt>诊断</dt><dd>{{ clinical.result.diagnosis }}</dd><dt>处理建议</dt><dd>{{ clinical.result.treatmentPlan }}</dd></dl><p v-if="clinical.result.body">补充说明：{{ clinical.result.body }}</p><p>作者：{{ clinical.result.authorId }} · 版本：{{ clinical.result.versionNo }} · 时间：{{ new Date(clinical.result.createdAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) }}</p></section>
    <section v-for="order in orders" :key="order.id"><h3>药费订单：{{ order.state === 'PENDING_PAYMENT' ? '待缴费' : order.state === 'PAID' ? '已支付' : '已超时' }}</h3><p>合计：{{ (order.totalMinor / 100).toFixed(2) }} 元</p><p v-for="line in order.lines" :key="line.medicineId">{{ line.name }} · {{ line.spec }} × {{ line.quantity }} · {{ line.usage }}</p><button v-if="order.state === 'PENDING_PAYMENT'" :disabled="busy" @click="pay(order.id)">模拟支付药费</button></section>
    <section v-if="clinical?.corrections.length"><h3>追加更正</h3><p v-for="correction in clinical.corrections" :key="correction.reason + correction.body">{{ correction.reason }}：{{ correction.body }}</p></section>
    <form v-if="clinical?.consultation?.state !== 'CLOSED'" @submit.prevent="sendMessage"><label for="message">图文消息</label><textarea id="message" v-model="message" maxlength="10000" required></textarea><button :disabled="busy">发送消息</button></form>
  </section>
</template>
