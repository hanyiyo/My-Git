<script setup lang="ts">
import NotificationPanel from './components/NotificationPanel.vue'
import { useRoute } from 'vue-router'
const route = useRoute()
</script>
<template><main :class="{ 'login-shell': route.path === '/login' }"><template v-if="route.path !== '/login'"><header><p class="eyebrow">单医院 · 演示环境</p><h1>校医院健康服务</h1></header><NotificationPanel /></template><RouterView /></main></template>
<style scoped>
.login-shell{max-width:1180px}
</style>
