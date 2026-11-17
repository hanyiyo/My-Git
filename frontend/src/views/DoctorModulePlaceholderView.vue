<script setup lang="ts">
import { useRoute } from 'vue-router'
const route = useRoute()
const title = route.path.endsWith('/notifications') ? '站内通知' : '个人资料'
</script>
<template><section class="card doctor-placeholder"><h2>{{ title }}</h2><p>该模块保留为正式工作区入口，真实数据接入由后续任务完成。</p><p class="hint">当前页面不使用演示数据，也不会改变任何业务状态。</p></section></template>
<style scoped>.doctor-placeholder { max-width: 680px; margin: 0 auto; }.doctor-placeholder .hint { color: #64748b; }</style>
