<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from './patient-home-prototype/PrototypeIcon.vue'
import AdminOperationsShell from '../components/AdminOperationsShell.vue'
import './admin-home-prototype.css'
import './admin-news-prototype.css'

type NewsState = 'DRAFT' | 'SCHEDULED' | 'PUBLISHED' | 'WITHDRAWN'
type NewsAttachment = { name: string; type: '图片' | 'PDF' }
type HospitalNews = {
  id: string
  title: string
  summary: string
  body: string
  state: NewsState
  cover: string
  attachments: NewsAttachment[]
  publishAt: number | null
  createdAt: number
  updatedAt: number
}

const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })
const router = useRouter()
const today = '2026-11-14'
const news = ref<HospitalNews[]>([
  { id: 'NEWS-20261114-001', title: '冬季流感门诊服务时间调整公告', summary: '流感高发季期间，发热门诊与线上问诊服务时间同步调整。', body: '为方便患者在冬季流感高发期就诊，我院将根据门诊安排提供连续的线上咨询与线下服务。请按预约时间就诊，并关注医院后续公告。', state: 'PUBLISHED', cover: '冬季门诊.jpg', attachments: [{ name: '门诊时间表.pdf', type: 'PDF' }], publishAt: Date.parse('2026-11-14T08:00:00+08:00'), createdAt: Date.parse('2026-11-13T16:20:00+08:00'), updatedAt: Date.parse('2026-11-14T08:00:00+08:00') },
  { id: 'NEWS-20261113-002', title: '市一医院线上复诊服务正式开放', summary: '已完成首诊的患者可通过预约入口申请线上复诊。', body: '线上复诊服务面向符合条件的复诊患者开放。具体服务时间以医生排班和预约页面展示为准。', state: 'SCHEDULED', cover: '', attachments: [{ name: '线上复诊说明.png', type: '图片' }], publishAt: Date.parse('2026-11-15T09:00:00+08:00'), createdAt: Date.parse('2026-11-13T12:10:00+08:00'), updatedAt: Date.parse('2026-11-13T12:25:00+08:00') },
  { id: 'NEWS-20261112-003', title: '儿童健康义诊活动报名提醒', summary: '儿科健康义诊将于本周六上午举行，名额有限。', body: '活动面向本院服务区域内儿童家庭开放，报名和现场安排请以活动页面信息为准。', state: 'DRAFT', cover: '儿童义诊.jpg', attachments: [], publishAt: null, createdAt: Date.parse('2026-11-12T10:00:00+08:00'), updatedAt: Date.parse('2026-11-14T07:35:00+08:00') },
  { id: 'NEWS-20261110-004', title: '门诊楼一层导诊区域升级完成', summary: '新的导诊台和自助服务区已投入使用。', body: '门诊楼一层导诊区域已完成升级，患者可在导诊台获取就诊指引和业务办理帮助。', state: 'WITHDRAWN', cover: '', attachments: [{ name: '导诊区域.png', type: '图片' }, { name: '服务指引.pdf', type: 'PDF' }], publishAt: Date.parse('2026-11-10T08:00:00+08:00'), createdAt: Date.parse('2026-11-09T09:30:00+08:00'), updatedAt: Date.parse('2026-11-11T11:20:00+08:00') },
])

const statusLabels: Record<NewsState, string> = { DRAFT: '草稿', SCHEDULED: '定时发布', PUBLISHED: '已发布', WITHDRAWN: '已撤回' }
const statusTone: Record<NewsState, string> = { DRAFT: 'draft', SCHEDULED: 'scheduled', PUBLISHED: 'published', WITHDRAWN: 'withdrawn' }
const statusFilter = ref<NewsState | 'ALL'>('ALL')
const keyword = ref('')
const fromDate = ref('')
const toDate = ref('')
const selected = ref<HospitalNews | null>(null)
const notice = ref('')
const modalMode = ref<'create' | 'edit' | null>(null)
const formError = ref('')
const form = ref({ id: '', title: '', summary: '', body: '', cover: '', attachments: [] as NewsAttachment[], publishMode: 'DRAFT' as 'DRAFT' | 'SCHEDULED' | 'PUBLISHED', publishAt: '' })
let ticker: number | undefined

