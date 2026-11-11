<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import Icon from './PrototypeIcon.vue'
import PrototypeDatePicker from './PrototypeDatePicker.vue'
import { prototypeDateEnd, prototypeToday } from './prototype-date-options'
import { orderStatusTabs, prototypeOrders, statusForTab, type OrderTab, type PrototypeOrder } from './order-data'

const props = withDefaults(defineProps<{ initialStatus?: string; selectedOrder?: string; orders?: PrototypeOrder[] }>(), { initialStatus: '待就诊' })
const emit = defineEmits<{ consultation: [orderId: string] }>()
const activeTab = ref<OrderTab>(orderStatusTabs.includes(props.initialStatus as OrderTab) ? props.initialStatus as OrderTab : '待就诊')
const appointmentDate = ref('ALL')
const historyDate = ref('ALL')
const orders = ref(props.orders ?? prototypeOrders.map(order => ({ ...order })))
const cancelTarget = ref<PrototypeOrder | null>(null)
const resultTarget = ref<PrototypeOrder | null>(null)

const todayOrders = computed(() => orders.value.filter(order => order.date === prototypeToday && statusForTab(order) === '待就诊'))
const upcomingOrders = computed(() => orders.value.filter(order => order.date > prototypeToday && statusForTab(order) === '待就诊' && (appointmentDate.value === 'ALL' || order.date === appointmentDate.value)).sort((a, b) => a.date.localeCompare(b.date) || a.time.localeCompare(b.time)))
const filteredOrders = computed(() => orders.value.filter(order => statusForTab(order) === activeTab.value && (historyDate.value === 'ALL' || order.date === historyDate.value)).sort((a, b) => b.date.localeCompare(a.date) || a.time.localeCompare(b.time)))
const currentCount = computed(() => activeTab.value === '待就诊' ? todayOrders.value.length + upcomingOrders.value.length : activeTab.value === '全部' ? orders.value.length : filteredOrders.value.length)

function setTab(tab: OrderTab) {
  activeTab.value = tab
  if (tab === '待就诊') appointmentDate.value = 'ALL'
  if (tab !== '待就诊') historyDate.value = 'ALL'
}
function statusLabel(order: PrototypeOrder) {
  const labels: Record<PrototypeOrder['status'], string> = { PENDING_PAYMENT: '待付款', PENDING_VISIT: '待签到', WAITING: '候诊中', MISSED: '已过号', IN_PROGRESS: '就诊中', COMPLETED: '已完成', CANCELLED: '已取消', EXPIRED: '已过期' }
  return labels[order.status]
}
function checkIn(order: PrototypeOrder) {
  if (order.status !== 'PENDING_VISIT' || order.checkinOpen === false) return
  order.status = 'WAITING'; order.queueNumber = order.mode === '线上问诊' ? 'B012' : 'A018'; order.queuePosition = '第 6 位'; order.ahead = 5; order.waitMinutes = 18; order.roomAvailable = order.mode === '线上问诊'
}
function restore(order: PrototypeOrder) {
  if (order.status !== 'MISSED' || order.restoreUsed) return
  order.status = 'WAITING'; order.restoreUsed = true; order.queueNumber = 'A021'; order.queuePosition = '恢复排队 · 第 9 位'; order.ahead = 8; order.waitMinutes = 26; order.roomAvailable = order.mode === '线上问诊'
}
function pay(order: PrototypeOrder) {
  if (order.status === 'PENDING_PAYMENT') order.status = 'PENDING_VISIT'
}
function requestCancel(order: PrototypeOrder) {
  if (['PENDING_PAYMENT', 'PENDING_VISIT', 'WAITING', 'MISSED'].includes(order.status)) cancelTarget.value = order
}
function confirmCancel() {
  if (!cancelTarget.value) return
  cancelTarget.value.status = 'CANCELLED'; cancelTarget.value.reason = '患者主动取消挂号。'; cancelTarget.value = null
}
function openConsultation(order: PrototypeOrder) {
  if (order.mode === '线上问诊' && (order.status === 'WAITING' || order.status === 'IN_PROGRESS')) emit('consultation', order.id)
}
function isCancelable(order: PrototypeOrder) {
  return ['PENDING_PAYMENT', 'PENDING_VISIT', 'WAITING', 'MISSED'].includes(order.status)
}
watch(() => props.initialStatus, value => { if (orderStatusTabs.includes(value as OrderTab)) activeTab.value = value as OrderTab })
watch(() => props.selectedOrder, async id => {
  if (!id) return
  await nextTick()
  const card = Array.from(document.querySelectorAll<HTMLElement>('[data-order-id]')).find(item => item.dataset.orderId === id)
  card?.scrollIntoView({ block: 'center' }); card?.focus({ preventScroll: true })
}, { immediate: true })
function modeClass(order: PrototypeOrder) {
  return order.mode === '线上问诊' ? 'online' : 'offline'
}
</script>

