<script setup lang="ts">
// Development prototype: administrator account governance workspace with fictional data.
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from './patient-home-prototype/PrototypeIcon.vue'
import './admin-home-prototype.css'
import './admin-accounts-prototype.css'

type Role = 'PATIENT' | 'DOCTOR' | 'ADMIN'
type Action = 'enable' | 'disable' | 'lock' | 'unlock' | 'reset'
type Operation = { id: string; action: string; time: string; operator: string; note: string }
type Account = {
  id: string; name: string; login: string; role: Role; source: string; phone: string; email: string
  enabled: boolean; locked: boolean; lastLogin: string; lastLoginSort: number; registered: string
  risk: string; statusChanged: string; operations: Operation[]
}
type NavItem = { label: string; icon: string; badge?: string; active?: boolean }
type NavGroup = { name: string; items: NavItem[] }

const route = useRoute(), router = useRouter()
const groups: NavGroup[] = [
  { name: '运营', items: [{ label: '运营总览', icon: 'grid' }, { label: '预约与订单', icon: 'calendar' }, { label: '退款 / 异常', icon: 'warning', badge: '3' }] },
  { name: '资源', items: [{ label: '患者', icon: 'user' }, { label: '医生', icon: 'doctor' }, { label: '科室', icon: 'hospital' }, { label: '排班', icon: 'calendar' }, { label: '号源', icon: 'grid' }, { label: '账户管理', icon: 'scan', active: true }] },
  { name: '药品', items: [{ label: '药品与库存', icon: 'heart', badge: '4' }, { label: '药费订单', icon: 'wallet' }] },
  { name: '治理', items: [{ label: '审批与替诊', icon: 'file', badge: '6' }, { label: '新闻通知', icon: 'bell' }, { label: '医疗访问审核', icon: 'lock', badge: '1' }, { label: '审计日志', icon: 'scan' }] },
  { name: '系统', items: [{ label: '全局规则', icon: 'settings' }, { label: 'AI 配置', icon: 'sparkles' }, { label: '管理员账户', icon: 'user' }] },
]
const accounts = ref<Account[]>([
  { id: 'ACC-1007', name: '林知夏', login: 'lin.zhixia', role: 'PATIENT', source: '患者注册', phone: '13812345678', email: 'lin.zhixia@example.com', enabled: true, locked: false, lastLogin: '今天 09:42', lastLoginSort: 6, registered: '2026-08-14', risk: '低风险', statusChanged: '2026-08-14 10:12', operations: [{ id: 'op-1', action: '账户启用', time: '2026-08-14 10:12', operator: '系统', note: '完成患者注册' }, { id: 'op-2', action: '登录成功', time: '今天 09:42', operator: '林知夏', note: '常用设备' }] },
  { id: 'ACC-1021', name: '周宁', login: 'zhou.ning', role: 'DOCTOR', source: '医生认证流程', phone: '13987654321', email: 'zhou.ning@hospital.example', enabled: true, locked: false, lastLogin: '今天 08:16', lastLoginSort: 5, registered: '2026-06-02', risk: '低风险', statusChanged: '2026-06-02 15:30', operations: [{ id: 'op-3', action: '账户启用', time: '2026-06-02 15:30', operator: '运营管理员', note: '医生认证通过' }, { id: 'op-4', action: '登录成功', time: '今天 08:16', operator: '周宁', note: '常用设备' }] },
  { id: 'ACC-1044', name: '沈嘉禾', login: 'shen.jiahe', role: 'PATIENT', source: '患者注册', phone: '13723456789', email: 'shen.jiahe@example.com', enabled: false, locked: false, lastLogin: '11月 10日 18:20', lastLoginSort: 3, registered: '2026-05-21', risk: '低风险', statusChanged: '11月 10日 18:26', operations: [{ id: 'op-5', action: '账户停用', time: '11月 10日 18:26', operator: '运营管理员', note: '用户主动申请停用' }] },
  { id: 'ACC-1098', name: '马凌', login: 'ma.ling', role: 'DOCTOR', source: '医生认证流程', phone: '13698761234', email: 'ma.ling@hospital.example', enabled: true, locked: true, lastLogin: '11月 12日 21:08', lastLoginSort: 4, registered: '2026-04-09', risk: '需关注', statusChanged: '11月 12日 21:09', operations: [{ id: 'op-6', action: '登录锁定', time: '11月 12日 21:09', operator: '安全策略', note: '连续登录失败 5 次' }, { id: 'op-7', action: '登录失败', time: '11月 12日 21:08', operator: '马凌', note: '密码错误' }] },
  { id: 'ACC-1106', name: '顾思齐', login: 'gu.siqi', role: 'PATIENT', source: '患者注册', phone: '13534567890', email: 'gu.siqi@example.com', enabled: true, locked: false, lastLogin: '11月 12日 16:35', lastLoginSort: 2, registered: '2026-03-18', risk: '低风险', statusChanged: '2026-03-18 09:11', operations: [{ id: 'op-8', action: '登录成功', time: '11月 12日 16:35', operator: '顾思齐', note: '常用设备' }] },
  { id: 'ACC-1138', name: '郭妤', login: 'guo.yu', role: 'ADMIN', source: '管理员授权', phone: '13376541230', email: 'guo.yu@hospital.example', enabled: true, locked: false, lastLogin: '11月 12日 14:02', lastLoginSort: 1, registered: '2026-01-06', risk: '低风险', statusChanged: '2026-01-06 11:20', operations: [{ id: 'op-9', action: '管理员授权', time: '2026-01-06 11:20', operator: '院方管理员', note: '平台运营权限' }] },
])
const roleLabels: Record<Role, string> = { PATIENT: '患者', DOCTOR: '医生', ADMIN: '管理员' }
const roleClass: Record<Role, string> = { PATIENT: 'patient', DOCTOR: 'doctor', ADMIN: 'admin' }
const roleFilter = ref(typeof route.query.role === 'string' ? route.query.role : 'ALL')
const statusFilter = ref(typeof route.query.status === 'string' ? route.query.status : 'ALL')
const keyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')
const selected = ref<Account | null>(null)
const pendingAction = ref<{ account: Account; action: Action } | null>(null)
const notice = ref(''), collapsed = ref(false), mobileOpen = ref(false)
const roleOptions: { value: Role | 'ALL'; label: string }[] = [{ value: 'ALL', label: '全部角色' }, { value: 'PATIENT', label: '患者' }, { value: 'DOCTOR', label: '医生' }, { value: 'ADMIN', label: '管理员' }]

