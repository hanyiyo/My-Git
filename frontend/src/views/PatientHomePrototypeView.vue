<script setup lang="ts">
// Three homepage layouts at /prototype/patient-home?variant=A|B|C, using fictional in-memory data.
// The complete patient shell is previewed outside the shared authenticated multi-role HomeView.
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useRouter } from 'vue-router'
import PrototypeSwitcher from '../components/PrototypeSwitcher.vue'
import Icon from './patient-home-prototype/PrototypeIcon.vue'
import VariantA from './patient-home-prototype/VariantA.vue'
import VariantB from './patient-home-prototype/VariantB.vue'
import VariantC from './patient-home-prototype/VariantC.vue'
import DepartmentDirectoryPrototypeView from './patient-home-prototype/DepartmentDirectoryPrototypeView.vue'
import RegistrationPrototypeView from './patient-home-prototype/RegistrationPrototypeView.vue'
import DoctorTeamPrototypeView from './patient-home-prototype/DoctorTeamPrototypeView.vue'
import OrdersPrototypeView from './patient-home-prototype/OrdersPrototypeView.vue'
import ConsultationPrototypeView from './patient-home-prototype/ConsultationPrototypeView.vue'
import NotificationsPrototypeView from './patient-home-prototype/NotificationsPrototypeView.vue'
import { createPrototypeNotices } from './patient-home-prototype/notification-data'
import { prototypeOrders, statusForTab } from './patient-home-prototype/order-data'
import { departments, type HomeAction } from './patient-home-prototype/data'
import { departmentDoctors, departmentProfiles, type PrototypeDepartmentDoctor } from './patient-home-prototype/department-data'
import './patient-home-prototype/patient-home-prototype.css'
import './patient-home-prototype/profile-prototype.css'

interface AiAttachment {
  id: string
  name: string
  url: string
  size: number
}

interface AiDoctorRecommendation {
  id: string
  name: string
  title: string
  specialty: string
  time: string
  place: string
  mode: PrototypeDepartmentDoctor['mode']
  slots: number | null
}

interface AiResult {
  summary: string
  risk: string
  department: string
  departmentDescription: string
  doctors: AiDoctorRecommendation[]
  advice: string
  imageNote: string
}

interface AiMessage {
  id: string
  role: 'patient' | 'assistant'
  text: string
  images: AiAttachment[]
  result?: AiResult
}

interface AiRetryPayload {
  text: string
  images: AiAttachment[]
}

type ProfileSection = 'overview' | 'edit' | 'notifications' | 'password' | 'privacy' | 'agreement' | 'about'
type ProfileConfirmKind = 'logout' | 'discard'

const aiScenarioRules: { keywords: string[]; department: string; summary: string; advice: string }[] = [
  { keywords: ['牙疼', '牙痛', '牙龈', '口腔', '牙齿'], department: '口腔科', summary: '你提到了牙齿或口腔不适，系统先整理为口腔相关症状。', advice: '先避免刺激性食物，记录疼痛出现的时间和诱因；持续或加重时尽快咨询口腔科医生。' },
  { keywords: ['皮肤', '红疹', '瘙痒', '过敏', '湿疹'], department: '皮肤科', summary: '你提到了皮肤变化或瘙痒，系统先整理为皮肤相关症状。', advice: '保持患处清洁，暂时避免已知刺激物，不要自行叠加使用多种药膏。' },
  { keywords: ['咳嗽', '咽痛', '嗓子', '鼻塞', '呼吸道'], department: '内科', summary: '你提到了咳嗽、咽喉或鼻部不适，系统先整理为呼吸道相关症状。', advice: '注意休息和补充水分，记录体温与症状持续时间；症状持续或加重时咨询内科医生。' },
  { keywords: ['胃痛', '腹痛', '恶心', '消化', '腹泻'], department: '内科', summary: '你提到了胃肠道不适，系统先整理为消化系统相关症状。', advice: '饮食尽量清淡，记录进食与症状的关系；持续不适时咨询内科医生。' },
  { keywords: ['胸痛', '呼吸困难', '意识不清', '昏厥', '大量出血'], department: '全科医学科', summary: '你的描述包含需要优先人工关注的信号，系统只做风险提示，不作判断。', advice: '请优先联系人工医疗服务或急救服务，不要仅依赖线上导诊结果。' },
  { keywords: ['头晕', '乏力', '发热', '不舒服', '症状'], department: '全科医学科', summary: '你提到了全身不适，系统先整理为需要综合咨询的症状。', advice: '保证休息和饮水，记录症状开始时间与变化；不确定挂什么科时可以先咨询全科医学科。' },
]

