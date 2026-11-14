<script setup lang="ts">
// Development prototype: administrator operations cockpit with fictional data.
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from './patient-home-prototype/PrototypeIcon.vue'
import AdminPharmacyView from './AdminPharmacyView.vue'
import AdminMedicineOrdersPrototypeView from './AdminMedicineOrdersPrototypeView.vue'
import AdminGovernancePrototypeView from './AdminGovernancePrototypeView.vue'
import AdminNewsPrototypeView from './AdminNewsPrototypeView.vue'
import './admin-home-prototype.css'

type Priority = 'urgent' | 'today' | 'notice'
type Todo = { id: string; title: string; detail: string; priority: Priority; action: string; module: string }

const route = useRoute(), router = useRouter()
const collapsed = ref(false), mobileOpen = ref(false), activeTodo = ref<Todo | null>(null)
const activeModule = computed(() => String(route.query.module || '运营总览'))
const pharmacyModule = computed(() => activeModule.value === '药品与库存')
const medicineOrdersModule = computed(() => activeModule.value === '药费订单')
const governanceModule = computed(() => activeModule.value === '审批与替诊')
const newsModule = computed(() => activeModule.value === '新闻通知')
const lastRefreshed = ref('刚刚'), refreshCount = ref(0), range = ref('today')
const groups = [
  { name: '运营', items: [{ label: '运营总览', icon: 'grid', active: true }, { label: '预约与订单', icon: 'calendar' }, { label: '退款 / 异常', icon: 'warning', badge: '3' }] },
  { name: '资源', items: [{ label: '患者', icon: 'user' }, { label: '医生', icon: 'doctor' }, { label: '科室', icon: 'hospital' }, { label: '排班', icon: 'calendar' }, { label: '号源', icon: 'grid' }] },
  { name: '药品', items: [{ label: '药品与库存', icon: 'heart', badge: '4' }, { label: '药费订单', icon: 'wallet' }] },
  { name: '治理', items: [{ label: '审批与替诊', icon: 'file', badge: '6' }, { label: '新闻通知', icon: 'bell' }, { label: '医疗访问审核', icon: 'lock', badge: '1' }, { label: '审计日志', icon: 'scan' }] },
  { name: '系统', items: [{ label: '全局规则', icon: 'settings' }, { label: 'AI 配置', icon: 'sparkles' }, { label: '管理员账户', icon: 'user' }] },
]
const todos: Todo[] = [
  { id: 'approval', title: '停诊替诊终审', detail: '6 条申请 · 其中 2 条将在 24 小时内截止', priority: 'urgent', action: '去处理', module: '审批与替诊' },
  { id: 'refund', title: '退款失败待处理', detail: '3 笔退款需要重新发起或人工核对', priority: 'urgent', action: '查看异常', module: '退款 / 异常' },
  { id: 'medical', title: '医疗访问审核', detail: '1 条申请等待独立授权与理由登记', priority: 'urgent', action: '进入审核', module: '医疗访问审核' },
  { id: 'schedule', title: '今日即将开诊', detail: '上午 08:00 · 4 个科室 · 12 位医生', priority: 'today', action: '查看排班', module: '排班' },
  { id: 'hold', title: '未支付占号', detail: '18 个临时占号将在 10 分钟内到期', priority: 'today', action: '查看订单', module: '预约与订单' },
  { id: 'stock', title: '库存预警', detail: '4 个药品低于预警阈值，其中 1 个已缺货', priority: 'today', action: '补充库存', module: '药品与库存' },
  { id: 'news', title: '医院动态待发布', detail: '2 篇公告处于草稿状态', priority: 'notice', action: '查看草稿', module: '新闻通知' },
]
const visibleTodos = computed(() => range.value === 'today' ? todos : todos.filter(todo => todo.priority !== 'notice'))
const priorityLabel: Record<Priority, string> = { urgent: '需要马上处理', today: '今天处理', notice: '提醒' }
const stats = [
  { label: '今日挂号', value: '286', change: '+12.6%', note: '较昨日同期', icon: 'calendar', tone: 'blue' },
  { label: '今日完成接诊', value: '198', change: '+8.4%', note: '线上 76 · 线下 122', icon: 'heart', tone: 'teal' },
  { label: '待处理事项', value: '13', change: '3 项紧急', note: '审批、退款、访问审核', icon: 'file', tone: 'purple' },
  { label: '库存预警', value: '4', change: '1 个缺货', note: '药品品类共 128 个', icon: 'warning', tone: 'amber' },
]
const trend = [72, 86, 66, 94, 82, 108, 116]
const departments = [{ name: '内科', value: 78, color: 'blue' }, { name: '儿科', value: 61, color: 'teal' }, { name: '皮肤科', value: 48, color: 'purple' }, { name: '妇科', value: 39, color: 'amber' }, { name: '其他', value: 27, color: 'slate' }]
const schedules = [{ time: '08:00–12:00', department: '内科', doctors: '5 位医生', mode: '线上问诊', filled: '42 / 60', state: '正常' }, { time: '08:00–12:00', department: '儿科', doctors: '3 位医生', mode: '线下面诊', filled: '31 / 36', state: '接近满号' }, { time: '14:00–18:00', department: '皮肤科', doctors: '2 位医生', mode: '线上问诊', filled: '18 / 30', state: '正常' }]

