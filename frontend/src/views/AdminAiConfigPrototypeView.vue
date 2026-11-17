<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import Icon from './patient-home-prototype/PrototypeIcon.vue'
import { aiChanges, aiDisplay, aiGroups, aiTime, defaultAiConfig, prepareAi, type AiConfig, type AiGroup, type AiHistory } from './admin-ai-config-prototype'
import './admin-rules-prototype.css'
import './admin-ai-config-prototype.css'

// shortcut: configuration and history are session-only demonstration state; integrate server-side controls before real use.
const saved = ref<AiConfig>({ ...defaultAiConfig }), draft = ref<AiConfig>({ ...defaultAiConfig })
const history = ref<AiHistory[]>([]), group = ref<AiGroup | null>(null)
const dialog = ref<HTMLDialogElement | null>(null), testDialog = ref<HTMLDialogElement | null>(null)
const stage = ref<'edit' | 'confirm' | 'discard'>('edit'), previousStage = ref<'edit' | 'confirm'>('edit')
const attempted = ref(false), notice = ref(''), refreshedAt = ref<number | null>(null)
const scenario = ref('success'), testResult = ref('')
let opener: HTMLElement | null = null, testOpener: HTMLElement | null = null
const master = aiGroups[0]!
const prepared = computed(() => group.value ? prepareAi(group.value, draft.value) : null)
const changes = computed(() => group.value && prepared.value && !prepared.value.error ? aiChanges(group.value, saved.value, prepared.value.config) : [])
const dirty = computed(() => Boolean(group.value?.fields.some(field => draft.value[field.key] !== saved.value[field.key]) && (prepared.value?.error || changes.value.length)))
const title = computed(() => stage.value === 'discard' ? '放弃未保存修改' : stage.value === 'confirm' ? '确认 AI 配置变更' : `编辑${group.value?.name || ''}`)

async function edit(item: AiGroup) {
  opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
  group.value = item; draft.value = { ...saved.value }; attempted.value = false; notice.value = ''; stage.value = 'edit'
  if (item.id === 'master') { draft.value.enabled = saved.value.enabled === 'on' ? 'off' : 'on'; stage.value = 'confirm' }
  await nextTick(); dialog.value?.showModal()
}
function requestClose() {
  if (stage.value === 'discard') { stage.value = previousStage.value; return }
  if (dirty.value) { previousStage.value = stage.value; stage.value = 'discard' }
  else dialog.value?.close()
}
function closed() { group.value = null; draft.value = { ...saved.value }; if (opener?.isConnected) opener.focus() }
function review() { attempted.value = true; if (!prepared.value?.error && changes.value.length) stage.value = 'confirm' }
function save() {
  if (stage.value !== 'confirm' || !group.value || !prepared.value || prepared.value.error || !changes.value.length) return
  history.value.unshift({ id: history.value.length + 1, group: group.value.name, time: Date.now(), changes: changes.value.map(change => ({ ...change })) })
  saved.value = { ...prepared.value.config }; testResult.value = ''
  notice.value = `“${group.value.name}”已保存为本页演示配置，真实业务配置未修改。`
  dialog.value?.close()
}
function refresh() { refreshedAt.value = Date.now(); notice.value = '已刷新配置，保留本页已确认保存的演示值和修改记录。' }
async function openTest() {
  testOpener = document.activeElement instanceof HTMLElement ? document.activeElement : null
  scenario.value = 'success'; testResult.value = ''; await nextTick(); testDialog.value?.showModal()
}
function runTest() {
  testResult.value = scenario.value === 'success' ? '模拟成功：配置测试已完成，未连接真实模型。' : scenario.value === 'failure' ? '模拟连接失败：请核对接口与模型配置。运行模式保持不变。' : '模拟超时：服务未在预期时间内响应。运行模式保持不变。'
}
function testClosed() { if (testOpener?.isConnected) testOpener.focus() }
</script>

