<script setup lang="ts">
import { computed, ref } from 'vue'

type Role = 'PATIENT' | 'DOCTOR' | 'ADMIN'
type Mode = 'login' | 'register'

const roles: Array<{ id: Role; label: string; description: string; initials: string; accent: string }> = [
  { id: 'PATIENT', label: '患者端', description: '预约、问诊与健康档案', initials: '患', accent: '#2b7a78' },
  { id: 'DOCTOR', label: '医生端', description: '排班、候诊与临床接诊', initials: '医', accent: '#5267a7' },
  { id: 'ADMIN', label: '管理员端', description: '目录、审核与系统治理', initials: '管', accent: '#b77935' },
]

const mode = ref<Mode>('login')
const role = ref<Role>('PATIENT')
const identifier = ref('')
const password = ref('')
const confirmPassword = ref('')
const displayName = ref('')
const showPassword = ref(false)
const remember = ref(true)
const status = ref('')
const submitted = ref(false)
const registered = ref(false)

const selectedRole = computed(() => roles.find(item => item.id === role.value) ?? roles[0])
const actionLabel = computed(() => mode.value === 'login' ? '进入演示工作台' : '创建演示账户')
const loggedIn = computed(() => submitted.value && mode.value === 'login')

function chooseRole(next: Role) {
  role.value = next
  status.value = ''
  submitted.value = false
}

function switchMode(next: Mode) {
  mode.value = next
  status.value = ''
  submitted.value = false
  showPassword.value = false
}

function fillDemo() {
  const demoNames: Record<Role, string> = { PATIENT: 'patient-demo', DOCTOR: 'doctor-demo', ADMIN: 'admin-demo' }
  identifier.value = demoNames[role.value]
  password.value = 'demo-password'
  status.value = '已填入演示账号，可以直接进入。'
}

function submit() {
  status.value = ''
  if (!identifier.value.trim() || !password.value.trim()) {
    status.value = '请先填写账号和密码。'
    return
  }
  if (mode.value === 'register') {
    if (password.value.length < 8) {
      status.value = '演示密码至少需要 8 位。'
      return
    }
    if (password.value !== confirmPassword.value) {
      status.value = '两次输入的密码不一致。'
      return
    }
    registered.value = true
    mode.value = 'login'
    confirmPassword.value = ''
    status.value = `注册成功，${selectedRole.value.label}账号已准备好，请登录。`
    return
  }
  submitted.value = true
  status.value = `${selectedRole.value.label}登录成功。当前为纯前端演示，不会调用后端。`
}

function signOut() {
  submitted.value = false
  status.value = '已退出演示工作台。'
}
</script>

