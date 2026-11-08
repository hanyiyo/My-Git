<script setup lang="ts">
import { ref, watch, onUnmounted } from 'vue'
import { api, notificationStream } from '../api'
import { useSession } from '../session'
interface Notification { id: number; cursorNo: number; eventType: string; objectId: string; payload: string; readAt: number | null }
const session = useSession(), messages = ref<Notification[]>([]), unread = ref(0), state = ref('正在连接'), error = ref('')
let controller: AbortController | null = null, timer: ReturnType<typeof setTimeout> | undefined, cursor = 0, syncing: Promise<void> | null = null, generation = 0
function sync(current: number) {
  if (syncing) return syncing
  const pending = (async () => {
    let batch: Notification[]
    do {
      batch = await api<Notification[]>(`/notifications?after=${cursor}&limit=100`)
      if (current !== generation) return
      for (const item of batch) {
        cursor = Math.max(cursor, item.cursorNo)
        if (!messages.value.some(value => value.id === item.id)) {
          messages.value.push(item)
          window.dispatchEvent(new CustomEvent('medical-notification', { detail: item }))
        }
      }
    } while (batch.length === 100)
    const count = await api<number>('/notifications/unread'); if (current === generation) { unread.value = count; error.value = '' }
  })().catch(e => { if (current === generation) error.value = e instanceof Error ? e.message : '通知暂时不可用' }).finally(() => { if (syncing === pending) syncing = null })
  syncing = pending
  return pending
}
async function connect(current: number) {
  if (current !== generation || !session.account) return
  controller = new AbortController()
  try {
    await notificationStream(cursor, controller.signal, () => { if (current === generation) { state.value = '实时连接'; void sync(current) } })
  } catch { /* Polling keeps the persisted inbox usable when the stream fails. */ }
  if (current === generation) { state.value = '轮询补齐'; timer = setTimeout(() => connect(current), 10000) }
}
const polling = setInterval(() => { if (session.account) void sync(generation) }, 2000)
function stop() { generation++; controller?.abort(); if (timer) clearTimeout(timer); cursor = 0; messages.value = []; unread.value = 0; syncing = null }
watch(() => session.account?.id, () => { stop(); if (session.account) { const current = generation; void sync(current); void connect(current) } }, { immediate: true })
async function mark(item: Notification) { try { await api(`/notifications/${item.id}/read`, 'POST'); item.readAt = Date.now(); await sync(generation) } catch (e) { error.value = e instanceof Error ? e.message : '标记失败' } }
onUnmounted(() => { stop(); clearInterval(polling) })
</script>
<template><section v-if="session.account" class="card" aria-label="站内通知"><h2>站内通知</h2><p>未读提醒：{{ unread }} · {{ state }}</p><p v-if="error" role="alert">{{ error }}</p>
  <details><summary>查看关键提醒</summary><p v-if="!messages.length">暂无通知</p><ul><li v-for="item in [...messages].reverse()" :key="item.id"><span>{{ item.payload }}</span> <button v-if="!item.readAt" type="button" @click="mark(item)">标为已读</button></li></ul></details>
</section></template>
