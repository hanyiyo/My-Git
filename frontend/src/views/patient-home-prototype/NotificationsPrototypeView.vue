<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from './PrototypeIcon.vue'
import { notificationNow, type NotificationTab, type PrototypeNotice } from './notification-data'
import type { PrototypeOrder } from './order-data'

const props = defineProps<{ receipts: PrototypeNotice[]; orders: PrototypeOrder[] }>()
const emit = defineEmits<{ 'update:receipts': [items: PrototypeNotice[]]; order: [id: string] }>()
const route = useRoute(), router = useRouter()
const tab = computed<NotificationTab>(() => route.query.tab === 'news' ? 'news' : 'system')
const onlyUnread = ref(false), loading = ref(true), loaded = ref(false), error = ref(''), feedback = ref('')
const demo = ref(String(route.query.demo || 'normal'))
const detailDialog = ref<HTMLDialogElement>(), confirmDialog = ref<HTMLDialogElement>(), cancelButton = ref<HTMLButtonElement>()
const pending = ref<{ tab: NotificationTab; id?: string } | null>(null)
let loadTimer: number | undefined, feedbackTimer: number | undefined
let detailTrigger: HTMLElement | null = null, confirmTrigger: HTMLElement | null = null
const records = computed(() => props.receipts.filter(item => item.tab === tab.value && !item.deleted))
const unread = computed(() => records.value.filter(item => !item.read).length)
const visible = computed(() => records.value.filter(item => !onlyUnread.value || !item.read).sort((a, b) => b.createdAt.localeCompare(a.createdAt)))
const selected = computed(() => props.receipts.find(item => item.id === route.query.notice && !item.deleted))
const selectedOrder = computed(() => props.orders.find(order => order.id === selected.value?.orderId))
const pendingItems = computed(() => props.receipts.filter(item => !item.deleted && item.tab === pending.value?.tab && (!pending.value.id || item.id === pending.value.id)))
const pendingImportant = computed(() => pendingItems.value.some(item => item.priority === 'high'))
const confirmTitle = computed(() => pending.value?.id ? '确认删除这条通知？' : `确认清空当前标签下的 ${pendingItems.value.length} 条通知？`)

