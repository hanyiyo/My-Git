<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'

interface Payment { id: string; amountMinor: number; state: string; paidAt: number }
interface Refund { id: string; amountMinor: number; reason: string; state: string; attempts: number; lastError: string | null; providerReference: string | null }
interface Order {
  id: string; doctorName: string; departmentName: string; clinicDate: string; session: string
  serviceMode: string; feeMinor: number; holdExpiresAt: number; state: string; credential: string | null
  cancellationReason: string | null; payment: Payment | null; refund: Refund | null
}

const orders = ref<Order[]>([]), error = ref(''), busy = ref(false)
const stateLabels: Record<string, string> = { PENDING_PAYMENT: '待支付', CONFIRMED: '已确认', CANCELLED: '已取消', EXPIRED: '已过期' }
const refundLabels: Record<string, string> = { REFUND_PENDING: '退款处理中', REFUNDED: '已退款', REFUND_FAILED: '退款待重试' }
function sessionLabel(value: string) { return value === 'AM' ? '上午' : '下午' }
function modeLabel(value: string) { return value === 'ONLINE' ? '线上' : '线下' }
function money(value: number) { return `¥${(value / 100).toFixed(2)}` }
async function load() {
  busy.value = true; error.value = ''
  try { orders.value = await api<Order[]>('/patient/appointments') }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '订单加载失败，请重试' }
  finally { busy.value = false }
}
async function simulatePayment(order: Order) {
  busy.value = true; error.value = ''
  try { await api(`/patient/appointments/${order.id}/simulate-payment`, 'POST'); await load() }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '支付失败，请重试'; busy.value = false }
}
async function cancel(order: Order) {
  busy.value = true; error.value = ''
  try { await api(`/patient/appointments/${order.id}/cancel`, 'POST'); await load() }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '取消失败，请重试'; busy.value = false }
}
onMounted(load)
</script>
<template>
  <RouterLink to="/patient">返回工作台</RouterLink>
  <section class="card catalog orders"><h2>我的预约订单</h2>
    <p v-if="error" role="alert" class="notice">{{ error }}</p>
    <p v-if="busy && !orders.length" class="hint">订单加载中…</p>
    <p v-if="!busy && !orders.length" class="hint">暂无预约订单。</p>
    <ul class="resource-list"><li v-for="order in orders" :key="order.id">
      <strong>{{ order.doctorName }} · {{ order.departmentName }}</strong>
      <p>{{ order.clinicDate }} {{ sessionLabel(order.session) }} · {{ modeLabel(order.serviceMode) }} · {{ money(order.feeMinor) }}</p>
      <p>订单号：{{ order.id }}</p>
      <p>状态：{{ stateLabels[order.state] ?? order.state }}</p>
      <p v-if="order.state === 'PENDING_PAYMENT'">支付截止：{{ new Date(order.holdExpiresAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) }}</p>
      <p v-if="order.credential">预约凭证：{{ order.credential }}</p>
      <p v-if="order.refund">{{ refundLabels[order.refund.state] ?? order.refund.state }}</p>
      <div class="catalog-links"><button v-if="order.state === 'PENDING_PAYMENT'" type="button" :disabled="busy" @click="simulatePayment(order)">模拟支付</button><button v-if="order.state === 'PENDING_PAYMENT' || order.state === 'CONFIRMED'" type="button" class="secondary" :disabled="busy" @click="cancel(order)">取消预约</button></div>
    </li></ul>
  </section>
</template>
