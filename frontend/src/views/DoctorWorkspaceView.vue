<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSession } from '../session'
import NotificationPanel from '../components/NotificationPanel.vue'

const route = useRoute()
const router = useRouter()
const session = useSession()
const mobileOpen = ref(false)
const tabs = ref<string[]>(['home'])
const modules = [
  { key: 'home', label: '首页', path: '/doctor' },
  { key: 'reception', label: '接诊工作台', path: '/doctor/reception' },
  { key: 'catalog', label: '我的排班', path: '/doctor/catalog' },
  { key: 'governance', label: '申请处理', path: '/doctor/governance' },
  { key: 'notifications', label: '站内通知', path: '/doctor/notifications' },
  { key: 'profile', label: '个人资料', path: '/doctor/profile' },
]
const currentKey = computed(() => {
  const segment = String(route.path).split('/')[2] || 'home'
  return modules.some(module => module.key === segment) ? segment : 'home'
})
const currentModule = computed(() => modules.find(module => module.key === currentKey.value) ?? modules[0])
const accountName = computed(() => session.account?.displayName || session.account?.identifier || '医生')
watch(currentKey, key => { if (!tabs.value.includes(key)) tabs.value.push(key) }, { immediate: true })
function go(key: string) {
  const target = modules.find(module => module.key === key) ?? modules[0]
  if (!tabs.value.includes(target.key)) tabs.value.push(target.key)
  mobileOpen.value = false
  void router.push(target.path)
}
function closeTab(key: string) {
  if (key === 'home') return
  tabs.value = tabs.value.filter(tab => tab !== key)
  if (currentKey.value === key) go('home')
}
async function logout() { await session.logout(); await router.push('/login') }
</script>

<template>
  <div class="doctor-shell">
    <button v-if="mobileOpen" class="doctor-shell-backdrop" aria-label="关闭医生导航遮罩" @click="mobileOpen = false"></button>
    <aside :class="['doctor-shell-sidebar', 'app-sidebar', { open: mobileOpen }]" aria-label="医生端侧栏">
      <div class="doctor-shell-brand"><strong>智能医疗问诊预约系统</strong><small>医生工作区</small></div>
      <div class="doctor-shell-account"><span>{{ accountName.slice(0, 1) }}</span><div><strong>{{ accountName }}</strong><small>医生端正式工作区</small></div></div>
      <nav aria-label="医生端导航">
        <button v-for="module in modules" :key="module.key" type="button" :class="{ active: currentKey === module.key }" :aria-current="currentKey === module.key ? 'page' : undefined" @click="go(module.key)">{{ module.label }}</button>
      </nav>
      <button class="doctor-shell-logout" type="button" @click="logout">退出登录</button>
    </aside>
    <section class="doctor-shell-main">
      <header class="doctor-shell-header"><button class="doctor-shell-menu" type="button" aria-label="打开医生导航" :aria-expanded="mobileOpen" @click="mobileOpen = true">☰</button><RouterLink class="doctor-shell-home-link" to="/doctor">首页</RouterLink><div><strong>{{ currentModule.label }}</strong><small>校医院 · 医生工作区</small></div><button type="button" class="doctor-shell-user" @click="go('profile')">{{ accountName }}</button></header>
      <nav class="doctor-shell-tabs" aria-label="医生临时工作区标签">
        <div v-for="key in tabs" :key="key" :class="{ active: currentKey === key }"><button type="button" @click="go(key)">{{ modules.find(module => module.key === key)?.label }}</button><button v-if="key !== 'home'" type="button" :aria-label="`关闭${modules.find(module => module.key === key)?.label}标签`" @click="closeTab(key)">×</button></div>
      </nav>
      <main class="doctor-shell-content"><RouterView /><NotificationPanel /></main>
    </section>
  </div>
</template>

<style scoped>
.doctor-shell { min-height: 100vh; display: flex; background: #f5f7fb; color: #1f2937; }
.doctor-shell-sidebar { width: 236px; flex: 0 0 236px; padding: 24px 16px; background: #102a43; color: #fff; display: flex; flex-direction: column; gap: 22px; }
.doctor-shell-brand { display: grid; gap: 4px; padding: 0 8px; }.doctor-shell-brand small, .doctor-shell-account small, .doctor-shell-header small { color: #9fb3c8; font-size: .78rem; }
.doctor-shell-account { display: flex; gap: 10px; align-items: center; padding: 12px 8px; border: 1px solid #2f5574; border-radius: 12px; }.doctor-shell-account > span { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 50%; background: #2f80ed; font-weight: 700; }.doctor-shell-account div { display: grid; gap: 3px; min-width: 0; }.doctor-shell-account strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.doctor-shell-sidebar nav { display: grid; gap: 6px; }.doctor-shell-sidebar nav button, .doctor-shell-logout { border: 0; border-radius: 8px; padding: 11px 12px; color: #d9e2ec; background: transparent; text-align: left; cursor: pointer; font: inherit; }.doctor-shell-sidebar nav button:hover, .doctor-shell-sidebar nav button.active { color: #fff; background: #2f5574; }.doctor-shell-logout { margin-top: auto; border: 1px solid #486581; text-align: center; }
.doctor-shell-main { flex: 1; min-width: 0; }.doctor-shell-header { min-height: 72px; display: flex; align-items: center; gap: 14px; padding: 12px 28px; background: #fff; border-bottom: 1px solid #e5e7eb; }.doctor-shell-header > div { display: grid; gap: 3px; flex: 1; }.doctor-shell-menu { display: none; border: 0; background: transparent; font-size: 1.4rem; }.doctor-shell-home-link { color: #2563eb; text-decoration: none; white-space: nowrap; }.doctor-shell-user { border: 0; background: transparent; color: #2563eb; cursor: pointer; font: inherit; }.doctor-shell-tabs { display: flex; gap: 4px; min-height: 44px; padding: 6px 28px; background: #fff; border-bottom: 1px solid #e5e7eb; overflow-x: auto; }.doctor-shell-tabs > div { display: flex; align-items: center; border-radius: 7px; background: #f1f5f9; }.doctor-shell-tabs > div.active { background: #dbeafe; }.doctor-shell-tabs button { border: 0; background: transparent; padding: 7px 10px; color: #475569; white-space: nowrap; cursor: pointer; }.doctor-shell-tabs > div.active button { color: #1d4ed8; font-weight: 600; }.doctor-shell-tabs button + button { padding-left: 2px; color: #64748b; }.doctor-shell-content { padding: 28px; max-width: 1180px; margin: 0 auto; }
.doctor-shell-backdrop { display: none; }
@media (max-width: 700px) { .doctor-shell-sidebar { position: fixed; z-index: 5; inset: 0 auto 0 0; transform: translateX(-105%); transition: transform .2s ease; box-shadow: 8px 0 24px #102a4340; }.doctor-shell-sidebar.open { transform: translateX(0); }.doctor-shell-backdrop { display: block; position: fixed; z-index: 4; inset: 0; border: 0; background: #102a4366; }.doctor-shell-header { padding: 12px 16px; }.doctor-shell-menu { display: block; }.doctor-shell-tabs { padding: 6px 16px; }.doctor-shell-content { padding: 20px 16px; } }
</style>
