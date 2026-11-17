<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../api'
import AdminOperationsShell from '../components/AdminOperationsShell.vue'
import Icon from './patient-home-prototype/PrototypeIcon.vue'
import './admin-resource-prototype.css'
import './admin-pharmacy.css'

const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

type Medicine = { id: string; name: string; spec: string; feeMinor: number; stock: number; availableStock: number; heldStock: number; totalStock: number; threshold: number; active: boolean; hasPrescriptionHistory: boolean; createdAt: number; updatedAt: number }
type Consumption = { id: string; state: string; quantity: number; createdAt: number; paidAt: number | null }

const demoMedicines: Medicine[] = [
  { id: 'MED-001', name: '对乙酰氨基酚片', spec: '0.5g × 12 片 / 盒', feeMinor: 1800, stock: 36, availableStock: 32, heldStock: 4, totalStock: 36, threshold: 10, active: true, hasPrescriptionHistory: true, createdAt: 1762675200000, updatedAt: 1762675200000 },
  { id: 'MED-002', name: '阿莫西林胶囊', spec: '0.25g × 24 粒 / 盒', feeMinor: 3200, stock: 8, availableStock: 6, heldStock: 2, totalStock: 8, threshold: 8, active: true, hasPrescriptionHistory: true, createdAt: 1762675200000, updatedAt: 1762675200000 },
  { id: 'MED-003', name: '氯雷他定片', spec: '10mg × 6 片 / 盒', feeMinor: 1500, stock: 0, availableStock: 0, heldStock: 0, totalStock: 0, threshold: 5, active: true, hasPrescriptionHistory: false, createdAt: 1762675200000, updatedAt: 1762675200000 },
  { id: 'MED-004', name: '维生素 C 片', spec: '100mg × 100 片 / 瓶', feeMinor: 980, stock: 48, availableStock: 48, heldStock: 0, totalStock: 48, threshold: 12, active: true, hasPrescriptionHistory: false, createdAt: 1762675200000, updatedAt: 1762675200000 },
  { id: 'MED-005', name: '布洛芬缓释胶囊', spec: '0.3g × 20 粒 / 盒', feeMinor: 2600, stock: 13, availableStock: 10, heldStock: 3, totalStock: 13, threshold: 6, active: false, hasPrescriptionHistory: true, createdAt: 1762675200000, updatedAt: 1762675200000 },
]
const demoConsumptions: Record<string, Consumption[]> = {
  'MED-001': [{ id: 'DEMO-ORDER-001', state: 'PAID', quantity: 2, createdAt: 1762761600000, paidAt: 1762765200000 }],
  'MED-002': [{ id: 'DEMO-ORDER-002', state: 'PENDING_PAYMENT', quantity: 2, createdAt: 1762765200000, paidAt: null }],
}
const copyMedicines = (items: Medicine[]) => items.map(item => ({ ...item }))

const medicines = ref<Medicine[]>([]), selected = ref<Medicine | null>(null), stockTarget = ref<Medicine | null>(null), deleteTarget = ref<Medicine | null>(null), statusTarget = ref<Medicine | null>(null), consumptions = ref<Consumption[]>([])
const localDemoMedicines = ref(copyMedicines(demoMedicines))
const busy = ref(false), historyBusy = ref(false), notice = ref(''), error = ref(''), historyError = ref('')
const keyword = ref(''), statusFilter = ref<'ALL' | 'ACTIVE' | 'INACTIVE'>('ALL'), warningFilter = ref<'ALL' | 'LOW'>('ALL')
const profileModal = ref(false), stockModal = ref(false), deleteModal = ref(false), statusModal = ref(false)
const profile = reactive({ id: '', name: '', spec: '', feeMinor: 0, threshold: 0, active: true })
const stockForm = reactive({ totalStock: 0 })

const filtered = computed(() => medicines.value.filter(item => {
  const query = keyword.value.trim().toLowerCase()
  return (!query || `${item.id}${item.name}${item.spec}`.toLowerCase().includes(query)) && (statusFilter.value === 'ALL' || (statusFilter.value === 'ACTIVE' ? item.active : !item.active)) && (warningFilter.value === 'ALL' || item.availableStock <= item.threshold)
}))
const summary = computed(() => [
  { label: '药品品类', value: medicines.value.length, note: '已登记药品' },
  { label: '启用药品', value: medicines.value.filter(item => item.active).length, note: '医生端可开具' },
  { label: '库存预警', value: medicines.value.filter(item => item.availableStock <= item.threshold).length, note: '可用库存 ≤ 阈值' },
  { label: '锁定库存', value: medicines.value.reduce((sum, item) => sum + item.heldStock, 0), note: '待缴费订单占用' },
])
const formTitle = computed(() => profile.id ? '编辑药品资料' : '新增药品')
const demoMode = computed(() => props.embedded)