function isImportant(item: PrototypeNotice) {
  if (item.priority !== 'high' || !item.importantUntil || demo.value === 'expired' || item.importantUntil <= notificationNow) return false
  const order = props.orders.find(order => order.id === item.orderId)
  if (item.category === '签到提醒') return order?.status === 'PENDING_VISIT'
  if (item.category === '替诊通知') return order && !['CANCELLED', 'COMPLETED', 'EXPIRED'].includes(order.status)
  return true
}
const groups = computed(() => {
  const important = visible.value.filter(isImportant)
  const regular = visible.value.filter(item => !isImportant(item))
  return [
    { title: '重要提醒', items: important, important: true },
    { title: '今天', items: regular.filter(item => item.createdAt.startsWith('2026-11-10')), important: false },
    { title: '昨天', items: regular.filter(item => item.createdAt.startsWith('2026-11-09')), important: false },
    { title: '更早', items: regular.filter(item => item.createdAt.slice(0, 10) < '2026-11-09'), important: false },
  ].filter(group => group.items.length)
})
function formatTime(value: string) { return `${value.slice(5, 10).replace('-', '月')}日 ${value.slice(11, 16)}` }
function updateReceipts(ids: string[], change: Partial<PrototypeNotice>) {
  emit('update:receipts', props.receipts.map(item => ids.includes(item.id) ? { ...item, ...change } : item))
}
function notify(message: string) {
  feedback.value = message
  window.clearTimeout(feedbackTimer)
  feedbackTimer = window.setTimeout(() => { feedback.value = '' }, 3000)
}
function markRead(item: PrototypeNotice) { if (!item.read) updateReceipts([item.id], { read: true }) }
function markAllRead() { updateReceipts(records.value.map(item => item.id), { read: true }); notify('当前标签的通知已全部标为已读') }
function setTab(value: NotificationTab) { onlyUnread.value = false; void router.push({ query: { tab: value } }) }
function openDetail(item: PrototypeNotice) {
  detailTrigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
  void router.push({ query: { ...route.query, tab: item.tab, notice: item.id } })
}
function restoreFocus(element: HTMLElement | null) {
  if (element?.isConnected) element.focus()
  else document.querySelector<HTMLButtonElement>('.ph-notice-tabs [aria-selected="true"]')?.focus()
}
function closeDetail() {
  detailDialog.value?.close()
  if (route.query.notice) { const { notice, ...query } = route.query; void router.replace({ query }) }
  restoreFocus(detailTrigger)
}
async function syncDetail() {
  if (!loaded.value || loading.value) return
  if (!route.query.notice) { detailDialog.value?.close(); return }
  const receipt = props.receipts.find(item => item.id === route.query.notice)
  if (receipt && receipt.tab !== tab.value) { await router.replace({ query: { ...route.query, tab: receipt.tab } }); return }
  if (receipt && !receipt.deleted) markRead(receipt)
  await nextTick()
  if (!detailDialog.value?.open) detailDialog.value?.showModal()
}
function reload(fail = false, initial = false) {
  window.clearTimeout(loadTimer)
  loading.value = true; error.value = ''
  if (initial) loaded.value = false
  loadTimer = window.setTimeout(() => {
    loading.value = false
    if (fail) error.value = '通知暂时加载失败，请稍后重试。'
    else { loaded.value = true; notify('通知已更新'); void syncDetail() }
  }, 220)
}
function changeDemo() {
  if (demo.value === 'error') reload(true, true)
  else if (demo.value === 'refresh-error') reload(true)
  else reload()
}
async function requestDelete(id?: string) {
  confirmTrigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
  pending.value = { tab: tab.value, id }
  await nextTick(); confirmDialog.value?.showModal(); cancelButton.value?.focus()
}
function cancelDelete() { confirmDialog.value?.close(); pending.value = null; restoreFocus(confirmTrigger) }
function confirmDelete() {
  const ids = pendingItems.value.map(item => item.id)
  updateReceipts(ids, { deleted: true })
  const clearing = !pending.value?.id
  confirmDialog.value?.close(); pending.value = null
  if (ids.includes(String(route.query.notice || ''))) closeDetail()
  else restoreFocus(confirmTrigger)
  notify(clearing ? `已清空 ${ids.length} 条通知` : '通知已删除')
}
function viewOrder() { if (selectedOrder.value) { detailDialog.value?.close(); emit('order', selectedOrder.value.id) } }
watch(() => [route.query.notice, tab.value], () => { void syncDetail() })
onMounted(() => reload(demo.value === 'error'))
onUnmounted(() => { window.clearTimeout(loadTimer); window.clearTimeout(feedbackTimer) })
</script>

