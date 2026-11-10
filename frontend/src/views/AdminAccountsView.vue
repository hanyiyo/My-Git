<script setup lang="ts">
import { ref } from 'vue'
import { api } from '../api'
const displayName = ref(''), phoneNumber = ref(''), identifier = ref(''), password = ref('')
const error = ref(''), status = ref(''), busy = ref(false)
async function create() {
  busy.value = true; error.value = ''; status.value = ''
  try {
    await api('/admin/accounts', 'POST', { displayName: displayName.value, phoneNumber: phoneNumber.value, identifier: identifier.value, password: password.value, role: 'PATIENT' })
    status.value = `已添加患者账户：${displayName.value}`
    displayName.value = ''; phoneNumber.value = ''; identifier.value = ''; password.value = ''
  } catch (failure) { error.value = failure instanceof Error ? failure.message : '添加失败' }
  finally { busy.value = false }
}
</script>
<template>
  <RouterLink to="/admin">返回管理员工作台</RouterLink>
  <section class="card"><h2>手动添加患者</h2><p class="hint">管理员可为现场登记或线下建档的患者创建账户。后续健康资料在患者授权后再补充。</p>
    <p v-if="error" role="alert">{{ error }}</p><p v-if="status" role="status">{{ status }}</p>
    <form @submit.prevent="create">
      <label for="patient-display-name">患者姓名</label><input id="patient-display-name" v-model="displayName" autocomplete="name" required maxlength="120">
      <label for="patient-phone-number">手机号</label><input id="patient-phone-number" v-model="phoneNumber" type="tel" autocomplete="tel" pattern="1[3-9][0-9]{9}" required maxlength="11">
      <label for="patient-identifier">登录账号</label><input id="patient-identifier" v-model="identifier" autocomplete="username" required maxlength="190">
      <label for="patient-password">初始密码</label><input id="patient-password" v-model="password" type="password" autocomplete="new-password" minlength="12" maxlength="72" required>
      <button :disabled="busy">{{ busy ? '添加中…' : '添加患者账户' }}</button>
    </form>
  </section>
</template>