<template>
  <section class="prototype-shell" :style="{ '--role-accent': selectedRole.accent }">
    <div class="prototype-hero">
      <div class="hero-copy">
        <div class="prototype-pill"><span class="pulse-dot" /> UI PROTOTYPE · 纯前端模拟</div>
        <h2>一个入口，三种工作方式。</h2>
        <p>先选择身份，再进入对应的医疗协作空间。这里用于快速体验登录与注册状态，不会保存任何数据。</p>
        <div class="hero-proof">
          <span>✓ 三端角色切换</span><span>✓ 登录 / 注册状态</span><span>✓ 内存模拟交互</span>
        </div>
      </div>
      <div class="hero-orbit" aria-hidden="true">
        <div class="orbit-ring ring-one" /><div class="orbit-ring ring-two" />
        <div class="orbit-core">M</div><span class="orbit-label label-patient">患</span><span class="orbit-label label-doctor">医</span><span class="orbit-label label-admin">管</span>
      </div>
    </div>

    <div class="prototype-card">
      <div class="role-heading"><div><span class="section-kicker">STEP 01</span><h3>你将以哪种身份进入？</h3></div><span class="memory-note">状态仅保留在当前页面</span></div>
      <div class="role-grid" role="tablist" aria-label="选择登录端">
        <button v-for="item in roles" :key="item.id" class="role-card" :class="{ active: role === item.id }" type="button" role="tab" :aria-selected="role === item.id" @click="chooseRole(item.id)">
          <span class="role-avatar" :style="{ background: item.accent }">{{ item.initials }}</span><span class="role-text"><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span><span class="role-check">{{ role === item.id ? '✓' : '＋' }}</span>
        </button>
      </div>

      <div v-if="loggedIn" class="success-panel" role="status">
        <div class="success-icon">✓</div><div><span class="section-kicker">DEMO SESSION ACTIVE</span><h3>{{ selectedRole.label }}工作台已打开</h3><p>欢迎回来，{{ identifier }}。你可以退出并切换其他角色继续体验。</p></div><button class="ghost-button" type="button" @click="signOut">退出体验</button>
      </div>

      <form v-else class="auth-panel" @submit.prevent="submit">
        <div class="auth-tabs" role="tablist" aria-label="登录或注册"><button type="button" :class="{ active: mode === 'login' }" role="tab" :aria-selected="mode === 'login'" @click="switchMode('login')">登录</button><button type="button" :class="{ active: mode === 'register' }" role="tab" :aria-selected="mode === 'register'" @click="switchMode('register')">注册</button></div>
        <div class="form-heading"><div><span class="section-kicker">STEP 02 · {{ selectedRole.label }}</span><h3>{{ mode === 'login' ? '欢迎回来' : '创建演示账户' }}</h3></div><button class="demo-link" type="button" @click="fillDemo">填入演示账号 ↗</button></div>
        <p v-if="status" role="status" class="status-message" :class="{ positive: registered || submitted }">{{ status }}</p>
        <label>账号<input v-model="identifier" :placeholder="mode === 'login' ? '例如：patient-demo' : '设置一个演示账号'" autocomplete="username" /></label>
        <label v-if="mode === 'register'">显示名称<input v-model="displayName" placeholder="例如：林小满" /></label>
        <label>密码<div class="password-wrap"><input v-model="password" :type="showPassword ? 'text' : 'password'" :placeholder="mode === 'login' ? '输入演示密码' : '至少 8 位'" autocomplete="new-password" /><button class="password-toggle" type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword">{{ showPassword ? '隐藏' : '显示' }}</button></div></label>
        <label v-if="mode === 'register'">确认密码<input v-model="confirmPassword" type="password" placeholder="再次输入密码" autocomplete="new-password" /></label>
        <div v-if="mode === 'login'" class="form-options"><label class="checkbox-label"><input v-model="remember" type="checkbox" />记住本次演示</label><button class="forgot-link" type="button" @click="status = '原型中仅展示视觉反馈，未接入找回密码流程。'">忘记密码？</button></div>
        <button class="primary-action" type="submit">{{ actionLabel }} <span>→</span></button>
        <p class="form-footnote">{{ mode === 'login' ? '登录后将展示对应角色的工作台入口。' : '这是一次性演示，注册信息不会写入数据库。' }}</p>
      </form>
    </div>
    <p class="prototype-footer">医疗协作平台 · 交互原型 v0.1 <span>安全、清晰、以人为本</span></p>
  </section>
</template>