async function load() { medicines.value = demoMode.value ? copyMedicines(localDemoMedicines.value) : await api<Medicine[]>('/admin/pharmacy/medicines') }
function money(value: number) { return `¥${(value / 100).toFixed(2)}` }
function statusLabel(item: Medicine) { return item.active ? '启用' : '停用' }
function isLow(item: Medicine) { return item.availableStock <= item.threshold }
function stateLabel(state: string) { return state === 'PAID' ? '已支付' : state === 'EXPIRED' ? '已失效' : '待缴费' }
function formatTime(value: number) { return new Date(value).toLocaleString('zh-CN', { dateStyle: 'short', timeStyle: 'short' }) }
function resetFilters() { keyword.value = ''; statusFilter.value = 'ALL'; warningFilter.value = 'ALL' }
function openNew() { Object.assign(profile, { id: '', name: '', spec: '', feeMinor: 0, threshold: 0, active: true }); error.value = ''; profileModal.value = true }
function openEdit(item: Medicine) { Object.assign(profile, { id: item.id, name: item.name, spec: item.spec, feeMinor: item.feeMinor, threshold: item.threshold, active: item.active }); error.value = ''; profileModal.value = true }
async function saveProfile() {
  busy.value = true; error.value = ''; notice.value = ''
  try {
    if (demoMode.value) {
      if (profile.id) {
        const item = localDemoMedicines.value.find(value => value.id === profile.id)
        if (item) Object.assign(item, { name: profile.name, spec: profile.spec, feeMinor: profile.feeMinor, threshold: profile.threshold, active: profile.active })
      } else {
        const nextId = Math.max(0, ...localDemoMedicines.value.map(item => Number(item.id.replace('MED-', '')) || 0)) + 1
        const id = `MED-${String(nextId).padStart(3, '0')}`
        localDemoMedicines.value.push({ id, name: profile.name, spec: profile.spec, feeMinor: profile.feeMinor, stock: 0, availableStock: 0, heldStock: 0, totalStock: 0, threshold: profile.threshold, active: profile.active, hasPrescriptionHistory: false, createdAt: Date.now(), updatedAt: Date.now() })
      }
    } else if (profile.id) await api(`/admin/pharmacy/medicines/${profile.id}`, 'PUT', { name: profile.name, spec: profile.spec, feeMinor: profile.feeMinor, threshold: profile.threshold, active: profile.active })
    else await api('/admin/pharmacy/medicines', 'POST', { name: profile.name, spec: profile.spec, feeMinor: profile.feeMinor, stock: 0, threshold: profile.threshold, active: profile.active })
    const id = profile.id; await load(); profileModal.value = false; notice.value = id ? '药品资料已更新。' : '药品已创建，可继续调整库存。'
    if (selected.value) selected.value = medicines.value.find(item => item.id === selected.value?.id) ?? null
  } catch (failure) { error.value = failure instanceof Error ? failure.message : '保存失败，请重试' }
  finally { busy.value = false }
}
async function toggleActive(item: Medicine) {
  busy.value = true; error.value = ''; notice.value = ''
  try { if (demoMode.value) { const target = localDemoMedicines.value.find(value => value.id === item.id); if (target) target.active = !target.active } else await api(`/admin/pharmacy/medicines/${item.id}`, 'PUT', { name: item.name, spec: item.spec, feeMinor: item.feeMinor, threshold: item.threshold, active: !item.active }); await load(); notice.value = `${item.name}已${item.active ? '停用' : '启用'}。`; if (selected.value?.id === item.id) selected.value = medicines.value.find(value => value.id === item.id) ?? null }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '状态更新失败，请重试' }
  finally { busy.value = false }
}
function requestToggleActive(item: Medicine) { statusTarget.value = item; error.value = ''; statusModal.value = true }
async function confirmToggleActive() {
  if (!statusTarget.value) return
  await toggleActive(statusTarget.value)
  if (!error.value) { statusModal.value = false; statusTarget.value = null }
}
function openStock(item: Medicine) { selected.value = null; stockTarget.value = item; stockForm.totalStock = item.totalStock; error.value = ''; stockModal.value = true }
function closeStock() { stockModal.value = false; stockTarget.value = null; error.value = '' }
async function saveStock() {
  if (!stockTarget.value) return
  busy.value = true; error.value = ''; notice.value = ''
  try { let updated: Medicine; if (demoMode.value) { const target = localDemoMedicines.value.find(item => item.id === stockTarget.value!.id); if (!target || !Number.isInteger(stockForm.totalStock) || stockForm.totalStock < target.heldStock) throw new Error('目标总库存不能低于锁定库存。'); target.totalStock = stockForm.totalStock; target.stock = stockForm.totalStock; target.availableStock = stockForm.totalStock - target.heldStock; target.updatedAt = Date.now(); updated = { ...target } } else updated = await api<Medicine>(`/admin/pharmacy/medicines/${stockTarget.value.id}/stock`, 'PATCH', { totalStock: stockForm.totalStock }); await load(); closeStock(); notice.value = `总库存已调整为 ${updated.totalStock}，可用库存为 ${updated.availableStock}。` }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '库存调整失败，请重试' }
  finally { busy.value = false }
}
function requestDelete(item: Medicine) { selected.value = null; deleteTarget.value = item; error.value = ''; deleteModal.value = true }
function closeDelete() { deleteModal.value = false; deleteTarget.value = null; error.value = '' }
async function confirmDelete() {
  if (!deleteTarget.value) return
  busy.value = true; error.value = ''
  try { const target = deleteTarget.value; const name = target.name; if (demoMode.value) localDemoMedicines.value = localDemoMedicines.value.filter(item => item.id !== target.id); else await api(`/admin/pharmacy/medicines/${target.id}`, 'DELETE'); closeDelete(); await load(); notice.value = `${name}已删除。` }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '删除失败，请重试' }
  finally { busy.value = false }
}
async function openDetails(item: Medicine) {
  selected.value = item; consumptions.value = []; historyError.value = ''; historyBusy.value = true
  try { consumptions.value = demoMode.value ? (demoConsumptions[item.id] ?? []).map(record => ({ ...record })) : await api<Consumption[]>(`/admin/pharmacy/medicines/${item.id}/history`) }
  catch (failure) { historyError.value = failure instanceof Error ? failure.message : '消耗记录暂不可用' }
  finally { historyBusy.value = false }
}
function closeDetails() { selected.value = null; consumptions.value = [] }
onMounted(() => load().catch(failure => { error.value = failure instanceof Error ? failure.message : '药品数据暂不可用' }))
</script>