<template>
  <section class="ph-orders-page" data-testid="orders-page" aria-labelledby="orders-page-title">
    <header class="ph-orders-heading"><div><span class="ph-eyebrow">PATIENT SERVICES · MY APPOINTMENTS</span><h1 id="orders-page-title">我的挂号</h1><p>把今天的候诊、未来的预约和已完成的就诊放在一处查看。</p></div><div class="ph-orders-summary"><strong>{{ currentCount }}</strong><span>{{ activeTab === '待就诊' ? '项待就诊' : '项记录' }}</span><small>虚构演示数据 · 状态随操作更新</small></div></header>

    <p v-if="selectedOrder" class="ph-order-located" role="status"><Icon name="file" />已定位通知关联的挂号单，当前状态以此页面为准。</p>
    <nav class="ph-order-status-slider" role="tablist" aria-label="挂号状态"><button v-for="tab in orderStatusTabs" :key="tab" type="button" role="tab" :aria-selected="activeTab === tab" :class="{ active: activeTab === tab }" @click="setTab(tab)">{{ tab }}</button></nav>

    <template v-if="activeTab === '待就诊'">
      <section class="ph-orders-section ph-orders-today" aria-labelledby="today-orders-title"><div class="ph-orders-section-heading"><div><span class="ph-eyebrow">TODAY'S VISITS · 今日主视图</span><h2 id="today-orders-title">今日挂号</h2></div><span>{{ todayOrders.length }} 项</span></div><div v-if="todayOrders.length" class="ph-orders-list"><article v-for="order in todayOrders" :key="order.id" :data-order-id="order.id" :data-selected="order.id === selectedOrder" tabindex="-1" class="ph-order-card ph-order-card-primary"><div class="ph-order-card-top"><div class="ph-order-badges"><span :class="['ph-order-type-tag', modeClass(order)]">{{ order.mode === '线上问诊' ? '线上问诊单' : '线下面诊号' }}</span><span class="ph-order-source-tag">{{ order.source }}</span><span class="ph-order-status-tag">{{ statusLabel(order) }}</span></div><button v-if="isCancelable(order)" class="ph-order-cancel-link" type="button" @click="requestCancel(order)">取消挂号</button></div><div class="ph-order-card-main"><div class="ph-order-doctor"><span class="ph-order-avatar">{{ order.doctor[0] }}</span><div><h3>{{ order.doctor }} <small>{{ order.title }}</small></h3><p>{{ order.department }} · {{ order.specialty }}</p></div></div><div class="ph-order-time"><strong>{{ order.dateLabel }}</strong><span>{{ order.time }}</span><small>{{ order.room }}</small></div><div class="ph-order-credential"><span>预约凭证</span><strong>{{ order.credential }}</strong><small>挂号费 {{ order.fee }}</small></div></div><div v-if="order.queueNumber" class="ph-order-queue"><div><span>就诊号码</span><strong>{{ order.queueNumber }}</strong></div><div><span>当前排队</span><strong>{{ order.queuePosition }}</strong></div><div><span>前方还有</span><strong>{{ order.ahead }} 人</strong></div><div><span>预计等待</span><strong>约 {{ order.waitMinutes }} 分钟</strong></div></div><div class="ph-order-card-bottom"><div><span>签到窗口：{{ order.checkinWindow }}</span><small v-if="order.status === 'MISSED'">已过号后仅可申请一次恢复排队</small><small v-else-if="order.status === 'WAITING'">已签到，请留意站内叫号通知</small><small v-else>请在窗口内完成签到，后端会再次校验时间</small></div><div class="ph-order-actions"><button v-if="order.status === 'PENDING_VISIT'" class="ph-primary" type="button" :disabled="order.checkinOpen === false" @click="checkIn(order)">{{ order.checkinOpen === false ? '暂未开放' : '签到' }}</button><button v-if="order.status === 'WAITING'" class="ph-soft-button" type="button" @click="openConsultation(order)">查看候诊</button><button v-if="order.status === 'MISSED'" class="ph-primary" type="button" :disabled="order.restoreUsed" @click="restore(order)">{{ order.restoreUsed ? '已恢复排队' : '申请恢复排队' }}</button><button v-if="order.mode === '线上问诊' && (order.status === 'WAITING' || order.status === 'IN_PROGRESS')" class="ph-soft-button ph-room-action" type="button" @click="openConsultation(order)">进入诊室 <Icon name="arrow" /></button></div></div></article></div><div v-else class="ph-orders-empty"><Icon name="calendar" /><strong>今日暂无待就诊挂号</strong><small>完成预约并支付后，今日的签到和候诊信息会显示在这里。</small></div></section>

      <section class="ph-orders-section ph-orders-upcoming" aria-labelledby="upcoming-orders-title"><div class="ph-orders-section-heading"><div><span class="ph-eyebrow">UPCOMING APPOINTMENTS · 预约安排</span><h2 id="upcoming-orders-title">预约挂号</h2></div><div class="ph-orders-filter"><PrototypeDatePicker v-model="appointmentDate" label="预约日期" min-date="2026-11-11" :max-date="prototypeDateEnd" allow-all /></div></div><div v-if="upcomingOrders.length" class="ph-orders-list ph-orders-list-secondary"><article v-for="order in upcomingOrders" :key="order.id" :data-order-id="order.id" :data-selected="order.id === selectedOrder" tabindex="-1" class="ph-order-card ph-order-card-secondary"><div class="ph-order-card-top"><div class="ph-order-badges"><span :class="['ph-order-type-tag', modeClass(order)]">{{ order.mode === '线上问诊' ? '线上问诊单' : '线下面诊号' }}</span><span class="ph-order-source-tag">{{ order.source }}</span><span class="ph-order-status-tag">{{ statusLabel(order) }}</span></div><button v-if="isCancelable(order)" class="ph-order-cancel-link" type="button" @click="requestCancel(order)">取消挂号</button></div><div class="ph-order-card-main"><div class="ph-order-doctor"><span class="ph-order-avatar">{{ order.doctor[0] }}</span><div><h3>{{ order.doctor }} <small>{{ order.title }}</small></h3><p>{{ order.department }} · {{ order.specialty }}</p></div></div><div class="ph-order-time"><strong>{{ order.dateLabel }}</strong><span>{{ order.time }}</span><small>{{ order.room }}</small></div><div class="ph-order-credential"><span>预约凭证</span><strong>{{ order.credential }}</strong><small>挂号费 {{ order.fee }}</small></div></div><div class="ph-order-card-bottom"><div><span>预约成功，签到将在出诊前 30 分钟开放</span><small>可在就诊前取消挂号</small></div><div class="ph-order-actions"><button class="ph-soft-button" type="button">查看详情</button></div></div></article></div><div v-else class="ph-orders-empty"><Icon name="calendar" /><strong>所选日期暂无预约挂号</strong><small>可以切换月历日期，或继续前往立即挂号。</small></div></section>
    </template>

    <template v-else-if="activeTab === '全部'"><section class="ph-orders-section"><div class="ph-orders-section-heading"><div><span class="ph-eyebrow">ALL APPOINTMENTS · 全部记录</span><h2>全部挂号</h2></div><span>{{ orders.length }} 项</span></div><div class="ph-orders-list"><article v-for="order in orders" :key="order.id" :data-order-id="order.id" :data-selected="order.id === selectedOrder" tabindex="-1" class="ph-order-card ph-order-card-secondary"><div class="ph-order-card-top"><div class="ph-order-badges"><span :class="['ph-order-type-tag', modeClass(order)]">{{ order.mode === '线上问诊' ? '线上问诊单' : '线下面诊号' }}</span><span class="ph-order-source-tag">{{ order.source }}</span><span class="ph-order-status-tag">{{ statusLabel(order) }}</span></div><button v-if="isCancelable(order)" class="ph-order-cancel-link" type="button" @click="requestCancel(order)">取消挂号</button></div><div class="ph-order-card-main"><div class="ph-order-doctor"><span class="ph-order-avatar">{{ order.doctor[0] }}</span><div><h3>{{ order.doctor }} <small>{{ order.title }}</small></h3><p>{{ order.department }} · {{ order.specialty }}</p></div></div><div class="ph-order-time"><strong>{{ order.dateLabel }}</strong><span>{{ order.time }}</span><small>{{ order.room }}</small></div><div class="ph-order-credential"><span>状态</span><strong>{{ statusLabel(order) }}</strong><small>{{ order.reason || order.result || '可在对应状态页查看详情' }}</small></div></div><div class="ph-order-card-bottom"><div><span>{{ order.source }} · {{ order.mode }}</span><small>{{ order.reason || '预约记录保留在患者账户中' }}</small></div><div class="ph-order-actions"><button v-if="order.status === 'PENDING_PAYMENT'" class="ph-primary" type="button" @click="pay(order)">立即付款</button><button v-if="order.status === 'COMPLETED'" class="ph-soft-button" type="button" @click="resultTarget = order">查看就诊结果</button><button v-if="order.mode === '线上问诊' && (order.status === 'WAITING' || order.status === 'IN_PROGRESS')" class="ph-soft-button" type="button" @click="openConsultation(order)">进入诊室</button></div></div></article></div></section></template>

    <template v-else><section class="ph-orders-section"><div class="ph-orders-section-heading"><div><span class="ph-eyebrow">{{ activeTab.toUpperCase() }} · STATUS FILTER</span><h2>{{ activeTab }}</h2></div><div class="ph-orders-filter"><PrototypeDatePicker v-model="historyDate" label="就诊日期" min-date="2026-10-01" max-date="2026-12-31" allow-all /></div></div><div v-if="filteredOrders.length" class="ph-orders-list"><article v-for="order in filteredOrders" :key="order.id" :data-order-id="order.id" :data-selected="order.id === selectedOrder" tabindex="-1" class="ph-order-card ph-order-card-secondary"><div class="ph-order-card-top"><div class="ph-order-badges"><span :class="['ph-order-type-tag', modeClass(order)]">{{ order.mode === '线上问诊' ? '线上问诊单' : '线下面诊号' }}</span><span class="ph-order-source-tag">{{ order.source }}</span><span class="ph-order-status-tag">{{ statusLabel(order) }}</span></div><button v-if="isCancelable(order)" class="ph-order-cancel-link" type="button" @click="requestCancel(order)">取消挂号</button></div><div class="ph-order-card-main"><div class="ph-order-doctor"><span class="ph-order-avatar">{{ order.doctor[0] }}</span><div><h3>{{ order.doctor }} <small>{{ order.title }}</small></h3><p>{{ order.department }} · {{ order.specialty }}</p></div></div><div class="ph-order-time"><strong>{{ order.dateLabel }}</strong><span>{{ order.time }}</span><small>{{ order.room }}</small></div><div class="ph-order-credential"><span>状态</span><strong>{{ statusLabel(order) }}</strong><small>{{ order.reason || order.result || order.credential }}</small></div></div><div class="ph-order-card-bottom"><div><span>{{ order.source }} · {{ order.mode }}</span><small>{{ order.reason || order.result || '挂号记录详情' }}</small></div><div class="ph-order-actions"><button v-if="order.status === 'PENDING_PAYMENT'" class="ph-primary" type="button" @click="pay(order)">立即付款</button><button v-if="order.status === 'COMPLETED'" class="ph-soft-button" type="button" @click="resultTarget = order">查看就诊结果</button><button v-if="order.mode === '线上问诊' && (order.status === 'WAITING' || order.status === 'IN_PROGRESS')" class="ph-soft-button" type="button" @click="openConsultation(order)">进入诊室</button></div></div></article></div><div v-else class="ph-orders-empty"><Icon name="file" /><strong>当前状态暂无挂号记录</strong><small>切换其他状态或调整日期筛选。</small></div></section></template>

    <p class="ph-orders-footnote"><Icon name="shield" /> 订单状态遵循预约、签到、候诊和接诊的独立生命周期；当前页面使用虚构演示数据。</p>

    <div v-if="cancelTarget" class="ph-confirm-overlay" role="dialog" aria-modal="true" aria-labelledby="cancel-order-title"><div class="ph-confirm-dialog"><span class="ph-confirm-icon"><Icon name="info" /></span><h2 id="cancel-order-title">确认取消挂号？</h2><p>取消后该订单将进入“已取消”，本次预约凭证不再保留。{{ cancelTarget.doctor }} · {{ cancelTarget.dateLabel }}</p><div class="ph-confirm-actions"><button class="ph-soft-button" type="button" @click="cancelTarget = null">暂不取消</button><button class="ph-danger-button" type="button" @click="confirmCancel">确认取消</button></div></div></div>
    <div v-if="resultTarget" class="ph-confirm-overlay" role="dialog" aria-modal="true" aria-labelledby="result-title"><div class="ph-result-dialog"><button class="ph-icon-button" aria-label="关闭就诊结果" @click="resultTarget = null"><Icon name="close" /></button><span class="ph-eyebrow">COMPLETED ENCOUNTER</span><h2 id="result-title">{{ resultTarget.doctor }}医生 · 就诊结果</h2><p>{{ resultTarget.result }}</p><small>{{ resultTarget.department }} · {{ resultTarget.dateLabel }} · {{ resultTarget.time }}</small></div></div>
  </section>
</template>
