<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import Icon from './patient-home-prototype/PrototypeIcon.vue'
import { createDirectoryDemo, currentAdminId, directoryActionImpact, directoryActionLabel, directoryActionReason, directoryDraftError, directoryPhone, directoryTime, emptyDirectoryDraft, type DirectoryAccount, type DirectoryAction } from './admin-directory-prototype'
import './admin-audit-prototype.css'
import './admin-rules-prototype.css'
import './admin-directory-prototype.css'

// shortcut: this directory and its operations are session-only demo data; server-side identity and session APIs are required for real use.
const accounts = ref(createDirectoryDemo(Date.now()))
const keyword = ref(''), statusFilter = ref('ALL'), pageNumber = ref(1), pageSize = 10
const selectedId = ref(''), targetId = ref(''), action = ref<DirectoryAction>('enable')
const drawer = ref<HTMLDialogElement | null>(null), formDialog = ref<HTMLDialogElement | null>(null)
const mode = ref<'create' | 'edit' | 'action' | null>(null), stage = ref<'edit' | 'discard'>('edit')
const draft = ref(emptyDirectoryDraft()), attempted = ref(false), notice = ref(''), actionError = ref(''), refreshedAt = ref<number | null>(null)
let drawerOpener: HTMLElement | null = null, formOpener: HTMLElement | null = null, nextAccountId = accounts.value.length + 1, operationId = 0
const enabledCount = computed(() => accounts.value.filter(account => account.enabled).length)
const filtered = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return accounts.value.filter(account => (statusFilter.value === 'ALL' || account.enabled === (statusFilter.value === 'ENABLED')) && (!query || [account.id, account.name, account.login].some(value => value.toLowerCase().includes(query))))
    .sort((a, b) => b.createdAt - a.createdAt || b.id.localeCompare(a.id))
})
const pageCount = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))
const visible = computed(() => filtered.value.slice((pageNumber.value - 1) * pageSize, pageNumber.value * pageSize))
const selected = computed(() => accounts.value.find(account => account.id === selectedId.value))
const target = computed(() => accounts.value.find(account => account.id === targetId.value))
const error = computed(() => directoryDraftError(draft.value, accounts.value, mode.value === 'create'))
const dirty = computed(() => mode.value === 'create' ? Object.values(draft.value).some(value => value.length > 0) : mode.value === 'edit' && !!target.value && (draft.value.name.trim() !== target.value.name || draft.value.phone.trim() !== target.value.phone))
const formTitle = computed(() => stage.value === 'discard' ? '放弃未保存修改' : mode.value === 'create' ? '新增管理员' : mode.value === 'edit' ? '编辑管理员资料' : `确认${directoryActionLabel(action.value)}`)
watch([keyword, statusFilter], () => { pageNumber.value = 1 })
watch(pageCount, value => { pageNumber.value = Math.min(pageNumber.value, value) })