<template>
  <AdminOperationsShell :embedded="props.embedded">
    <main class="pharmacy-content"><div class="pharmacy-heading"><div><span class="admin-eyebrow">药品 / 库存</span><h1>药品与库存工作台</h1><p>维护可开具药品资料，掌握可用库存和待缴费订单锁定情况。</p></div><div class="pharmacy-heading-actions"><span class="resource-demo-chip"><i></i>{{ demoMode ? '演示数据 · 不连接真实业务接口' : '真实业务接口' }}</span><button type="button" class="admin-primary pharmacy-add" @click="openNew">＋新增药品</button></div></div>
      <section class="resource-summary pharmacy-summary" aria-label="药品库存摘要"><article v-for="item in summary" :key="item.label"><span>{{ item.label }}</span><strong>{{ item.value }}</strong><small>{{ item.note }}</small></article></section>
      <p v-if="notice" class="pharmacy-feedback" role="status">{{ notice }}</p><p v-if="error && !profileModal && !stockModal && !deleteModal && !statusModal" class="pharmacy-error" role="alert">{{ error }}</p>
      <section class="pharmacy-toolbar" aria-label="药品筛选"><label class="pharmacy-search"><span>搜索药品</span><input v-model="keyword" aria-label="搜索药品" placeholder="名称、规格或药品编号"></label><label><span>药品状态</span><select v-model="statusFilter" aria-label="药品状态"><option value="ACTIVE">启用药品</option><option value="INACTIVE">停用药品</option><option value="ALL">全部状态</option></select></label><label><span>库存状态</span><select v-model="warningFilter" aria-label="库存状态"><option value="ALL">全部库存</option><option value="LOW">仅看库存预警</option></select></label><button type="button" class="pharmacy-reset" @click="resetFilters">重置筛选</button></section>
      <section class="resource-panel pharmacy-panel" aria-label="药品库存表"><div class="resource-panel-heading"><div><h2>药品库存 <span>{{ filtered.length }} 项</span></h2><p>总库存 = 可用库存 + 锁定库存；锁定库存来自待缴费药费订单</p></div><button type="button" class="resource-reset" @click="load">刷新数据</button></div><div class="resource-table-wrap"><table class="resource-table pharmacy-table"><caption class="visually-hidden">药品与库存列表</caption><thead><tr><th>药品</th><th>单价</th><th>可用库存</th><th>库存锁定</th><th>总库存</th><th>预警阈值</th><th>状态</th><th><span class="visually-hidden">操作</span></th></tr></thead><tbody><tr v-for="item in filtered" :key="item.id" @click="openDetails(item)"><td><div class="resource-person"><span class="medicine-avatar">药</span><div><strong>{{ item.name }}</strong><small>{{ item.spec }} · {{ item.id }}</small></div></div></td><td><strong class="pharmacy-money">{{ money(item.feeMinor) }}</strong></td><td><strong class="pharmacy-stock" :class="{ low: isLow(item) }">{{ item.availableStock }}</strong></td><td><strong class="pharmacy-held">{{ item.heldStock }}</strong></td><td><strong class="pharmacy-total">{{ item.totalStock }}</strong></td><td><small>{{ item.threshold }}</small></td><td><span class="resource-status" :class="{ disabled: !item.active }"><i></i>{{ statusLabel(item) }}</span><em v-if="isLow(item)" class="pharmacy-warning">库存预警</em></td><td><div class="pharmacy-row-actions"><button type="button" class="resource-row-action" @click.stop="openDetails(item)">详情</button><button type="button" class="resource-row-action" @click.stop="openStock(item)">调库存</button><button type="button" class="resource-row-action" @click.stop="openEdit(item)">编辑</button><button type="button" class="resource-row-action" @click.stop="item.active ? requestToggleActive(item) : toggleActive(item)">{{ item.active ? '停用' : '启用' }}</button><button v-if="!item.hasPrescriptionHistory" type="button" class="resource-row-action danger" @click.stop="requestDelete(item)">删除</button></div></td></tr><tr v-if="!filtered.length"><td colspan="8" class="resource-empty"><strong>当前筛选暂无药品</strong><span>可以调整状态或搜索条件后继续查看。</span></td></tr></tbody></table></div></section><p class="resource-footnote"><Icon name="shield" /> 医生端只加载启用药品；停用或删除不会修改既有处方和药费订单。</p>
    </main>
    <div v-if="selected && !profileModal && !stockModal && !deleteModal && !statusModal" class="pharmacy-drawer-backdrop" @click.self="closeDetails"><aside class="pharmacy-drawer" aria-label="药品详情" @click.stop><button type="button" class="pharmacy-drawer-close" aria-label="关闭药品详情" @click="closeDetails">×</button><span class="pharmacy-kicker">{{ selected.id }}</span><div class="pharmacy-drawer-title"><div><span class="resource-status" :class="{ disabled: !selected.active }"><i></i>{{ statusLabel(selected) }}</span><h2>{{ selected.name }}</h2><p>{{ selected.spec }} · 单价 {{ money(selected.feeMinor) }}</p></div><strong v-if="isLow(selected)" class="pharmacy-warning">库存预警</strong></div><div class="pharmacy-detail-grid"><span>可用库存<strong :class="{ low: isLow(selected) }">{{ selected.availableStock }} 个</strong></span><span>库存锁定<strong>{{ selected.heldStock }} 个</strong></span><span>总库存<strong>{{ selected.totalStock }} 个</strong></span><span>预警阈值<strong>{{ selected.threshold }} 个</strong></span></div><section class="pharmacy-detail-section"><h3>库存说明</h3><p>总库存 = 可用库存 + 锁定库存。锁定库存会在药费支付成功后转为消耗，订单超时后释放回可用库存。</p></section><section class="pharmacy-history"><div class="pharmacy-history-heading"><h3>消耗记录</h3><span v-if="historyBusy">加载中…</span></div><p v-if="historyError" class="pharmacy-error" role="alert">{{ historyError }}</p><p v-else-if="!historyBusy && !consumptions.length" class="pharmacy-empty">暂无药费订单记录。</p><ul v-else><li v-for="record in consumptions" :key="record.id"><div><strong>{{ stateLabel(record.state) }}</strong><small>{{ formatTime(record.createdAt) }}</small></div><b>× {{ record.quantity }}</b></li></ul></section><div class="pharmacy-detail-actions"><button type="button" class="admin-primary" @click="openStock(selected)">调整库存</button><button type="button" class="admin-outline" @click="openEdit(selected)">编辑资料</button></div></aside></div>
    <div v-if="profileModal" class="pharmacy-modal-backdrop"><section class="pharmacy-modal" role="dialog" aria-modal="true" :aria-label="formTitle"><button type="button" class="pharmacy-modal-close" aria-label="关闭药品表单" @click="profileModal = false">×</button><span class="pharmacy-kicker">药品资料</span><h2>{{ formTitle }}</h2><label>药品名称<input v-model="profile.name" aria-label="药品名称" required maxlength="120"></label><label>规格<input v-model="profile.spec" aria-label="药品规格" required maxlength="200"></label><div class="pharmacy-form-two"><label>单价（元）<input :value="(profile.feeMinor / 100).toFixed(2)" aria-label="单价（元）" type="number" min="0" step="0.01" @input="profile.feeMinor = Math.round(Number(($event.target as HTMLInputElement).value) * 100)"></label><label>预警阈值<input v-model.number="profile.threshold" aria-label="预警阈值" type="number" min="0" step="1"></label></div><label>药品状态<select v-model="profile.active" aria-label="药品状态"><option :value="true">启用</option><option :value="false">停用</option></select></label><p class="pharmacy-form-note">库存不在资料表单中修改；请保存资料后使用独立的“调整库存”操作。</p><p v-if="error" class="pharmacy-error" role="alert">{{ error }}</p><div class="pharmacy-modal-actions"><button type="button" class="admin-soft" @click="profileModal = false">取消</button><button type="button" class="admin-primary" :disabled="busy" @click="saveProfile">{{ busy ? '保存中…' : '保存资料' }}</button></div></section></div>
    <div v-if="stockModal && stockTarget" class="pharmacy-modal-backdrop"><section class="pharmacy-modal" role="dialog" aria-modal="true" aria-label="调整库存"><button type="button" class="pharmacy-modal-close" aria-label="关闭库存表单" @click="closeStock">×</button><span class="pharmacy-kicker">库存调整</span><h2>调整总库存</h2><p>{{ stockTarget.name }} · {{ stockTarget.spec }}</p><div class="pharmacy-stock-compare"><span>可用 {{ stockTarget.availableStock }}<small>锁定 {{ stockTarget.heldStock }}</small></span><b>→</b><span>调整后可用 {{ Math.max(0, stockForm.totalStock - stockTarget.heldStock) }}<small>总库存 {{ stockForm.totalStock }}</small></span></div><label>目标总库存<input v-model.number="stockForm.totalStock" aria-label="目标总库存" type="number" min="0" step="1"></label><p class="pharmacy-form-note">锁定库存保持 {{ stockTarget.heldStock }} 个不变，目标总库存不能低于锁定库存。</p><p v-if="error" class="pharmacy-error" role="alert">{{ error }}</p><div class="pharmacy-modal-actions"><button type="button" class="admin-soft" @click="closeStock">取消</button><button type="button" class="admin-primary" :disabled="busy" @click="saveStock">{{ busy ? '提交中…' : '确认调整' }}</button></div></section></div>
    <div v-if="deleteModal && deleteTarget" class="pharmacy-modal-backdrop"><section class="pharmacy-modal pharmacy-confirm" role="dialog" aria-modal="true" aria-label="确认删除药品"><span class="pharmacy-confirm-icon">!</span><h2>确认删除药品</h2><p>{{ deleteTarget.name }} · {{ deleteTarget.spec }}</p><p class="pharmacy-form-note">该药品没有处方历史，可以删除。只有点击“确认删除”后才会从药品库中移除。</p><p v-if="error" class="pharmacy-error" role="alert">{{ error }}</p><div class="pharmacy-modal-actions"><button type="button" class="admin-soft" @click="closeDelete">取消</button><button type="button" class="pharmacy-danger" :disabled="busy" @click="confirmDelete">确认删除</button></div></section></div>
    <div v-if="statusModal && statusTarget" class="pharmacy-modal-backdrop"><section class="pharmacy-modal pharmacy-confirm" role="dialog" aria-modal="true" aria-label="确认停用药品"><span class="pharmacy-confirm-icon">!</span><h2>确认停用药品</h2><p>{{ statusTarget.name }} · {{ statusTarget.spec }}</p><p class="pharmacy-form-note">停用后医生端将不能再开具该药品，既有处方和药费订单不受影响。</p><p v-if="error" class="pharmacy-error" role="alert">{{ error }}</p><div class="pharmacy-modal-actions"><button type="button" class="admin-soft" @click="statusModal = false; statusTarget = null">取消</button><button type="button" class="pharmacy-danger" :disabled="busy" @click="confirmToggleActive">确认停用</button></div></section></div>
  </AdminOperationsShell>
</template>
