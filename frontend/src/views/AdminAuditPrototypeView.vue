<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import Icon from './patient-home-prototype/PrototypeIcon.vue'
import { auditModules, createAuditDemo, formatAuditTime, hospitalDate, type AuditEvent } from './admin-audit-prototype'
import './admin-audit-prototype.css'

const loadedAt = Date.now()
const today = hospitalDate(loadedAt)
const defaultStart = hospitalDate(Date.parse(`${today}T00:00:00+08:00`) - 6 * 86400000)
const events = ref(createAuditDemo(loadedAt))
const startDate = ref(defaultStart), endDate = ref(today), moduleFilter = ref('全部模块'), keyword = ref('')
const pageNumber = ref(1), pageSize = 10
const selectedId = ref(''), drawer = ref<HTMLDialogElement | null>(null)
const notice = ref(''), refreshedAt = ref<number | null>(null)
let opener: HTMLElement | null = null

const dateError = computed(() => !startDate.value || !endDate.value ? '请选择完整的开始日期和结束日期。' : startDate.value > endDate.value ? '开始日期不能晚于结束日期。' : '')
const filtered = computed(() => {
  if (dateError.value) return []
  const query = keyword.value.trim().toLowerCase()
  return events.value.filter(event => {
    const date = hospitalDate(event.createdAt)
    return date >= startDate.value && date <= endDate.value && (moduleFilter.value === '全部模块' || event.module === moduleFilter.value)
      && (!query || [event.id, event.actorId, event.actorName, event.action, event.objectId].some(value => value.toLowerCase().includes(query)))
  }).sort((a, b) => b.createdAt - a.createdAt || b.id.localeCompare(a.id))
})
const actorCount = computed(() => new Set(filtered.value.map(event => event.actorId)).size)
const moduleCount = computed(() => new Set(filtered.value.map(event => event.module)).size)
const pageCount = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))
const visible = computed(() => filtered.value.slice((pageNumber.value - 1) * pageSize, pageNumber.value * pageSize))
const selected = computed(() => events.value.find(event => event.id === selectedId.value))
watch([startDate, endDate, moduleFilter, keyword], () => { pageNumber.value = 1; notice.value = '' })

function resetFilters() {
  startDate.value = defaultStart; endDate.value = today; moduleFilter.value = '全部模块'; keyword.value = ''; pageNumber.value = 1
}
async function openDetails(event: AuditEvent) {
  opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
  selectedId.value = event.id; notice.value = ''
  await nextTick()
  if (!drawer.value?.open) drawer.value?.showModal()
}
function closed() {
  selectedId.value = ''; notice.value = ''
  if (opener?.isConnected) opener.focus()
}
function refresh() {
  events.value = createAuditDemo(loadedAt)
  pageNumber.value = Math.min(pageNumber.value, pageCount.value)
  refreshedAt.value = Date.now()
  notice.value = '已刷新演示日志，筛选条件与当前页已保留。'
}
async function copy(value: string, label: string) {
  try { await navigator.clipboard.writeText(value); notice.value = `${label}已复制。` }
  catch { notice.value = `复制未完成，请手动复制${label}：${value}` }
}
function publicDescription(event: AuditEvent) { return event.module === '医疗访问' ? '访问理由已隐藏' : event.description }
onBeforeUnmount(() => drawer.value?.close())
</script>