function reason(item: DirectoryAccount, value: DirectoryAction) { return directoryActionReason(value, item, accounts.value) }
function resetFilters() { keyword.value = ''; statusFilter.value = 'ALL'; pageNumber.value = 1 }
async function openDetails(item: DirectoryAccount) {
  if (!drawer.value?.open) drawerOpener = document.activeElement instanceof HTMLElement ? document.activeElement : null
  selectedId.value = item.id; await nextTick()
  if (!drawer.value?.open) drawer.value?.showModal()
}
function drawerClosed() {
  if (drawer.value?.open) return
  selectedId.value = ''
  if (document.activeElement === document.body && drawerOpener?.isConnected) drawerOpener.focus()
}
async function openForm(value: 'create' | 'edit' | 'action', item?: DirectoryAccount, pendingAction?: DirectoryAction) {
  if (value === 'action' && item && pendingAction && reason(item, pendingAction)) return
  formOpener = document.activeElement instanceof HTMLElement ? document.activeElement : null
  mode.value = value; stage.value = 'edit'; attempted.value = false; actionError.value = ''; notice.value = ''
  targetId.value = item?.id || ''; draft.value = emptyDirectoryDraft()
  if (value === 'edit' && item) draft.value = { ...emptyDirectoryDraft(), name: item.name, phone: item.phone, login: item.login }
  if (pendingAction) action.value = pendingAction
  await nextTick(); formDialog.value?.showModal()
}
function requestFormClose() {
  if (stage.value === 'discard') { stage.value = 'edit'; return }
  if (mode.value !== 'action' && dirty.value) stage.value = 'discard'
  else formDialog.value?.close()
}
function formClosed() {
  if (formDialog.value?.open) return
  draft.value = emptyDirectoryDraft(); mode.value = null; targetId.value = ''; stage.value = 'edit'; attempted.value = false
  const active = document.activeElement
  if (active && active !== document.body && active !== formDialog.value && !formDialog.value?.contains(active)) return
  if (formOpener?.isConnected && !formOpener.matches(':disabled') && (!drawer.value?.open || drawer.value.contains(formOpener))) formOpener.focus()
  else if (drawer.value?.open) drawer.value.querySelector<HTMLButtonElement>('button')?.focus()
}
function record(item: DirectoryAccount, label: string, description: string) {
  item.operations.unshift({ id: ++operationId, action: label, actor: '运营管理员', time: Date.now(), description })
}
async function saveProfile() {
  if (stage.value !== 'edit' || mode.value === 'action' || !dirty.value) return
  attempted.value = true
  if (error.value) return
  const creating = mode.value === 'create'
  let item: DirectoryAccount
  if (creating) {
    item = { id: `ADM-${String(nextAccountId++).padStart(3, '0')}`, name: draft.value.name.trim(), phone: draft.value.phone.trim(), login: draft.value.login.trim().toLowerCase(), enabled: true, createdAt: Date.now(), operations: [] }
    accounts.value.unshift(item); record(item, '新增管理员', '创建管理员账户，默认启用。'); resetFilters()
  } else {
    if (!target.value) return
    item = target.value; const fields: string[] = []
    if (item.name !== draft.value.name.trim()) fields.push('姓名')
    if (item.phone !== draft.value.phone.trim()) fields.push('手机号')
    item.name = draft.value.name.trim(); item.phone = draft.value.phone.trim(); record(item, '编辑管理员资料', `更新${fields.join('、')}，登录账号保持不变。`)
  }
  draft.value.password = ''; draft.value.confirmation = ''
  notice.value = `${creating ? '已新增管理员' : '已更新管理员资料'}：${item.name}。仅保存本页演示数据。`
  formDialog.value?.close(); await openDetails(item)
}
function applyAction() {
  if (mode.value !== 'action' || !target.value) return
  const item = target.value, blocked = reason(item, action.value)
  if (blocked) { actionError.value = blocked; return }
  if (action.value !== 'reset') item.enabled = action.value === 'enable'
  record(item, directoryActionLabel(action.value), directoryActionImpact(action.value))
  notice.value = `已对“${item.name}”演示${directoryActionLabel(action.value)}。真实账户与登录未改变。`
  formDialog.value?.close()
}
function refresh() { refreshedAt.value = Date.now(); pageNumber.value = Math.min(pageNumber.value, pageCount.value); notice.value = '已刷新演示账户，保留筛选、已保存数据和操作记录。' }
async function copy(value: string, label: string) {
  try { await navigator.clipboard.writeText(value); notice.value = `${label}已复制。` }
  catch { notice.value = `复制未完成，请手动复制${label}：${value}` }
}
</script>