<template>
  <div class="rules-workspace ai-config-workspace">
    <header class="rules-heading"><div><span class="admin-eyebrow">系统 / AI 配置</span><h1>AI 配置工作台</h1><p>分别维护患者导诊与医生摘要，统一管理模型和调用记录策略。</p></div><div class="rules-heading-actions"><span class="rules-demo"><i></i>虚构演示数据</span><button type="button" class="rules-secondary" @click="refresh"><Icon name="refresh" />刷新配置</button></div></header>
    <section class="rules-overview ai-master" aria-label="AI 总开关"><span class="rules-overview-icon"><Icon name="sparkles" /></span><div><div class="ai-master-title"><h2>AI 服务</h2><span class="ai-state" :class="{ off: saved.enabled === 'off' }">{{ saved.enabled === 'on' ? '已启用' : '已停用' }}</span><span class="ai-mode">{{ saved.mode === 'local' ? '本地演示' : '外部模型（演示）' }}</span></div><p>{{ saved.enabled === 'on' ? '患者导诊与医生摘要已在演示配置中启用。' : '目标行为：隐藏患者入口，停止新生成，不再展示在途返回的新结果。' }}</p><small>本页仅演示配置交互，不修改真实业务；浏览器重载后恢复初始值。</small></div><button type="button" :class="saved.enabled === 'on' ? 'rules-danger' : 'rules-primary'" @click="edit(master)">{{ saved.enabled === 'on' ? '停用 AI' : '启用 AI' }}</button></section>
    <div class="rules-last-update" aria-label="最近修改"><span>{{ history[0] ? `最近修改：运营管理员 · ${aiTime(history[0].time)}` : '当前为默认演示配置 · 尚未修改' }}</span><span>医院时间 · Asia/Shanghai</span></div>
    <p v-if="notice" class="rules-feedback" role="status">{{ notice }}</p>
    <section class="rules-grid" aria-label="AI 配置分组">
      <article v-for="item in aiGroups.slice(1)" :key="item.id" class="rules-card" :aria-label="item.name">
        <header><span class="rules-card-icon" :class="item.id"><Icon :name="item.icon" /></span><div><h2>{{ item.name }}</h2><p>{{ item.description }}</p></div><button type="button" class="rules-edit" :aria-label="`编辑${item.name}`" @click="edit(item)"><Icon name="edit" />编辑</button></header>
        <dl><div v-for="field in item.fields" :key="field.key" :class="{ 'ai-long-value': field.kind === 'long' || field.key === 'endpoint' }"><dt>{{ field.label }}</dt><dd :title="aiDisplay(field, saved)">{{ aiDisplay(field, saved) }}</dd></div></dl>
        <p class="rules-example"><Icon name="shield" /><span>{{ item.impact }}</span></p>
        <div v-if="item.id === 'provider'" class="ai-card-action"><button type="button" class="rules-secondary" @click="openTest"><Icon name="scan" />测试配置</button><small>仅模拟当前已保存配置</small></div>
        <div v-if="item.id === 'records'" class="ai-card-action"><RouterLink to="/admin/ai" class="ai-record-link">查看调用记录<Icon name="arrow" /></RouterLink><small>正式页 · 需管理员登录</small></div>
      </article>
    </section>
    <section class="rules-history" aria-label="本次演示修改记录"><header><div><h2>本次演示修改记录 <small>{{ history.length }} 条</small></h2><p>仅记录本页会话内的配置变更，不包含密钥内容。</p></div><Icon name="file" /></header><p v-if="!history.length" class="rules-history-empty">暂无修改记录，确认保存后将在这里显示变更内容。</p><details v-for="entry in history" :key="entry.id"><summary><span class="rules-history-dot"></span><strong>{{ entry.group }}</strong><span>{{ entry.changes.length }} 项变更</span><small>运营管理员 · {{ aiTime(entry.time) }}</small><Icon name="chevron" /></summary><div class="rules-history-changes"><div v-for="change in entry.changes" :key="change.label"><span>{{ change.label }}</span><del>{{ change.before }}</del><Icon name="arrow" /><strong>{{ change.after }}</strong></div></div></details></section>
    <footer class="rules-footer"><span><Icon name="shield" />配置不能扩大授权范围；本地演示尚不解析图片。</span><span>{{ refreshedAt ? `配置刷新于 ${aiTime(refreshedAt)}` : '演示修改仅在本页会话内保留' }}</span></footer>

    <dialog ref="dialog" class="rules-dialog ai-config-dialog" :aria-label="title" @cancel.prevent="requestClose" @close="closed" @click.self="requestClose">
      <form v-if="group" novalidate @submit.prevent="stage === 'edit' ? review() : stage === 'confirm' ? save() : undefined">
        <header><div><span class="admin-eyebrow">{{ group.name }}</span><h2>{{ title }}</h2></div><button type="button" class="rules-close" aria-label="关闭配置弹窗" @click="requestClose"><Icon name="close" /></button></header>
        <template v-if="stage === 'edit'">
          <p class="rules-dialog-intro">{{ group.description }}。先确认变更，再保存为本页演示配置。</p>
          <div class="rules-fields ai-fields"><div v-for="field in group.fields" :key="field.key" class="ai-field" :class="{ wide: field.kind === 'long' || field.kind === 'key' || field.key === 'endpoint' }">
            <label v-if="field.kind !== 'key'" :for="`ai-${field.key}`">{{ field.label }}</label><span v-else class="ai-field-label">{{ field.label }}</span>
            <textarea v-if="field.kind === 'long'" :id="`ai-${field.key}`" v-model="draft[field.key]" rows="3" :aria-describedby="`ai-help-${field.key}`" :aria-invalid="attempted && !!prepared?.error"></textarea>
            <select v-else-if="field.kind === 'toggle' || field.kind === 'mode'" :id="`ai-${field.key}`" v-model="draft[field.key]" :aria-describedby="`ai-help-${field.key}`"><template v-if="field.kind === 'mode'"><option value="local">本地演示</option><option value="external">外部模型（演示）</option></template><template v-else><option value="on">开启</option><option value="off">关闭</option></template></select>
            <div v-else-if="field.kind === 'key'" class="ai-key-control" role="group" :aria-label="field.label"><span>{{ draft.keyConfigured === 'on' ? '已配置（演示）' : '未配置' }}</span><button type="button" class="rules-secondary" :disabled="draft.keyConfigured === 'on'" @click="draft.keyConfigured = 'on'">设置演示密钥</button><button type="button" class="rules-danger" :disabled="draft.keyConfigured === 'off'" @click="draft.keyConfigured = 'off'">清除</button></div>
            <input v-else :id="`ai-${field.key}`" v-model="draft[field.key]" type="text" :inputmode="field.kind === 'integer' ? 'numeric' : field.kind === 'decimal' ? 'decimal' : undefined" autocomplete="off" :aria-describedby="`ai-help-${field.key}`" :aria-invalid="attempted && !!prepared?.error">
            <small :id="`ai-help-${field.key}`">{{ field.help }}</small>
          </div></div>
          <p v-if="attempted && prepared?.error" class="rules-error" role="alert">{{ prepared.error }}</p><p class="rules-dialog-note">{{ group.impact }}</p>
          <footer><button type="button" class="rules-secondary" @click="requestClose">取消</button><button type="submit" class="rules-primary" :disabled="!dirty">下一步：确认变更<Icon name="arrow" /></button></footer>
        </template>
        <template v-else-if="stage === 'confirm'">
          <p class="rules-dialog-intro">请确认以下 {{ changes.length }} 项变更及影响，确认前不会更新配置。</p>
          <div class="rules-comparison" aria-label="AI 配置变更对比"><div class="rules-comparison-head"><span>配置项</span><span>当前值</span><span>新值</span></div><div v-for="change in changes" :key="change.label"><span>{{ change.label }}</span><del>{{ change.before }}</del><strong>{{ change.after }}</strong></div></div>
          <p class="rules-dialog-note">{{ group.impact }} 本次操作仅保存演示值。</p><footer><button type="button" class="rules-secondary" @click="group.id === 'master' ? dialog?.close() : stage = 'edit'">{{ group.id === 'master' ? '取消' : '返回修改' }}</button><button type="submit" class="rules-primary">确认保存</button></footer>
        </template>
        <template v-else><p class="rules-dialog-intro">“{{ group.name }}”尚未保存，放弃后将清空本次输入。</p><footer><button type="button" class="rules-secondary" @click="stage = previousStage">继续编辑</button><button type="button" class="rules-danger" @click="dialog?.close()">放弃修改</button></footer></template>
      </form>
    </dialog>

    <dialog ref="testDialog" class="rules-dialog ai-config-dialog" aria-label="测试 AI 配置" @click.self="testDialog?.close()" @close="testClosed">
      <form @submit.prevent="runTest"><header><div><span class="admin-eyebrow">连接测试 / 模拟</span><h2>测试 AI 配置</h2></div><button type="button" class="rules-close" aria-label="关闭测试弹窗" @click="testDialog?.close()"><Icon name="close" /></button></header>
        <p class="rules-dialog-intro">当前已保存模式：{{ saved.mode === 'local' ? '本地演示' : '外部模型（演示）' }}。本测试不发真实请求、不发送患者内容；总开关关闭时也可模拟连接测试。</p>
        <div class="ai-field"><label for="ai-test-scenario">模拟场景</label><select id="ai-test-scenario" v-model="scenario" @change="testResult = ''"><option value="success">模拟成功</option><option value="failure">连接失败</option><option value="timeout">请求超时</option></select></div>
        <p v-if="testResult" class="rules-dialog-note" :class="{ 'ai-test-error': scenario !== 'success' }" role="status">{{ testResult }}</p>
        <footer><button type="button" class="rules-secondary" @click="testDialog?.close()">关闭</button><button type="submit" class="rules-primary">运行模拟测试<Icon name="arrow" /></button></footer>
      </form>
    </dialog>
  </div>
</template>
