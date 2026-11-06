<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useSession, type Profile } from '../session'
import { api } from '../api'
const session = useSession(), router = useRouter(), route = useRoute(), error = ref(''), profile = ref<Profile | null>(null)
const labels: Record<string, string> = { PATIENT: '患者服务', DOCTOR: '医生工作台', ADMIN: '管理员工作台' }
async function logout() { try { await session.logout(); await router.push('/login') } catch (e) { error.value = e instanceof Error ? e.message : '退出失败' } }
onMounted(async () => { try { profile.value = await api<Profile>('/' + String(route.meta.role).toLowerCase() + '/home') } catch (e) { error.value = e instanceof Error ? e.message : '暂不可用' } })
</script>
<template><section class="card"><h2>{{ labels[String(route.meta.role)] }}</h2><p v-if="error" role="alert">{{ error }}</p><template v-if="profile"><p>欢迎，{{ profile.identifier }}</p><p v-if="profile.credentialStatus">医生资质：{{ profile.credentialStatus === 'PENDING' ? '待审核' : profile.credentialStatus }}</p><p class="hint">账户会话已验证。后续业务功能按 issue 分阶段开放。</p></template><button type="button" @click="logout">退出登录</button></section></template>
