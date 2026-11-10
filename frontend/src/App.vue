<script setup lang="ts">
import NotificationPanel from './components/NotificationPanel.vue'
import { useRoute } from 'vue-router'
import { computed } from 'vue'
import { useSession } from './session'
import AssistantDrawer from './components/AssistantDrawer.vue'
const route = useRoute()
const prototypeWorkspace = computed(() => import.meta.env.DEV && route.meta.prototypeWorkspace === true)
const session = useSession()
const links: Record<string,string[][]> = { PATIENT:[['首页','/patient'],['科室与医生','/patient/catalog'],['我的挂号','/patient/orders'],['站内通知','/notifications'],['个人中心','/profile']], DOCTOR:[['首页','/doctor'],['待接诊','/doctor/reception'],['排班管理','/doctor/catalog'],['申请管理','/doctor/governance']], ADMIN:[['首页','/admin'],['目录与排班','/admin/catalog'],['药品管理','/admin/pharmacy'],['参数配置','/admin/settings']] }
</script>
<template><RouterView v-if="prototypeWorkspace"/><main v-else :class="{ 'app-shell-wide': route.path === '/prototype/auth' }"><header v-if="route.path !== '/prototype/auth'"><p class="eyebrow">单医院 · 演示环境</p><h1>智能医疗问诊预约系统</h1></header><aside v-if="session.account && route.path !== '/prototype/auth'" class="app-sidebar"><strong>{{session.account.displayName || session.account.identifier}}</strong><nav><RouterLink v-for="link in (links[session.account.roles[0]]??[])" :key="link[1]" :to="link[1]">{{link[0]}}</RouterLink></nav><RouterLink v-if="session.account.roles.includes('PATIENT')" class="quick-register" to="/patient/catalog">立即挂号</RouterLink></aside><NotificationPanel/><AssistantDrawer v-if="session.account?.roles.includes('PATIENT')"/><RouterView/></main></template>
