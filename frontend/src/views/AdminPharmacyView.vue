<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api } from '../api'
type Medicine={id:string;name:string;spec:string;feeMinor:number;stock:number;threshold:number;active:boolean}
const list=ref<Medicine[]>([]), form=reactive({id:'',name:'',spec:'',feeMinor:0,stock:0,threshold:0,active:true}), error=ref(''), message=ref('')
async function load(){list.value=await api<Medicine[]>('/admin/pharmacy/medicines')}
function edit(item:Medicine){Object.assign(form,item)}
async function save(){try{const {id,...body}=form; await api('/admin/pharmacy/medicines'+(id?'/'+id:''),id?'PUT':'POST',body);message.value='药品已保存';Object.assign(form,{id:'',name:'',spec:'',feeMinor:0,stock:0,threshold:0,active:true});await load()}catch(e){error.value=e instanceof Error?e.message:'保存失败'}}
onMounted(()=>load().catch(()=>error.value='药品暂不可用'))
</script>
<template><section class="card"><h2>药品管理</h2><p v-if="error" role="alert">{{ error }}</p><p v-if="message" role="status">{{ message }}</p><form @submit.prevent="save"><input v-model="form.name" placeholder="药品名称" required><input v-model="form.spec" placeholder="规格" required><input v-model.number="form.feeMinor" type="number" min="0" placeholder="价格（分）"><input v-model.number="form.stock" type="number" min="0" placeholder="库存"><input v-model.number="form.threshold" type="number" min="0" placeholder="预警阈值"><label><input v-model="form.active" type="checkbox">启用</label><button>保存</button></form><table><tr><th>名称</th><th>规格</th><th>价格</th><th>库存</th><th>操作</th></tr><tr v-for="item in list" :key="item.id"><td>{{item.name}}</td><td>{{item.spec}}</td><td>¥{{(item.feeMinor/100).toFixed(2)}}</td><td>{{item.stock}}</td><td><button @click="edit(item)">编辑</button></td></tr></table></section></template>
