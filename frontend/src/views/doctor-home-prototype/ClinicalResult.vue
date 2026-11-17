<script setup lang="ts">
import { money, formatTime, type DemoVisit } from './reception-demo'
defineProps<{ visit: Pick<DemoVisit, 'result' | 'payment'> }>()
</script>
<template>
  <div v-if="visit.result" class="rd-result" aria-label="已提交医疗结果">
    <p class="rd-result-meta">许知遥医生 · 2026-11-12 {{ formatTime(visit.result.at) }} · 已提交，当前只读</p>
    <dl><template v-for="field in ([['complaint', '主诉'], ['present', '现病史'], ['history', '既往史/过敏史'], ['diagnosis', '诊断'], ['advice', '处理建议']] as const)" :key="field[0]"><dt>{{ field[1] }}</dt><dd>{{ visit.result.clinical[field[0]] || '未填写' }}</dd></template></dl>
    <h3>开药记录</h3><p v-if="!visit.result.medicines.length">本次未开药</p>
    <div v-for="line in visit.result.medicines" :key="line.medicineId" class="rd-result-medicine"><strong>{{ line.name }}</strong><span>{{ line.specification }} × {{ line.quantity }} · {{ money(line.price * line.quantity) }}</span><p>用法用量：{{ line.usage }}</p></div>
    <p v-if="visit.payment">药费合计 {{ money(visit.payment.total) }} · {{ visit.payment.status === 'PAID' ? '已模拟支付' : visit.payment.status === 'EXPIRED' ? '订单已超时失效，库存已释放' : `待缴费，截止 ${formatTime(visit.payment.expiresAt)}` }}</p>
    <small>医疗结果已保存，药费付款或超时不会撤销本次病历。</small>
  </div>
</template>
<style scoped>
.rd-result{color:#486b84;font:13px/1.8 system-ui,"Microsoft YaHei",sans-serif;overflow-wrap:anywhere}.rd-result-meta{color:#7b95a8;font-size:11px;padding-bottom:15px;border-bottom:1px solid #e5edf3}.rd-result dl{display:grid;grid-template-columns:95px minmax(0,1fr);gap:14px;margin:22px 0}.rd-result dt{color:#8aa0b2}.rd-result dd{margin:0;white-space:pre-wrap}.rd-result h3{font-size:14px;margin:20px 0 12px}.rd-result-medicine{padding:12px;background:#f3f8fc;border-radius:9px;margin:10px 0}.rd-result-medicine>span{display:block;color:#7893a8;font-size:11px}.rd-result p{white-space:pre-wrap}.rd-result>small{display:block;margin-top:16px;color:#7893a8}
</style>
