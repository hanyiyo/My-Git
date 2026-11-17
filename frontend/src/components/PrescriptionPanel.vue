<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '../api'
export type PrescriptionLine = { medicineId: string; quantity: number }
type Medicine = { id:string; name:string; spec:string; feeMinor:number; stock:number }
const props = defineProps<{ modelValue: PrescriptionLine[]; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue':[PrescriptionLine[]] }>()
const medicines = ref<Medicine[]>([])
onMounted(async () => { medicines.value = await api<Medicine[]>('/doctor/pharmacy/medicines') })
function add() { emit('update:modelValue', [...props.modelValue, { medicineId: medicines.value[0]?.id ?? '', quantity: 1 }]) }
function remove(i:number) { emit('update:modelValue', props.modelValue.filter((_, index) => index !== i)) }
function update(i:number, key:'medicineId'|'quantity', value:string) { const next = props.modelValue.map((line,index) => index===i ? {...line,[key]: key==='quantity' ? Math.max(1,Number(value)) : value} : line); emit('update:modelValue',next) }
const total = computed(() => props.modelValue.reduce((sum,line) => sum + (medicines.value.find(m=>m.id===line.medicineId)?.feeMinor ?? 0) * line.quantity, 0))
</script>
<template><section class="prescription-panel"><h3>开具处方</h3><p v-if="!medicines.length">暂无可用药品</p><div v-for="(line,index) in modelValue" :key="index"><select :value="line.medicineId" :disabled="disabled" @change="update(index,'medicineId',($event.target as HTMLSelectElement).value)"><option v-for="medicine in medicines" :key="medicine.id" :value="medicine.id">{{ medicine.name }}（{{ medicine.spec }}，库存 {{ medicine.stock }}）</option></select><input type="number" min="1" :value="line.quantity" :disabled="disabled" @input="update(index,'quantity',($event.target as HTMLInputElement).value)"><button type="button" :disabled="disabled" @click="remove(index)">删除</button></div><button type="button" :disabled="disabled || !medicines.length" @click="add">添加药品</button><strong>合计 ¥{{ (total/100).toFixed(2) }}</strong></section></template>