<template>
  <section class="ph-notifications-page" data-testid="notifications-page" aria-labelledby="notifications-title">
    <header class="ph-notice-heading"><div><span class="ph-eyebrow">PATIENT SERVICES · NOTIFICATIONS</span><h1 id="notifications-title">站内通知</h1><p>就诊的每个重要变化，都在这里为你留好提醒。</p></div><span class="ph-notice-heading-icon"><Icon name="bell" /></span></header>
    <div class="ph-notice-workspace">
      <div class="ph-notice-controls"><div class="ph-notice-toolbar">
        <nav class="ph-notice-tabs" role="tablist" aria-label="通知分类"><button v-for="entry in ([{ id: 'system', name: '系统通知', icon: 'bell' }, { id: 'news', name: '医院新闻', icon: 'file' }] as const)" :key="entry.id" type="button" role="tab" :aria-selected="tab === entry.id" @click="setTab(entry.id)"><Icon :name="entry.icon" />{{ entry.name }}<span v-if="receipts.filter(item => item.tab === entry.id && !item.read && !item.deleted).length">{{ receipts.filter(item => item.tab === entry.id && !item.read && !item.deleted).length }}</span></button></nav>
        <div class="ph-notice-tools"><button type="button" :disabled="!unread || !loaded" @click="markAllRead"><Icon name="scan" />全部标为已读</button><button type="button" class="ph-notice-delete" :disabled="!records.length || !loaded" @click="requestDelete()"><Icon name="trash" />清空</button></div>
      </div>
      <div class="ph-notice-filterbar"><div class="ph-notice-filters" role="group" aria-label="通知阅读筛选"><button type="button" :aria-pressed="!onlyUnread" @click="onlyUnread = false">全部</button><button type="button" :aria-pressed="onlyUnread" @click="onlyUnread = true">未读</button></div><span><b>{{ unread }}</b> 条未读 <span class="ph-notice-unread-desktop"><i>·</i> {{ tab === 'system' ? '与你的就诊和账户有关' : '医院公告与服务动态' }}</span></span><button class="ph-notice-refresh" type="button" :disabled="loading" @click="reload()"><Icon name="refresh" />刷新</button></div>
      </div>
      <div v-if="loading" class="ph-notice-loading" role="status"><Icon name="refresh" />正在加载通知…</div>
      <div v-if="error" class="ph-notice-error" role="alert"><Icon name="info" /><span>{{ error }}<small v-if="loaded">已展示的内容仍可继续浏览。</small></span><button class="ph-soft-button" type="button" @click="reload()">重新加载</button></div>
      <div v-if="loaded" class="ph-notice-content" :aria-busy="loading">
        <section v-for="group in groups" :key="group.title" :class="['ph-notice-group', { important: group.important }]" :aria-label="group.title"><div class="ph-notice-group-title"><h2><Icon v-if="group.important" name="warning" />{{ group.title }}</h2><span>{{ group.items.length }} 条{{ group.important ? '需要留意' : '' }}</span></div>
          <article v-for="item in group.items" :key="item.id" :data-testid="`notice-${item.id}`" :class="['ph-notice-row', { unread: !item.read }]">
            <button class="ph-notice-open" type="button" :aria-label="`阅读：${item.title}`" @click="openDetail(item)"><span :class="['ph-notice-type-icon', { important: group.important }]"><Icon :name="item.icon" /></span><span class="ph-notice-copy"><span class="ph-notice-meta"><span>{{ item.category }}</span><b v-if="group.important">重要</b><time :datetime="item.createdAt">{{ formatTime(item.createdAt) }}</time></span><span class="ph-notice-row-title">{{ item.title }}<i v-if="!item.read" aria-label="未读"></i></span><span class="ph-notice-summary">{{ item.summary }}</span></span><Icon class="ph-notice-chevron" name="chevron" /></button>
            <div class="ph-notice-row-actions"><button v-if="!item.read" type="button" :aria-label="`标为已读：${item.title}`" @click="markRead(item)">标为已读</button><button class="ph-notice-delete" type="button" :aria-label="`删除：${item.title}`" @click="requestDelete(item.id)"><Icon name="trash" /><span>删除</span></button></div>
          </article>
        </section>
        <div v-if="!visible.length" class="ph-notice-empty"><span><Icon :name="onlyUnread ? 'scan' : 'bell'" /></span><h2>{{ onlyUnread && records.length ? '暂无未读通知' : '暂无通知' }}</h2><p>{{ onlyUnread && records.length ? '重要信息已经看过了，愿你就诊顺利。' : '新的就诊提醒和医院动态，会在这里与你见面。' }}</p><button v-if="onlyUnread" class="ph-soft-button" type="button" @click="onlyUnread = false">查看全部</button></div>
        <p v-if="visible.length" class="ph-notice-list-end">以上是当前全部{{ tab === 'system' ? '系统通知' : '医院动态' }}</p>
      </div>
    </div>
    <p class="ph-notice-footnote"><Icon name="shield" />{{ tab === 'system' ? '通知仅作信息提醒，就诊与费用状态请以关联订单为准。' : '医院新闻仅供浏览，不影响你的挂号与系统通知未读数。' }}</p>
    <p v-if="feedback" class="ph-notice-toast" role="status">{{ feedback }}</p>
    <details class="ph-notice-demo"><summary>原型演示 · 虚构数据，刷新重置</summary><label>演示状态<select v-model="demo" aria-label="通知演示状态" @change="changeDemo"><option value="normal">正常</option><option value="error">首次加载失败</option><option value="refresh-error">刷新失败（保留内容）</option><option value="expired">重要提醒已到期</option></select></label><p>暂无通知可通过“清空”演示；暂无未读可通过“全部标为已读”演示。</p></details>

    <dialog ref="detailDialog" class="ph-notice-detail" aria-labelledby="notice-detail-title" @cancel.prevent="closeDetail">
      <header><span><Icon :name="selected?.tab === 'news' ? 'file' : 'bell'" />{{ selected?.tab === 'news' ? '医院新闻' : '通知详情' }}</span><button class="ph-icon-button" type="button" aria-label="关闭通知详情" @click="closeDetail"><Icon name="close" /></button></header>
      <template v-if="selected"><div class="ph-notice-detail-body"><div class="ph-notice-detail-tags"><span>{{ selected.category }}</span><b v-if="isImportant(selected)">重要提醒</b><small>已读</small></div><h2 id="notice-detail-title">{{ selected.title }}</h2><p class="ph-notice-detail-source">{{ selected.source }} <i>·</i> {{ formatTime(selected.createdAt) }}</p><div class="ph-notice-detail-text"><p v-for="paragraph in selected.body" :key="paragraph">{{ paragraph }}</p></div><section v-if="selectedOrder" class="ph-notice-related"><h3><Icon name="file" />关联挂号</h3><strong>{{ selectedOrder.doctor }} <span>· {{ selectedOrder.department }}</span></strong><p>{{ selectedOrder.dateLabel }} {{ selectedOrder.time }}</p><p>{{ selectedOrder.mode }} · {{ selectedOrder.credential }}</p><small>当前业务状态以订单页为准</small></section><p class="ph-notice-detail-boundary"><Icon name="shield" />{{ selected.tab === 'system' ? '阅读和删除通知不会改变你的挂号或费用状态。' : '医院发布内容，仅供浏览。' }}</p></div><footer><button class="ph-notice-delete" type="button" @click="requestDelete(selected.id)"><Icon name="trash" />删除{{ selected.tab === 'news' ? '此条新闻' : '通知' }}</button><button v-if="selectedOrder" class="ph-primary" type="button" @click="viewOrder">查看订单 <Icon name="arrow" /></button></footer></template>
      <div v-else class="ph-notice-empty"><Icon name="info" /><h2 id="notice-detail-title">该通知已删除或暂不可用</h2><p>可以返回列表查看其他通知。</p><button class="ph-soft-button" type="button" @click="closeDetail">返回列表</button></div>
    </dialog>
    <dialog ref="confirmDialog" class="ph-notice-confirm" aria-labelledby="notice-confirm-title" @cancel.prevent="cancelDelete"><span class="ph-notice-confirm-icon"><Icon name="trash" /></span><h2 id="notice-confirm-title">{{ confirmTitle }}</h2><p>{{ pending?.id ? '删除后，该条内容将从你的列表中移除。' : `将移除${pending?.tab === 'news' ? '医院新闻' : '系统通知'}中的全部内容，包括已读和未读，不受当前筛选影响。` }}</p><p v-if="pendingImportant">删除后仍可在我的挂号查看相关业务状态。</p><small>仅影响你的收件列表，关联业务记录与医院发布原文保留。</small><div><button ref="cancelButton" class="ph-soft-button" type="button" @click="cancelDelete">取消</button><button class="ph-danger-button" type="button" @click="confirmDelete">{{ pending?.id ? '确认删除' : '确认清空' }}</button></div></dialog>
  </section>
</template>

<style src="./notifications-prototype.css"></style>