const counts = computed(() => ({
  all: news.value.length,
  draft: news.value.filter(item => item.state === 'DRAFT').length,
  scheduled: news.value.filter(item => item.state === 'SCHEDULED').length,
  published: news.value.filter(item => item.state === 'PUBLISHED').length,
  withdrawn: news.value.filter(item => item.state === 'WITHDRAWN').length,
}))
const filtered = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return news.value.filter(item => {
    const textMatch = !query || `${item.id}${item.title}${item.summary}${item.body}`.toLowerCase().includes(query)
    const stateMatch = statusFilter.value === 'ALL' || item.state === statusFilter.value
    const date = item.publishAt ? new Date(item.publishAt).toISOString().slice(0, 10) : ''
    const fromMatch = !fromDate.value || (date && date >= fromDate.value)
    const toMatch = !toDate.value || (date && date <= toDate.value)
    return textMatch && stateMatch && fromMatch && toMatch
  }).sort((a, b) => b.updatedAt - a.updatedAt)
})
const formatTime = (value: number | null) => value ? new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '未设置'
const formatInputTime = (value: number) => {
  const d = new Date(value)
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}
const shortText = (value: string, length = 44) => value.length > length ? `${value.slice(0, length)}…` : value

function syncScheduled() {
  const now = Date.now()
  let changed = false
  news.value = news.value.map(item => {
    if (item.state === 'SCHEDULED' && item.publishAt && item.publishAt <= now) { changed = true; return { ...item, state: 'PUBLISHED', updatedAt: now } }
    return item
  })
  if (changed) notice.value = '定时发布内容已到点发布。'
}
function resetFilters() { statusFilter.value = 'ALL'; keyword.value = ''; fromDate.value = ''; toDate.value = '' }
function openCreate() {
  formError.value = ''
  form.value = { id: '', title: '', summary: '', body: '', cover: '', attachments: [], publishMode: 'DRAFT', publishAt: '' }
  modalMode.value = 'create'
}
function openEdit(item: HospitalNews) {
  if (!['DRAFT', 'SCHEDULED'].includes(item.state)) return
  formError.value = ''
  form.value = { id: item.id, title: item.title, summary: item.summary, body: item.body, cover: item.cover, attachments: [...item.attachments], publishMode: item.state as 'DRAFT' | 'SCHEDULED', publishAt: item.publishAt ? formatInputTime(item.publishAt) : '' }
  modalMode.value = 'edit'
}
function closeModal() { modalMode.value = null; formError.value = '' }
function addAttachments(event: Event) {
  const files = Array.from((event.target as HTMLInputElement).files || [])
  const accepted = files.filter(file => file.type.startsWith('image/') || file.type === 'application/pdf')
  form.value.attachments = [...form.value.attachments, ...accepted.map(file => ({ name: file.name, type: file.type === 'application/pdf' ? 'PDF' as const : '图片' as const }))].slice(0, 3)
  if (files.length !== accepted.length) formError.value = '附件仅支持图片或 PDF。'
  else if (form.value.attachments.length >= 3) formError.value = '附件最多上传 3 个。'
}
function removeAttachment(index: number) { form.value.attachments.splice(index, 1) }
function validateForm() {
  if (!form.value.title.trim() || !form.value.body.trim()) return '标题和正文为必填项。'
  if (form.value.publishMode === 'SCHEDULED') {
    if (!form.value.publishAt) return '定时发布必须选择发布时间。'
    if (new Date(form.value.publishAt).getTime() <= Date.now()) return '定时发布时间必须晚于当前时间。'
  }
  if (form.value.attachments.length > 3) return '附件最多上传 3 个。'
  return ''
}
function saveNews() {
  const error = validateForm()
  if (error) { formError.value = error; return }
  const now = Date.now()
  const state = form.value.publishMode
  const publishAt = state === 'DRAFT' ? null : state === 'PUBLISHED' ? now : new Date(form.value.publishAt).getTime()
  if (modalMode.value === 'edit') {
    const index = news.value.findIndex(item => item.id === form.value.id)
    if (index >= 0) news.value[index] = { ...news.value[index], title: form.value.title.trim(), summary: form.value.summary.trim(), body: form.value.body.trim(), cover: form.value.cover.trim(), attachments: [...form.value.attachments], state, publishAt, updatedAt: now }
  } else {
    news.value.unshift({ id: `NEWS-${today.replaceAll('-', '')}-${String(news.value.length + 1).padStart(3, '0')}`, title: form.value.title.trim(), summary: form.value.summary.trim(), body: form.value.body.trim(), cover: form.value.cover.trim(), attachments: [...form.value.attachments], state, publishAt, createdAt: now, updatedAt: now })
  }
  closeModal(); notice.value = state === 'DRAFT' ? '草稿已保存。' : state === 'SCHEDULED' ? '已保存定时发布。' : '新闻已发布。'
}
function openDetails(item: HospitalNews) { selected.value = item; notice.value = '' }
function closeDetails() { selected.value = null }
function publishDraft(item: HospitalNews) { item.state = 'PUBLISHED'; item.publishAt = Date.now(); item.updatedAt = Date.now(); notice.value = '新闻已发布。' }
function cancelSchedule(item: HospitalNews) { item.state = 'DRAFT'; item.publishAt = null; item.updatedAt = Date.now(); notice.value = '定时发布已取消，内容回到草稿。' }
function withdraw(item: HospitalNews) { item.state = 'WITHDRAWN'; item.updatedAt = Date.now(); notice.value = '新闻已撤回，历史内容已保留。' }
function deleteDraft(item: HospitalNews) { if (item.state !== 'DRAFT') return; news.value = news.value.filter(entry => entry.id !== item.id); if (selected.value?.id === item.id) closeDetails(); notice.value = '草稿已删除。' }
function navigateToSchedule() { router.push('/prototype/admin-schedules') }
function onCover(event: Event) { const file = (event.target as HTMLInputElement).files?.[0]; if (file) form.value.cover = file.name }
function onKeydown(event: KeyboardEvent) { if (event.key === 'Escape') { closeModal(); closeDetails() } }
onMounted(() => { syncScheduled(); ticker = window.setInterval(syncScheduled, 30000); window.addEventListener('keydown', onKeydown) })
onBeforeUnmount(() => { if (ticker) window.clearInterval(ticker); window.removeEventListener('keydown', onKeydown) })
</script>