<template>
  <div class="audit-workspace">
    <header class="audit-heading">
      <div><span class="admin-eyebrow">治理 / 审计日志</span><h1>审计日志工作台</h1><p>追溯管理员的运营操作，查看操作时间、人员和关联对象。</p></div>
      <span class="audit-demo"><i></i>虚构演示数据</span>
    </header>
    <section class="audit-stats" aria-label="审计日志摘要">
      <article><span class="audit-stat-icon blue"><Icon name="scan" /></span><div><span>日志总数</span><strong>{{ filtered.length }}</strong><small>当前筛选的全部日志</small></div></article>
      <article><span class="audit-stat-icon teal"><Icon name="user" /></span><div><span>涉及管理员</span><strong>{{ actorCount }}</strong><small>按操作者编号去重</small></div></article>
      <article><span class="audit-stat-icon purple"><Icon name="grid" /></span><div><span>涉及模块</span><strong>{{ moduleCount }}</strong><small>当前筛选的业务范围</small></div></article>
    </section>
    <section class="audit-filters" aria-label="审计日志筛选">
      <label>开始日期<input v-model="startDate" type="date" aria-label="开始日期"></label>
      <label>结束日期<input v-model="endDate" type="date" aria-label="结束日期"></label>
      <label>业务模块<select v-model="moduleFilter" aria-label="业务模块"><option>全部模块</option><option v-for="module in auditModules" :key="module">{{ module }}</option></select></label>
      <label class="audit-search">关键词<input v-model="keyword" type="search" aria-label="审计日志关键词" placeholder="操作者、操作名称或编号"></label>
      <button type="button" class="audit-secondary" @click="resetFilters">重置筛选</button>
      <p v-if="dateError" class="audit-error" role="alert">{{ dateError }}</p>
    </section>
    <p v-if="notice && !selected" class="audit-feedback" role="status">{{ notice }}</p>
    <section class="audit-panel" aria-label="审计日志列表">
      <div class="audit-panel-heading"><div><h2>操作记录 <small>{{ filtered.length }} 条</small></h2><p>按操作时间倒序 · 医院时间 Asia/Shanghai</p></div><button type="button" class="audit-secondary" @click="refresh"><Icon name="refresh" />刷新日志</button></div>
      <div class="audit-table-scroll"><table aria-label="审计日志"><thead><tr><th>操作时间</th><th>操作者</th><th>业务模块</th><th>操作名称</th><th>关联对象 / 业务编号</th><th>详情</th></tr></thead><tbody>
        <tr v-for="event in visible" :key="event.id" tabindex="0" @click="openDetails(event)" @keydown.enter.prevent="openDetails(event)" @keydown.space.prevent="openDetails(event)">
          <td><time :datetime="new Date(event.createdAt).toISOString()">{{ formatAuditTime(event.createdAt) }}</time><small>{{ event.id }}</small></td>
          <td><strong>{{ event.actorName }}</strong><small>{{ event.actorId }}</small></td>
          <td><span class="audit-module" :class="{ medical: event.module === '医疗访问' }">{{ event.module }}</span></td>
          <td><strong>{{ event.action }}</strong></td>
          <td><strong>{{ event.objectType }}</strong><small>{{ event.objectId }}</small></td>
          <td><button type="button" class="audit-row-action" :aria-label="`查看${event.id}详情`" @click.stop="openDetails(event)">查看详情<Icon name="chevron" /></button></td>
        </tr>
      </tbody></table></div>
      <div v-if="!visible.length" class="audit-empty"><Icon name="scan" /><h3>{{ dateError ? '请修正日期范围' : '暂无符合条件的日志' }}</h3><p>{{ dateError ? '选择完整且有效的日期后继续查询。' : '尝试更换关键词、业务模块或扩大日期范围。' }}</p><button type="button" class="audit-secondary" @click="resetFilters">重置筛选</button></div>
      <footer class="audit-pagination" aria-label="日志分页"><span>{{ filtered.length ? `第 ${(pageNumber - 1) * pageSize + 1}–${Math.min(pageNumber * pageSize, filtered.length)} 条，共 ${filtered.length} 条` : '共 0 条' }} · 每页 10 条</span><div><button type="button" class="audit-secondary" :disabled="pageNumber === 1" @click="pageNumber--">上一页</button><span aria-live="polite">{{ pageNumber }} / {{ pageCount }}</span><button type="button" class="audit-secondary" :disabled="pageNumber >= pageCount" @click="pageNumber++">下一页</button></div></footer>
    </section>
    <footer class="audit-footnote"><span><Icon name="shield" />仅展示必要操作信息，医疗访问理由已隐藏。</span><span>{{ refreshedAt ? `日志刷新于 ${formatAuditTime(refreshedAt)}` : '演示数据 · 不连接真实业务接口' }}</span></footer>
    <dialog ref="drawer" class="audit-drawer" aria-label="审计日志详情" @close="closed" @click.self="drawer?.close()">
      <section v-if="selected" class="audit-drawer-content">
        <header><div><span class="admin-eyebrow">操作记录详情</span><h2>{{ selected.action }}</h2></div><button type="button" class="audit-close" aria-label="关闭日志详情" @click="drawer?.close()"><Icon name="close" /></button></header>
        <span class="audit-module">{{ selected.module }}</span>
        <dl class="audit-details"><div><dt>日志编号</dt><dd>{{ selected.id }}</dd></div><div><dt>操作时间</dt><dd>{{ formatAuditTime(selected.createdAt) }}<small>医院时间 · Asia/Shanghai</small></dd></div><div><dt>操作者</dt><dd>{{ selected.actorName }}<small>{{ selected.actorId }}</small></dd></div><div><dt>对象类型</dt><dd>{{ selected.objectType }}</dd></div><div><dt>业务编号</dt><dd>{{ selected.objectId }}</dd></div></dl>
        <section class="audit-description"><h3>操作说明</h3><p>{{ publicDescription(selected) }}</p></section>
        <p class="audit-boundary"><Icon name="shield" />本页仅查看操作信息，日志不可修改或删除。</p>
        <p v-if="notice" class="audit-feedback" role="status">{{ notice }}</p>
        <div class="audit-drawer-actions"><button type="button" class="audit-primary" @click="copy(selected.id, '日志编号')">复制日志编号</button><button type="button" class="audit-secondary" @click="copy(selected.objectId, '业务编号')">复制业务编号</button><button type="button" class="audit-secondary" @click="refresh"><Icon name="refresh" />刷新日志</button></div>
      </section>
    </dialog>
  </div>
</template>
