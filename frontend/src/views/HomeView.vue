<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useSession, type Profile } from '../session'
import { api } from '../api'
const session = useSession(), router = useRouter(), route = useRoute(), error = ref(''), profile = ref<Profile | null>(null), unread = ref(0)
const labels: Record<string, string> = { PATIENT: '患者服务', DOCTOR: '医生工作台', ADMIN: '管理员工作台' }
async function logout() { try { await session.logout(); await router.push('/login') } catch (e) { error.value = e instanceof Error ? e.message : '退出失败' } }
onMounted(async () => { try { profile.value = await api<Profile>('/' + String(route.meta.role).toLowerCase() + '/home'); unread.value = await api<number>('/notifications/unread') } catch (e) { error.value = e instanceof Error ? e.message : '暂不可用' } })
</script>
<template><section class="card"><h2>{{ labels[String(route.meta.role)] }}</h2><p v-if="error" role="alert">{{ error }}</p><template v-if="profile"><p>欢迎，{{ profile.identifier }}</p><p>未读提醒：{{ unread }}</p><p v-if="profile.credentialStatus">医生资质：{{ profile.credentialStatus === 'PENDING' ? '待审核' : profile.credentialStatus }}</p><nav class="catalog-links"><RouterLink v-if="route.meta.role === 'PATIENT'" to="/patient/preconsultation">预约前 AI 预问诊</RouterLink><RouterLink v-if="route.meta.role === 'ADMIN'" to="/admin/ai">AI 调用元数据</RouterLink><RouterLink to="/assistant/business">医院业务咨询</RouterLink><RouterLink v-if="route.meta.role === 'ADMIN'" to="/admin/catalog">医院目录与排班管理</RouterLink><RouterLink v-if="route.meta.role === 'PATIENT'" to="/patient/catalog">查询科室、医生与排班</RouterLink><RouterLink v-if="route.meta.role === 'PATIENT'" to="/patient/orders">我的预约订单</RouterLink><RouterLink v-if="route.meta.role === 'PATIENT'" to="/patient/substitutions">替诊确认</RouterLink><RouterLink v-if="route.meta.role === 'DOCTOR'" to="/doctor/catalog">我的认证与排班</RouterLink><RouterLink v-if="route.meta.role === 'DOCTOR'" to="/doctor/reception">候诊与接诊</RouterLink></nav></template><button type="button" @click="logout">退出登录</button></section></template>