const route = useRoute()
const router = useRouter()
const variants = [{ key: 'A', name: '清新服务台' }, { key: 'B', name: '就诊时间线' }, { key: 'C', name: '导诊探索' }]
const directoryPage = computed(() => route.path === '/prototype/patient-home/departments')
const registrationPage = computed(() => directoryPage.value && route.query.view === 'registration')
const doctorsPage = computed(() => directoryPage.value && route.query.view === 'doctors')
const ordersPage = computed(() => route.path === '/prototype/patient-home/orders')
const consultationPage = computed(() => route.path === '/prototype/patient-home/consultation')
const notificationsPage = computed(() => route.path === '/prototype/patient-home/notifications')
const profilePage = computed(() => route.path === '/prototype/patient-home/profile')
const homePage = computed(() => route.path === '/prototype/patient-home')
const departmentVariants = [{ key: 'A', name: '左侧科室导航' }, { key: 'B', name: '今日出诊看板' }, { key: 'C', name: '医生列表优先' }]
const variant = computed(() => { const options = directoryPage.value ? departmentVariants : variants; return options.some(item => item.key === route.query.variant) ? String(route.query.variant) : 'A' })
const prototypeVariants = computed(() => directoryPage.value ? departmentVariants : variants)
const sidebarCollapsed = ref(window.innerWidth < 760), bannerIndex = ref(0), checkedIn = ref(false), paid = ref(false), bookingPaid = ref(false)
const panel = ref<HomeAction | null>(null), panelDialog = ref<HTMLDialogElement>(), aiDialog = ref<HTMLDialogElement>()
const orderFilter = ref('全部'), mode = ref<'线下面诊' | '线上问诊'>('线下面诊'), department = ref('全科医学科'), registrationDoctor = ref('陈予安'), registered = ref(false)
const aiOpen = ref(false), question = ref(''), consent = ref(false), aiBusy = ref(false), aiError = ref('')
const aiAttachments = ref<AiAttachment[]>([]), aiMessages = ref<AiMessage[]>([]), aiRetryPayload = ref<AiRetryPayload | null>(null)
const noticeReceipts = ref(createPrototypeNotices())
const notificationUnread = computed(() => noticeReceipts.value.filter(item => item.tab === 'system' && !item.read && !item.deleted).length)
const patientOrders = ref(prototypeOrders.map(order => ({ ...order })))
const profileSection = ref<ProfileSection>('overview')
const profileName = ref('林同学'), profilePhone = ref('13800138000'), profileAvatarUrl = ref('')
const draftProfileName = ref(''), draftProfilePhone = ref(''), draftProfilePhoneCode = ref(''), draftProfileAvatarUrl = ref('')
const profileError = ref(''), profileNotice = ref(''), passwordError = ref('')
const oldPassword = ref(''), newPassword = ref(''), confirmNewPassword = ref('')
const systemNotifications = ref(true), appointmentReminders = ref(true)
const profileConfirmDialog = ref<HTMLDialogElement>(), profileConfirmKind = ref<ProfileConfirmKind>('logout')
const selectedAgreement = ref<'隐私协议' | '用户协议'>('隐私协议')
const profileConfirmTitle = computed(() => profileConfirmKind.value === 'logout' ? '确认退出登录？' : '放弃未保存的修改？')
const profileConfirmDescription = computed(() => profileConfirmKind.value === 'logout' ? '退出后需要重新登录才能继续使用患者端。' : '当前编辑内容还没有保存，关闭后这些修改会丢失。')
const profileDirty = computed(() => profileSection.value === 'edit' ? draftProfileName.value !== profileName.value || draftProfilePhone.value !== profilePhone.value || draftProfileAvatarUrl.value !== profileAvatarUrl.value || !!draftProfilePhoneCode.value : profileSection.value === 'password' && !!(oldPassword.value || newPassword.value || confirmNewPassword.value))
const maskedProfilePhone = computed(() => profilePhone.value.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2'))
const profileInitial = computed(() => (profileName.value.trim() || '林')[0])
let bannerTimer: number | undefined
let aiMessageId = 0
const nav: { action: HomeAction | 'home'; icon: string; name: string }[] = [
  { action: 'home', icon: 'home', name: '首页' }, { action: 'departments', icon: 'grid', name: '科室信息' },
  { action: 'doctors', icon: 'doctor', name: '医生列表' }, { action: 'registration', icon: 'calendar', name: '立即挂号' },
  { action: 'orders', icon: 'file', name: '我的挂号' }, { action: 'consultation', icon: 'chat', name: '图文诊室' }, { action: 'notifications', icon: 'bell', name: '站内通知' },
  { action: 'profile', icon: 'user', name: '个人中心' },
]
const topNav = nav
const panelTitles: Partial<Record<HomeAction, string>> = { registration: '挂号预览', orders: '我的挂号', departments: '科室信息', doctors: '医生列表', consultation: '图文诊室', notifications: '站内通知', profile: '个人中心' }
const panelTitle = computed(() => panel.value ? panelTitles[panel.value] ?? '服务预览' : '服务预览')
const state = computed(() => `${sidebarCollapsed.value ? '侧栏收起' : '侧栏展开'} · 公告 ${bannerIndex.value + 1}/3 · ${checkedIn.value ? '候诊中' : '待签到'} · ${aiOpen.value ? 'AI助手打开' : panel.value ? panelTitle.value : '首页'} · 虚构数据`)
function open(action: HomeAction) {
  if (action === 'profile') {
    closePanelImmediately()
    if (window.innerWidth < 760) sidebarCollapsed.value = true
    void router.push('/prototype/patient-home/profile')
    return
  }
  if (action === 'departments') { goToDepartments(); return }
  if (action === 'doctors') { goToDoctors(); return }
  if (action === 'orders') { goToOrders('待就诊'); return }
  if (action === 'notifications') { goToNotifications(); return }
  if (action === 'consultation') { goToConsultation('ai'); return }
  if (action === 'registration' || action === 'online' || action === 'offline') {
    mode.value = action === 'online' ? '线上问诊' : '线下面诊'
    if (!directoryPage.value) department.value = '全部科室'
    goToRegistration(directoryPage.value ? registrationDoctor.value : '', directoryPage.value)
    return
  }
  if (action === 'ai') { aiOpen.value = true; aiDialog.value?.showModal(); return }
  if (['checkin', 'payment', 'report'].includes(action)) {
    goToOrders(action === 'checkin' ? '待就诊' : action === 'payment' ? '待付款' : '已完成'); return
  } else {
    panel.value = action
  }
  panelDialog.value?.showModal()
}
function openProfileOverview() {
  profileSection.value = 'overview'
  profileError.value = ''
  profileNotice.value = ''
  passwordError.value = ''
  oldPassword.value = newPassword.value = confirmNewPassword.value = ''
}
function backToProfile() {
  closePanel()
}
function openProfileSection(section: Exclude<ProfileSection, 'overview'>) {
  profileError.value = ''
  profileNotice.value = ''
  passwordError.value = ''
  if (section === 'edit') {
    draftProfileName.value = profileName.value
    draftProfilePhone.value = profilePhone.value
    draftProfilePhoneCode.value = ''
    draftProfileAvatarUrl.value = profileAvatarUrl.value
  }
  profileSection.value = section
  panel.value = 'profile'
  panelDialog.value?.showModal()
}
function closePanelImmediately() {
  panelDialog.value?.close()
  panel.value = null
  openProfileOverview()
}
function askProfileConfirmation(kind: ProfileConfirmKind) {
  profileConfirmKind.value = kind
  profileConfirmDialog.value?.showModal()
}
function closePanel() {
  if (panel.value === 'profile' && profileDirty.value) {
    askProfileConfirmation('discard')
    return
  }
  closePanelImmediately()
}
function confirmProfileAction() {
  profileConfirmDialog.value?.close()
  if (profileConfirmKind.value === 'logout') {
    closePanelImmediately()
    void router.push('/prototype/auth')
    return
  }
  if (draftProfileAvatarUrl.value && draftProfileAvatarUrl.value !== profileAvatarUrl.value) URL.revokeObjectURL(draftProfileAvatarUrl.value)
  draftProfileAvatarUrl.value = profileAvatarUrl.value
  closePanelImmediately()
}
function cancelProfileConfirmation() { profileConfirmDialog.value?.close() }
function chooseProfileAvatar(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) {
    profileError.value = '请选择 JPG 或 PNG 图片，且大小不超过 5MB。'
    input.value = ''
    return
  }
  if (draftProfileAvatarUrl.value && draftProfileAvatarUrl.value !== profileAvatarUrl.value) URL.revokeObjectURL(draftProfileAvatarUrl.value)
  draftProfileAvatarUrl.value = URL.createObjectURL(file)
  profileError.value = ''
  input.value = ''
}
function saveProfile() {
  const name = draftProfileName.value.trim()
  const phone = draftProfilePhone.value.trim()
  profileError.value = ''
  if (!name) { profileError.value = '请输入昵称。'; return }
  if (!/^1[3-9]\d{9}$/.test(phone)) { profileError.value = '请输入有效的中国大陆手机号。'; return }
  if (phone !== profilePhone.value && !/^\d{6}$/.test(draftProfilePhoneCode.value.trim())) { profileError.value = '手机号变更需要填写 6 位验证码。'; return }
  if (profileAvatarUrl.value && profileAvatarUrl.value !== draftProfileAvatarUrl.value) URL.revokeObjectURL(profileAvatarUrl.value)
  profileName.value = name
  profilePhone.value = phone
  profileAvatarUrl.value = draftProfileAvatarUrl.value
  closePanelImmediately()
  profileNotice.value = '资料已保存。'
}
function notificationSaved() { profileNotice.value = '通知偏好已保存。' }
function submitPassword() {
  passwordError.value = ''
  if (!oldPassword.value || !newPassword.value || !confirmNewPassword.value) { passwordError.value = '请完整填写三项密码。'; return }
  if (newPassword.value.length < 8) { passwordError.value = '新密码至少需要 8 位。'; return }
  if (newPassword.value !== confirmNewPassword.value) { passwordError.value = '两次输入的新密码不一致。'; return }
  closePanelImmediately()
  void router.push('/prototype/auth?reason=password-changed')
}
function goHome() { closePanel(); void router.push({ path: '/prototype/patient-home', query: { variant: 'A' } }) }
function goToDepartments() { closePanel(); void router.push({ path: '/prototype/patient-home/departments', query: { variant: variant.value } }) }
function goToDoctors() { closePanel(); void router.push({ path: '/prototype/patient-home/departments', query: { variant: variant.value, view: 'doctors' } }) }
function goToRegistration(doctor = '', resetFilters = true) { closePanel(); registrationDoctor.value = doctor; if (resetFilters) { mode.value = '线下面诊'; department.value = '全部科室' }; void router.push({ path: '/prototype/patient-home/departments', query: { variant: variant.value, view: 'registration' } }) }
function goToOrders(status = '待就诊') { closePanel(); void router.push({ path: '/prototype/patient-home/orders', query: { status } }) }
function goToNotifications(tab = 'system', notice?: string) { closePanel(); void router.push({ path: '/prototype/patient-home/notifications', query: { tab, ...(notice ? { notice } : {}) } }) }
function goToNoticeOrder(id: string) { const order = patientOrders.value.find(item => item.id === id); if (order) void router.push({ path: '/prototype/patient-home/orders', query: { status: statusForTab(order), order: id } }) }
function goToConsultation(conversation = 'ai') { closePanel(); void router.push({ path: '/prototype/patient-home/consultation', query: { conversation } }) }
function register() { registered.value = true; bookingPaid.value = false; panel.value = 'orders'; orderFilter.value = '待付款' }
function chooseDepartment(name: string) { department.value = name; panel.value = 'registration' }
function selectDepartment(name: string, doctor?: string) { department.value = name; if (doctor) registrationDoctor.value = doctor }
function nextAiId(prefix: string) { aiMessageId += 1; return `${prefix}-${aiMessageId}` }
function modeLabel(value: PrototypeDepartmentDoctor['mode']) { return value === 'ONLINE' ? '线上图文问诊' : value === 'OFFLINE' ? '线下面诊' : '暂无今日号源' }
function recommendDoctors(departmentName: string): AiDoctorRecommendation[] {
  const doctors = [...(departmentDoctors[departmentName] ?? [])].sort((a, b) => {
    const aAvailable = a.slots !== null ? 1 : 0
    const bAvailable = b.slots !== null ? 1 : 0
    return bAvailable - aAvailable || (b.slots ?? -1) - (a.slots ?? -1)
  })
  return doctors.slice(0, 2).map(doctor => ({ id: doctor.id, name: doctor.name, title: doctor.title, specialty: doctor.specialty, time: `${doctor.time} · ${modeLabel(doctor.mode)}`, place: doctor.place, mode: doctor.mode, slots: doctor.slots }))
}
function analyzeAiMessage(text: string, images: AiAttachment[]): AiResult {
  const matched = aiScenarioRules.find(rule => rule.keywords.some(keyword => text.includes(keyword))) ?? aiScenarioRules[aiScenarioRules.length - 1]
  const profile = departmentProfiles[matched.department] ?? departmentProfiles['全科医学科']
  const highRisk = ['胸痛', '呼吸困难', '意识不清', '昏厥', '大量出血'].some(keyword => text.includes(keyword))
  return {
    summary: images.length && !text ? '已收到你上传的图片。当前原型只完成图片预览，暂不做图片识别；如需导诊建议，请补充文字症状。' : matched.summary,
    risk: highRisk ? '出现胸痛、呼吸困难或意识异常等情况时，请立即联系人工医疗服务或急救服务。' : '',
    department: matched.department,
    departmentDescription: profile.description,
    doctors: recommendDoctors(matched.department),
    advice: matched.advice,
    imageNote: images.length ? `本条消息包含 ${images.length} 张图片；当前仅保存预览，不进行图片识别。` : '',
  }
}
function chooseImages(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  const remaining = 5 - aiAttachments.value.length
  const invalid = files.find(file => !['image/jpeg', 'image/png'].includes(file.type) || file.size > 10 * 1024 * 1024)
  if (invalid) aiError.value = `${invalid.name} 不是支持的图片，或超过 10MB。`
  const accepted = files.filter(file => ['image/jpeg', 'image/png'].includes(file.type) && file.size <= 10 * 1024 * 1024).slice(0, Math.max(0, remaining))
  aiAttachments.value.push(...accepted.map(file => ({ id: nextAiId('image'), name: file.name, url: URL.createObjectURL(file), size: file.size })))
  if (files.length > remaining) aiError.value = '每条消息最多上传 5 张图片。'
  input.value = ''
}
function removeImage(id: string) {
  const attachment = aiAttachments.value.find(item => item.id === id)
  if (attachment) URL.revokeObjectURL(attachment.url)
  aiAttachments.value = aiAttachments.value.filter(item => item.id !== id)
}
async function runAiAnalysis(payload: AiRetryPayload, retry = false) {
  aiBusy.value = true
  aiError.value = ''
  await new Promise(resolve => window.setTimeout(resolve, 180))
  if (payload.text.includes('演示失败') && !retry) {
    aiRetryPayload.value = payload
    aiError.value = '暂时无法完成辅助分析，请稍后重试。'
  } else {
    aiMessages.value.push({ id: nextAiId('assistant'), role: 'assistant', text: '', images: [], result: analyzeAiMessage(payload.text, payload.images) })
    aiRetryPayload.value = null
  }
  aiBusy.value = false
}
async function ask() {
  const text = question.value.trim()
  if ((!text && !aiAttachments.value.length) || !consent.value || aiBusy.value) return
  const payload = { text, images: aiAttachments.value.map(item => ({ ...item })) }
  aiMessages.value.push({ id: nextAiId('patient'), role: 'patient', text, images: payload.images })
  question.value = ''
  aiAttachments.value = []
  await runAiAnalysis(payload)
}
async function retryAi() {
  if (aiRetryPayload.value && !aiBusy.value) await runAiAnalysis(aiRetryPayload.value, true)
}
function newAiSession() {
  aiMessages.value = []
  question.value = ''
  consent.value = false
  aiError.value = ''
  aiRetryPayload.value = null
  aiAttachments.value = []
}
function closeAi() { aiDialog.value?.close(); aiOpen.value = false }
function goToAiRegistration(departmentName: string, doctor?: AiDoctorRecommendation) {
  department.value = departmentName
  registrationDoctor.value = doctor?.name ?? ''
  if (doctor?.mode === 'ONLINE') mode.value = '线上问诊'
  if (doctor?.mode === 'OFFLINE') mode.value = '线下面诊'
  closeAi()
  goToRegistration(doctor?.name ?? '', false)
}
const temporaryTabs = ref<string[]>([])
const tabDefinitions = computed(() => {
  const currentConversation = String(route.query.conversation || 'ai')
  const consultationLabel = currentConversation === 'today-online-waiting' ? '许知遥 · 图文诊室' : currentConversation === 'completed-consultation' ? '陈予安 · 图文诊室' : '图文诊室'
  return [
    { id: 'home', label: '首页', path: '/prototype/patient-home', query: { variant: 'A' }, fixed: true },
    { id: 'registration', label: '立即挂号', path: '/prototype/patient-home/departments', query: { variant: variant.value, view: 'registration' }, fixed: false },
    { id: 'departments', label: '科室信息', path: '/prototype/patient-home/departments', query: { variant: variant.value }, fixed: false },
    { id: 'doctors', label: '医生列表', path: '/prototype/patient-home/departments', query: { variant: variant.value, view: 'doctors' }, fixed: false },
    { id: 'orders', label: '我的挂号', path: '/prototype/patient-home/orders', query: { status: String(route.query.status || '待就诊') }, fixed: false },
    { id: 'notifications', label: '站内通知', path: '/prototype/patient-home/notifications', query: { tab: String(route.query.tab || 'system') }, fixed: false },
    { id: 'consultation', label: consultationLabel, path: '/prototype/patient-home/consultation', query: { conversation: currentConversation }, fixed: false },
    { id: 'profile', label: '个人中心', path: '/prototype/patient-home/profile', query: {}, fixed: false },
  ]
})
const currentTabId = computed(() => profilePage.value ? 'profile' : notificationsPage.value ? 'notifications' : consultationPage.value ? 'consultation' : ordersPage.value ? 'orders' : registrationPage.value ? 'registration' : doctorsPage.value ? 'doctors' : directoryPage.value ? 'departments' : homePage.value ? 'home' : '')
const visibleTabs = computed(() => {
  const ids = new Set(['home', ...temporaryTabs.value, ...(currentTabId.value ? [currentTabId.value] : [])])
  return tabDefinitions.value.filter(tab => ids.has(tab.id))
})
function openTab(tab: { path: string; query: Record<string, string | undefined> }) { void router.push({ path: tab.path, query: tab.query }) }
function closeTab(id: string) {
  if (id === 'home') return
  temporaryTabs.value = temporaryTabs.value.filter(item => item !== id)
  if (currentTabId.value === id) openTab(tabDefinitions.value.find(item => item.id === 'home')!)
}
watch(currentTabId, id => { if (id && id !== 'home' && !temporaryTabs.value.includes(id)) temporaryTabs.value.push(id) }, { immediate: true })
onMounted(() => {
  bannerTimer = window.setInterval(() => { bannerIndex.value = (bannerIndex.value + 1) % 3 }, 6000)
})
onUnmounted(() => {
  if (bannerTimer) window.clearInterval(bannerTimer)
  new Set([profileAvatarUrl.value, draftProfileAvatarUrl.value]).forEach(url => { if (url) URL.revokeObjectURL(url) })
})
</script>

