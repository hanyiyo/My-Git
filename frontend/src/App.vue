<script setup lang="ts">
import NotificationPanel from './components/NotificationPanel.vue'
import { useRoute } from 'vue-router'
const route = useRoute()
</script>
<template><main :class="{ 'app-shell-wide': route.path === '/prototype/auth' }"><header v-if="route.path !== '/prototype/auth'"><p class="eyebrow">单医院 · 演示环境</p><h1>校医院健康服务</h1></header><NotificationPanel /><RouterView /></main></template>
