<script setup lang="ts">
import { computed } from 'vue'
import { formatTime, money, payMedicine, useReceptionDemo } from './reception-demo'
const props = defineProps<{ pendingOnly: boolean }>()
const { state, busy } = useReceptionDemo()
const visits = computed(() => state.value.visits.filter(v => v.owner === 'lin' && v.payment && (!props.pendingOnly || v.payment.status === 'PENDING')))
</script>
<template>
  <section v-if="visits.length" class="rd-patient-medicine" aria-label="联动药费订单"><h2>接诊药费</h2><article v-for="visit in visits" :key="visit.id" :data-payment-id="visit.id"><div><span class="rd-patient-tag">药品{{ visit.payment!.status === 'PENDING' ? '待缴费' : visit.payment!.status === 'PAID' ? '已支付' : '已超时失效' }}</span><strong>许知遥医生 · {{ visit.number }}</strong><p>病历已保存，可在“已完成”查看就诊结果。</p><p v-for="line in visit.result!.medicines" :key="line.medicineId">{{ line.name }} · {{ line.specification }} × {{ line.quantity }} · {{ line.usage }}</p><small v-if="visit.payment!.status === 'PENDING'">模拟支付截止 {{ formatTime(visit.payment!.expiresAt) }}，超时释放库存。</small><small v-else-if="visit.payment!.status === 'EXPIRED'">库存已释放，已提交医疗结果继续保留。</small></div><div><strong>{{ money(visit.payment!.total) }}</strong><button v-if="visit.payment!.status === 'PENDING'" class="ph-primary" :disabled="busy" @click="payMedicine(visit.id)">模拟缴药费</button></div></article></section>
</template>
<style scoped>
.rd-patient-medicine{margin:24px 0;color:#4b718d}.rd-patient-medicine h2{font-size:18px;margin-bottom:16px}.rd-patient-medicine article{display:flex;justify-content:space-between;gap:20px;padding:22px;border:1px solid #deebf3;background:#fff;border-radius:14px;margin:12px 0}.rd-patient-medicine article>div:last-child{display:flex;flex-direction:column;justify-content:center;gap:14px;text-align:right;flex-shrink:0}.rd-patient-medicine article>div:last-child>strong{font-size:23px;color:#4d91b6}.rd-patient-medicine p{font-size:12px;line-height:1.8;margin-top:9px}.rd-patient-medicine small{color:#91a6b7;font-size:10px}.rd-patient-tag{font-size:10px;color:#b08c53;padding:4px 9px;background:#fff7e8;border-radius:5px;margin-right:10px}.rd-patient-medicine button{padding:9px 14px!important;white-space:nowrap}@media(max-width:760px){.rd-patient-medicine article{flex-direction:column;padding:18px}.rd-patient-medicine article>div:last-child{flex-direction:row;align-items:center;justify-content:space-between}.rd-patient-medicine article>div:first-child>strong{display:block;margin-top:12px}}
</style>
