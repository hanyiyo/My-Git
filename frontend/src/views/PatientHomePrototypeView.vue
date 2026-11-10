<script setup lang="ts">
// Three homepage layouts at /prototype/patient-home?variant=A|B|C, using fictional in-memory data.
// The complete patient shell is previewed outside the shared authenticated multi-role HomeView.
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useRouter } from 'vue-router'
import PrototypeSwitcher from '../components/PrototypeSwitcher.vue'
import Icon from './patient-home-prototype/PrototypeIcon.vue'
import VariantA from './patient-home-prototype/VariantA.vue'
import VariantB from './patient-home-prototype/VariantB.vue'
import VariantC from './patient-home-prototype/VariantC.vue'
import DepartmentDirectoryPrototypeView from './patient-home-prototype/DepartmentDirectoryPrototypeView.vue'
import { departments, notices, type HomeAction } from './patient-home-prototype/data'
import './patient-home-prototype/patient-home-prototype.css'

const route = useRoute()
const router = useRouter()
const variants = [{ key: 'A', name: '清新服务台' }, { key: 'B', name: '就诊时间线' }, { key: 'C', name: '导诊探索' }]
const variant = computed(() => variants.some(item => item.key === route.query.variant) ? String(route.query.variant) : 'A')
const directoryPage = computed(() => route.path === '/prototype/patient-home/departments')
const sidebarCollapsed = ref(window.innerWidth < 760), bannerIndex = ref(0), checkedIn = ref(false), paid = ref(false), bookingPaid = ref(false)
const panel = ref<HomeAction | null>(null), panelDialog = ref<HTMLDialogElement>(), aiDialog = ref<HTMLDialogElement>()
const orderFilter = ref('全部'), mode = ref('线下面诊'), department = ref('全科医学科'), registered = ref(false)
const aiOpen = ref(false), question = ref(''), imageName = ref(''), consent = ref(false), answered = ref(false)
const noticeRead = ref(false)
let bannerTimer: number | undefined
const nav: { action: HomeAction | 'home'; icon: string; name: string }[] = [
  { action: 'home', icon: 'home', name: '首页' }, { action: 'departments', icon: 'grid', name: '科室信息' },
  { action: 'doctors', icon: 'doctor', name: '医生团队' }, { action: 'registration', icon: 'calendar', name: '立即挂号' },
  { action: 'orders', icon: 'file', name: '我的挂号' }, { action: 'notifications', icon: 'bell', name: '站内通知' },
  { action: 'profile', icon: 'user', name: '个人中心' },
]
const topNav: { action: HomeAction | 'home'; name: string }[] = [
  { action: 'home', name: '首页' }, { action: 'doctors', name: '医生团队' },
  { action: 'departments', name: '科室信息' }, { action: 'registration', name: '立即挂号' },
  { action: 'orders', name: '我的挂号' }, { action: 'notifications', name: '站内通知' },
]
const panelTitles: Partial<Record<HomeAction, string>> = { registration: '挂号预览', orders: '我的挂号', departments: '科室信息', doctors: '医生团队', notifications: '站内通知', profile: '个人中心' }
const panelTitle = computed(() => panel.value ? panelTitles[panel.value] ?? '服务预览' : '服务预览')
const state = computed(() => `${sidebarCollapsed.value ? '侧栏收起' : '侧栏展开'} · 公告 ${bannerIndex.value + 1}/3 · ${checkedIn.value ? '候诊中' : '待签到'} · ${aiOpen.value ? '导诊打开' : panel.value ? panelTitle.value : '首页'} · 虚构数据`)
function open(action: HomeAction) {
  if (action === 'departments') { goToDepartments(); return }
  if (action === 'ai') { aiOpen.value = true; aiDialog.value?.showModal(); return }
  if (action === 'online' || action === 'offline' || action === 'registration') {
    mode.value = action === 'online' ? '线上问诊' : '线下面诊'; panel.value = 'registration'
  } else if (['checkin', 'payment', 'report', 'orders'].includes(action)) {
    orderFilter.value = action === 'checkin' ? '待就诊' : action === 'payment' ? '待付款' : action === 'report' ? '已完成' : '全部'; panel.value = 'orders'
  } else panel.value = action
  panelDialog.value?.showModal()
}
function closePanel() { panelDialog.value?.close(); panel.value = null }
function goHome() { closePanel(); void router.push({ path: '/prototype/patient-home', query: { variant: variant.value } }) }
function goToDepartments() { closePanel(); void router.push({ path: '/prototype/patient-home/departments', query: { variant: variant.value } }) }
function register() { registered.value = true; bookingPaid.value = false; panel.value = 'orders'; orderFilter.value = '待付款' }
function chooseDepartment(name: string) { department.value = name; panel.value = 'registration' }
function chooseImage(event: Event) { imageName.value = (event.target as HTMLInputElement).files?.[0]?.name ?? ''; consent.value = false; answered.value = false }
function ask() { if (question.value.trim() && consent.value) answered.value = true }
function recommend() { aiDialog.value?.close(); aiOpen.value = false; department.value = '全科医学科'; open('registration') }
onMounted(() => {
  bannerTimer = window.setInterval(() => { bannerIndex.value = (bannerIndex.value + 1) % 3 }, 6000)
})
onUnmounted(() => { if (bannerTimer) window.clearInterval(bannerTimer) })
</script>

