<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
type Order = { id:string; state:string; totalMinor:number; expiresAt:number; lines:{medicineName:string; quantity:number; feeMinor:number}[] }
defineProps<{ filter?: string }>()
const orders = ref<Order[]>([]), error = ref('')
async function load(){try{orders.value=await api<Order[]>('/patient/pharmacy/orders')}catch(e){error.value=e instanceof Error?e.message:'药品订单暂不可用'}}
async function pay(id:string){await api('/patient/pharmacy/orders/'+id+'/pay','POST'); await load()}
onMounted(load)
</script>
<template><section class="patient-medicine-orders"><h2>药品订单</h2><p v-if="error" role="alert">{{ error }}</p><article v-for="order in orders.filter(item => !$props.filter || item.state===$props.filter)" :key="order.id"><p>状态：{{ order.state }}　金额：¥{{ (order.totalMinor/100).toFixed(2) }}</p><ul><li v-for="line in order.lines" :key="line.medicineName">{{ line.medicineName }} × {{ line.quantity }}</li></ul><button v-if="order.state==='PENDING_PAYMENT'" @click="pay(order.id)">模拟支付药费</button></article></section></template>