function refresh() { refreshCount.value += 1; lastRefreshed.value = refreshCount.value === 1 ? '刚刚' : `${refreshCount.value} 次刷新` }
function openTodo(todo: Todo) { activeTodo.value = todo }
function closeTodo() { activeTodo.value = null }
function navigate(module: string) {
  activeTodo.value = null
  if (module === '运营总览') return void router.push('/prototype/admin-home')
  if (module === '预约与订单') return void router.push({ path: '/prototype/admin-orders', query: { tab: 'appointments' } })
  if (module === '退款 / 异常') return void router.push({ path: '/prototype/admin-orders', query: { tab: 'refunds' } })
  if (module === '患者') return void router.push({ path: '/prototype/admin-patients' })
  if (module === '医生') return void router.push({ path: '/prototype/admin-doctors' })
  if (module === '科室') return void router.push({ path: '/prototype/admin-departments' })
  if (module === '排班') return void router.push({ path: '/prototype/admin-schedules' })
  if (module === '号源') return void router.push({ path: '/prototype/admin-slots' })
  void router.push({ path: '/prototype/admin-home', query: { module } })
}
</script>

<template>
  <div class="admin-prototype" :class="{ 'admin-collapsed': collapsed }">
    <aside class="admin-sidebar" :class="{ 'admin-mobile-open': mobileOpen }" aria-label="管理员端导航">
      <div class="admin-profile"><span class="admin-avatar">管</span><div class="admin-profile-copy"><strong>运营管理员</strong><span>平台治理与运营</span><em>管理员</em></div></div>
      <nav class="admin-nav" aria-label="管理员功能导航">
        <template v-for="group in groups" :key="group.name">
          <p class="admin-nav-caption">{{ group.name }}</p>
          <button v-for="item in group.items" :key="item.label" type="button" :class="{ active: item.label === activeModule }" :aria-label="item.label" @click="navigate(item.label)"><Icon :name="item.icon" /><span>{{ item.label }}</span><b v-if="item.badge">{{ item.badge }}</b></button>
        </template>
      </nav>
      <div class="admin-sidebar-bottom"><div class="admin-permission"><Icon name="lock" /><div><strong>医疗内容权限</strong><span>默认未开启</span></div></div><button type="button" class="admin-collapse" :aria-label="collapsed ? '展开侧栏' : '收起侧栏'" :aria-expanded="!collapsed" @click="collapsed = !collapsed"><Icon name="chevron" /><span>{{ collapsed ? '展开侧栏' : '收起侧栏' }}</span></button></div>
    </aside>
    <button v-if="mobileOpen" type="button" class="admin-backdrop" aria-label="关闭侧栏" @click="mobileOpen = false"></button>
    <section class="admin-workspace">
      <header class="admin-topbar">
        <div class="admin-topbar-left"><button type="button" class="admin-mobile-toggle" aria-label="打开侧栏" @click="mobileOpen = true"><Icon name="menu" /></button><div class="admin-brand"><span><Icon name="hospital" /></span><div><strong>智能医疗问诊预约系统</strong><small>ADMIN OPERATIONS</small></div></div><div class="admin-hospital"><i></i>市一医院 · 管理员工作台</div></div>
        <div class="admin-topbar-right"><div class="admin-updated"><span>数据更新于 {{ lastRefreshed }}</span><button type="button" aria-label="刷新运营数据" @click="refresh"><Icon name="refresh" /></button></div><button type="button" class="admin-bell" aria-label="查看通知"><Icon name="bell" /><i></i></button><div class="admin-user"><span>管</span><strong>运营管理员</strong><Icon name="chevron" /></div></div>
      </header>
      <main class="admin-content">
        <AdminPharmacyView v-if="pharmacyModule" :embedded="true" />
        <AdminMedicineOrdersPrototypeView v-else-if="medicineOrdersModule" :embedded="true" />
        <AdminGovernancePrototypeView v-else-if="governanceModule" :embedded="true" />
        <AdminNewsPrototypeView v-else-if="newsModule" :embedded="true" />
        <template v-else>
        <div class="admin-page-heading"><div><span class="admin-eyebrow">运营总览 / {{ range === 'today' ? '今日' : '近 7 日' }}</span><h1>早上好，管理员。</h1><p>这里是今天的医院运营状态，优先处理需要人工介入的事项。</p></div><div class="admin-date"><strong>13</strong><span>星期五<small>2026 年 11 月</small></span></div></div>
        <section class="admin-hero"><div><span class="admin-status"><i></i>系统运行正常</span><h2>今天有 {{ visibleTodos.length }} 项运营事项需要关注</h2><p>审批、退款和库存提醒已按紧急程度整理，点击即可进入对应处理流程。</p><div class="admin-hero-actions"><button type="button" class="admin-primary" @click="openTodo(visibleTodos[0])">查看紧急事项 <Icon name="arrow" /></button><button type="button" class="admin-hero-secondary" @click="navigate('排班')">查看今日排班 <Icon name="chevron" /></button></div></div><div class="admin-hero-card"><span class="admin-hero-kicker">TODAY'S OPERATIONS</span><strong>286</strong><span>今日挂号总量</span><div><b>线上 104</b><b>线下 182</b></div></div></section>
        <div class="admin-toolbar"><div class="admin-range" role="group" aria-label="统计时间范围"><button type="button" :class="{ active: range === 'today' }" @click="range = 'today'">今日</button><button type="button" :class="{ active: range === '7d' }" @click="range = '7d'">近 7 日</button></div><span>统计口径：医院当地时间 · 实时演示数据</span></div>
        <section class="admin-stats" aria-label="运营指标"><article v-for="stat in stats" :key="stat.label" class="admin-stat"><span class="admin-stat-icon" :class="stat.tone"><Icon :name="stat.icon" /></span><div><span>{{ stat.label }}</span><strong>{{ stat.value }}</strong><p><b>{{ stat.change }}</b> {{ stat.note }}</p></div></article></section>
        <section class="admin-dashboard">
          <div class="admin-main-column">
            <article class="admin-panel"><div class="admin-section-heading"><div><h2>近 7 日挂号趋势</h2><p>线上与线下登记总量</p></div><span class="admin-panel-note">总计 1,742</span></div><div class="admin-chart"><div class="admin-chart-y"><span>120</span><span>80</span><span>40</span><span>0</span></div><div class="admin-chart-area"><div class="admin-grid-lines"><i></i><i></i><i></i><i></i></div><div class="admin-bars"><span v-for="(value, index) in trend" :key="index" :style="{ height: `${value / 1.25}%` }"><b :title="`${value} 条登记`"></b><small>{{ ['11/07', '11/08', '11/09', '11/10', '11/11', '11/12', '今天'][index] }}</small></span></div></div></div><div class="admin-chart-legend"><span><i class="blue"></i>全部挂号</span><span><i class="teal"></i>今日为 286 条</span></div></article>
            <article class="admin-panel"><div class="admin-section-heading"><div><h2>今日出诊安排</h2><p>上午与下午有效排班 · 号源使用情况</p></div><button type="button" class="admin-link" @click="navigate('排班')">查看全部 <Icon name="arrow" /></button></div><div class="admin-schedule-list"><div v-for="item in schedules" :key="item.time + item.department" class="admin-schedule-row"><span class="admin-schedule-time">{{ item.time }}</span><div class="admin-schedule-info"><strong>{{ item.department }}</strong><span>{{ item.doctors }} · {{ item.mode }}</span></div><span class="admin-schedule-fill">{{ item.filled }}</span><span class="admin-schedule-state" :class="{ warning: item.state !== '正常' }"><i></i>{{ item.state }}</span><button type="button" :aria-label="`查看${item.department}排班`" @click="navigate('排班')"><Icon name="chevron" /></button></div></div></article>
          </div>
          <aside class="admin-side-column"><article class="admin-panel admin-todo-panel"><div class="admin-section-heading"><div><h2>待处理事项 <span>{{ visibleTodos.filter(t => t.priority === 'urgent').length }} 紧急</span></h2><p>按优先级排列</p></div><button type="button" class="admin-link" @click="navigate('预约与订单')">全部 <Icon name="arrow" /></button></div><div v-for="todo in visibleTodos" :key="todo.id" class="admin-todo-row" :class="todo.priority" @click="openTodo(todo)"><i></i><div><strong>{{ todo.title }}</strong><span>{{ todo.detail }}</span></div><button type="button" :aria-label="`${todo.title}详情`"><Icon name="chevron" /></button></div></article><article class="admin-panel"><div class="admin-section-heading"><div><h2>科室挂号分布</h2><p>今日有效登记</p></div><span class="admin-panel-note">253 条</span></div><div class="admin-department-list"><div v-for="department in departments" :key="department.name"><span>{{ department.name }}</span><div><i :class="department.color" :style="{ width: `${department.value}%` }"></i></div><b>{{ department.value }}</b></div></div></article></aside>
        </section>
        <footer class="admin-footer"><span>智能医疗问诊预约系统 · 运营驾驶舱</span><span><i></i>演示数据 · 不连接真实业务接口</span></footer>
        </template>
      </main>
    </section>
    <dialog v-if="activeTodo" open class="admin-dialog" aria-label="待处理事项详情" @click.self="closeTodo"><button type="button" class="admin-dialog-close" aria-label="关闭详情" @click="closeTodo"><Icon name="close" /></button><span class="admin-dialog-kicker">{{ priorityLabel[activeTodo.priority] }}</span><h2>{{ activeTodo.title }}</h2><p>{{ activeTodo.detail }}</p><div class="admin-dialog-detail"><span>所属模块</span><strong>{{ activeTodo.module }}</strong><span>当前状态</span><strong>{{ activeTodo.priority === 'urgent' ? '需要管理员处理' : '待处理' }}</strong></div><p class="admin-dialog-note">此处为首页演示抽屉，复杂业务会进入对应管理页面，医疗正文与附件不会在首页展示。</p><div class="admin-dialog-actions"><button type="button" class="admin-primary" @click="navigate(activeTodo.module)">进入{{ activeTodo.module }}</button><button type="button" class="admin-soft" @click="closeTodo">稍后处理</button></div></dialog>
  </div>
</template>