<template>
  <AdminOperationsShell :embedded="props.embedded" active="新闻通知">
    <div class="news-content">
      <header class="news-heading"><div><span class="admin-eyebrow">治理 / 医院新闻</span><h1>新闻通知工作台</h1><p>管理面向患者和医生公开展示的医院资讯，保留发布历史与状态边界。</p></div><div class="news-heading-actions"><span class="news-demo-chip"><i></i>虚构演示数据</span><button type="button" class="admin-primary" @click="openCreate"><Icon name="plus" />新建新闻</button></div></header>
      <section class="news-summary" aria-label="医院新闻摘要"><article><span>全部内容</span><strong>{{ counts.all }}</strong><small>按更新时间倒序</small></article><article class="warning"><span>待发布</span><strong>{{ counts.draft + counts.scheduled }}</strong><small>草稿 {{ counts.draft }} · 定时 {{ counts.scheduled }}</small></article><article class="teal"><span>已发布</span><strong>{{ counts.published }}</strong><small>公开展示中</small></article><article class="slate"><span>已撤回</span><strong>{{ counts.withdrawn }}</strong><small>历史内容保留</small></article></section>
      <div class="news-status-tabs" role="tablist" aria-label="新闻状态"><button type="button" role="tab" :aria-selected="statusFilter === 'ALL'" @click="statusFilter = 'ALL'">全部 <span>{{ counts.all }}</span></button><button type="button" role="tab" :aria-selected="statusFilter === 'DRAFT'" @click="statusFilter = 'DRAFT'">草稿 <span>{{ counts.draft }}</span></button><button type="button" role="tab" :aria-selected="statusFilter === 'SCHEDULED'" @click="statusFilter = 'SCHEDULED'">定时发布 <span>{{ counts.scheduled }}</span></button><button type="button" role="tab" :aria-selected="statusFilter === 'PUBLISHED'" @click="statusFilter = 'PUBLISHED'">已发布 <span>{{ counts.published }}</span></button><button type="button" role="tab" :aria-selected="statusFilter === 'WITHDRAWN'" @click="statusFilter = 'WITHDRAWN'">已撤回 <span>{{ counts.withdrawn }}</span></button></div>
      <section class="news-filters" aria-label="新闻筛选"><label>关键词<input v-model="keyword" aria-label="搜索新闻" placeholder="标题、摘要或编号"></label><label>发布时间起<input v-model="fromDate" type="date" aria-label="发布时间起"></label><label>发布时间止<input v-model="toDate" type="date" aria-label="发布时间止"></label><button type="button" class="admin-soft" @click="resetFilters"><Icon name="refresh" />重置筛选</button><span>默认显示全部状态 · 按更新时间倒序</span></section>
      <p v-if="notice" class="news-feedback" role="status">{{ notice }}</p>
      <section class="news-table-panel" aria-label="医院新闻列表"><div class="news-table-head"><div><h2>医院新闻 <span>{{ filtered.length }} 条</span></h2><p>草稿和定时发布可编辑；已发布可撤回；已撤回仅供查看。</p></div><button type="button" class="admin-link" @click="syncScheduled"><Icon name="refresh" />刷新状态</button></div><div class="news-table-wrap"><table class="news-table"><caption class="visually-hidden">医院新闻列表</caption><thead><tr><th>新闻内容</th><th>状态</th><th>发布时间</th><th>附件</th><th>更新时间</th><th>操作</th></tr></thead><tbody><tr v-for="item in filtered" :key="item.id" tabindex="0" @click="openDetails(item)" @keydown.enter="openDetails(item)"><td><div class="news-title-cell"><span v-if="item.cover" class="news-cover-dot">图</span><div><strong>{{ item.title }}</strong><span>{{ shortText(item.summary) }}</span><small>{{ item.id }}</small></div></div></td><td><span :class="['news-state', statusTone[item.state]]">{{ statusLabels[item.state] }}</span><small v-if="item.state === 'SCHEDULED'" class="news-time-note">{{ formatTime(item.publishAt) }} 发布</small><small v-if="item.state === 'WITHDRAWN'" class="news-time-note">保留历史 · 不可编辑</small></td><td>{{ item.publishAt ? formatTime(item.publishAt) : '未安排' }}</td><td><span class="news-attachment-count"><Icon name="file" />{{ item.attachments.length }} 个</span></td><td>{{ formatTime(item.updatedAt) }}</td><td><button type="button" class="news-row-action" @click.stop="openDetails(item)">查看详情 <Icon name="chevron" /></button></td></tr><tr v-if="!filtered.length"><td colspan="6" class="news-empty"><Icon name="file" /><strong>暂无符合条件的医院新闻</strong><span>可以重置筛选，或新建一篇医院新闻。</span></td></tr></tbody></table></div></section>
      <p class="news-footnote"><Icon name="shield" /> 医院新闻面向患者和医生公开展示；个人业务提醒仍由系统通知负责。</p>
    </div>
    <div v-if="selected" class="news-drawer-backdrop" @click.self="closeDetails"><aside class="news-drawer" aria-label="医院新闻详情" @click.stop><button type="button" class="news-drawer-close" aria-label="关闭新闻详情" @click="closeDetails"><Icon name="close" /></button><span :class="['news-drawer-kicker', statusTone[selected.state]]">医院新闻 · {{ statusLabels[selected.state] }}</span><h2>{{ selected.title }}</h2><p class="news-drawer-meta">{{ selected.id }} · 更新于 {{ formatTime(selected.updatedAt) }}</p><p class="news-drawer-summary">{{ selected.summary }}</p><section class="news-detail-section"><h3>正文</h3><p>{{ selected.body }}</p></section><section class="news-detail-section"><div class="news-detail-section-heading"><h3>发布信息</h3><span>{{ selected.state === 'SCHEDULED' ? '到点自动发布' : selected.state === 'WITHDRAWN' ? '历史保留' : '医院公开资讯' }}</span></div><div class="news-detail-grid"><span>发布时间<strong>{{ formatTime(selected.publishAt) }}</strong></span><span>更新时间<strong>{{ formatTime(selected.updatedAt) }}</strong></span></div></section><section class="news-detail-section"><div class="news-detail-section-heading"><h3>封面与附件</h3><span>{{ selected.attachments.length }} 个附件</span></div><p v-if="selected.cover" class="news-file-row"><Icon name="image" />{{ selected.cover }}<small>封面图片</small></p><p v-else class="news-no-file">未设置封面</p><p v-for="file in selected.attachments" :key="file.name" class="news-file-row"><Icon name="file" />{{ file.name }}<small>{{ file.type }} · 可预览</small></p><p v-if="!selected.attachments.length" class="news-no-file">暂无附件</p></section><p class="news-boundary"><Icon name="shield" /> 不展示患者个人通知或医疗正文。</p><div class="news-drawer-actions"><button v-if="selected.state === 'DRAFT' || selected.state === 'SCHEDULED'" type="button" class="admin-soft" @click="openEdit(selected)"><Icon name="edit" />编辑</button><button v-if="selected.state === 'DRAFT'" type="button" class="admin-soft" @click="deleteDraft(selected)">删除草稿</button><button v-if="selected.state === 'DRAFT'" type="button" class="admin-primary" @click="publishDraft(selected)">立即发布</button><button v-if="selected.state === 'SCHEDULED'" type="button" class="admin-soft" @click="cancelSchedule(selected)">取消定时</button><button v-if="selected.state === 'PUBLISHED'" type="button" class="admin-soft news-withdraw" @click="withdraw(selected)">撤回新闻</button><button type="button" class="admin-soft" @click="navigateToSchedule">查看排班</button></div></aside></div>
    <div v-if="modalMode" class="news-modal-backdrop" @click.self="closeModal"><section class="news-modal" role="dialog" aria-modal="true" :aria-label="modalMode === 'create' ? '新建医院新闻' : '编辑医院新闻'"><header><div><span class="admin-eyebrow">医院新闻</span><h2>{{ modalMode === 'create' ? '新建新闻' : '编辑新闻' }}</h2></div><button type="button" class="news-drawer-close" aria-label="关闭新闻编辑" @click="closeModal"><Icon name="close" /></button></header><form @submit.prevent="saveNews"><label>标题 <input v-model="form.title" aria-label="新闻标题" maxlength="80" placeholder="请输入新闻标题"></label><label>摘要 <input v-model="form.summary" aria-label="新闻摘要" maxlength="140" placeholder="用于列表和患者端预览"></label><label>正文 <textarea v-model="form.body" aria-label="新闻正文" rows="6" placeholder="请输入医院公开资讯正文"></textarea></label><div class="news-form-grid"><label>封面图片 <input type="file" accept="image/*" aria-label="封面图片" @change="onCover"><small>{{ form.cover || '可选，最多 1 张图片' }}</small></label><label>附件 <input type="file" accept="image/*,.pdf,application/pdf" multiple aria-label="新闻附件" @change="addAttachments"><small>仅图片/PDF，最多 3 个</small></label></div><div v-if="form.attachments.length" class="news-form-files"><span v-for="(file, index) in form.attachments" :key="file.name"><Icon name="file" />{{ file.name }}<button type="button" :aria-label="`移除${file.name}`" @click="removeAttachment(index)">×</button></span></div><fieldset><legend>发布方式</legend><label><input v-model="form.publishMode" type="radio" value="DRAFT">保存草稿</label><label><input v-model="form.publishMode" type="radio" value="PUBLISHED">立即发布</label><label><input v-model="form.publishMode" type="radio" value="SCHEDULED">定时发布</label><input v-if="form.publishMode === 'SCHEDULED'" v-model="form.publishAt" type="datetime-local" aria-label="定时发布时间"></fieldset><p v-if="formError" class="news-form-error" role="alert">{{ formError }}</p><footer><button type="button" class="admin-soft" @click="closeModal">取消</button><button type="submit" class="admin-primary">保存</button></footer></form></section></div>
  </AdminOperationsShell>
</template>
