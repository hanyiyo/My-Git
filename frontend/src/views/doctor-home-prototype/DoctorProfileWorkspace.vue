<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import Icon from '../patient-home-prototype/PrototypeIcon.vue'
import type { DemoRole } from './data'
import './doctor-profile.css'

const props = defineProps<{ role: DemoRole; hasActiveVisit: boolean }>()
const emit = defineEmits<{ navigate: [section: 'applications']; logout: []; 'profile-change': [{ name: string; avatar: string }] }>()
const profileKey = 'medical-doctor-profile-v1'
type Profile = { name: string; phone: string; avatar: string; reception: boolean; news: boolean; revision: number }
const defaults = (): Profile => ({ name: '许知遥', phone: '13800000628', avatar: '', reception: true, news: true, revision: 0 })
const profile = ref<Profile>(defaults()), editing = ref(false), passwordOpen = ref(false), noticeOpen = ref(false), confirmOpen = ref(false), confirmKind = ref<'profile' | 'password' | 'logout'>('profile'), noticeSection = ref<'notifications' | 'privacy' | 'about'>('notifications')
const draftName = ref(''), draftPhone = ref(''), draftAvatar = ref(''), code = ref(''), codeSent = ref(false), error = ref(''), passwordError = ref(''), dirty = ref(false)
const oldPassword = ref(''), newPassword = ref(''), confirmPassword = ref('')
const maskedPhone = computed(() => profile.value.phone.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2'))
const initial = computed(() => profile.value.name.trim().slice(0, 1) || '许')
const draftInitial = computed(() => draftName.value.trim().slice(0, 1) || initial.value)
function read() { try { const value = JSON.parse(localStorage.getItem(profileKey) || 'null'); if (value && typeof value.phone === 'string') profile.value = { ...defaults(), ...value } } catch { profile.value = defaults() } }
function save(): boolean { try { profile.value.revision++; localStorage.setItem(profileKey, JSON.stringify(profile.value)); return true } catch { error.value = '浏览器暂时无法保存资料，请稍后重试。'; return false } }
function sync(event: StorageEvent) { if (event.key === profileKey) read(); if (event.key === null) read() }
function startEdit() { draftName.value = profile.value.name; draftPhone.value = profile.value.phone; draftAvatar.value = profile.value.avatar; code.value = ''; codeSent.value = false; error.value = ''; dirty.value = false; editing.value = true }
function closeEditor() { if (dirty.value) { confirmKind.value = 'profile'; confirmOpen.value = true } else editing.value = false }
function closePassword() { if (dirty.value) { confirmKind.value = 'password'; confirmOpen.value = true } else passwordOpen.value = false }
function abandon() { const kind = confirmKind.value; confirmOpen.value = false; dirty.value = false; if (kind === 'profile') editing.value = false; else if (kind === 'password') { passwordOpen.value = false; emit('logout') } else emit('logout') }
function chooseAvatar(event: Event) { const file = (event.target as HTMLInputElement).files?.[0]; if (!file) return; if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) { error.value = '请选择 JPG 或 PNG 图片，且大小不超过 5MB。'; return } const reader = new FileReader(); reader.onload = () => { draftAvatar.value = String(reader.result); dirty.value = true }; reader.readAsDataURL(file) }
function sendCode() { codeSent.value = true; error.value = '演示验证码已发送，不会发送真实短信。' }
function saveProfile() { error.value = ''; const phone = draftPhone.value.trim(); if (!/^1[3-9]\d{9}$/.test(phone)) { error.value = '请输入有效的中国大陆手机号。'; return } if (phone !== profile.value.phone && (!codeSent.value || !/^\d{6}$/.test(code.value))) { error.value = '更换手机号需要先获取并输入 6 位演示验证码。'; return } profile.value = { ...profile.value, name: profile.value.name, phone, avatar: draftAvatar.value }; if (!save()) return; emit('profile-change', { name: profile.value.name, avatar: profile.value.avatar }); dirty.value = false; editing.value = false }
function openPassword() { oldPassword.value = ''; newPassword.value = ''; confirmPassword.value = ''; passwordError.value = ''; dirty.value = false; passwordOpen.value = true }
function submitPassword() { passwordError.value = ''; if (!oldPassword.value || !newPassword.value || !confirmPassword.value) { passwordError.value = '请完整填写三项密码。'; return } if (newPassword.value.length < 12) { passwordError.value = '新密码至少需要 12 个字符。'; return } if (oldPassword.value === newPassword.value) { passwordError.value = '新密码不能与旧密码相同。'; return } if (newPassword.value !== confirmPassword.value) { passwordError.value = '两次输入的新密码不一致。'; return } if (props.hasActiveVisit) { confirmKind.value = 'password'; confirmOpen.value = true; return } passwordOpen.value = false; emit('logout') }
function requestLogout() { confirmKind.value = 'logout'; confirmOpen.value = true }
function resetProfile() { profile.value = defaults(); localStorage.setItem(profileKey, JSON.stringify(profile.value)); editing.value = false; passwordOpen.value = false; noticeOpen.value = false }
function onReset(event: Event) { if (event instanceof CustomEvent && event.detail === 'doctor-profile') resetProfile() }
onMounted(() => { read(); window.addEventListener('storage', sync); window.addEventListener('doctor-profile-reset', onReset) })
onUnmounted(() => { window.removeEventListener('storage', sync); window.removeEventListener('doctor-profile-reset', onReset) })
</script>

