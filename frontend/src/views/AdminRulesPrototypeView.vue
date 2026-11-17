<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import Icon from './patient-home-prototype/PrototypeIcon.vue'
import { changesFor, defaultRules, displayValue, inputValue, prepareRules, ruleExample, ruleGroups, ruleTime, type RuleGroup, type RuleHistory, type RuleKey } from './admin-rules-prototype'
import './admin-rules-prototype.css'

// shortcut: values and history live only in this prototype session; replace with versioned APIs when integrating.
const saved = ref({ ...defaultRules })
const history = ref<RuleHistory[]>([])
const group = ref<RuleGroup | null>(null), dialog = ref<HTMLDialogElement | null>(null)
const draft = ref<Partial<Record<RuleKey, string>>>({})
const stage = ref<'edit' | 'confirm' | 'discard'>('edit'), previousStage = ref<'edit' | 'confirm'>('edit')
const attempted = ref(false), notice = ref(''), refreshedAt = ref<number | null>(null)
let opener: HTMLElement | null = null
const prepared = computed(() => group.value ? prepareRules(group.value, draft.value, saved.value) : null)
const changes = computed(() => group.value && prepared.value && !prepared.value.error ? changesFor(group.value, saved.value, prepared.value.rules) : [])
const dirty = computed(() => Boolean(group.value?.fields.some(field => draft.value[field.key] !== inputValue(field, saved.value)) && (prepared.value?.error || changes.value.length)))
const dialogTitle = computed(() => stage.value === 'discard' ? '放弃未保存修改' : stage.value === 'confirm' ? '确认规则变更' : `编辑${group.value?.name || ''}`)

async function edit(item: RuleGroup) {
  opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
  group.value = item; draft.value = {}; stage.value = 'edit'; attempted.value = false; notice.value = ''
  for (const field of item.fields) draft.value[field.key] = inputValue(field, saved.value)
  await nextTick()
  dialog.value?.showModal()
}
function requestClose() {
  if (stage.value === 'discard') { stage.value = previousStage.value; return }
  if (dirty.value) { previousStage.value = stage.value; stage.value = 'discard' }
  else dialog.value?.close()
}
function closed() {
  group.value = null; draft.value = {}; attempted.value = false; stage.value = 'edit'
  if (opener?.isConnected) opener.focus()
}
function review() {
  attempted.value = true
  if (!prepared.value?.error && changes.value.length) stage.value = 'confirm'
}
function save() {
  if (stage.value !== 'confirm' || !group.value || !prepared.value || prepared.value.error || !changes.value.length) return
  history.value.unshift({ id: history.value.length + 1, group: group.value.name, actor: '运营管理员', time: Date.now(), changes: changes.value.map(change => ({ ...change })) })
  saved.value = { ...prepared.value.rules }
  notice.value = `“${group.value.name}”已保存为本页演示规则。真实业务配置未修改。`
  dialog.value?.close()
}
function refresh() {
  saved.value = { ...saved.value }; refreshedAt.value = Date.now()
  notice.value = '已刷新规则，保留本页已确认保存的演示值和修改记录。'
}
</script>

