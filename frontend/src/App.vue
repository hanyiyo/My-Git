<script setup lang="ts">
import NotificationPanel from './components/NotificationPanel.vue'
import { useRoute } from 'vue-router'
import { computed, ref } from 'vue'
import { useSession } from './session'
import AssistantDrawer from './components/AssistantDrawer.vue'
const route = useRoute()
const prototypeWorkspace = computed(() => import.meta.env.DEV && route.meta.prototypeWorkspace === true)
const adminWorkspace = computed(() => route.meta.adminWorkspace === true)
const session = useSession()
const patientWorkspace = computed(() => session.account?.roles[0] === 'PATIENT' && route.path !== '/prototype/auth')
const sidebarOpen = ref(false)
const links: Record<string,string[][]> = { PATIENT:[['首页','/patient'],['科室与医生','/patient/catalog'],['我的挂号','/patient/orders'],['站内通知','/notifications'],['个人中心','/profile']], DOCTOR:[['首页','/doctor'],['待接诊','/doctor/reception'],['排班管理','/doctor/catalog'],['申请管理','/doctor/governance']], ADMIN:[['首页','/admin'],['目录与排班','/admin/catalog'],['药品管理','/admin/pharmacy'],['参数配置','/admin/settings']] }
</script>
<template>
  <RouterView v-if="prototypeWorkspace || adminWorkspace"/>
  <main v-else :class="{ 'app-shell-wide': route.path === '/prototype/auth', 'patient-shell': patientWorkspace }">
    <header v-if="route.path !== '/prototype/auth'" :class="{ 'patient-topbar': patientWorkspace }">
      <div><p v-if="!patientWorkspace" class="eyebrow">单医院 · 演示环境</p><h1>智能医疗问诊预约系统</h1><span v-if="patientWorkspace" class="hint">患者服务</span></div>
      <nav v-if="patientWorkspace" aria-label="顶部快捷导航"><RouterLink to="/patient/catalog">立即挂号</RouterLink><RouterLink to="/patient/orders">我的挂号</RouterLink><RouterLink to="/notifications">站内通知</RouterLink></nav>
      <button v-if="patientWorkspace" type="button" class="patient-menu" :aria-expanded="sidebarOpen" @click="sidebarOpen = !sidebarOpen">{{ sidebarOpen ? '收起导航' : '展开导航' }}</button>
    </header>
    <aside v-if="session.account && route.path !== '/prototype/auth'" :class="['app-sidebar', { 'patient-sidebar': patientWorkspace, 'sidebar-open': sidebarOpen }]">
      <RouterLink v-if="patientWorkspace" class="patient-profile" to="/profile"><span>{{ (session.account.displayName || session.account.identifier).slice(0, 1) }}</span><strong>{{ session.account.displayName || session.account.identifier }}<small>个人中心 →</small></strong></RouterLink>
      <strong v-else>{{ session.account.displayName || session.account.identifier }}</strong>
      <nav aria-label="侧栏导航"><RouterLink v-for="link in (links[session.account.roles[0]]??[])" :key="link[1]" :to="link[1]" @click="sidebarOpen = false">{{link[0]}}</RouterLink></nav>
    </aside>
    <RouterLink v-if="patientWorkspace" class="quick-register registration-fab" to="/patient/catalog">＋ 立即挂号</RouterLink>
    <AssistantDrawer v-if="session.account?.roles.includes('PATIENT')"/>
    <RouterView/>
    <NotificationPanel/>
    <footer v-if="patientWorkspace" class="patient-footer">智能医疗问诊预约系统 · 每一次关怀，都值得认真对待</footer>
  </main>
</template>
