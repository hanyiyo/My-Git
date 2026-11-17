<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, download } from '../api'
interface Appointment { id: string; patientId: string; doctorId: string; state: string }
interface Attachment { id: string; originalName: string }
interface Material { id: string; versionNo: number; symptomText: string | null; attachments: Attachment[] }
interface Entry { id: string; body: string; reason?: string }
interface Review { reviewId: string; appointmentId: string; scope: string; content: { materials: Material[]; results: Entry[]; messages: Entry[]; corrections: Entry[] }; flags: { targetId: string; state: string; reason: string }[] }
const appointments = ref<Appointment[]>([]), authorized = ref(false), identifier = ref(''), password = ref(''), permissionReason = ref(''), enabled = ref(true)
const appointmentId = ref(''), scope = ref('RECORD'), reason = ref(''), reviewId = ref(''), content = ref<Review | null>(null), decisionReason = ref(''), error = ref(''), status = ref(''), busy = ref(false)
const integrity = ref<{ valid: boolean; signedEvents: number; legacyUnsignedEvents: number } | null>(null)
async function run(action: () => Promise<void>) { busy.value = true; error.value = ''; status.value = ''; try { await action() } catch (e) { content.value = null; error.value = e instanceof Error ? e.message : '操作失败' } finally { busy.value = false } }
async function load() { appointments.value = await api<Appointment[]>('/admin/medical/appointments'); authorized.value = await api<boolean>('/admin/medical/permission') }
async function permission(create = false) { await run(async () => {
  if (create) await api('/admin/accounts', 'POST', { identifier: identifier.value, password: password.value, role: 'ADMIN' })
  await api('/admin/medical/permissions', 'POST', { identifier: identifier.value, enabled: enabled.value, reason: permissionReason.value }); password.value = ''; status.value = enabled.value ? '已独立授权审核账户' : '已撤回审核权限及已有访问授权'; await load()
}) }
async function request() { await run(async () => { const review = await api<{ id: string }>('/admin/medical/reviews', 'POST', { appointmentId: appointmentId.value, reason: reason.value, scope: scope.value }); reviewId.value = review.id; await read() }) }
async function read() { content.value = null; content.value = await api<Review>(`/admin/medical/reviews/${reviewId.value}/content`) }
async function flag(type: string, id: string, state: string) { await run(async () => { await api('/admin/medical/flags', 'POST', { reviewId: reviewId.value, appointmentId: content.value!.appointmentId, targetType: type, targetId: id, state, reason: decisionReason.value }); await read(); status.value = state === 'LIMITED' ? '已限制展示，原记录保留' : '已解除限制' }) }
async function open(id: string) { await run(async () => { const url = URL.createObjectURL(await download(`/admin/medical/reviews/${reviewId.value}/attachments/${id}`)); window.open(url, '_blank', 'noopener'); setTimeout(() => URL.revokeObjectURL(url), 60000); status.value = '附件下载已记录审计' }) }
async function revoke() { await run(async () => { await api(`/admin/medical/reviews/${reviewId.value}/revoke`, 'POST', { reason: reason.value }); content.value = null; status.value = '已撤回本次访问授权' }) }
onMounted(() => run(load))
</script>
<template>
  <RouterLink to="/admin">返回管理员工作台</RouterLink>
  <section class="card catalog"><h2>医疗内容审核</h2><p v-if="error" role="alert">{{ error }}</p><p role="status">{{ status }}</p>
    <p>当前审核资格：{{ authorized ? '已独立授权' : '未授权；请由另一位管理员授权' }}</p>
    <details><summary>授予或撤回独立审核权限</summary><form @submit.prevent="permission()">
      <label for="reviewer">审核账户</label><input id="reviewer" v-model="identifier" required>
      <label for="permissionReason">独立授权理由</label><input id="permissionReason" v-model="permissionReason" required maxlength="1000">
      <label><input v-model="enabled" type="checkbox">启用审核资格</label><button :disabled="busy">保存审核资格</button>
      <label for="reviewerPassword">新建审核账户的初始密码（至少12字符）</label><input id="reviewerPassword" v-model="password" type="password" minlength="12" autocomplete="new-password">
      <button type="button" :disabled="busy || !identifier || !permissionReason || password.length < 12 || !enabled" @click="permission(true)">新建管理员并独立授权</button>
    </form></details>
    <form @submit.prevent="request"><label for="reviewAppointment">审核预约</label><select id="reviewAppointment" v-model="appointmentId" required><option value="" disabled>请选择预约（只含业务元数据）</option><option v-for="a in appointments" :key="a.id" :value="a.id">{{ a.id }} · {{ a.state }} · 患者 {{ a.patientId }}</option></select>
      <label for="reviewScope">本次访问范围</label><select id="reviewScope" v-model="scope"><option value="RECORD">医疗正文</option><option value="ATTACHMENTS">医疗附件</option><option value="ALL">正文和附件</option></select>
      <label for="reviewReason">本次访问理由</label><textarea id="reviewReason" v-model="reason" maxlength="1000" required></textarea><button :disabled="busy || !authorized">申请30分钟访问并查看</button>
    </form>
    <template v-if="reviewId"><button :disabled="busy" @click="run(read)">重新读取授权内容</button><button :disabled="busy || !reason.trim()" @click="revoke">撤回本次访问授权</button></template>
    <section v-if="content"><h3>授权范围内原始内容</h3><p>仅用于审核取证；受限原文在患者和医生页面显示为限制提示。每次读取和下载均留审计。</p>
      <label for="decisionReason">审核决定理由</label><input id="decisionReason" v-model="decisionReason" maxlength="1000">
      <article v-for="m in content.content.materials" :key="m.id" class="notice"><h4>材料版本 {{ m.versionNo }}</h4><p>{{ m.symptomText }}</p>
        <template v-if="content.scope !== 'ATTACHMENTS'"><button :disabled="busy || !decisionReason.trim()" @click="flag('MATERIAL',m.id,'LIMITED')">限制材料展示</button><button :disabled="busy || !decisionReason.trim()" @click="flag('MATERIAL',m.id,'CLEARED')">解除材料限制</button></template>
        <p v-for="a in m.attachments" :key="a.id"><button @click="open(a.id)">{{ a.originalName }}</button><button :disabled="busy || !decisionReason.trim()" @click="flag('ATTACHMENT',a.id,'LIMITED')">限制附件</button><button :disabled="busy || !decisionReason.trim()" @click="flag('ATTACHMENT',a.id,'CLEARED')">解除附件限制</button></p>
      </article>
      <section v-for="group in [{ type: 'RESULT', name: '医疗结果', items: content.content.results },{ type: 'MESSAGE', name: '图文消息', items: content.content.messages },{ type: 'CORRECTION', name: '追加更正', items: content.content.corrections }]" :key="group.type"><h4>{{ group.name }}</h4><article v-for="entry in group.items" :key="entry.id"><p>{{ entry.reason }} {{ entry.body }}</p><button :disabled="busy || !decisionReason.trim()" @click="flag(group.type,entry.id,'LIMITED')">限制此内容</button><button :disabled="busy || !decisionReason.trim()" @click="flag(group.type,entry.id,'CLEARED')">解除此限制</button></article></section>
      <h4>审核决定记录</h4><p v-for="(f,index) in content.flags" :key="index">{{ f.targetId }}：{{ f.state }} · {{ f.reason }}</p>
    </section>
    <button :disabled="busy" @click="run(async () => { integrity = await api('/admin/medical/audit-integrity') })">校验审计完整性</button><p v-if="integrity">完整性：{{ integrity.valid ? '通过' : '失败，请停止操作并核查' }} · 已签名 {{ integrity.signedEvents }} · 历史未签名 {{ integrity.legacyUnsignedEvents }}</p>
  </section>
</template>
