<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { useSession } from '../session'
const router = useRouter(), session = useSession()
const identifier = ref(''), password = ref(''), captcha = ref(''), challengeId = ref(''), image = ref('')
const role = ref('PATIENT'), register = ref(false), message = ref(''), busy = ref(false)
const refreshing = ref(false), showPassword = ref(false), confirmPassword = ref('')
let currentRefresh: Promise<boolean> | null = null
type Role = 'PATIENT' | 'DOCTOR' | 'ADMIN'
const roles: Array<{ id: Role; name: string; short: string; description: string }> = [
  { id: 'PATIENT', name: '患者端', short: '患', description: '预约、问诊与健康档案' },
  { id: 'DOCTOR', name: '医生端', short: '医', description: '排班、候诊与临床接诊' },
  { id: 'ADMIN', name: '管理员端', short: '管', description: '目录、审核与系统治理' },
]
function switchMode(next: boolean) {
  if (busy.value || (next && role.value === 'ADMIN')) return
  register.value = next; message.value = ''; confirmPassword.value = ''; showPassword.value = false
}
function moveMode(event: KeyboardEvent) {
  if (busy.value || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const next = role.value !== 'ADMIN' && (event.key === 'End' || (event.key !== 'Home' && !register.value))
  switchMode(next)
  document.getElementById(next ? 'register-tab' : 'login-tab')?.focus()
}
function chooseRole(next: Role) {
  if (busy.value) return
  role.value = next; message.value = ''; showPassword.value = false
  if (next === 'ADMIN') switchMode(false)
}
function refresh(): Promise<boolean> {
  if (currentRefresh) return currentRefresh
  refreshing.value = true; challengeId.value = ''; image.value = ''; captcha.value = ''
  currentRefresh = (async () => {
    try { const challenge = await api<{ challengeId: string; image: string }>('/auth/captcha'); challengeId.value = challenge.challengeId; image.value = challenge.image; captcha.value = ''; return true }
    catch (error) { image.value = ''; message.value = error instanceof Error ? error.message : '验证码暂不可用'; return false }
    finally { refreshing.value = false; currentRefresh = null }
  })()
  return currentRefresh
}
async function submit() {
  if (busy.value || (!register.value && (refreshing.value || !challengeId.value))) return
  if (register.value && role.value === 'ADMIN') return
  if (register.value && password.value !== confirmPassword.value) {
    message.value = '两次输入的密码不一致'; return
  }
  busy.value = true; message.value = ''
  try {
    if (register.value) {
      await api('/auth/register', 'POST', { identifier: identifier.value, password: password.value, role: role.value })
      register.value = false; confirmPassword.value = ''; showPassword.value = false
      const ready = await refresh()
      if (ready) message.value = '注册成功，请输入图形验证码后登录'
    } else {
      await session.login(identifier.value, password.value, challengeId.value, captcha.value)
      // The selected entrance never grants a role; navigate using the authenticated account.
      await router.push('/' + session.account!.roles[0]!.toLowerCase())
    }
  } catch (error) { message.value = error instanceof Error ? error.message : '操作失败'; if (!register.value) await refresh() }
  finally { busy.value = false }
}
onMounted(refresh)
</script>

<template>
  <section class="auth-page" aria-label="账户登录与注册">
    <aside class="identity-panel">
      <div class="brand"><span class="brand-mark" aria-hidden="true">+</span><span>校医院健康服务<small>CAMPUS HEALTH</small></span></div>
      <div class="introduction">
        <span class="overline">CARE BEGINS HERE</span>
        <h1>每一次就诊，<br>从这里开始。</h1>
        <p>让预约更从容，让沟通更简单。<br>选择你的身份，进入专属服务空间。</p>
      </div>
      <fieldset class="role-list" :disabled="busy">
        <legend>选择你的身份 <span>01 / 02</span></legend>
        <label v-for="item in roles" :key="item.id" class="role-card" :class="{ selected: role === item.id }">
          <input type="radio" name="service-role" :value="item.id" :checked="role === item.id" @change="chooseRole(item.id)" />
          <span class="role-avatar" aria-hidden="true">{{ item.short }}</span>
          <span class="role-copy"><strong>{{ item.name }}</strong><small>{{ item.description }}</small></span>
        </label>
      </fieldset>
      <p class="identity-footer"><span aria-hidden="true">◈</span> 一个入口，连接患者、医生与管理员</p>
    </aside>

    <div class="account-panel">
      <div class="entrance-label"><span class="status-dot" /><span>{{ roles.find(item => item.id === role)?.name }}入口</span><span class="service-badge">校医院服务</span></div>
      <div class="mode-tabs" role="tablist" aria-label="登录或注册" @keydown="moveMode">
        <button id="login-tab" role="tab" type="button" :aria-selected="!register" :tabindex="register ? -1 : 0" aria-controls="account-form" :disabled="busy" @click="switchMode(false)">登录</button>
        <button v-if="role !== 'ADMIN'" id="register-tab" role="tab" type="button" :aria-selected="register" :tabindex="register ? 0 : -1" aria-controls="account-form" :disabled="busy" @click="switchMode(true)">注册</button>
      </div>
      <div class="form-heading">
        <span class="overline">STEP 02 · {{ roles.find(item => item.id === role)?.name }}</span>
        <h2>{{ register ? '创建账户' : '欢迎回来' }}</h2>
        <p>{{ register ? '填写账户信息，开启你的专属服务空间。' : '登录你的账户，继续今天的健康服务。' }}</p>
        <p v-if="role === 'ADMIN'" class="account-note">管理员账号只能由管理员在后台创建。</p>
        <p v-if="register && role === 'DOCTOR'" class="account-note">医生注册后需通过资质审核。</p>
      </div>
      <p v-if="message" role="status" class="feedback" :class="{ success: message.startsWith('注册成功') }">{{ message }}</p>
      <form id="account-form" role="tabpanel" :aria-labelledby="register ? 'register-tab' : 'login-tab'" :aria-busy="busy || refreshing" @submit.prevent="submit">
        <div class="field"><label for="identifier">账号</label><input id="identifier" v-model="identifier" autocomplete="username" :placeholder="`请输入${role === 'ADMIN' ? '管理员' : role === 'DOCTOR' ? '医生' : '患者'}账号`" required maxlength="190" :disabled="busy" /></div>
        <div class="field">
          <label for="password">密码</label>
          <div class="password-wrap"><input id="password" v-model="password" :type="showPassword ? 'text' : 'password'" :autocomplete="register ? 'new-password' : 'current-password'" :aria-describedby="register ? 'password-hint' : undefined" placeholder="请输入密码" required minlength="12" maxlength="72" :disabled="busy" /><button class="password-toggle" type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" :aria-pressed="showPassword" :disabled="busy" @click="showPassword = !showPassword">{{ showPassword ? '隐藏' : '显示' }}</button></div>
          <small v-if="register" id="password-hint" class="field-hint">密码为 12–72 位，请使用虚构演示账户。</small>
        </div>
        <div v-if="register" class="field"><label for="confirm-password">确认密码</label><input id="confirm-password" v-model="confirmPassword" type="password" autocomplete="new-password" placeholder="再次输入密码" required minlength="12" maxlength="72" :disabled="busy" /></div>
        <div v-else class="field">
          <label for="captcha">图形验证码</label>
          <div class="captcha-row">
            <input id="captcha" v-model="captcha" :disabled="busy || refreshing || !challengeId" placeholder="输入图中字符" autocomplete="off" required maxlength="8" />
            <div class="captcha-image"><img v-if="image" :src="image" alt="图形验证码" /><span v-else>{{ refreshing ? '正在获取…' : '暂不可用' }}</span></div>
            <button class="captcha-refresh" type="button" :disabled="busy || refreshing" @click="refresh">{{ refreshing ? '刷新中…' : '换一张' }}</button>
          </div>
          <small class="field-hint">看不清可以换一张，刷新后请使用新图中的字符。</small>
        </div>
        <button class="primary-action" :disabled="busy || (!register && (refreshing || !challengeId))" type="submit">{{ busy ? '处理中…' : register ? '注册' : '登录' }}</button>
      </form>
      <div class="mode-footer">
        <button v-if="register" class="mode-link" type="button" :disabled="busy" @click="switchMode(false)">已有账户，返回登录</button>
        <button v-else-if="role !== 'ADMIN'" class="mode-link" type="button" :disabled="busy" @click="switchMode(true)">患者或医生注册</button>
        <span v-else>请使用由管理员后台创建的账号登录</span>
      </div>
      <p class="account-footer">温暖的服务，从清晰的第一步开始。</p>
    </div>
  </section>
</template>

<style scoped>
.auth-page{--navy:#193b68;--ink:#263d5b;display:grid;grid-template-columns:46% 54%;max-width:1120px;min-height:720px;margin:0 auto;border:1px solid #dce3ee;border-radius:26px;overflow:hidden;background:#fff;color:var(--ink);box-shadow:0 20px 70px #132c5014}
.identity-panel{padding:38px 42px 28px;display:flex;flex-direction:column;background:#122a4d;color:#f1f5fc}
.brand{display:flex;align-items:center;gap:12px;font-size:14px;font-weight:650;letter-spacing:.06em}
.brand-mark{display:grid;place-items:center;width:36px;height:36px;border:1px solid #91acc955;border-radius:11px;color:#c8dcf6;font-size:31px;font-weight:300}
.brand small{display:block;color:#a8beda;font-size:8px;letter-spacing:.18em;margin-top:5px;line-height:1.5}
.introduction{padding:55px 0 34px}
.overline{font-size:9px;letter-spacing:.16em;color:#9fb8dc;font-weight:550}
.introduction h1{font-size:39px;line-height:1.4;font-weight:550;letter-spacing:-.05em;margin:17px 0 20px;color:#fff}
.introduction p{font-size:12px;line-height:1.9;color:#bbcae0;margin:0}
.role-list{display:grid;gap:10px;margin:0;padding:0;border:0;min-width:0}
.role-list legend{display:flex;justify-content:space-between;width:100%;font-size:11px;color:#cad7eb;margin:0 0 13px;padding:0}
.role-list legend span{color:#91a8ca;font-size:9px;letter-spacing:.1em}
.role-card{display:flex;align-items:center;gap:12px;min-height:68px;margin:0;padding:14px;border:1px solid #7894bb55;border-radius:11px;background:#ffffff04;color:#c4d2e8;cursor:pointer}
.role-card.selected{background:#e8f0fc;color:#193e6c;border-color:#d1def4}
.role-card:hover{border-color:#b6cbed}
.role-card:focus-within{outline:3px solid #7499d0;outline-offset:3px}
.role-card input{order:3;flex:none;width:15px;height:15px;padding:0;margin:0 0 0 auto;accent-color:#365b8d}
.role-card input:focus-visible{outline:0}
.role-list:disabled .role-card{cursor:wait;opacity:.65}
.role-avatar{display:grid;place-items:center;flex:none;width:33px;height:33px;border-radius:9px;background:#ffffff10;font-size:13px}
.selected .role-avatar{background:#ccdcf4;color:#264e7f}
.role-copy{display:grid;gap:5px;min-width:0}
.role-copy strong{font-size:14px;font-weight:650}
.role-copy small{font-size:11px;line-height:1.5;color:inherit;opacity:.8}
.identity-footer{font-size:10px;line-height:1.7;color:#9bb1d0;margin:25px 0 0}
.identity-footer span{margin-right:5px}
.account-panel{display:flex;flex-direction:column;min-width:0;padding:38px 52px 24px}
.entrance-label{display:flex;align-items:center;gap:7px;color:#677c9a;font-size:11px;margin-bottom:35px}
.status-dot{width:6px;height:6px;border-radius:50%;background:#3e6eab}
.service-badge{margin-left:auto;padding:4px 8px;border:1px solid #dce4f0;border-radius:5px;background:#f0f4fa;color:#7186a4;font-size:9px}
.mode-tabs{display:flex;gap:5px;padding:4px;border-radius:9px;background:#eef2f8;margin-bottom:28px}
.mode-tabs button{flex:1;padding:12px;border:1px solid transparent;border-radius:6px;background:none;color:#7385a0;font-size:14px;font-weight:600;min-height:42px}
.mode-tabs button[aria-selected=true]{background:#fff;color:#193c69;border-color:#dce6f3;box-shadow:0 2px 5px #1b3f6810}
.form-heading{margin-bottom:24px}
.form-heading .overline{color:#7f91aa;letter-spacing:.09em}
.form-heading h2{font-size:28px;font-weight:600;letter-spacing:-.05em;margin:9px 0;color:var(--ink)}
.form-heading p{font-size:12px;line-height:1.8;color:#78889d;margin:0}
.form-heading .account-note{padding:10px 12px;margin-top:13px;border-left:3px solid #7895bb;border-radius:4px;background:#f0f4fa;color:#536f91;font-size:11px;line-height:1.8}
.field{margin-bottom:19px}
.field label{display:block;margin:0 0 8px;color:#435b7a;font-size:13px;font-weight:550}
.field input{width:100%;height:44px;min-width:0;padding:12px 13px;border:1px solid #d8e2ef;border-radius:8px;background:#fcfdff;color:#293f5d;font-size:13px}
.field input::placeholder{color:#98a5b6}
.password-wrap{position:relative}
.password-wrap input{padding-right:58px}
.password-toggle{position:absolute;top:0;right:12px;height:44px;min-height:0;padding:0;background:none;color:#53749e;font-size:11px;font-weight:400}
.field-hint{display:block;color:#8998ad;font-size:10px;line-height:1.8;margin-top:7px}
.captcha-row{display:grid;grid-template-columns:minmax(80px,1fr) 116px auto;gap:8px;align-items:center}
.captcha-image{display:flex;align-items:center;justify-content:center;width:116px;min-height:44px;border-radius:6px;overflow:hidden;background:#eef3fb}
.captcha-image img{display:block;width:116px;height:auto}
.captcha-image span{font-size:10px;color:#7086a3}
.captcha-refresh{padding:4px 0;min-height:36px;background:none;color:#53749e;font-size:11px;font-weight:400;white-space:nowrap}
.primary-action{width:100%;min-height:46px;margin:4px 0 0;padding:13px 16px;border-radius:8px;background:var(--navy);color:#fff;font-size:14px;font-weight:600}
.primary-action:hover:enabled{background:#122f55}
.mode-footer{display:flex;align-items:center;justify-content:center;min-height:45px;text-align:center;font-size:10px;line-height:1.8;color:#8998ad;margin-top:4px}
.mode-link{padding:6px 8px;min-height:34px;margin:0;background:none;color:#53749e;font-size:11px;font-weight:400}
.account-footer{padding-top:16px;margin:8px 0 0;border-top:1px solid #edf1f7;color:#8695ac;font-size:10px;line-height:1.8;text-align:center}
.feedback{padding:10px 12px;margin:0 0 18px;border:1px solid #ebdfbe;border-radius:7px;background:#fcf8eb;color:#8c6d35;font-size:12px;line-height:1.7;overflow-wrap:anywhere}
.feedback.success{background:#f1f7f0;border-color:#d5e6d1;color:#4d7047}
.auth-page button:focus-visible,.field input:focus-visible{outline:3px solid #7499d0;outline-offset:3px}
@media(max-width:760px){
  .auth-page{grid-template-columns:1fr;min-height:0;border-radius:18px}
  .identity-panel{padding:25px}
  .introduction{padding:25px 0 20px}
  .introduction h1{font-size:30px;margin:10px 0}
  .introduction p,.identity-footer{display:none}
  .role-list{grid-template-columns:repeat(3,minmax(0,1fr));gap:7px}
  .role-list legend{margin-bottom:10px}
  .role-list legend span{display:none}
  .role-card{padding:12px 6px;gap:7px;justify-content:center;flex-wrap:wrap;min-height:55px}
  .role-card input{width:12px;height:12px;margin-left:0}
  .role-avatar{display:none}
  .role-copy strong{font-size:12px}
  .role-copy small{display:none}
  .account-panel{padding:25px}
  .entrance-label{margin-bottom:23px}
  .field input{font-size:16px;height:46px}
  .password-toggle{height:46px}
  .captcha-row{grid-template-columns:minmax(55px,1fr) 100px auto;gap:7px}
  .captcha-image,.captcha-image img{width:100px}
  .captcha-refresh{font-size:10px}
}
@media(prefers-reduced-motion:reduce){*{scroll-behavior:auto}}
</style>