<template>
  <div :class="['patient-prototype', `ph-variant-${variant}`, { 'ph-collapsed': sidebarCollapsed }]">
    <button v-if="!sidebarCollapsed" class="ph-mobile-backdrop" aria-label="收起侧栏" @click="sidebarCollapsed = true"></button>
    <aside class="ph-sidebar" aria-label="患者端侧栏">
      <button class="ph-patient-card" aria-label="打开个人中心" @click="open('profile')"><span class="ph-patient-avatar"><img v-if="profileAvatarUrl" :src="profileAvatarUrl" alt="患者头像"><template v-else>{{ profileInitial }}</template></span><span><strong>{{ profileName }} <i class="ph-online-dot"></i></strong><small>愿你拥有健康的每一天</small></span><Icon name="chevron" /></button>
      <p class="ph-nav-caption">就医服务</p>
      <nav aria-label="患者端导航"><button v-for="item in nav" :key="item.name" :class="{ active: item.action === currentTabId }" :aria-label="item.name" :aria-current="item.action === currentTabId ? 'page' : undefined" :title="sidebarCollapsed ? item.name : undefined" @click="item.action === 'home' ? goHome() : open(item.action)"><Icon :name="item.icon" /><span>{{ item.name }}</span><b v-if="item.action === 'notifications' && notificationUnread > 0" class="ph-nav-count" data-testid="system-unread-count">{{ notificationUnread }}</b></button></nav>
      <div class="ph-sidebar-bottom"><div class="ph-sidebar-help"><Icon name="heart" /><strong>健康有约，服务有温度</strong><small>线上线下，都在你身边</small></div><button class="ph-register-fixed" aria-label="快捷立即挂号" @click="open('registration')"><Icon name="calendar" /><span>立即挂号</span><Icon name="arrow" /></button><button class="ph-collapse-button" :aria-label="sidebarCollapsed ? '展开侧栏' : '收起侧栏'" :aria-expanded="!sidebarCollapsed" @click="sidebarCollapsed = !sidebarCollapsed"><Icon name="menu" /><span>收起侧栏</span></button></div>
    </aside>
    <div class="ph-workspace">
      <header class="ph-topbar"><div class="ph-topbar-left"><button class="ph-mobile-menu" aria-label="展开侧栏" @click="sidebarCollapsed = false"><Icon name="menu" /></button><a class="ph-topbar-brand" href="#" aria-label="智能医疗问诊预约系统首页" @click.prevent="goHome"><span class="ph-brand-symbol"><Icon name="cross" /></span><span><strong>智能医疗问诊预约系统</strong><small>SMART HEALTHCARE</small></span></a><nav class="ph-topnav" aria-label="顶部快捷导航"><button v-for="item in topNav" :key="item.name" :class="{ active: item.action === currentTabId }" :aria-current="item.action === currentTabId ? 'page' : undefined" @click="item.action === 'home' ? goHome() : open(item.action)">{{ item.name }}</button></nav></div><div class="ph-topbar-right"><span class="ph-hospital-status"><i></i> 门诊服务开放中</span><span class="ph-topbar-divider"></span><button aria-label="查看站内通知" class="ph-notification-button" @click="open('notifications')"><Icon name="bell" /><i v-if="notificationUnread > 0" data-testid="notification-unread-dot"></i></button><button class="ph-topbar-user" @click="open('profile')"><span><img v-if="profileAvatarUrl" :src="profileAvatarUrl" alt="患者头像"><template v-else>{{ profileInitial }}</template></span><strong>{{ profileName }}</strong><Icon name="chevron" /></button></div></header>
      <nav class="ph-workspace-tabs" aria-label="临时工作区标签"><button v-for="tab in visibleTabs" :key="tab.id" type="button" :class="{ active: tab.id === currentTabId }" @click="openTab(tab)"><Icon :name="tab.id === 'home' ? 'home' : tab.id === 'orders' ? 'file' : tab.id === 'registration' ? 'calendar' : tab.id === 'departments' ? 'grid' : tab.id === 'doctors' ? 'doctor' : tab.id === 'notifications' ? 'bell' : tab.id === 'profile' ? 'user' : 'chat'" /><span>{{ tab.label }}</span><i v-if="!tab.fixed" class="ph-workspace-tab-close" role="button" :aria-label="`关闭${tab.label}`" @click.stop="closeTab(tab.id)">×</i></button></nav>
      <div class="ph-page-content" role="main" aria-label="患者端首页原型">
        <section v-if="profilePage" class="ph-profile-page" data-testid="profile-page" aria-labelledby="profile-page-title">
          <header class="ph-profile-page-heading"><span class="ph-eyebrow">YOUR PERSONAL SPACE</span><h1 id="profile-page-title">个人中心</h1><p>管理个人资料与账户设置，让每一次就医更安心。</p></header>
          <div class="ph-profile-page-grid"><div class="ph-profile-card"><div class="ph-profile-overview">
            <div class="ph-profile-identity"><span class="ph-profile-avatar"><img v-if="profileAvatarUrl" :src="profileAvatarUrl" alt="当前头像"><span v-else>{{ profileInitial }}</span></span><div><h3>{{ profileName }}</h3><p>患者账户 · 虚构演示资料</p></div><button class="ph-soft-button" type="button" @click="openProfileSection('edit')">编辑资料 <Icon name="edit" /></button></div>
            <dl class="ph-profile-facts"><div><dt>手机号</dt><dd>{{ maskedProfilePhone }}</dd></div><div><dt>账户状态</dt><dd><span class="ph-profile-online-dot" />正常使用</dd></div></dl>
          </div>
          <p v-if="profileNotice" class="ph-inline-message" role="status">{{ profileNotice }}</p>
          </div><div class="ph-profile-card"><h2 class="ph-profile-card-title">账户与设置</h2><div class="ph-profile-section-list" aria-label="个人中心功能">
            <button type="button" @click="openProfileSection('notifications')"><span class="ph-profile-action-icon teal"><Icon name="bell" /></span><span><strong>通知设置</strong><small>管理系统通知和预约提醒</small></span><Icon name="chevron" /></button>
            <button type="button" @click="openProfileSection('password')"><span class="ph-profile-action-icon blue"><Icon name="lock" /></span><span><strong>修改密码</strong><small>定期更新密码，保护账户安全</small></span><Icon name="chevron" /></button>
            <button type="button" @click="openProfileSection('privacy')"><span class="ph-profile-action-icon purple"><Icon name="shield" /></span><span><strong>隐私与协议</strong><small>查看隐私协议和用户协议</small></span><Icon name="chevron" /></button>
            <button type="button" @click="openProfileSection('about')"><span class="ph-profile-action-icon orange"><Icon name="info" /></span><span><strong>关于系统</strong><small>版本信息与服务说明</small></span><Icon name="chevron" /></button>
          </div>
          <button class="ph-profile-logout" type="button" @click="askProfileConfirmation('logout')"><Icon name="logout" />退出登录</button>
          </div></div><p class="ph-profile-boundary"><Icon name="shield" />病历、检查报告和 AI 会话在独立医疗服务模块中管理。</p>
        </section>
        <OrdersPrototypeView v-else-if="ordersPage" :initial-status="String(route.query.status || '待就诊')" :orders="patientOrders" :selected-order="String(route.query.order || '')" @consultation="goToConsultation" />
        <NotificationsPrototypeView v-else-if="notificationsPage" v-model:receipts="noticeReceipts" :orders="patientOrders" @order="goToNoticeOrder" />
        <ConsultationPrototypeView v-else-if="consultationPage" :initial-conversation="String(route.query.conversation || 'ai')" />
        <DoctorTeamPrototypeView v-else-if="doctorsPage" @register="goToRegistration" />
        <RegistrationPrototypeView v-else-if="registrationPage" :initial-mode="mode" :initial-department="department" :initial-doctor="registrationDoctor" @back="goToDepartments" @orders="goToOrders('待付款')" />
        <DepartmentDirectoryPrototypeView v-else-if="directoryPage" :variant="variant" @action="open" @back="goHome" @select="selectDepartment" />
        <template v-else><VariantA v-if="variant === 'A'" :banner-index="bannerIndex" :checked-in="checkedIn" @action="open" @news="goToNotifications('news', $event)" @banner="bannerIndex = $event" /><VariantB v-else-if="variant === 'B'" :checked-in="checkedIn" @action="open" @news="goToNotifications('news', $event)" /><VariantC v-else @action="open" @news="goToNotifications('news', $event)" /><section class="ph-process-strip" aria-labelledby="patient-process-title"><div class="ph-process-heading"><span class="ph-eyebrow">YOUR CARE, STEP BY STEP</span><h2 id="patient-process-title">就诊流程，一看就懂</h2><p>从选择服务到完成就诊，每一步都有清晰提示。</p></div><ol class="ph-process-steps"><li><span>01</span><strong>选择服务</strong><small>科室、医生与就诊方式</small></li><li><span>02</span><strong>提交挂号</strong><small>选择日期、时段与号源</small></li><li><span>03</span><strong>完成支付</strong><small>挂号费确认预约凭证</small></li><li><span>04</span><strong>签到候诊</strong><small>在窗口内签到进入队列</small></li><li><span>05</span><strong>完成就诊</strong><small>查看病历，按需缴药费</small></li></ol></section><footer class="ph-footer"><span>智能医疗问诊预约系统 · 每一次关怀，都值得认真对待</span><span>隐私保护 <b>·</b> 服务指南 <b>·</b> 联系我们</span></footer></template>
      </div>
    </div>
    <button class="ph-ai-fab" aria-label="打开AI助手" @click="open('ai')"><span><Icon name="sparkles" /></span><strong>AI 助手</strong><small>描述症状，获取辅助建议</small></button>
    <PrototypeSwitcher :variants="prototypeVariants" :state="state" />

    <dialog ref="panelDialog" class="ph-dialog" :class="{ 'ph-profile-dialog': panel === 'profile' }" aria-labelledby="patient-panel-title" @cancel.prevent="closePanel" @close="panel = null">
      <div class="ph-dialog-heading"><div><span class="ph-dialog-kicker">PATIENT SERVICES · 交互预览</span><h2 id="patient-panel-title">{{ panelTitle }}</h2></div><button class="ph-icon-button" :aria-label="panel === 'profile' ? '关闭个人中心' : '关闭服务预览'" @click="closePanel"><Icon name="close" /></button></div>
      <template v-if="panel === 'registration'"><p class="ph-dialog-note">选择就诊方式与科室，预览挂号流程。以下号源均为虚构数据。</p><div class="ph-form-row"><label>就诊类型<select v-model="mode" aria-label="就诊类型"><option>线上问诊</option><option>线下面诊</option></select></label><label>选择科室<select v-model="department" aria-label="选择科室"><option v-for="item in departments" :key="item.name">{{ item.name }}</option></select></label></div><div class="ph-schedule-preview"><span class="ph-doctor-avatar">{{ registrationDoctor[0] }}</span><div><h3>{{ registrationDoctor }} <small>主治医师</small></h3><p>{{ department }} · {{ mode }}</p><p>11 月 10 日 下午 14:00–18:00 · 剩余 8 号</p></div><strong>¥12.00</strong></div><button class="ph-primary ph-full" @click="register">选择此号源，生成演示挂号单 <Icon name="arrow" /></button><small class="ph-dialog-footnote">提交后先进入待付款；完成模拟支付后才确认挂号。</small></template>
      <template v-else-if="panel === 'orders'"><div class="ph-order-tabs" role="tablist" aria-label="挂号状态"><button v-for="tab in ['全部', '待付款', '待就诊', '就诊中', '已完成', '已取消']" :key="tab" role="tab" :aria-selected="orderFilter === tab" @click="orderFilter = tab">{{ tab }}</button></div><p v-if="registered" class="ph-inline-message" role="status">{{ bookingPaid ? '模拟支付成功，演示挂号已确认。' : '已生成演示挂号单，请在待付款中完成模拟支付。' }}</p><article v-if="orderFilter === '全部' || orderFilter === '待就诊'" class="ph-order-preview"><span class="ph-status-tag">{{ checkedIn ? '候诊中' : '待签到' }}</span><h3>全科医学科 · {{ registrationDoctor }}医生</h3><p>线下面诊 · 11 月 10 日 14:00–18:00</p><p>签到窗口：13:30 至 17:45 前 · 演示时间 13:35</p><p v-if="checkedIn" role="status">签到成功 · 候诊序号 A012，请留意叫号通知。</p><button class="ph-primary" :disabled="checkedIn" @click="checkedIn = true">{{ checkedIn ? '已签到，等待叫号' : '模拟签到' }}</button></article><article v-if="orderFilter === '全部' || orderFilter === '待付款'" class="ph-order-preview"><span class="ph-status-tag orange">{{ paid ? '药费已缴清' : '药品待缴费' }}</span><h3>已完成就诊 · 独立药费单</h3><p>药费 ¥18.00 · 诊疗病历已保存，药费状态不影响查看</p><button class="ph-primary" :disabled="paid" @click="paid = true">{{ paid ? '已支付' : '模拟支付药费 ¥18.00' }}</button></article><article v-if="registered && !bookingPaid && (orderFilter === '待付款' || orderFilter === '全部')" class="ph-order-preview"><span class="ph-status-tag">挂号待缴费</span><h3>{{ department }} · {{ mode }}</h3><p>{{ registrationDoctor }}医生 · 11 月 10 日 14:00–18:00 · ¥12.00</p><button class="ph-primary" @click="bookingPaid = true; orderFilter = '待就诊'">模拟支付挂号费 ¥12.00</button></article><article v-if="orderFilter === '已完成'" class="ph-order-preview"><span class="ph-status-tag">已完成</span><h3>11 月 06 日 · 全科医学科</h3><p>病历总结：本次诊疗记录已归档。</p><p>报告入口用于查看已完成就诊资料；此处展示虚构病历示例。</p></article><p v-if="['就诊中', '已取消'].includes(orderFilter)" class="ph-empty">当前没有{{ orderFilter }}的演示挂号单。</p></template>
      <template v-else-if="panel === 'departments'"><p class="ph-dialog-note">选择科室后，查看相应的演示号源。</p><button v-for="item in departments" :key="item.name" class="ph-directory-row" @click="chooseDepartment(item.name)"><span class="ph-icon-tile teal"><Icon :name="item.icon" /></span><span><strong>{{ item.name }}</strong><small>{{ item.detail }} · 今日 {{ item.count }} 位医生出诊</small></span><Icon name="chevron" /></button></template>
      <template v-else-if="panel === 'doctors'"><div v-for="name in ['陈予安', '许知遥']" :key="name" class="ph-schedule-preview"><span class="ph-doctor-avatar">{{ name[0] }}</span><div><h3>{{ name }} <small>主治医师</small></h3><p>全科医学科 · 常见不适与健康咨询</p></div><button class="ph-soft-button" @click="panel = 'registration'">查看排班</button></div></template>
      <template v-else-if="panel === 'profile'">
        <template v-if="profileSection === 'edit'">
          <div class="ph-profile-subheading"><button class="ph-back-button" type="button" @click="backToProfile">← 返回个人中心</button><span>资料编辑</span></div>
          <p class="ph-dialog-note">更新你的展示资料，手机号变更需要完成验证码校验。</p>
          <p v-if="profileError" class="ph-profile-error" role="alert">{{ profileError }}</p>
          <div class="ph-profile-edit-avatar"><span class="ph-profile-avatar large"><img v-if="draftProfileAvatarUrl" :src="draftProfileAvatarUrl" alt="待保存头像"><span v-else>{{ (draftProfileName.trim() || '林')[0] }}</span></span><label class="ph-upload-avatar">更换头像<input type="file" accept="image/jpeg,image/png" aria-label="选择头像" @change="chooseProfileAvatar"></label><small>支持 JPG、PNG，最大 5MB</small></div>
          <label class="ph-profile-field">昵称<input v-model="draftProfileName" aria-label="昵称" maxlength="20" placeholder="请输入昵称"></label>
          <label class="ph-profile-field">手机号<input v-model="draftProfilePhone" aria-label="手机号" inputmode="numeric" maxlength="11" placeholder="请输入手机号"></label>
          <label v-if="draftProfilePhone !== profilePhone" class="ph-profile-field">验证码<input v-model="draftProfilePhoneCode" aria-label="手机号验证码" inputmode="numeric" maxlength="6" placeholder="输入 6 位验证码"><small>原型演示中输入任意 6 位数字即可通过</small></label>
          <div class="ph-profile-edit-actions"><button class="ph-soft-button" type="button" @click="closePanel">取消</button><button class="ph-primary" type="button" @click="saveProfile">保存资料</button></div>
        </template>
        <template v-else-if="profileSection === 'notifications'">
          <div class="ph-profile-subheading"><button class="ph-back-button" type="button" @click="backToProfile">← 返回个人中心</button><span>通知设置</span></div>
          <p class="ph-dialog-note">选择你希望接收的患者端提醒，开关变更会立即保存。</p>
          <p v-if="profileNotice" class="ph-inline-message" role="status">{{ profileNotice }}</p>
          <div class="ph-setting-list"><label class="ph-setting-row"><span><strong>系统通知</strong><small>账户安全、服务公告和状态变化</small></span><input v-model="systemNotifications" type="checkbox" aria-label="系统通知" @change="notificationSaved"></label><label class="ph-setting-row"><span><strong>预约提醒</strong><small>挂号、签到和候诊时间提醒</small></span><input v-model="appointmentReminders" type="checkbox" aria-label="预约提醒" @change="notificationSaved"></label></div>
        </template>
        <template v-else-if="profileSection === 'password'">
          <div class="ph-profile-subheading"><button class="ph-back-button" type="button" @click="backToProfile">← 返回个人中心</button><span>修改密码</span></div>
          <p class="ph-dialog-note">修改成功后会退出当前演示工作台，请使用新密码重新登录。</p>
          <p v-if="passwordError" class="ph-profile-error" role="alert">{{ passwordError }}</p>

          <form class="ph-profile-password-form" @submit.prevent="submitPassword"><label>旧密码<input v-model="oldPassword" type="password" autocomplete="current-password" aria-label="旧密码"></label><label>新密码<input v-model="newPassword" type="password" autocomplete="new-password" aria-label="新密码" placeholder="至少 8 位"></label><label>确认新密码<input v-model="confirmNewPassword" type="password" autocomplete="new-password" aria-label="确认新密码"></label><button class="ph-primary ph-full" type="submit">确认修改 <Icon name="arrow" /></button></form>
        </template>
        <template v-else-if="profileSection === 'privacy'">
          <div class="ph-profile-subheading"><button class="ph-back-button" type="button" @click="backToProfile">← 返回个人中心</button><span>隐私与协议</span></div>
          <div class="ph-profile-section-list"><button v-for="agreement in (['隐私协议', '用户协议'] as const)" :key="agreement" type="button" @click="selectedAgreement = agreement; profileSection = 'agreement'"><span>{{ agreement }}</span><Icon name="chevron" /></button></div>
        </template>
        <template v-else-if="profileSection === 'agreement'">
          <div class="ph-profile-subheading"><button class="ph-back-button" type="button" @click="profileSection = 'privacy'">← 返回隐私与协议</button><span>{{ selectedAgreement }}</span></div>
          <div class="ph-profile-doc"><h3>{{ selectedAgreement }}</h3><p v-if="selectedAgreement === '隐私协议'">我们只在提供预约、问诊和账户服务所需的范围内处理信息。医疗资料按照明确授权范围使用，患者 AI 助手会话不会自动成为病历。</p><p v-else>请在使用预约、问诊和消息服务前阅读服务说明，系统输出的健康建议不能替代医生诊疗。</p><small>原型示例文本 · 以正式上线版本为准</small></div>
        </template>
        <template v-else-if="profileSection === 'about'">
          <div class="ph-profile-subheading"><button class="ph-back-button" type="button" @click="backToProfile">← 返回个人中心</button><span>关于系统</span></div>
          <div class="ph-profile-about"><span class="ph-brand-symbol"><Icon name="cross" /></span><h3>智能医疗问诊预约系统</h3><p>为患者提供清晰、连续的预约与问诊服务体验。</p><dl><div><dt>当前版本</dt><dd>患者端原型 v0.1</dd></div><div><dt>服务说明</dt><dd>预约、图文诊室、站内通知与 AI 辅助导诊</dd></div></dl></div>
        </template>
      </template>
    </dialog>

    <dialog ref="profileConfirmDialog" class="ph-profile-confirm-dialog" aria-labelledby="profile-confirm-title">
      <span class="ph-profile-confirm-icon"><Icon :name="profileConfirmKind === 'logout' ? 'logout' : 'warning'" /></span><h2 id="profile-confirm-title">{{ profileConfirmTitle }}</h2><p>{{ profileConfirmDescription }}</p><div class="ph-profile-confirm-actions"><button class="ph-soft-button" type="button" @click="cancelProfileConfirmation">再想想</button><button class="ph-primary" type="button" @click="confirmProfileAction">{{ profileConfirmKind === 'logout' ? '确认退出' : '放弃修改' }}</button></div>
    </dialog>

    <dialog ref="aiDialog" class="ph-ai-dialog" @close="aiOpen = false">
      <div class="ph-dialog-heading"><div class="ph-ai-title"><span class="ph-icon-tile teal"><Icon name="sparkles" /></span><div><h2>AI 助手</h2><p>描述症状，获取科室和健康建议。</p></div></div><div class="ph-ai-heading-actions"><button class="ph-text-button" type="button" @click="newAiSession">新建会话</button><button class="ph-icon-button" aria-label="关闭AI助手" @click="closeAi"><Icon name="close" /></button></div></div>
      <div class="ph-ai-chat">
        <div v-if="!aiMessages.length" class="ph-ai-welcome"><span class="ph-ai-chip">AI</span><div><strong>你好，林同学 👋</strong><p>可以描述不适，也可以上传检查图片。我会整理症状、推荐可咨询的科室和医生，并提供通用健康建议。</p></div></div>
        <p class="ph-ai-boundary"><Icon name="shield" /> 仅提供 AI 辅助结果，不提供诊断、处方或医生意见。</p>
        <div class="ph-ai-message-list" aria-live="polite">
          <template v-for="message in aiMessages" :key="message.id">
            <article v-if="message.role === 'patient'" class="ph-ai-message patient"><div class="ph-ai-message-avatar">我</div><div class="ph-ai-message-body"><p v-if="message.text">{{ message.text }}</p><div v-if="message.images.length" class="ph-ai-message-images"><figure v-for="image in message.images" :key="image.id"><img :src="image.url" :alt="image.name"><figcaption>{{ image.name }}</figcaption></figure></div></div></article>
            <article v-else-if="message.result" class="ph-ai-message assistant"><div class="ph-ai-message-avatar ai">AI</div><div class="ph-ai-result" role="status"><span class="ph-status-tag">AI 辅助结果</span><h3>症状分析</h3><p>{{ message.result.summary }}</p><p v-if="message.result.imageNote" class="ph-ai-image-note">{{ message.result.imageNote }}</p><div v-if="message.result.risk" class="ph-ai-risk"><Icon name="warning" /><div><strong>风险提示</strong><p>{{ message.result.risk }}</p></div></div><div class="ph-ai-result-section"><div class="ph-ai-result-section-heading"><strong>推荐科室</strong><button class="ph-soft-button" type="button" @click="goToAiRegistration(message.result.department)">查看科室 <Icon name="arrow" /></button></div><h4>{{ message.result.department }}</h4><p>{{ message.result.departmentDescription }}</p></div><div class="ph-ai-result-section"><div class="ph-ai-result-section-heading"><strong>推荐医生</strong><small>最多展示 2 位</small></div><div class="ph-ai-doctors"><article v-for="doctor in message.result.doctors" :key="doctor.id" class="ph-ai-doctor-card"><span class="ph-doctor-avatar">{{ doctor.name[0] }}</span><div><strong>{{ doctor.name }} <small>{{ doctor.title }}</small></strong><p>{{ doctor.specialty }}</p><small>{{ doctor.time }}</small><small v-if="doctor.slots !== null">剩余 {{ doctor.slots }} 号</small></div><button class="ph-soft-button" type="button" @click="goToAiRegistration(message.result.department, doctor)">{{ doctor.slots !== null ? '去挂号' : '查看排班' }}</button></article></div></div><div class="ph-ai-result-section ph-ai-advice"><strong>健康建议</strong><p>{{ message.result.advice }}</p></div></div></article>
          </template>
        </div>
        <div v-if="aiBusy" class="ph-ai-loading" role="status"><span class="ph-ai-loading-dot"></span>正在整理你的描述…</div>
        <div v-if="aiError" class="ph-ai-error" role="alert"><span>{{ aiError }}</span><button v-if="aiRetryPayload" type="button" @click="retryAi">重新分析</button></div>
        <form class="ph-ai-composer" @submit.prevent="ask"><label for="prototype-symptoms">你想了解什么？</label><textarea id="prototype-symptoms" v-model="question" placeholder="例如：最近嗓子不舒服，想了解该挂什么科……" maxlength="500"></textarea><div v-if="aiAttachments.length" class="ph-ai-attachment-list"><span v-for="image in aiAttachments" :key="image.id" class="ph-ai-attachment"><img :src="image.url" :alt="image.name"><span>{{ image.name }}</span><button type="button" :aria-label="`删除${image.name}`" @click="removeImage(image.id)">×</button></span></div><div class="ph-ai-composer-tools"><label class="ph-upload-label"><Icon name="image" /><span>添加检查图片（最多 5 张）</span><input type="file" accept="image/jpeg,image/png" multiple aria-label="添加检查图片" @change="chooseImages"></label><label class="ph-consent"><input v-model="consent" type="checkbox" :disabled="consent"><span>{{ consent ? '本次会话已授权，后续消息可继续发送' : '同意将本次会话用于辅助导诊建议' }}</span></label></div><small class="ph-local-demo">本地原型演示，图片只做上传和预览，暂不进行多模态识别；内容不代表诊断或处方。</small><button class="ph-primary ph-full" :disabled="(!question.trim() && !aiAttachments.length) || !consent || aiBusy">{{ aiBusy ? '分析中…' : '发送消息' }} <Icon name="arrow" /></button></form>
      </div>
    </dialog>
  </div>
</template>