<template>
  <div class="rules-workspace">
    <header class="rules-heading"><div><span class="admin-eyebrow">系统 / 全局规则</span><h1>全局规则工作台</h1><p>统一维护门诊时间、业务时限与默认费用，按业务分组查看和修改。</p></div><div class="rules-heading-actions"><span class="rules-demo"><i></i>虚构演示数据</span><button type="button" class="rules-secondary" @click="refresh"><Icon name="refresh" />刷新规则</button></div></header>
    <section class="rules-overview" aria-label="规则生效说明"><span class="rules-overview-icon"><Icon name="settings" /></span><div><h2>5 组业务规则 · 12 项配置</h2><p>目标生效范围：新业务使用新规则，已有排班、预约和订单保留原规则快照。</p><small>本页仅演示配置交互，不改变真实业务。浏览器重载后恢复初始演示数据。</small></div><span class="rules-overview-tag"><Icon name="shield" />逐组确认保存</span></section>
    <div class="rules-last-update" aria-label="最近修改"><span v-if="history[0]">最近修改：{{ history[0].actor }} · {{ ruleTime(history[0].time) }}</span><span v-else>当前为默认演示规则 · 尚未修改</span><span>医院时间 · Asia/Shanghai</span></div>
    <p v-if="notice" class="rules-feedback" role="status">{{ notice }}</p>
    <section class="rules-grid" aria-label="全局规则分组">
      <article v-for="item in ruleGroups" :key="item.id" class="rules-card" :aria-label="item.name">
        <header><span class="rules-card-icon" :class="item.id"><Icon :name="item.icon" /></span><div><h2>{{ item.name }}</h2><p>{{ item.description }}</p></div><button type="button" class="rules-edit" :aria-label="`编辑${item.name}`" @click="edit(item)"><Icon name="edit" />编辑</button></header>
        <dl><div v-for="field in item.fields" :key="field.key"><dt>{{ field.label }}</dt><dd>{{ displayValue(field, saved) }}</dd></div></dl>
        <p class="rules-example"><Icon name="bell" /><span>{{ ruleExample(item, saved) }}</span></p>
      </article>
    </section>
    <section class="rules-history" aria-label="本次演示修改记录"><header><div><h2>本次演示修改记录 <small>{{ history.length }} 条</small></h2><p>只读记录本次页面会话内的确认修改。</p></div><Icon name="file" /></header><p v-if="!history.length" class="rules-history-empty">暂无修改记录，确认保存后将在这里显示变更内容。</p><details v-for="entry in history" :key="entry.id"><summary><span class="rules-history-dot"></span><strong>{{ entry.group }}</strong><span>{{ entry.changes.length }} 项变更</span><small>{{ entry.actor }} · {{ ruleTime(entry.time) }}</small><Icon name="chevron" /></summary><div class="rules-history-changes"><div v-for="change in entry.changes" :key="change.label"><span>{{ change.label }}</span><del>{{ change.before }}</del><Icon name="arrow" /><strong>{{ change.after }}</strong></div></div></details></section>
    <footer class="rules-footer"><span><Icon name="shield" />AI 配置、账户安全和单场次号源在对应页面维护。</span><span>{{ refreshedAt ? `规则刷新于 ${ruleTime(refreshedAt)}` : '演示修改仅在本页会话内保留' }}</span></footer>

    <dialog ref="dialog" class="rules-dialog" :aria-label="dialogTitle" @cancel.prevent="requestClose" @close="closed" @click.self="requestClose">
      <form v-if="group" @submit.prevent="stage === 'edit' ? review() : stage === 'confirm' ? save() : undefined" novalidate>
        <header><div><span class="admin-eyebrow">{{ stage === 'discard' ? '未保存的修改' : group.name }}</span><h2>{{ dialogTitle }}</h2></div><button type="button" class="rules-close" aria-label="关闭规则弹窗" @click="requestClose"><Icon name="close" /></button></header>
        <template v-if="stage === 'edit'">
          <p class="rules-dialog-intro">{{ group.description }} 修改后先确认变更内容，再保存。</p>
          <div class="rules-fields"><label v-for="field in group.fields" :key="field.key">{{ field.label }}<div><input v-model="draft[field.key]" :type="field.kind === 'time' ? 'time' : 'text'" :inputmode="field.kind === 'money' ? 'decimal' : field.kind === 'integer' ? 'numeric' : undefined" :aria-label="field.label" :aria-invalid="attempted && !!prepared?.error" :aria-describedby="`rule-help-${field.key}`" autocomplete="off"><span v-if="field.unit">{{ field.unit }}</span></div><small :id="`rule-help-${field.key}`">{{ field.kind === 'time' ? '医院时间，24小时制' : `${field.min}–${field.max} ${field.unit} · ${field.kind === 'money' ? '最多两位小数' : '整数'}` }}</small></label></div>
          <p v-if="attempted && prepared?.error" class="rules-error" role="alert">{{ prepared.error }}</p>
          <p class="rules-dialog-note">{{ group.impact }}</p>
          <footer><button type="button" class="rules-secondary" @click="requestClose">取消</button><button type="submit" class="rules-primary" :disabled="!dirty">下一步：确认变更<Icon name="arrow" /></button></footer>
        </template>
        <template v-else-if="stage === 'confirm'">
          <p class="rules-dialog-intro">以下 {{ changes.length }} 项配置将更新为本页演示值，请确认变更与影响。</p>
          <div class="rules-comparison" aria-label="规则变更对比"><div class="rules-comparison-head"><span>规则项</span><span>当前值</span><span>新值</span></div><div v-for="change in changes" :key="change.label"><span>{{ change.label }}</span><del>{{ change.before }}</del><strong>{{ change.after }}</strong></div></div>
          <p class="rules-dialog-note">{{ group.impact }} 本次确认仅保存演示值，真实业务配置未修改。</p>
          <footer><button type="button" class="rules-secondary" @click="stage = 'edit'">返回修改</button><button type="submit" class="rules-primary">确认保存</button></footer>
        </template>
        <template v-else>
          <p class="rules-dialog-intro">“{{ group.name }}”的修改尚未保存，放弃后将清空本次输入，当前规则和历史记录保持不变。</p>
          <footer><button type="button" class="rules-secondary" @click="stage = previousStage">继续编辑</button><button type="button" class="rules-danger" @click="dialog?.close()">放弃修改</button></footer>
        </template>
      </form>
    </dialog>
  </div>
</template>