const filteredAccounts = computed(() => accounts.value.filter(item => {
  const query = keyword.value.trim().toLowerCase()
  const matchesKeyword = !query || `${item.name}${item.login}${item.id}${item.phone}${item.email}`.toLowerCase().includes(query)
  const matchesRole = roleFilter.value === 'ALL' || item.role === roleFilter.value
  const matchesStatus = statusFilter.value === 'ALL' || statusFilter.value === 'LOCKED' && item.locked || statusFilter.value === 'DISABLED' && !item.enabled || statusFilter.value === 'ENABLED' && item.enabled && !item.locked
  return matchesKeyword && matchesRole && matchesStatus
}).sort((a, b) => b.lastLoginSort - a.lastLoginSort))
const counts = computed(() => ({ total: accounts.value.length, enabled: accounts.value.filter(item => item.enabled && !item.locked).length, locked: accounts.value.filter(item => item.locked).length, disabled: accounts.value.filter(item => !item.enabled).length }))

function syncUrl() { void router.replace({ query: { ...(roleFilter.value !== 'ALL' ? { role: roleFilter.value } : {}), ...(statusFilter.value !== 'ALL' ? { status: statusFilter.value } : {}), ...(keyword.value ? { keyword: keyword.value } : {}) } }) }
function navigate(module: string) {
  mobileOpen.value = false
  if (module === '运营总览') return void router.push({ path: '/prototype/admin-home', query: { variant: 'A' } })
  if (module === '预约与订单') return void router.push({ path: '/prototype/admin-orders', query: { tab: 'appointments' } })
  if (module === '退款 / 异常') return void router.push({ path: '/prototype/admin-orders', query: { tab: 'refunds' } })
  if (module === '账户管理') return
  if (module === '患者') return void router.push({ path: '/prototype/admin-patients' })
  if (module === '医生') return void router.push({ path: '/prototype/admin-doctors' })
  if (module === '科室') return void router.push({ path: '/prototype/admin-departments' })
  void router.push({ path: '/prototype/admin-home', query: { module } })
}
function openAccount(account: Account) { selected.value = account; notice.value = '' }
function closeDetails() { selected.value = null; notice.value = '' }
function actionLabel(action: Action) { return ({ enable: '启用账户', disable: '停用账户', lock: '锁定登录', unlock: '解除登录锁定', reset: '重置登录状态' })[action] }
function actionImpact(action: Action) { return ({ enable: '账户恢复登录后，用户可以继续使用已有角色和业务记录。', disable: '停用只影响登录，不会取消预约、停诊或删除历史医疗记录。', lock: '锁定只阻止登录，用于安全处置；账户资料和业务记录继续保留。', unlock: '解除锁定后，账户可以重新尝试登录，建议确认风险来源已处理。', reset: '将清除当前登录状态，用户需要重新登录；不会修改密码或业务数据。' })[action] }
function confirmAction(action: Action) { if (selected.value) pendingAction.value = { account: selected.value, action } }
function applyAction() {
  const request = pendingAction.value
  if (!request) return
  const item = request.account
  if (request.action === 'enable') item.enabled = true
  if (request.action === 'disable') item.enabled = false
  if (request.action === 'lock') item.locked = true
  if (request.action === 'unlock') item.locked = false
  const labels: Record<Action, string> = { enable: '账户启用', disable: '账户停用', lock: '登录锁定', unlock: '解除登录锁定', reset: '登录状态重置' }
  const operation: Operation = { id: `op-${Date.now()}`, action: labels[request.action], time: '刚刚', operator: '运营管理员', note: actionImpact(request.action) }
  item.operations.unshift(operation); item.statusChanged = '刚刚'; item.risk = request.action === 'lock' ? '需关注' : '低风险'
  notice.value = `${actionLabel(request.action)}已完成。`; pendingAction.value = null
}
function resetFilters() { roleFilter.value = 'ALL'; statusFilter.value = 'ALL'; keyword.value = ''; syncUrl() }
function maskedPhone(value: string) { return `${value.slice(0, 3)} **** ${value.slice(-4)}` }
function maskedEmail(value: string) { const [name, domain] = value.split('@'); return `${name.slice(0, 2)}${'•'.repeat(Math.max(2, name.length - 2))}@${domain}` }
function onKeydown(event: KeyboardEvent) { if (event.key === 'Escape') { pendingAction.value = null; closeDetails() } }
watch([roleFilter, statusFilter, keyword], syncUrl)
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="admin-prototype" :class="{ 'admin-collapsed': collapsed }">
    <aside class="admin-sidebar" :class="{ 'admin-mobile-open': mobileOpen }" aria-label="管理员端导航">
      <div class="admin-profile"><span class="admin-avatar">管</span><div class="admin-profile-copy"><strong>运营管理员</strong><span>平台治理与运营</span><em>管理员</em></div></div>
      <nav class="admin-nav" aria-label="管理员功能导航"><template v-for="group in groups" :key="group.name"><p class="admin-nav-caption">{{ group.name }}</p><button v-for="item in group.items" :key="item.label" type="button" :class="{ active: item.label === '账户管理' }" :aria-label="item.label" @click="navigate(item.label)"><Icon :name="item.icon" /><span>{{ item.label }}</span><b v-if="item.badge">{{ item.badge }}</b></button></template></nav>
      <div class="admin-sidebar-bottom"><div class="admin-permission"><Icon name="lock" /><div><strong>医疗内容权限</strong><span>默认未开启</span></div></div><button type="button" class="admin-collapse" :aria-label="collapsed ? '展开侧栏' : '收起侧栏'" :aria-expanded="!collapsed" @click="collapsed = !collapsed"><Icon name="chevron" /><span>{{ collapsed ? '展开侧栏' : '收起侧栏' }}</span></button></div>
    </aside>
    <button v-if="mobileOpen" type="button" class="admin-backdrop" aria-label="关闭侧栏" @click="mobileOpen = false"></button>
    <section class="admin-workspace">
      <header class="admin-topbar"><div class="admin-topbar-left"><button type="button" class="admin-mobile-toggle" aria-label="打开侧栏" @click="mobileOpen = true"><Icon name="menu" /></button><div class="admin-brand"><span><Icon name="hospital" /></span><div><strong>智能医疗问诊预约系统</strong><small>ADMIN OPERATIONS</small></div></div><div class="admin-hospital"><i></i>市一医院 · 管理员工作台</div></div><div class="admin-topbar-right"><div class="admin-updated"><span>实时演示数据</span><Icon name="refresh" /></div><button type="button" class="admin-bell" aria-label="查看通知"><Icon name="bell" /><i></i></button><div class="admin-user"><span>管</span><strong>运营管理员</strong><Icon name="chevron" /></div></div></header>
      <main class="admin-content accounts-content">
        <div class="accounts-heading"><div><span class="admin-eyebrow">资源 / 账户管理</span><h1>账户管理</h1><p>统一查看患者、医生和管理员账户，处理登录与账户状态治理。</p></div><div class="accounts-heading-actions"><span class="accounts-demo-chip"><i></i>虚构演示数据</span><button type="button" class="admin-primary" @click="resetFilters"><Icon name="refresh" />重置筛选</button></div></div>
        <section class="accounts-summary" aria-label="账户治理摘要"><article><span>账户总数</span><strong>{{ counts.total }}</strong><small>患者、医生、管理员统一目录</small></article><article><span>正常账户</span><strong>{{ counts.enabled }}</strong><small>已启用且未锁定</small></article><article class="warning"><span>登录锁定</span><strong>{{ counts.locked }}</strong><small>需要安全处置</small></article><article><span>已停用</span><strong>{{ counts.disabled }}</strong><small>不影响历史业务记录</small></article></section>
        <section class="accounts-panel" aria-label="账户目录"><div class="accounts-panel-heading"><div><h2>统一账户目录 <span>{{ filteredAccounts.length }} 条</span></h2><p>角色只读展示，账户治理操作会留下记录</p></div><span class="accounts-sort-note"><Icon name="scan" />按最近登录排序</span></div>
          <div class="accounts-filters" aria-label="账户筛选"><label class="accounts-search"><span>关键词</span><div><Icon name="search" /><input v-model="keyword" aria-label="搜索账户" placeholder="姓名、账号或账户编号"></div></label><label><span>角色</span><select v-model="roleFilter" aria-label="账户角色"><option v-for="item in roleOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label><label><span>账户状态</span><select v-model="statusFilter" aria-label="账户状态"><option value="ALL">全部状态</option><option value="ENABLED">已启用</option><option value="DISABLED">已停用</option><option value="LOCKED">登录锁定</option></select></label><button type="button" class="accounts-clear" @click="resetFilters">清除条件</button></div>
          <div class="accounts-table-wrap"><table class="accounts-table"><thead><tr><th>账户</th><th>角色</th><th>来源</th><th>状态</th><th>最近登录</th><th>注册时间</th><th>操作</th></tr></thead><tbody><tr v-for="item in filteredAccounts" :key="item.id"><td><div class="accounts-person"><span>{{ item.name.slice(0, 1) }}</span><div><strong>{{ item.name }}</strong><small>{{ item.login }} · {{ item.id }}</small></div></div></td><td><span :class="['account-role', `role-${roleClass[item.role]}`]">{{ roleLabels[item.role] }}</span></td><td><span class="accounts-muted">{{ item.source }}</span></td><td><div class="account-statuses"><span :class="['account-status', item.enabled ? 'enabled' : 'disabled']"><i></i>{{ item.enabled ? '已启用' : '已停用' }}</span><span v-if="item.locked" class="account-status locked"><i></i>登录锁定</span></div></td><td><strong class="accounts-last-login">{{ item.lastLogin }}</strong></td><td><span class="accounts-muted">{{ item.registered }}</span></td><td><button type="button" class="accounts-row-action" :aria-label="`查看${item.id}详情`" @click="openAccount(item)">查看详情 <Icon name="chevron" /></button></td></tr><tr v-if="!filteredAccounts.length"><td colspan="7" class="accounts-empty"><Icon name="user" /><strong>没有匹配的账户</strong><span>可以清除筛选后查看统一账户目录。</span></td></tr></tbody></table></div>
        </section>
        <p class="accounts-footnote"><Icon name="shield" /> 账户管理只展示必要身份信息；医疗正文、附件、预约内容和医生资质审核均在独立模块处理。</p>
      </main>
    </section>
    <div v-if="selected" class="accounts-drawer-backdrop" @click="closeDetails"><aside class="accounts-drawer" aria-label="账户详情" @click.stop><button type="button" class="accounts-drawer-close" aria-label="关闭账户详情" @click="closeDetails"><Icon name="close" /></button><span class="accounts-drawer-kicker">账户治理 · {{ roleLabels[selected.role] }}</span><div class="accounts-drawer-person"><span>{{ selected.name.slice(0, 1) }}</span><div><h2>{{ selected.name }}</h2><p>{{ selected.login }} · {{ selected.id }}</p></div></div><div class="account-role-tags"><span :class="['account-role', `role-${roleClass[selected.role]}`]">{{ roleLabels[selected.role] }}</span><span class="accounts-muted">角色由独立业务流程维护</span></div><div class="accounts-detail-grid"><span>账户状态<strong :class="selected.enabled ? 'good' : 'muted'">{{ selected.enabled ? '已启用' : '已停用' }}</strong></span><span>登录状态<strong :class="selected.locked ? 'risk' : 'good'">{{ selected.locked ? '已锁定' : '正常' }}</strong></span><span>手机号<strong>{{ maskedPhone(selected.phone) }}</strong></span><span>邮箱<strong>{{ maskedEmail(selected.email) }}</strong></span><span>注册时间<strong>{{ selected.registered }}</strong></span><span>状态变更<strong>{{ selected.statusChanged }}</strong></span></div><div class="accounts-risk"><Icon name="shield" /><div><strong>登录风险：{{ selected.risk }}</strong><span>仅用于账户安全处置，不代表医疗风险判断。</span></div></div><div class="accounts-operations"><div class="accounts-operations-heading"><h3>最近治理记录</h3><button type="button" class="accounts-link" @click="notice = '已打开全部操作记录（演示）'">查看全部 <Icon name="arrow" /></button></div><div v-for="operation in selected.operations.slice(0, 3)" :key="operation.id" class="accounts-operation"><i></i><div><strong>{{ operation.action }}</strong><span>{{ operation.time }} · {{ operation.operator }}</span><small>{{ operation.note }}</small></div></div></div><p v-if="notice" class="accounts-feedback" role="status">{{ notice }}</p><div class="accounts-drawer-actions"><button v-if="selected.enabled" type="button" class="accounts-danger" @click="confirmAction('disable')">停用账户</button><button v-else type="button" class="admin-soft" @click="confirmAction('enable')">启用账户</button><button v-if="selected.locked" type="button" class="admin-soft" @click="confirmAction('unlock')">解除登录锁定</button><button v-else type="button" class="accounts-outline" @click="confirmAction('lock')">锁定登录</button><button type="button" class="admin-primary" @click="confirmAction('reset')">重置登录状态</button></div><p class="accounts-drawer-note">高风险操作会要求二次确认；角色变更和医疗内容访问不在本页处理。</p></aside></div>
    <div v-if="pendingAction" class="accounts-confirm-backdrop" role="presentation"><section class="accounts-confirm" role="dialog" aria-modal="true" aria-labelledby="account-confirm-title"><span class="accounts-confirm-icon"><Icon name="warning" /></span><h2 id="account-confirm-title">确认{{ actionLabel(pendingAction.action) }}</h2><p>{{ pendingAction.account.name }}（{{ pendingAction.account.id }}）</p><div class="accounts-confirm-impact">{{ actionImpact(pendingAction.action) }}</div><div class="accounts-confirm-actions"><button type="button" class="admin-soft" @click="pendingAction = null">取消</button><button type="button" class="admin-primary" @click="applyAction">确认{{ actionLabel(pendingAction.action) }}</button></div></section></div>
  </div>
</template>
