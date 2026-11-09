<script setup lang="ts">
import { ref } from 'vue'
import { api } from '../api'
const open=ref(false), question=ref(''), answer=ref(''), error=ref(''), busy=ref(false)
async function ask(){busy.value=true;error.value='';try{answer.value=(await api<{answer:string}>('/assistant/business?question='+encodeURIComponent(question.value))).answer}catch(e){error.value=e instanceof Error?e.message:'导诊助手暂不可用'}finally{busy.value=false}}
</script>
<template><button v-if="!open" class="assistant-fab" type="button" aria-label="打开导诊助手" @click="open=true">AI 导诊</button><aside v-else class="assistant-drawer" aria-label="导诊助手"><button class="secondary" type="button" @click="open=false">关闭</button><h2>导诊助手</h2><p class="hint">可咨询医院流程和科室，也可描述症状获得辅助导诊；不提供诊断或处方。</p><form @submit.prevent="ask"><label for="assistant-question">描述问题或症状</label><textarea id="assistant-question" v-model="question" maxlength="500" required></textarea><button :disabled="busy">{{busy?'分析中…':'获取辅助建议'}}</button></form><p v-if="error" role="alert" class="notice">{{error}}</p><p v-if="answer" class="notice">{{answer}}</p></aside></template>