<style scoped>
.prototype-shell{--ink:#18363e;--muted:#6d8186;max-width:1120px;margin:0 auto;padding:18px 0 22px;color:var(--ink)}
.prototype-hero{min-height:278px;border-radius:30px;padding:42px 48px;display:flex;justify-content:space-between;align-items:center;overflow:hidden;position:relative;background:linear-gradient(122deg,#153f49,#205f67 58%,#dfede8);color:white;box-shadow:0 24px 60px #1747512e}.prototype-hero:after{content:"";position:absolute;right:-120px;top:-170px;width:460px;height:460px;border-radius:50%;background:#d4e8df20}.hero-copy{max-width:610px;position:relative;z-index:1}.prototype-pill{font-size:11px;letter-spacing:.14em;color:#d8f0e5;font-weight:800}.pulse-dot{display:inline-block;width:8px;height:8px;border-radius:50%;background:#8ee3be;box-shadow:0 0 0 5px #8ee3be20;margin-right:8px}.hero-copy h2{font-size:clamp(34px,5vw,58px);line-height:1.02;letter-spacing:-.045em;margin:20px 0 15px}.hero-copy p{max-width:520px;line-height:1.7;color:#d5e9e8;margin:0}.hero-proof{display:flex;flex-wrap:wrap;gap:18px;margin-top:26px;font-size:12px;color:#c1e4db}.hero-orbit{width:240px;height:200px;position:relative;flex:none;margin-right:20px}.orbit-ring{position:absolute;border:1px solid #d9f3e660;border-radius:50%;left:50%;top:50%;transform:translate(-50%,-50%) rotate(-15deg)}.ring-one{width:190px;height:95px}.ring-two{width:150px;height:150px;transform:translate(-50%,-50%) rotate(38deg)}.orbit-core{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);width:72px;height:72px;border-radius:24px;background:#f6fbf4;color:#246e67;display:grid;place-items:center;font-size:28px;font-weight:900;box-shadow:0 12px 30px #0d354566}.orbit-label{position:absolute;width:32px;height:32px;border-radius:12px;display:grid;place-items:center;font-weight:800;background:#ffffff22;border:1px solid #d9f3e680}.label-patient{left:15px;top:35px}.label-doctor{right:6px;top:53px}.label-admin{left:106px;bottom:1px}
.prototype-card{background:#fff;border:1px solid #d7e6e4;border-radius:24px;margin-top:-30px;position:relative;z-index:2;padding:28px 34px;box-shadow:0 18px 48px #284a5212}.role-heading,.form-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:16px}.section-kicker{font-size:10px;letter-spacing:.18em;color:var(--role-accent);font-weight:900}.role-heading h3,.form-heading h3{margin:6px 0 0;font-size:22px;letter-spacing:-.02em}.memory-note,.form-footnote{font-size:12px;color:var(--muted)}.role-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin-top:18px}.role-card{border:1px solid #d8e5e3;background:#fbfdfc;border-radius:16px;padding:14px;display:flex;align-items:center;gap:11px;text-align:left;color:var(--ink);transition:.18s ease;min-height:78px}.role-card:hover{border-color:#9fc8c2;transform:translateY(-2px)}.role-card.active{border:2px solid var(--role-accent);background:#f1faf6;box-shadow:0 8px 18px #245c5714}.role-avatar{width:35px;height:35px;border-radius:12px;display:grid;place-items:center;color:white;font-weight:900;flex:none}.role-text{display:grid;gap:3px;min-width:0}.role-text strong{font-size:14px}.role-text small{color:var(--muted);font-size:11px;line-height:1.35}.role-check{margin-left:auto;color:var(--role-accent);font-weight:900}.auth-panel{max-width:600px;margin:30px auto 0}.auth-tabs{display:flex;gap:24px;border-bottom:1px solid #dbe8e6}.auth-tabs button{background:none;border:0;color:#8ca0a1;padding:0 0 12px;min-height:0;border-radius:0;font-weight:800;position:relative}.auth-tabs button.active{color:var(--ink)}.auth-tabs button.active:after{content:"";position:absolute;bottom:-1px;left:0;right:0;height:3px;border-radius:3px;background:var(--role-accent)}.form-heading{margin:25px 0 18px}.demo-link,.forgot-link{background:none;border:0;color:var(--role-accent);padding:0;min-height:0;font-size:12px;font-weight:800}.status-message{padding:11px 13px;border-radius:10px;background:#fff7e6;color:#9a6a22;font-size:13px;line-height:1.5;margin:0 0 16px}.status-message.positive{background:#eaf8f0;color:#26704f}.auth-panel label{display:block;font-size:13px;font-weight:800;margin:16px 0 0}.auth-panel input{margin-top:7px;width:100%;border:1px solid #c7d9d6;border-radius:11px;padding:13px 14px;background:#fcfefd;color:var(--ink)}.auth-panel input:focus{outline:3px solid #a9ddd022;border-color:var(--role-accent)}.password-wrap{position:relative}.password-wrap input{padding-right:65px}.password-toggle{position:absolute;right:10px;top:14px;background:none;color:var(--role-accent);padding:0;min-height:0;font-size:12px}.form-options{display:flex;justify-content:space-between;align-items:center;margin-top:15px}.checkbox-label{display:flex!important;align-items:center;gap:7px;color:var(--muted);font-weight:600!important}.checkbox-label input{width:16px;margin:0}.primary-action{width:100%;margin-top:25px;border:0;border-radius:12px;background:var(--role-accent);color:white;padding:14px 17px;display:flex;justify-content:space-between;align-items:center;font-weight:900;box-shadow:0 10px 20px color-mix(in srgb,var(--role-accent) 20%,transparent)}.primary-action span{font-size:20px}.success-panel{margin:30px auto 0;max-width:700px;border-radius:18px;background:#edf9f2;border:1px solid #bfe5cc;padding:25px;display:flex;align-items:center;gap:15px}.success-panel h3{margin:5px 0;font-size:22px}.success-panel p{margin:0;color:#547267;font-size:13px}.success-icon{width:42px;height:42px;border-radius:50%;background:#2c9361;color:white;display:grid;place-items:center;font-size:22px;font-weight:900;flex:none}.ghost-button{margin-left:auto;background:white;color:#2b7a58;border:1px solid #a9d7b9;padding:9px 13px;min-height:0;white-space:nowrap}.prototype-footer{font-size:11px;color:#8ca0a1;margin:14px 2px 0;display:flex;justify-content:space-between}.prototype-footer span{color:#78958f}@media(max-width:760px){.prototype-shell{padding-top:4px}.prototype-hero{padding:30px 24px;min-height:320px}.hero-orbit{position:absolute;right:-36px;bottom:-34px;opacity:.6;transform:scale(.8)}.prototype-card{margin-top:-20px;padding:22px 20px}.role-grid{grid-template-columns:1fr}.role-card{min-height:66px}.memory-note{display:none}.success-panel{align-items:flex-start;flex-wrap:wrap}.success-panel .ghost-button{margin-left:57px}.prototype-footer{display:block;line-height:2}.prototype-footer span{display:block}}
</style>