<template>
  <div :class="['patient-prototype', `ph-variant-${variant}`, { 'ph-collapsed': sidebarCollapsed }]">
    <button v-if="!sidebarCollapsed" class="ph-mobile-backdrop" aria-label="收起侧栏" @click="sidebarCollapsed = true"></button>
    <aside class="ph-sidebar" aria-label="患者端侧栏">
      <button class="ph-patient-card" aria-label="打开个人中心" @click="open('profile')"><span class="ph-patient-avatar">林</span><span><strong>林同学 <i class="ph-online-dot"></i></strong><small>愿你拥有健康的每一天</small></span><Icon name="chevron" /></button>
      <p class="ph-nav-caption">就医服务</p>
      <nav aria-label="患者端导航"><button v-for="item in nav" :key="item.name" :class="{ active: item.action === 'home' && !directoryPage }" :aria-label="item.name" :aria-current="item.action === 'home' && !directoryPage ? 'page' : undefined" :title="sidebarCollapsed ? item.name : undefined" @click="item.action === 'home' ? goHome() : open(item.action)"><Icon :name="item.icon" /><span>{{ item.name }}</span><b v-if="item.action === 'notifications' && !noticeRead" class="ph-nav-count">3</b></button></nav>
      <div class="ph-sidebar-bottom"><div class="ph-sidebar-help"><Icon name="heart" /><strong>健康有约，服务有温度</strong><small>线上线下，都在你身边</small></div><button class="ph-register-fixed" aria-label="快捷立即挂号" @click="open('registration')"><Icon name="calendar" /><span>立即挂号</span><Icon name="arrow" /></button><button class="ph-collapse-button" :aria-label="sidebarCollapsed ? '展开侧栏' : '收起侧栏'" :aria-expanded="!sidebarCollapsed" @click="sidebarCollapsed = !sidebarCollapsed"><Icon name="menu" /><span>收起侧栏</span></button></div>
    </aside>
    <div class="ph-workspace">
      <header class="ph-topbar"><div class="ph-topbar-left"><button class="ph-mobile-menu" aria-label="展开侧栏" @click="sidebarCollapsed = false"><Icon name="menu" /></button><a class="ph-topbar-brand" href="#" aria-label="智能医疗问诊预约系统首页" @click.prevent="goHome"><span class="ph-brand-symbol"><Icon name="cross" /></span><span><strong>智能医疗问诊预约系统</strong><small>SMART HEALTHCARE</small></span></a><nav class="ph-topnav" aria-label="顶部快捷导航"><button v-for="item in topNav" :key="item.name" :class="{ active: item.action === 'home' ? !directoryPage : item.action === 'departments' ? directoryPage : false }" :aria-current="(item.action === 'home' ? !directoryPage : item.action === 'departments' ? directoryPage : false) ? 'page' : undefined" @click="item.action === 'home' ? goHome() : open(item.action)">{{ item.name }}</button></nav></div><div class="ph-topbar-right"><span class="ph-hospital-status"><i></i> 门诊服务开放中</span><span class="ph-topbar-divider"></span><button aria-label="查看站内通知" class="ph-notification-button" @click="open('notifications')"><Icon name="bell" /><i v-if="!noticeRead"></i></button><button class="ph-topbar-user" @click="open('profile')"><span>林</span><strong>林同学</strong><Icon name="chevron" /></button></div></header>
      <div class="ph-page-content" role="main" aria-label="患者端首页原型">
        <DepartmentDirectoryPrototypeView v-if="directoryPage" @action="open" @back="goHome" @select="department = $event" />
        <template v-else><VariantA v-if="variant === 'A'" :banner-index="bannerIndex" :checked-in="checkedIn" @action="open" @banner="bannerIndex = $event" /><VariantB v-else-if="variant === 'B'" :checked-in="checkedIn" @action="open" /><VariantC v-else @action="open" /><section class="ph-process-strip" aria-labelledby="patient-process-title"><div class="ph-process-heading"><span class="ph-eyebrow">YOUR CARE, STEP BY STEP</span><h2 id="patient-process-title">就诊流程，一看就懂</h2><p>从选择服务到完成就诊，每一步都有清晰提示。</p></div><ol class="ph-process-steps"><li><span>01</span><strong>选择服务</strong><small>科室、医生与就诊方式</small></li><li><span>02</span><strong>提交挂号</strong><small>选择日期、时段与号源</small></li><li><span>03</span><strong>完成支付</strong><small>挂号费确认预约凭证</small></li><li><span>04</span><strong>签到候诊</strong><small>在窗口内签到进入队列</small></li><li><span>05</span><strong>完成就诊</strong><small>查看病历，按需缴药费</small></li></ol></section><footer class="ph-footer"><span>智能医疗问诊预约系统 · 每一次关怀，都值得认真对待</span><span>隐私保护 <b>·</b> 服务指南 <b>·</b> 联系我们</span></footer></template>
      </div>
    </div>
    <button class="ph-ai-fab" aria-label="打开AI导诊助手" @click="open('ai')"><span><Icon name="sparkles" /></span><strong>AI 导诊</strong><small>就医问题，问问我</small></button>
    <PrototypeSwitcher :variants="variants" :state="state" />

    <dialog ref="panelDialog" class="ph-dialog" @close="panel = null">
      <div class="ph-dialog-heading"><div><span class="ph-dialog-kicker">PATIENT SERVICES · 交互预览</span><h2>{{ panelTitle }}</h2></div><button class="ph-icon-button" aria-label="关闭服务预览" @click="closePanel"><Icon name="close" /></button></div>
      <template v-if="panel === 'registration'"><p class="ph-dialog-note">选择就诊方式与科室，预览挂号流程。以下号源均为虚构数据。</p><div class="ph-form-row"><label>就诊类型<select v-model="mode" aria-label="就诊类型"><option>线上问诊</option><option>线下面诊</option></select></label><label>选择科室<select v-model="department" aria-label="选择科室"><option v-for="item in departments" :key="item.name">{{ item.name }}</option></select></label></div><div class="ph-schedule-preview"><span class="ph-doctor-avatar">陈</span><div><h3>陈予安 <small>主治医师</small></h3><p>{{ department }} · {{ mode }}</p><p>11 月 10 日 下午 14:00–18:00 · 剩余 8 号</p></div><strong>¥12.00</strong></div><button class="ph-primary ph-full" @click="register">选择此号源，生成演示挂号单 <Icon name="arrow" /></button><small class="ph-dialog-footnote">提交后先进入待付款；完成模拟支付后才确认挂号。</small></template>
      <template v-else-if="panel === 'orders'"><div class="ph-order-tabs" role="tablist" aria-label="挂号状态"><button v-for="tab in ['全部', '待付款', '待就诊', '就诊中', '已完成', '已取消']" :key="tab" role="tab" :aria-selected="orderFilter === tab" @click="orderFilter = tab">{{ tab }}</button></div><p v-if="registered" class="ph-inline-message" role="status">{{ bookingPaid ? '模拟支付成功，演示挂号已确认。' : '已生成演示挂号单，请在待付款中完成模拟支付。' }}</p><article v-if="orderFilter === '全部' || orderFilter === '待就诊'" class="ph-order-preview"><span class="ph-status-tag">{{ checkedIn ? '候诊中' : '待签到' }}</span><h3>全科医学科 · 陈予安医生</h3><p>线下面诊 · 11 月 10 日 14:00–18:00</p><p>签到窗口：13:30 至 17:45 前 · 演示时间 13:35</p><p v-if="checkedIn" role="status">签到成功 · 候诊序号 A012，请留意叫号通知。</p><button class="ph-primary" :disabled="checkedIn" @click="checkedIn = true">{{ checkedIn ? '已签到，等待叫号' : '模拟签到' }}</button></article><article v-if="orderFilter === '全部' || orderFilter === '待付款'" class="ph-order-preview"><span class="ph-status-tag orange">{{ paid ? '药费已缴清' : '药品待缴费' }}</span><h3>已完成就诊 · 独立药费单</h3><p>药费 ¥18.00 · 诊疗病历已保存，药费状态不影响查看</p><button class="ph-primary" :disabled="paid" @click="paid = true">{{ paid ? '已支付' : '模拟支付药费 ¥18.00' }}</button></article><article v-if="registered && !bookingPaid && (orderFilter === '待付款' || orderFilter === '全部')" class="ph-order-preview"><span class="ph-status-tag">挂号待缴费</span><h3>{{ department }} · {{ mode }}</h3><p>陈予安医生 · 11 月 10 日 14:00–18:00 · ¥12.00</p><button class="ph-primary" @click="bookingPaid = true; orderFilter = '待就诊'">模拟支付挂号费 ¥12.00</button></article><article v-if="orderFilter === '已完成'" class="ph-order-preview"><span class="ph-status-tag">已完成</span><h3>11 月 06 日 · 全科医学科</h3><p>病历总结：本次诊疗记录已归档。</p><p>报告入口用于查看已完成就诊资料；此处展示虚构病历示例。</p></article><p v-if="['就诊中', '已取消'].includes(orderFilter)" class="ph-empty">当前没有{{ orderFilter }}的演示挂号单。</p></template>
      <template v-else-if="panel === 'departments'"><p class="ph-dialog-note">选择科室后，查看相应的演示号源。</p><button v-for="item in departments" :key="item.name" class="ph-directory-row" @click="chooseDepartment(item.name)"><span class="ph-icon-tile teal"><Icon :name="item.icon" /></span><span><strong>{{ item.name }}</strong><small>{{ item.detail }} · 今日 {{ item.count }} 位医生出诊</small></span><Icon name="chevron" /></button></template>
      <template v-else-if="panel === 'doctors'"><div v-for="name in ['陈予安', '许知遥']" :key="name" class="ph-schedule-preview"><span class="ph-doctor-avatar">{{ name[0] }}</span><div><h3>{{ name }} <small>主治医师</small></h3><p>全科医学科 · 常见不适与健康咨询</p></div><button class="ph-soft-button" @click="panel = 'registration'">查看排班</button></div></template>
      <template v-else-if="panel === 'notifications'"><div class="ph-order-tabs"><span class="ph-status-tag">系统通知</span><span>医院新闻</span><button class="ph-text-button" @click="noticeRead = true">{{ noticeRead ? '已全部标记已读' : '全部标为已读' }}</button></div><article class="ph-order-preview"><h3>今日门诊签到提醒</h3><p>你的下午门诊将在 13:30 开放签到。</p><button class="ph-soft-button" @click="panel = 'orders'; orderFilter = '待就诊'">查看关联挂号</button></article><article v-for="notice in notices" :key="notice.title" class="ph-order-preview"><small>{{ notice.tag }} · {{ notice.date }}</small><h3>{{ notice.title }}</h3><p>{{ notice.body }}</p></article></template>
      <template v-else-if="panel === 'profile'"><div class="ph-profile-preview"><span class="ph-patient-avatar">林</span><h3>林同学</h3><p>患者账户 · 虚构演示资料</p></div><p class="ph-dialog-note">这里将展示头像、昵称、手机号，以及修改密码、隐私协议、关于系统和退出登录等入口。</p></template>
    </dialog>

    <dialog ref="aiDialog" class="ph-ai-dialog" @close="aiOpen = false">
      <div class="ph-dialog-heading"><div class="ph-ai-title"><span class="ph-icon-tile teal"><Icon name="sparkles" /></span><div><h2>AI 导诊助手</h2><p>就医这件事，我们一起理清楚。</p></div></div><button class="ph-icon-button" aria-label="关闭导诊助手" @click="aiDialog?.close()"><Icon name="close" /></button></div>
      <div class="ph-ai-chat"><div class="ph-ai-welcome"><span class="ph-ai-chip">AI</span><div><strong>你好，林同学 👋</strong><p>可以问我医院位置、就诊流程，也可以描述不适、选择检查图片，了解适合咨询的科室。</p></div></div><p class="ph-ai-boundary"><Icon name="shield" /> 仅辅助导诊，不提供诊断与处方。</p><form @submit.prevent="ask"><label for="prototype-symptoms">你想了解什么？</label><textarea id="prototype-symptoms" v-model="question" placeholder="例如：最近嗓子不舒服，想了解该挂什么科……" maxlength="500" required @input="consent = false; answered = false"></textarea><label class="ph-upload-label"><Icon name="image" /><span>{{ imageName || '添加检查图片（可选）' }}</span><input type="file" accept="image/jpeg,image/png" aria-label="添加检查图片" @change="chooseImage"></label><label class="ph-consent"><input v-model="consent" type="checkbox"><span>同意将本次输入用于辅助导诊建议</span></label><small class="ph-local-demo">本地原型演示，文字与图片不会发送到外部服务。</small><button class="ph-primary ph-full" :disabled="!question.trim() || !consent">获取演示科室建议 <Icon name="arrow" /></button></form><article v-if="answered" class="ph-ai-result" role="status"><span class="ph-status-tag">演示导诊建议</span><h3>可以先了解「全科医学科」</h3><p>全科提供常见不适的综合咨询，实际诊疗与进一步检查由医生判断。</p><p v-if="imageName">已选择图片：{{ imageName }}。此原型仅演示选择与授权，不进行图片识别。</p><button class="ph-soft-button" @click="recommend">查看全科号源 <Icon name="arrow" /></button></article></div>
    </dialog>
  </div>
</template>
