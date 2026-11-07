<script setup lang="ts">
import { ref } from 'vue'
import { api } from '../api'
interface Answer { mode: string; answer: string; sources: string[] }
const question = ref(''), answer = ref<Answer | null>(null), error = ref(''), busy = ref(false)
async function ask() {
  busy.value = true; error.value = ''; answer.value = null
  try { answer.value = await api<Answer>('/assistant/business?question=' + encodeURIComponent(question.value)) }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '业务咨询暂不可用' }
  finally { busy.value = false }
}
</script>
<template>
  <RouterLink to="/login">返回登录</RouterLink>
  <section class="card catalog">
    <h2>医院业务咨询</h2>
    <p class="hint">只回答医院地址、服务流程和科室职责等管理员维护的信息，不提供诊断或处方。</p>
    <form @submit.prevent="ask">
      <label for="question">咨询内容</label>
      <input id="question" v-model="question" maxlength="500" required placeholder="例如：医院地址在哪里？">
      <button :disabled="busy">{{ busy ? '查询中…' : '查询' }}</button>
    </form>
    <p v-if="error" role="alert" class="notice">{{ error }}</p>
    <section v-if="answer" class="notice">
      <h3>业务事实</h3>
      <p>{{ answer.answer }}</p>
      <small>来源：{{ answer.sources.join('、') }} · {{ answer.mode }}</small>
    </section>
  </section>
</template>
