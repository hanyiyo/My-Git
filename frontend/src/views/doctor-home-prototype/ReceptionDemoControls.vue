<script setup lang="ts">
import { ref } from 'vue'
import { adjustStock, advanceDemo, checkInVisit, formatTime, setAiFailure, setDemoTime, setScheduleDemoDateTime, useReceptionDemo } from './reception-demo'
const { state, now, error, warning } = useReceptionDemo()
const time = ref('14:20'), stock = ref(20), date = ref('2026-11-12')
</script>
<template>
  <fieldset class="rd-demo-controls"><legend>医患联动演示控制</legend>
    <p>虚构演示时间：{{ new Date(now).toLocaleDateString('en-CA', { timeZone: 'Asia/Shanghai' }) }} {{ formatTime(now) }} · 同一浏览器、同一地址的标签页同步。</p>
    <label>演示时间<input v-model="time" type="time" aria-label="联动演示时间"></label>
    <div><button type="button" @click="setDemoTime(time)">应用时间</button><button type="button" @click="advanceDemo(5)">推进 5 分钟</button><button type="button" @click="advanceDemo(30)">推进 30 分钟</button></div>
    <label>审批演示日期<input v-model="date" type="date" aria-label="审批演示日期"></label><button type="button" @click="setScheduleDemoDateTime(date, time)">应用审批演示日期时间</button><p>用于检查 24 小时申请截止与审批过期；接诊样例仍对应 11月12日。</p>
    <label><input type="checkbox" :checked="state.aiFailure" @change="setAiFailure(($event.target as HTMLInputElement).checked)">模拟摘要失败</label>
    <label>药品 A 总库存<input v-model.number="stock" aria-label="药品 A 总库存" type="number" min="0"></label><button type="button" @click="adjustStock('med-1', stock)">应用库存</button>
    <p>下午场可让第二位演示患者签到，用于比较预约与当日队列。</p><button type="button" @click="checkInVisit('online-002')">陈亦安模拟签到</button>
    <p v-if="error || warning" role="alert">{{ error || warning }}</p>
  </fieldset>
</template>
<style scoped>
.rd-demo-controls{margin:22px 0;border:1px dashed #bfd5e3;border-radius:12px;padding:16px;color:#58738a;font-size:12px}.rd-demo-controls legend{font-weight:600;padding:0 6px}.rd-demo-controls p{margin:5px 0 12px!important}.rd-demo-controls label{display:flex;align-items:center;gap:10px;margin:12px 0!important}.rd-demo-controls input:not([type=checkbox]){width:130px;border:1px solid #d9e5ec;border-radius:6px;padding:7px;color:inherit;background:white}.rd-demo-controls button{padding:6px 10px!important;background:#edf5fb!important;border:1px solid #d6e4ee!important;color:#487497!important;margin:3px}.rd-demo-controls input[type=checkbox]{width:16px;height:16px}
</style>
