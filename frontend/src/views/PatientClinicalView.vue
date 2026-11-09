<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, download, upload } from '../api'
import { useNotificationRefresh } from '../useNotificationRefresh'

interface Attachment { id: string; originalName: string; mediaType: string; sizeBytes: number }
interface Material { id: string; versionNo: number; symptomText: string; shared: boolean; attachments: Attachment[] }
interface Clinical { consultation: { state: string } | null; materials: Material[]; messages: { authorRole: string; body: string }[]; result: { body: string; authorId: string; versionNo: number; createdAt: number } | null; corrections: { reason: string; body: string }[] }

const route = useRoute(), id = String(route.params.id)
const clinical = ref<Clinical | null>(null)
const symptoms = ref(''), files = ref<File[]>([]), message = ref('')
const error = ref(''), busy = ref(false)

async function load() {
  clinical.value = null; error.value = ''
  try {
    clinical.value = await api<Clinical>(`/patient/appointments/${id}/clinical`)
  } catch (failure) { error.value = failure instanceof Error ? failure.message : '问诊记录加载失败' }
}
async function submitMaterials() {
  busy.value = true; error.value = ''
  try { const form = new FormData(); form.append('symptoms', symptoms.value); form.append('shared', 'true'); files.value.forEach(file => form.append('files', file)); clinical.value = await upload<Clinical>(`/patient/appointments/${id}/clinical/materials`, form); symptoms.value = ''; files.value = []; await load() }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '材料提交失败' }
  finally { busy.value = false }
}
async function sendMessage() { busy.value = true; error.value = ''; try { clinical.value = await api<Clinical>(`/patient/appointments/${id}/clinical/messages`, 'POST', { body: message.value }); message.value = '' } catch (failure) { error.value = failure instanceof Error ? failure.message : '消息发送失败' } finally { busy.value = false } }
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
    <section v-if="clinical?.result"><h3>医生结果（只读）</h3><p>{{ clinical.result.body }}</p><p>作者：{{ clinical.result.authorId }} · 版本：{{ clinical.result.versionNo }} · 时间：{{ new Date(clinical.result.createdAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) }}</p></section>
    <section v-if="clinical?.corrections.length"><h3>追加更正</h3><p v-for="correction in clinical.corrections" :key="correction.reason + correction.body">{{ correction.reason }}：{{ correction.body }}</p></section>
    <form v-if="clinical?.consultation?.state !== 'CLOSED'" @submit.prevent="sendMessage"><label for="message">图文消息</label><textarea id="message" v-model="message" maxlength="10000" required></textarea><button :disabled="busy">发送消息</button></form>
  </section>
</template>
