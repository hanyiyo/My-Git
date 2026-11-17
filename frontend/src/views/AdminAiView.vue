<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
interface Call { taskId: string; provider: string; purpose: string; state: string; attempt: number; durationMs: number; errorCode: string | null; createdAt: number; modelVersion: string; promptVersion: string }
const calls = ref<Call[]>([]), error = ref('')
async function load() { try { calls.value = await api<Call[]>('/admin/ai/calls') } catch (failure) { error.value = failure instanceof Error ? failure.message : '加载失败' } }
onMounted(load)
</script>
<template>
  <RouterLink to="/admin">返回管理工作台</RouterLink>
  <section class="card catalog"><h2>AI 调用元数据</h2><p>仅展示调用元数据，不展示症状正文或附件。</p>
    <p v-if="error" role="alert">{{ error }}</p><button type="button" @click="load">刷新调用记录</button>
    <ul class="resource-list"><li v-for="item in calls" :key="item.taskId + ':' + item.attempt">
      <p>任务：{{ item.taskId }} · {{ item.provider }} · {{ item.purpose }}</p>
      <p>{{ item.state }} · 第 {{ item.attempt }} 次 · {{ item.durationMs }} ms · {{ item.errorCode ?? '无错误' }}</p>
      <p>模型：{{ item.modelVersion }} · 提示版本：{{ item.promptVersion }} · {{ new Date(item.createdAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) }}</p>
    </li></ul>
  </section>
</template>
