<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from '../patient-home-prototype/PrototypeIcon.vue'
import { clearDoctorNotifications, deleteDoctorNotification, markAllDoctorNotificationsRead, markDoctorNotificationRead, useReceptionDemo, type DoctorNotification } from './reception-demo'
import './doctor-notifications.css'

const emit = defineEmits<{ navigate: [section: 'applications' | 'schedules' | 'reception', id?: string] }>()
const { state, error, warning, busy } = useReceptionDemo()
const tab = ref<'pending' | 'normal'>('pending')
const filter = ref<'all' | 'unread'>('all')
const selected = ref<DoctorNotification | null>(null)
const detail = ref<HTMLDialogElement>()
const confirm = ref<HTMLDialogElement>()
const pendingDelete = ref<{ id?: string; section: 'pending' | 'normal' } | null>(null)
const records = computed(() => state.value.notifications.filter(n => !n.deleted && n.section === tab.value))
const visible = computed(() => records.value.filter(n => filter.value === 'all' || !n.read))
const unread = computed(() => state.value.notifications.filter(n => !n.deleted && !n.read).length)
const pending = computed(() => state.value.notifications.filter(n => !n.deleted && n.section === 'pending').length)
const processedLabel = (n: DoctorNotification) => n.section === 'pending' ? '待处理' : '普通通知'
function openNotice(n: DoctorNotification) { selected.value = n; void markDoctorNotificationRead(n.id); detail.value?.showModal() }
function closeDetail() { detail.value?.close(); selected.value = null }
function requestDelete(id: string) { pendingDelete.value = { id, section: tab.value }; confirm.value?.showModal() }
function requestClear() { pendingDelete.value = { section: tab.value }; confirm.value?.showModal() }
async function remove() { if (!pendingDelete.value) return; const item = pendingDelete.value; if (item.id) await deleteDoctorNotification(item.id); else await clearDoctorNotifications(item.section); confirm.value?.close(); pendingDelete.value = null; if (selected.value?.id === item.id) closeDetail() }
function go(n: DoctorNotification) { closeDetail(); if (n.action) emit('navigate', n.action, n.relatedId) }
function dateLabel(at: number) { return new Date(at).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }) }
</script>
<template>
  <section class="dn-page" aria-label="医生站内通知">
    <div class="dn-workspace">
      <div class="dn-toolbar"><nav aria-label="通知分区"><button :class="{ active: tab === 'pending' }" @click="tab = 'pending'"><Icon name="shield"/>待处理 <b>{{ pending }}</b></button><button :class="{ active: tab === 'normal' }" @click="tab = 'normal'"><Icon name="bell"/>普通通知</button></nav><div class="dn-tools"><button :disabled="!records.some(n => !n.read) || busy" @click="markAllDoctorNotificationsRead(tab)">全部标为已读</button><button :disabled="!records.length || busy" @click="requestClear">清空</button></div></div>
      <div class="dn-filter"><div><button :class="{ active: filter === 'all' }" @click="filter = 'all'">全部</button><button :class="{ active: filter === 'unread' }" @click="filter = 'unread'">未读</button></div><span>{{ records.length }} 条通知</span><span class="dn-unread-total">未读 <b>{{ unread }}</b></span></div>
      <p v-if="error || warning" class="dn-alert" role="alert">{{ error || warning }}</p>
      <div v-if="visible.length" class="dn-list"><article v-for="notice in visible" :key="notice.id" :class="['dn-row', { unread: !notice.read }]" :data-testid="`doctor-notice-${notice.id}`"><button class="dn-open" @click="openNotice(notice)"><span class="dn-icon"><Icon :name="notice.kind === 'substitution' ? 'doctor' : notice.kind === 'application' ? 'file' : 'bell'"/></span><span class="dn-copy"><span class="dn-meta"><b>{{ processedLabel(notice) }}</b><time>{{ dateLabel(notice.createdAt) }}</time></span><strong>{{ notice.title }}<i v-if="!notice.read" aria-label="未读"/></strong><small>{{ notice.summary }}</small></span><Icon name="chevron"/></button><div class="dn-row-actions"><button v-if="!notice.read" @click="markDoctorNotificationRead(notice.id)">标为已读</button><button class="dn-delete" @click="requestDelete(notice.id)">删除</button></div></article></div>
      <div v-else class="dn-empty"><Icon name="bell"/><h3>{{ filter === 'unread' ? '暂无未读通知' : '暂无通知' }}</h3><p>新的接诊提醒和申请进度会显示在这里。</p><button v-if="filter === 'unread' && records.length" @click="filter = 'all'">查看全部</button></div>
    </div>
    <p class="dn-footnote"><Icon name="shield"/>删除通知只影响个人收件记录，不改变申请、排班或接诊状态；待处理事项仍保留在申请处理。</p>
    <dialog ref="detail" class="dn-detail" aria-label="通知详情" @cancel.prevent="closeDetail"><header><span><Icon name="bell"/>通知详情</span><button aria-label="关闭通知详情" @click="closeDetail"><Icon name="close"/></button></header><template v-if="selected"><div class="dn-detail-body"><div class="dn-detail-tags"><span>{{ processedLabel(selected) }}</span><small>{{ selected.read ? '已读' : '未读' }}</small></div><h2>{{ selected.title }}</h2><p class="dn-source">{{ dateLabel(selected.createdAt) }}</p><div class="dn-body"><p>{{ selected.body }}</p></div><p class="dn-boundary"><Icon name="shield"/>阅读通知不会完成替诊确认或审批。</p></div><footer><button class="dn-delete" @click="requestDelete(selected.id)">删除通知</button><button v-if="selected.action" class="dh-primary" @click="go(selected)">前往处理 <Icon name="arrow"/></button></footer></template></dialog>
    <dialog ref="confirm" class="dn-confirm" aria-label="确认删除通知" @cancel.prevent="confirm?.close()"><h2>{{ pendingDelete?.id ? '确认删除这条通知？' : '确认清空当前分区？' }}</h2><p>只影响你的收件记录，申请、排班和待处理业务仍会保留。</p><div><button class="ds-secondary" @click="confirm?.close()">取消</button><button class="dn-danger" @click="remove">确认{{ pendingDelete?.id ? '删除' : '清空' }}</button></div></dialog>
  </section>
</template>