<template>
  <section class="dp-page" aria-labelledby="dp-title">
    <div class="dp-grid">
      <section class="dp-card" aria-label="医生资料">
        <div class="dp-identity"><span class="dp-avatar"><img v-if="profile.avatar" :src="profile.avatar" alt="当前头像"><span v-else>{{ initial }}</span></span><div><h3>{{ profile.name }} <small>医生</small></h3><p>{{ props.role === 'head' ? '科主任 · ' : '' }}校医院 · 内科</p></div><button class="dp-soft" @click="startEdit"><Icon name="edit" />编辑资料</button></div>
        <div class="dp-facts"><div><dt>职称</dt><dd>副主任医师</dd></div><div><dt>执业状态</dt><dd><i></i>已认证</dd></div><div><dt>擅长方向</dt><dd>常见内科、健康咨询</dd></div><div><dt>联系手机</dt><dd>{{ maskedPhone }}</dd></div></div>
        <div class="dp-credential"><Icon name="shield" /><div><strong>执业信息已认证</strong><p>姓名、职称、科室和擅长介绍由管理员维护。</p></div></div>
      </section>
      <section class="dp-card" aria-label="账户与设置"><h3 class="dp-card-title">账户与设置</h3><div class="dp-actions">
        <button @click="noticeSection = 'notifications'; noticeOpen = true"><span class="teal"><Icon name="bell" /></span><span><strong>通知设置</strong><small>接诊提醒与医院动态</small></span><Icon name="chevron" /></button>
        <button @click="openPassword"><span class="blue"><Icon name="lock" /></span><span><strong>修改密码</strong><small>更新登录密码，保护账户安全</small></span><Icon name="chevron" /></button>
        <button v-if="props.role === 'head'" @click="emit('navigate', 'applications')"><span class="purple"><Icon name="shield" /></span><span><strong>科室审批</strong><small>进入申请处理查看科室待办</small></span><Icon name="chevron" /></button>
        <button @click="noticeSection = 'privacy'; noticeOpen = true"><span class="orange"><Icon name="info" /></span><span><strong>隐私与协议</strong><small>查看隐私协议和服务说明</small></span><Icon name="chevron" /></button>
        <button @click="noticeSection = 'about'; noticeOpen = true"><span class="blue"><Icon name="cross" /></span><span><strong>关于系统</strong><small>版本信息与工作区说明</small></span><Icon name="chevron" /></button>
      </div><button class="dp-logout" @click="requestLogout"><Icon name="logout" />退出登录</button></section>
    </div>
    <p class="dp-boundary"><Icon name="shield" />医疗资料、接诊记录和病历草稿在接诊工作台中独立管理。</p>
    <dialog ref="editDialog" class="dp-dialog" :open="editing" aria-label="编辑医生资料" @cancel.prevent="closeEditor"><button class="dp-close" aria-label="关闭资料编辑" @click="closeEditor"><Icon name="close" /></button><span class="dh-eyebrow">DOCTOR PROFILE</span><h3>编辑资料</h3><p class="dp-readonly">姓名、科室、职称和擅长介绍由管理员维护。</p><div class="dp-edit-avatar"><span class="dp-avatar large"><img v-if="draftAvatar" :src="draftAvatar" alt="待保存头像"><span v-else>{{ draftInitial }}</span></span><label class="dp-upload">更换头像<input type="file" accept="image/jpeg,image/png" aria-label="选择头像" @change="chooseAvatar"></label><small>支持 JPG、PNG，最大 5MB</small></div><label>联系手机号<input v-model="draftPhone" inputmode="numeric" maxlength="11" aria-label="联系手机号" @input="dirty = true"></label><div v-if="draftPhone !== profile.phone" class="dp-code"><button class="dp-soft" :disabled="codeSent" @click="sendCode">{{ codeSent ? '验证码已发送' : '获取演示验证码' }}</button><input v-model="code" inputmode="numeric" maxlength="6" placeholder="输入 6 位验证码" aria-label="演示验证码" @input="dirty = true"></div><p v-if="error" class="dp-error" role="alert">{{ error }}</p><div class="dp-dialog-actions"><button class="dp-soft" @click="closeEditor">取消</button><button class="dp-primary" @click="saveProfile">保存资料</button></div></dialog>
    <dialog class="dp-dialog" :open="passwordOpen" aria-label="修改密码" @cancel.prevent="closePassword"><button class="dp-close" aria-label="关闭修改密码" @click="closePassword"><Icon name="close" /></button><span class="dh-eyebrow">ACCOUNT SECURITY</span><h3>修改密码</h3><p class="dp-readonly">本轮为演示流程，不会保存或修改真实账户密码。</p><label>旧密码<input v-model="oldPassword" type="password" autocomplete="current-password" aria-label="旧密码" @input="dirty = true"></label><label>新密码<input v-model="newPassword" type="password" autocomplete="new-password" aria-label="新密码" placeholder="至少 12 个字符" @input="dirty = true"></label><label>确认新密码<input v-model="confirmPassword" type="password" autocomplete="new-password" aria-label="确认新密码" @input="dirty = true"></label><p v-if="passwordError" class="dp-error" role="alert">{{ passwordError }}</p><div class="dp-dialog-actions"><button class="dp-soft" @click="closePassword">取消</button><button class="dp-primary" @click="submitPassword">确认修改</button></div></dialog>
    <dialog class="dp-dialog" :open="noticeOpen" :aria-label="noticeSection === 'notifications' ? '通知设置' : noticeSection === 'privacy' ? '隐私与协议' : '关于系统'"><button class="dp-close" aria-label="关闭账户说明" @click="noticeOpen = false"><Icon name="close" /></button><span class="dh-eyebrow">ACCOUNT SETTINGS</span><template v-if="noticeSection === 'notifications'"><h3>通知设置</h3><div class="dp-setting"><label><input v-model="profile.reception" type="checkbox" @change="save">一般接诊提醒</label><small>控制额外的接诊提示，不影响重要业务通知。</small><label><input v-model="profile.news" type="checkbox" @change="save">医院动态提醒</label><small>停诊、替诊、审批结果和申请进度始终保留。</small></div></template><template v-else-if="noticeSection === 'privacy'"><h3>隐私与协议</h3><div class="dp-doc"><strong>隐私协议</strong><p>医疗资料仅在明确授权范围内使用，通知设置不会改变接诊权限。</p><strong>服务说明</strong><p>系统消息用于工作提醒，不能替代医生对医疗记录的专业判断。</p></div></template><template v-else><h3>关于系统</h3><div class="dp-doc"><strong>智能医疗问诊预约系统</strong><p>医生工作区原型 v0.1 · 排班、接诊、申请处理与站内通知。</p></div></template></dialog>
    <dialog class="dp-confirm" :open="confirmOpen" aria-label="确认离开"><h3>{{ confirmKind === 'logout' ? '确认退出登录？' : '放弃未保存修改？' }}</h3><p>{{ hasActiveVisit ? '当前接诊尚未结束。离开不会自动完成接诊或标记过号，接诊与病历草稿会保留。' : '当前填写内容尚未保存，离开后将丢失。' }}</p><div><button class="dp-soft" @click="confirmOpen = false">继续填写</button><button class="dp-primary" @click="abandon">确认离开</button></div></dialog>
  </section>
</template>
