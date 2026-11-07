<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, download } from '../api'
interface Clinical { consultation: { state: string } | null; materials: { id: string; versionNo: number; symptomText: string; attachments: { id: string; originalName: string }[] }[]; messages: { authorRole: string; body: string }[]; result: { body: string } | null; corrections: { reason: string; body: string }[] }
const route = useRoute(), id = String(route.params.id), clinical = ref<Clinical | null>(null), result = ref(''), reason = ref(''), correction = ref(''), error = ref(''), busy = ref(false)
async function load() { try { clinical.value = await api<Clinical>(`/doctor/clinical/${id}`) } catch (failure) { error.value = failure instanceof Error ? failure.message : '问诊记录加载失败' } }
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
  </section>
</template>