<template>
  <div class="audit-workspace rules-workspace admin-directory-workspace">
    <header class="audit-heading"><div><span class="admin-eyebrow">系统 / 管理员账户</span><h1>管理员账户工作台</h1><p>维护管理员资料与账户状态，敏感操作逐项确认。</p></div><div class="directory-heading-actions"><span class="audit-demo"><i></i>虚构演示数据</span><button type="button" class="audit-primary" @click="openForm('create')"><Icon name="plus" />新增管理员</button></div></header>
    <section class="audit-stats" aria-label="管理员账户摘要">
      <article><span class="audit-stat-icon blue"><Icon name="user" /></span><div><span>管理员总数</span><strong>{{ accounts.length }}</strong><small>完整管理员目录</small></div></article>
      <article><span class="audit-stat-icon teal"><Icon name="shield" /></span><div><span>已启用</span><strong>{{ enabledCount }}</strong><small>可使用管理员功能</small></div></article>
      <article><span class="audit-stat-icon purple"><Icon name="lock" /></span><div><span>已停用</span><strong>{{ accounts.length - enabledCount }}</strong><small>保留资料与历史记录</small></div></article>
    </section>
    <section class="audit-filters directory-filters" aria-label="管理员账户筛选"><label class="directory-search">关键词<input v-model="keyword" type="search" aria-label="管理员账户关键词" placeholder="姓名、登录账号或编号"></label><label>启用状态<select v-model="statusFilter" aria-label="管理员启用状态"><option value="ALL">全部状态</option><option value="ENABLED">已启用</option><option value="DISABLED">已停用</option></select></label><button type="button" class="audit-secondary" @click="resetFilters">重置筛选</button></section>
    <p v-if="notice && !drawer?.open" class="audit-feedback" role="status">{{ notice }}</p>
    <section class="audit-panel" aria-label="管理员账户列表"><div class="audit-panel-heading"><div><h2>管理员目录 <small>{{ filtered.length }} 条</small></h2><p>按创建时间倒序 · 摘要统计不受筛选影响</p></div><button type="button" class="audit-secondary" @click="refresh"><Icon name="refresh" />刷新账户</button></div>
      <div class="audit-table-scroll"><table aria-label="管理员账户"><thead><tr><th>管理员</th><th>登录账号</th><th>手机号</th><th>账户状态</th><th>创建时间</th><th>操作</th></tr></thead><tbody><tr v-for="item in visible" :key="item.id" tabindex="0" @click="openDetails(item)" @keydown.enter.self.prevent="openDetails(item)" @keydown.space.self.prevent="openDetails(item)">
        <td><strong>{{ item.name }}</strong><span v-if="item.id === currentAdminId" class="directory-current">当前账户</span><small>{{ item.id }}</small></td><td class="directory-login">{{ item.login }}</td><td class="directory-phone">{{ directoryPhone(item.phone) }}</td><td><span class="directory-state" :class="{ disabled: !item.enabled }">{{ item.enabled ? '已启用' : '已停用' }}</span></td><td><time :datetime="new Date(item.createdAt).toISOString()">{{ directoryTime(item.createdAt) }}</time></td>
        <td><div class="directory-row-actions"><button type="button" class="audit-row-action" :aria-label="`查看${item.id}详情`" @click.stop="openDetails(item)">详情<Icon name="chevron" /></button><button type="button" class="audit-row-action" :aria-label="`编辑${item.id}资料`" @click.stop="openForm('edit', item)">编辑</button><button type="button" class="audit-row-action" :disabled="!!reason(item, item.enabled ? 'disable' : 'enable')" :title="reason(item, item.enabled ? 'disable' : 'enable') || undefined" :aria-label="`${item.enabled ? '停用' : '启用'}${item.id}`" @click.stop="openForm('action', item, item.enabled ? 'disable' : 'enable')">{{ item.enabled ? '停用' : '启用' }}</button></div><small v-if="reason(item, item.enabled ? 'disable' : 'enable')" class="directory-protection">{{ reason(item, item.enabled ? 'disable' : 'enable') }}</small></td>
      </tr></tbody></table></div>
      <div v-if="!visible.length" class="audit-empty"><Icon name="user" /><h3>暂无符合条件的管理员</h3><p>尝试更换关键词或启用状态。</p><button type="button" class="audit-secondary" @click="resetFilters">重置筛选</button></div>
      <footer class="audit-pagination" aria-label="管理员分页"><span>{{ filtered.length ? `第 ${(pageNumber - 1) * pageSize + 1}–${Math.min(pageNumber * pageSize, filtered.length)} 条，共 ${filtered.length} 条` : '共 0 条' }} · 每页 10 条</span><div><button type="button" class="audit-secondary" :disabled="pageNumber === 1" @click="pageNumber--">上一页</button><span aria-live="polite">{{ pageNumber }} / {{ pageCount }}</span><button type="button" class="audit-secondary" :disabled="pageNumber >= pageCount" @click="pageNumber++">下一页</button></div></footer>
    </section>
    <footer class="audit-footnote"><span><Icon name="shield" />当前账户受保护，始终保留至少一名启用管理员。</span><span>{{ refreshedAt ? `账户刷新于 ${directoryTime(refreshedAt)}` : '演示数据 · 不连接真实业务接口' }}</span></footer>

    <dialog ref="drawer" class="audit-drawer" aria-label="管理员账户详情" @close="drawerClosed" @click.self="drawer?.close()"><section v-if="selected" class="audit-drawer-content">
      <header><div><span class="admin-eyebrow">管理员账户详情</span><h2>{{ selected.name }}</h2></div><button type="button" class="audit-close" aria-label="关闭账户详情" @click="drawer?.close()"><Icon name="close" /></button></header>
      <div class="directory-detail-tags"><span class="directory-state" :class="{ disabled: !selected.enabled }">{{ selected.enabled ? '已启用' : '已停用' }}</span><span v-if="selected.id === currentAdminId" class="directory-current">当前账户</span><span class="audit-module">管理员</span></div>
      <dl class="audit-details"><div><dt>账户编号</dt><dd>{{ selected.id }}</dd></div><div><dt>登录账号</dt><dd>{{ selected.login }}<small>创建后不可修改</small></dd></div><div><dt>手机号</dt><dd>{{ selected.phone }}</dd></div><div><dt>创建时间</dt><dd>{{ directoryTime(selected.createdAt) }}<small>医院时间 · Asia/Shanghai</small></dd></div></dl>
      <p v-if="!filtered.some(item => item.id === selected?.id)" class="directory-filter-note">此账户不在当前筛选结果中，详情仍保留。</p>
      <p v-if="notice" class="audit-feedback" role="status">{{ notice }}</p>
      <div class="audit-drawer-actions"><button type="button" class="audit-primary" @click="openForm('edit', selected)"><Icon name="edit" />编辑资料</button><button type="button" class="audit-secondary" @click="copy(selected.id, '账户编号')">复制账户编号</button><button type="button" class="audit-secondary" @click="copy(selected.login, '登录账号')">复制登录账号</button><button type="button" class="audit-secondary" @click="refresh"><Icon name="refresh" />刷新账户</button></div>
      <section class="directory-sensitive" aria-label="账户状态操作"><h3>账户状态操作</h3><div><button type="button" :class="selected.enabled ? 'rules-danger' : 'rules-primary'" :disabled="!!reason(selected, selected.enabled ? 'disable' : 'enable')" @click="openForm('action', selected, selected.enabled ? 'disable' : 'enable')">{{ selected.enabled ? '停用账户' : '启用账户' }}</button><button type="button" class="rules-secondary" :disabled="!!reason(selected, 'reset')" @click="openForm('action', selected, 'reset')">重置登录状态</button></div><p v-if="reason(selected, selected.enabled ? 'disable' : 'enable')">{{ reason(selected, selected.enabled ? 'disable' : 'enable') }}</p><p v-if="reason(selected, 'reset')">{{ reason(selected, 'reset') }}</p><small>启停和登录重置均需确认。仅演示影响，不改变真实账户。</small></section>
      <section class="directory-operations" aria-label="本页操作记录"><h3>本页操作记录 <small>{{ selected.operations.length }} 条</small></h3><p v-if="!selected.operations.length">暂无本页操作记录。</p><article v-for="operation in selected.operations" :key="operation.id"><strong>{{ operation.action }}</strong><small>{{ operation.actor }} · {{ directoryTime(operation.time) }}</small><p>{{ operation.description }}</p></article></section>
    </section></dialog>

    <dialog ref="formDialog" class="rules-dialog directory-dialog" :aria-label="formTitle" @cancel.prevent="requestFormClose" @click.self="requestFormClose" @close="formClosed"><form v-if="mode" novalidate @submit.prevent="stage === 'edit' ? mode === 'action' ? applyAction() : saveProfile() : undefined">
      <header><div><span class="admin-eyebrow">{{ mode === 'action' ? '确认目标及影响' : '管理员资料 / 演示' }}</span><h2>{{ formTitle }}</h2></div><button type="button" class="rules-close" aria-label="关闭管理员弹窗" @click="requestFormClose"><Icon name="close" /></button></header>
      <template v-if="stage === 'discard'"><p class="rules-dialog-intro">本次输入尚未保存，放弃后将清空输入内容及演示初始密码。</p><footer><button type="button" class="rules-secondary" @click="stage = 'edit'">继续编辑</button><button type="button" class="rules-danger" @click="formDialog?.close()">放弃修改</button></footer></template>
      <template v-else-if="mode === 'action' && target"><p class="rules-dialog-intro">即将对管理员“{{ target.name }}”执行{{ directoryActionLabel(action) }}，请核对目标账户。</p><dl class="directory-confirm-target"><div><dt>账户编号</dt><dd>{{ target.id }}</dd></div><div><dt>登录账号</dt><dd>{{ target.login }}</dd></div><div><dt>当前状态</dt><dd>{{ target.enabled ? '已启用' : '已停用' }}</dd></div></dl><p class="rules-dialog-note">{{ directoryActionImpact(action) }} 本次为演示操作，不改变真实账户或登录。</p><p v-if="actionError" class="rules-error" role="alert">{{ actionError }}</p><footer><button type="button" class="rules-secondary" @click="requestFormClose">取消</button><button type="submit" :class="action === 'disable' ? 'rules-danger' : 'rules-primary'">确认{{ directoryActionLabel(action) }}</button></footer></template>
      <template v-else><p class="rules-dialog-intro">{{ mode === 'create' ? '创建后身份固定为管理员，默认启用。请仅使用虚构资料和演示密码。' : '仅修改姓名和手机号，账户编号、登录账号和身份保持不变。' }}</p><div class="rules-fields">
        <label>管理员姓名<div><input v-model="draft.name" aria-label="管理员姓名" autocomplete="off" :aria-invalid="attempted && !!error"></div><small>必填，最多120字</small></label>
        <label>手机号<div><input v-model="draft.phone" aria-label="手机号" type="tel" autocomplete="off" :aria-invalid="attempted && !!error"></div><small>中国大陆11位手机号</small></label>
        <label class="directory-wide">登录账号<div><input v-model="draft.login" aria-label="登录账号" :readonly="mode === 'edit'" autocomplete="off" :aria-invalid="attempted && !!error"></div><small>{{ mode === 'create' ? '3–190位；字母或数字开头，允许 @._+-，保存时统一小写' : '创建后不可修改' }}</small></label>
        <template v-if="mode === 'create'"><label>演示初始密码<div><input v-model="draft.password" aria-label="演示初始密码" type="password" autocomplete="new-password" :aria-invalid="attempted && !!error"></div><small>至少12字符，UTF-8不超过72字节</small></label><label>确认演示初始密码<div><input v-model="draft.confirmation" aria-label="确认演示初始密码" type="password" autocomplete="new-password" :aria-invalid="attempted && !!error"></div><small>再次输入相同密码</small></label></template>
      </div><p v-if="attempted && error" class="rules-error" role="alert">{{ error }}</p><p class="rules-dialog-note">{{ mode === 'create' ? '密码仅用于表单校验，提交或实际关闭后立即清空，不进入账户数据和操作记录。' : '资料变更只保存在本页演示会话，不修改真实管理员资料。' }}</p><footer><button type="button" class="rules-secondary" @click="requestFormClose">取消</button><button type="submit" class="rules-primary" :disabled="!dirty">{{ mode === 'create' ? '创建管理员' : '保存资料' }}</button></footer></template>
    </form></dialog>
  </div>
</template>
