<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'

interface Payment { id: string; amountMinor: number; state: string; mode: string; paidAt: number }
interface Refund { id: string; amountMinor: number; reason: string; state: string; attempts: number; nextAttemptAt: number | null; lastError: string | null; providerReference: string | null }
interface Reception { state: string; queueGroup: number; queuedAt: number; checkedInAt: number; restorationCount: number; calledAt: number | null; startedAt: number | null; missedAt: number | null; completedAt: number | null }
interface Order {
  id: string; doctorName: string; departmentName: string; clinicDate: string; session: string
  serviceMode: string; feeMinor: number; holdExpiresAt: number; state: string; credential: string | null
  cancellationReason: string | null; cancelledAt: number | null; payment: Payment | null; refund: Refund | null; reception: Reception | null
}

const orders = ref<Order[]>([]), error = ref(''), busy = ref(false)
const stateLabels: Record<string, string> = { PENDING_PAYMENT: '待支付', CONFIRMED: '已确认', CANCELLED: '已取消', EXPIRED: '已过期' }
const refundLabels: Record<string, string> = { REFUND_PENDING: '退款处理中', REFUNDED: '已退款', REFUND_FAILED: '退款待重试' }
const refundReasonLabels: Record<string, string> = { PATIENT_CANCELLATION: '患者取消', LATE_PAYMENT: '迟到付款', CLINIC_SUSPENSION: '停诊' }
const refundErrorLabels: Record<string, string> = { REFUND_PROVIDER_UNAVAILABLE: '退款服务暂不可用，系统将自动重试' }
const queueStateLabels: Record<string, string> = { WAITING: '候诊中', CALLED: '已叫号', IN_CONSULTATION: '接诊中', MISSED: '已过号', COMPLETED: '已结束' }
const queueGroupLabels: Record<number, string> = { 1: '预约优先', 2: '现场挂号', 3: '恢复排队' }
function refundLabel(refund: Refund) {
  if (refund.state === 'REFUND_FAILED' && refund.nextAttemptAt === null) return '退款失败（已达重试上限）'
  return refundLabels[refund.state] ?? refund.state
}
function refundErrorLabel(refund: Refund) {
  if (refund.lastError && refundErrorLabels[refund.lastError]) return refundErrorLabels[refund.lastError]
  return refund.nextAttemptAt === null ? '退款处理已达重试上限，请人工处理' : '退款处理遇到可重试错误'
}
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
async function checkIn(order: Order) {
  busy.value = true; error.value = ''
  try { await api(`/patient/appointments/${order.id}/check-in`, 'POST'); await load() }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '签到失败，请重试'; busy.value = false }
}
async function restoreQueue(order: Order) {
  busy.value = true; error.value = ''
  try { await api(`/patient/appointments/${order.id}/restore-queue`, 'POST'); await load() }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '恢复排队失败，请重试'; busy.value = false }
}
onMounted(load)
</script>
<template>
  <RouterLink to="/patient">返回工作台</RouterLink>
  <section class="card catalog orders"><h2>我的预约订单</h2>
    <p v-if="error" role="alert" class="notice">{{ error }}</p>
    <p v-if="busy && !orders.length" class="hint">订单加载中…</p>
    <p v-if="!busy && !orders.length" class="hint">暂无预约订单。</p>
    <ul class="resource-list"><li v-for="order in orders" :key="order.id" :data-appointment-id="order.id">
      <strong>{{ order.doctorName }} · {{ order.departmentName }}</strong>
      <p>{{ order.clinicDate }} {{ sessionLabel(order.session) }} · {{ modeLabel(order.serviceMode) }} · {{ money(order.feeMinor) }}</p>
      <p>预约登记编号：{{ order.id }}</p>
      <p>状态：{{ stateLabels[order.state] ?? order.state }}</p>
      <p v-if="order.payment">支付事实：{{ order.payment.state === 'SUCCEEDED' ? '已支付' : order.payment.state }} {{ money(order.payment.amountMinor) }}（{{ order.payment.mode === 'SIMULATED' ? '模拟支付' : order.payment.mode }}）</p>
      <p v-if="order.payment">支付时间：{{ new Date(order.payment.paidAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) }}</p>
      <p v-if="order.cancellationReason">取消事实：{{ order.cancellationReason === 'PATIENT_CANCELLATION' ? '患者取消' : order.cancellationReason }}<span v-if="order.cancelledAt">（{{ new Date(order.cancelledAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) }}）</span></p>
      <div v-if="order.reception" class="notice"><p>签到与候诊：{{ queueStateLabels[order.reception.state] ?? order.reception.state }} · {{ queueGroupLabels[order.reception.queueGroup] ?? '候诊组' }}</p><p>签到时间：{{ new Date(order.reception.checkedInAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) }}</p><p v-if="order.reception.calledAt">叫号时间：{{ new Date(order.reception.calledAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) }}</p><p v-if="order.reception.missedAt">过号时间：{{ new Date(order.reception.missedAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) }}</p></div>
      <p v-if="order.state === 'PENDING_PAYMENT'">支付截止：{{ new Date(order.holdExpiresAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) }}</p>
      <p v-if="order.credential">预约凭证：{{ order.credential }}</p>
      <div v-if="order.refund" class="notice"><p>{{ refundLabel(order.refund) }}</p><p>退款编号：{{ order.refund.id }} · 金额：{{ money(order.refund.amountMinor) }}</p><p>退款原因：{{ refundReasonLabels[order.refund.reason] ?? order.refund.reason }} · 尝试次数：{{ order.refund.attempts }}</p><p v-if="order.refund.providerReference">支付渠道参考号：{{ order.refund.providerReference }}</p><p v-if="order.refund.lastError">最近状态：{{ refundErrorLabel(order.refund) }}</p></div>
      <div class="catalog-links"><RouterLink v-if="order.state === 'CONFIRMED' || order.state === 'COMPLETED'" :to="`/patient/orders/${order.id}/clinical`">问诊材料与结果</RouterLink><button v-if="order.state === 'PENDING_PAYMENT'" type="button" :disabled="busy" @click="simulatePayment(order)">模拟支付</button><button v-if="order.state === 'CONFIRMED' && !order.reception" type="button" :disabled="busy" @click="checkIn(order)">签到</button><button v-if="order.reception?.state === 'MISSED' && order.reception.restorationCount === 0" type="button" :disabled="busy" @click="restoreQueue(order)">恢复排队</button><button v-if="order.state === 'PENDING_PAYMENT' || order.state === 'CONFIRMED'" type="button" class="secondary" :disabled="busy" @click="cancel(order)">取消预约</button></div>
    </li></ul>
  </section>
</template>
