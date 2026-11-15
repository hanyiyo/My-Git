<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '../views/patient-home-prototype/PrototypeIcon.vue'
import '../views/admin-home-prototype.css'

const props = withDefaults(defineProps<{ active?: string; embedded?: boolean }>(), { active: '药品与库存', embedded: false })
const router = useRouter()
const collapsed = ref(false), mobileOpen = ref(false)
const groups = [
  { name: '运营', items: [{ label: '运营总览', icon: 'grid' }, { label: '预约与订单', icon: 'calendar' }, { label: '退款 / 异常', icon: 'warning', badge: '3' }] },
  { name: '资源', items: [{ label: '患者', icon: 'user' }, { label: '医生', icon: 'doctor' }, { label: '科室', icon: 'hospital' }, { label: '排班', icon: 'calendar' }, { label: '号源', icon: 'grid' }] },
  { name: '药品', items: [{ label: '药品与库存', icon: 'heart', badge: '4' }, { label: '药费订单', icon: 'wallet' }] },
  { name: '治理', items: [{ label: '审批与替诊', icon: 'file', badge: '6' }, { label: '新闻通知', icon: 'bell' }, { label: '审计日志', icon: 'scan' }] },
  { name: '系统', items: [{ label: '全局规则', icon: 'settings' }, { label: 'AI 配置', icon: 'sparkles' }, { label: '管理员账户', icon: 'user' }] },
]
function navigate(label: string) {
  mobileOpen.value = false
  if (label === '药品与库存') return void router.push('/admin/pharmacy')
  if (label === '运营总览') return void router.push('/admin')
  if (label === '目录与排班' || label === '排班') return void router.push('/admin/catalog')
  if (label === '患者') return void router.push('/admin/accounts')
  if (label === '号源') return void router.push(import.meta.env.DEV ? '/prototype/admin-slots' : '/admin/catalog')
  if (label === 'AI 配置' || label === '全局规则') return void router.push('/admin/settings')
  void router.push('/admin')
}
</script>

<template>
  <template v-if="props.embedded"><slot /></template>
  <div v-else class="admin-prototype" :class="{ 'admin-collapsed': collapsed }">
    <aside class="admin-sidebar" :class="{ 'admin-mobile-open': mobileOpen }" aria-label="管理员端导航"><div class="admin-profile"><span class="admin-avatar">管</span><div class="admin-profile-copy"><strong>运营管理员</strong><span>平台治理与运营</span><em>管理员</em></div></div><nav class="admin-nav" aria-label="管理员功能导航"><template v-for="group in groups" :key="group.name"><p class="admin-nav-caption">{{ group.name }}</p><button v-for="item in group.items" :key="item.label" type="button" :class="{ active: item.label === props.active }" :aria-label="item.label" @click="navigate(item.label)"><Icon :name="item.icon" /><span>{{ item.label }}</span><b v-if="item.badge">{{ item.badge }}</b></button></template></nav><div class="admin-sidebar-bottom"><div class="admin-permission"><Icon name="lock" /><div><strong>医疗内容权限</strong><span>默认未开启</span></div></div><button type="button" class="admin-collapse" :aria-label="collapsed ? '展开侧栏' : '收起侧栏'" :aria-expanded="!collapsed" @click="collapsed = !collapsed"><Icon name="chevron" /><span>{{ collapsed ? '展开侧栏' : '收起侧栏' }}</span></button></div></aside>
    <button v-if="mobileOpen" type="button" class="admin-backdrop" aria-label="关闭侧栏" @click="mobileOpen = false"></button>
    <section class="admin-workspace"><header class="admin-topbar"><div class="admin-topbar-left"><button type="button" class="admin-mobile-toggle" aria-label="打开侧栏" @click="mobileOpen = true"><Icon name="menu" /></button><div class="admin-brand"><span><Icon name="hospital" /></span><div><strong>智能医疗问诊预约系统</strong><small>ADMIN OPERATIONS</small></div></div><div class="admin-hospital"><i></i>市一医院 · 管理员工作台</div></div><div class="admin-topbar-right"><div class="admin-updated"><span>实时业务数据</span><Icon name="refresh" /></div><button type="button" class="admin-bell" aria-label="查看通知"><Icon name="bell" /><i></i></button><div class="admin-user"><span>管</span><strong>运营管理员</strong><Icon name="chevron" /></div></div></header><main class="admin-content"><slot /></main></section>
  </div>
</template>
