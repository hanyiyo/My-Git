<script setup lang="ts">
// Three left/right UI variants on /prototype/auth, switchable via ?variant=A/B/C.
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PrototypeSwitcher from '../components/PrototypeSwitcher.vue'
import VariantA from './prototype-auth/VariantA.vue'
import VariantB from './prototype-auth/VariantB.vue'
import VariantC from './prototype-auth/VariantC.vue'

const route = useRoute(), router = useRouter()
const variants = [
  { id: 'A', name: '温暖分栏', component: VariantA },
  { id: 'B', name: '角色引导', component: VariantB },
  { id: 'C', name: '服务工作台', component: VariantC },
] as const
const activeVariant = computed(() => variants.find(item => item.id === route.query.variant) ?? variants[0])
const isDevelopment = import.meta.env.DEV
function selectVariant(id: string) {
  router.replace({ query: { ...route.query, variant: id }, hash: route.hash })
}
const captcha = ref(''), captchaIndex = ref(0)
const captchaImage = computed(() => {
  const text = ['K7P4', 'M8N2', 'R3W6'][captchaIndex.value % 3]
  return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="116" height="44" viewBox="0 0 116 44"><rect width="116" height="44" rx="6" fill="#ecf1eb"/><path d="M0 30L116 12M0 9L116 36M14 0L95 44" stroke="#c3d0be" fill="none"/><text x="15" y="31" fill="#385e50" font-family="monospace" font-size="27" font-weight="600" letter-spacing="6" transform="rotate(-4 58 22)">${text}</text></svg>`)
})
function refreshCaptcha() { captchaIndex.value++; captcha.value = '' }

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

const previewState = computed(() => `${selectedRole.value.label} · ${mode.value === 'login' ? '登录' : '注册'} · ${loggedIn.value ? '已进入演示' : registered.value ? '注册完成' : '待提交'}`)
</script>

<template>
  <section class="prototype-shell" :class="`variant-${activeVariant.id.toLowerCase()}`" :style="{ '--role-accent': selectedRole.accent }">
    <component :is="activeVariant.component" :role-label="selectedRole.label">
      <template #roles>
        <div class="role-options" role="tablist" aria-label="选择登录端">
          <button v-for="(item, index) in roles" :key="item.id" class="role-option" :class="{ active: role === item.id }" type="button" role="tab" :aria-selected="role === item.id" @click="chooseRole(item.id)">
            <span class="role-number" aria-hidden="true">0{{ index + 1 }}</span>
            <span class="role-avatar" :style="{ '--avatar-accent': item.accent }">{{ item.initials }}</span>
            <span class="role-text"><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span>
            <span class="role-check" aria-hidden="true">{{ role === item.id ? '↗' : '→' }}</span>
          </button>
        </div>
      </template>

      <div v-if="loggedIn" class="success-panel" role="status">
        <span class="success-icon" aria-hidden="true">✓</span>
        <span class="section-kicker">DEMO SESSION ACTIVE</span>
        <h3>{{ selectedRole.label }}工作台已打开</h3>
        <p>欢迎回来，{{ identifier }}。你可以退出并切换其他角色继续体验。</p>
        <button class="ghost-button" type="button" @click="signOut">退出体验</button>
      </div>
      <form v-else class="auth-panel" :class="{ 'is-register': mode === 'register' }" @submit.prevent="submit">
        <div class="auth-tabs" role="tablist" aria-label="登录或注册">
          <button type="button" :class="{ active: mode === 'login' }" role="tab" :aria-selected="mode === 'login'" @click="switchMode('login')">登录<span class="tab-description">已有演示账户</span></button>
          <button type="button" :class="{ active: mode === 'register' }" role="tab" :aria-selected="mode === 'register'" @click="switchMode('register')">注册<span class="tab-description">创建新的账户</span></button>
        </div>
        <div class="form-heading">
          <span class="section-kicker">STEP 02 · {{ selectedRole.label }}</span>
          <h3>{{ mode === 'login' ? '欢迎回来' : '创建演示账户' }}</h3>
          <p>{{ mode === 'login' ? '登录你的账户，继续今天的健康服务。' : '填写账户信息，开启你的专属服务空间。' }}</p>
          <button class="demo-link" type="button" @click="fillDemo">填入演示账号 <span aria-hidden="true">↗</span></button>
        </div>
        <p v-if="status" role="status" class="status-message" :class="{ positive: registered || submitted }">{{ status }}</p>
        <div class="form-body">
          <div class="credentials">
            <div class="field"><label for="prototype-identifier">账号</label><input id="prototype-identifier" v-model="identifier" :placeholder="mode === 'login' ? '例如：patient-demo' : '设置一个演示账号'" autocomplete="username" /></div>
            <div v-if="mode === 'register'" class="field"><label for="prototype-display-name">显示名称</label><input id="prototype-display-name" v-model="displayName" placeholder="例如：林小满" /></div>
            <div class="field"><label for="prototype-password">密码</label><div class="password-wrap"><input id="prototype-password" v-model="password" :type="showPassword ? 'text' : 'password'" :placeholder="mode === 'login' ? '输入演示密码' : '至少 8 位'" autocomplete="new-password" /><button class="password-toggle" type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword">{{ showPassword ? '隐藏' : '显示' }}</button></div></div>
            <div v-if="mode === 'register'" class="field"><label for="prototype-confirm-password">确认密码</label><input id="prototype-confirm-password" v-model="confirmPassword" type="password" placeholder="再次输入密码" autocomplete="new-password" /></div>
          </div>
          <div v-if="mode === 'login'" class="captcha-panel">
            <div class="captcha-head"><label for="prototype-captcha">图形验证码</label><span>演示</span></div>
            <div class="captcha-line">
              <input id="prototype-captcha" v-model="captcha" placeholder="输入图中字符" autocomplete="off" aria-describedby="captcha-hint" />
              <button class="challenge-button" type="button" aria-label="换一张验证码" @click="refreshCaptcha"><img :src="captchaImage" alt="模拟图形验证码" width="116" height="44" /><span>换一张 ↻</span></button>
            </div>
            <p id="captcha-hint" class="captcha-hint">模拟图示，仅供体验布局，无需填写即可演示。</p>
          </div>
        </div>
        <div v-if="mode === 'login'" class="form-options"><label class="checkbox-label"><input v-model="remember" type="checkbox" />记住本次演示</label><button class="forgot-link" type="button" @click="status = '原型中仅展示视觉反馈，未接入找回密码流程。'">忘记密码？</button></div>
        <button class="primary-action" type="submit">{{ actionLabel }} <span aria-hidden="true">→</span></button>
        <p class="form-footnote">{{ mode === 'login' ? '登录后将展示对应角色的工作台入口。' : '这是一次性演示，注册信息不会写入数据库。' }}</p>
      </form>
    </component>
    <div class="prototype-caption"><span>方案 {{ activeVariant.id }} · {{ activeVariant.name }}</span><span aria-live="polite">当前状态：{{ previewState }}</span></div>
    <PrototypeSwitcher v-if="isDevelopment" :variants="variants" :current="activeVariant.id" :state="previewState" @select="selectVariant" />
  </section>
</template>

<style scoped>
.prototype-shell{--ink:#263e35;--muted:#829087;color:var(--ink);padding:0 0 86px;max-width:1120px;margin:0 auto;font-family:system-ui,"Microsoft YaHei",sans-serif}.prototype-shell *{box-sizing:border-box}.prototype-shell button,.prototype-shell input{font:inherit}.prototype-shell button{cursor:pointer;min-height:0}.prototype-shell button:focus-visible,.prototype-shell input:focus-visible{outline:3px solid #76aeb3;outline-offset:3px}.role-options{display:grid;gap:10px}.role-option{display:flex;align-items:center;gap:12px;padding:14px;text-align:left;border-radius:11px;border:1px solid #719d8b55;background:#ffffff05;color:#c4dbce;min-width:0}.role-option.active{background:#e7f0df;color:#274e3e;border-color:#e7f0df}.role-avatar{display:grid;place-items:center;width:33px;height:33px;flex:none;font-size:13px;border-radius:9px;background:#ffffff12;color:inherit}.active .role-avatar{background:#cddfbd;color:#335d3c}.role-text{display:grid;gap:5px;min-width:0}.role-text strong{font-size:14px;font-weight:650}.role-text small{font-size:11px;color:inherit;opacity:.72;line-height:1.4}.role-number{display:none}.role-check{margin-left:auto;font-size:17px;font-weight:400}.role-option:hover{border-color:#cadbc5}.auth-panel{width:100%;margin:0}.auth-tabs{display:flex;gap:5px;padding:4px;border-radius:9px;background:#f1f4ef;margin-bottom:28px}.auth-tabs>button{flex:1;padding:12px;border-radius:6px;background:none;color:#6d7d69;font-size:14px;font-weight:600;border:1px solid transparent}.auth-tabs>button.active{background:#fff;color:#314c3c;border-color:#e3e8df;box-shadow:0 2px 5px #274b3010}.tab-description{display:none}.form-heading{position:relative;margin-bottom:23px}.section-kicker{font-size:9px;font-weight:600;letter-spacing:.09em;color:#859581}.form-heading h3{font-size:28px;font-weight:600;letter-spacing:-.05em;margin:9px 0}.form-heading p{font-size:12px;line-height:1.8;color:#738171;margin:0}.demo-link{display:inline-flex;align-items:center;gap:12px;background:none;color:var(--role-accent);font-size:12px!important;font-weight:500;padding:0;margin-top:14px;min-height:24px!important}.form-body{display:grid;gap:18px}.credentials{display:grid;gap:17px}.auth-panel label{display:block;margin:0 0 8px;font-size:13px;font-weight:550;letter-spacing:.02em;color:#43574b}.auth-panel input{width:100%;border:1px solid #dce3d9;border-radius:8px;background:#fcfdfb;color:#304939;padding:12px 13px;min-width:0;height:44px;font-size:13px}.auth-panel input::placeholder{color:#a1aca1}.password-wrap{position:relative}.password-wrap input{padding-right:55px}.password-toggle{position:absolute;top:0;right:12px;height:44px;background:none;color:#839680;padding:0;font-size:11px!important;font-weight:400}.captcha-head{display:flex;align-items:center;justify-content:space-between}.captcha-head>span{font-size:8px;padding:2px 5px;color:#83917e;background:#eef3ea;border-radius:3px;margin-bottom:8px}.captcha-line{display:grid;grid-template-columns:minmax(0,1fr) 155px;gap:10px;align-items:start}.challenge-button{display:flex;gap:7px;align-items:center;justify-content:space-between;padding:0;background:none;border-radius:6px;color:#7e8e7b;font-size:9px!important;font-weight:400}.challenge-button img{width:106px;height:44px;display:block;border-radius:6px;flex:none}.challenge-button>span{width:40px;line-height:1.6}.captcha-hint{font-size:10px;line-height:1.7;color:#97a18f;margin:7px 0 0}.form-options{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-top:19px}.auth-panel .checkbox-label{display:flex;align-items:center;gap:7px;margin:0;font-size:12px;color:#718067;font-weight:400}.checkbox-label input{accent-color:var(--role-accent);width:13px;height:13px;padding:0;border-radius:3px}.forgot-link{background:none;color:#758a73;font-size:12px!important;padding:0;font-weight:400;min-height:26px!important}.primary-action{display:flex;align-items:center;justify-content:space-between;width:100%;border-radius:8px;padding:13px 16px;background:var(--role-accent);color:white;font-size:14px!important;font-weight:600;margin-top:23px;min-height:45px!important}.primary-action>span{font-size:18px;font-weight:400}.form-footnote{text-align:center;font-size:10px;line-height:1.8;color:#9aa492;margin:13px 0 0}.status-message{padding:10px 12px;border:1px solid #ebdfbe;border-radius:7px;background:#fcf8eb;color:#8c6d35;font-size:11px;line-height:1.7;margin:0 0 17px}.status-message.positive{border-color:#d5e7d6;background:#f2f8ed;color:#54764c}.success-panel{display:flex;flex-direction:column;align-items:flex-start;justify-content:center;min-height:470px;gap:16px}.success-icon{width:52px;height:52px;border:1px solid #cddfc8;border-radius:50%;display:grid;place-items:center;color:#4a7953;background:#f0f7ed;font-size:25px}.success-panel h3{font-size:25px;line-height:1.5;margin:0}.success-panel p{font-size:12px;line-height:1.9;color:#82917c;margin:0}.ghost-button{padding:10px 16px;background:#edf3e8;color:#476644;font-size:12px!important;margin-top:6px}.prototype-caption{display:flex;justify-content:space-between;gap:10px;font-size:9px;line-height:1.8;color:#929d95;padding:16px 4px 0;letter-spacing:.025em}
/* B puts mode selection in two explicit entry cards; the captcha image leads its row. */
.variant-b{--ink:#3b4436}.variant-b .role-options{gap:0}.variant-b .role-option{padding:19px 2px;border:0;border-bottom:1px solid #d9dbcc;border-radius:0;background:none;color:#8a9179;gap:16px}.variant-b .role-option.active{color:#3c5032;border-bottom:1px solid #a1b18c;background:none}.variant-b .role-number{display:block;font-size:10px;align-self:flex-start;padding-top:2px;opacity:.6}.variant-b .role-avatar{display:none}.variant-b .role-text strong{font-size:16px;font-weight:500}.variant-b .role-text small{font-size:10px;margin-top:1px}.variant-b .role-check{font-size:24px;color:#a1ab8a}.variant-b .active .role-check{color:#547043}.variant-b .auth-tabs{background:none;gap:11px;padding:0;border-radius:0;margin-bottom:26px}.variant-b .auth-tabs>button{border:1px solid #e2e3d8;text-align:left;padding:12px 16px;border-radius:3px;background:#fff;font-size:14px;color:#8f9687}.variant-b .auth-tabs>button.active{border-color:#758564;background:#f6f7f0;box-shadow:none;color:#455d34}.variant-b .tab-description{display:block;font-size:11px;line-height:1.6;color:#9aa18f;font-weight:400;margin-top:5px}.variant-b .form-heading h3{font-size:25px;font-weight:500}.variant-b .form-heading{margin-bottom:20px}.variant-b .form-heading>.section-kicker{display:none}.variant-b .auth-panel input{border-radius:3px;background:#fff;border-color:#dddfd3}.variant-b .auth-panel label{font-weight:450}.variant-b .primary-action{border-radius:3px;background:#536542}.variant-b .demo-link{color:#7b865e}.variant-b .captcha-line{grid-template-columns:140px minmax(0,1fr)}.variant-b .challenge-button{grid-row:1;grid-column:1;gap:4px}.variant-b .challenge-button img{width:100px}.variant-b #prototype-captcha{grid-column:2;grid-row:1}.variant-b .captcha-panel{border-top:1px solid #edeee6;padding-top:18px}.variant-b .captcha-head>span{background:#f1f2e8}.variant-b .success-panel{min-height:470px}.variant-b .section-kicker{color:#8d977d}
/* C separates credentials from the captcha; registration uses a two-column field grid. */
.variant-c{--ink:#304660}.variant-c .role-options{gap:8px}.variant-c .role-option{border:1px solid transparent;background:none;color:#8593a7;padding:12px 11px;gap:10px;border-radius:8px}.variant-c .role-option.active{border-color:#d8e1ed;background:#fff;color:#355073;box-shadow:0 3px 8px #324f7707}.variant-c .role-avatar{width:28px;height:28px;border-radius:7px;background:#e4eaf2;color:#8796ac;font-size:11px}.variant-c .active .role-avatar{color:#4c678a;background:#e7edf6}.variant-c .role-text strong{font-size:13px}.variant-c .role-text small{font-size:10px;line-height:1.6}.variant-c .role-check{font-size:13px}.variant-c .auth-tabs{background:none;padding:0;border-radius:0;border-bottom:1px solid #e6ebf2;gap:32px;justify-content:flex-start;margin-bottom:32px}.variant-c .auth-tabs>button{flex:none;padding:0 1px 14px;border:0;border-radius:0;color:#8c9aab;font-size:14px;position:relative}.variant-c .auth-tabs>button.active{background:none;box-shadow:none;color:#355477}.variant-c .auth-tabs>button.active:after{content:'';position:absolute;left:0;right:0;bottom:-1px;height:3px;background:#46688e;border-radius:2px}.variant-c .form-heading{margin-bottom:28px}.variant-c .form-heading h3{font-size:29px;color:#304966}.variant-c .form-heading p{color:#788b9f}.variant-c .section-kicker{color:#8b9aac}.variant-c .demo-link{color:#65809e}.variant-c .form-body{grid-template-columns:minmax(0,1.35fr) minmax(170px,1fr);gap:27px;align-items:stretch}.variant-c .auth-panel label{color:#62758e}.variant-c .auth-panel input{border-color:#dde4ed;background:#fff;color:#39516d;border-radius:6px}.variant-c .auth-panel input::placeholder{color:#a0adbc}.variant-c .credentials{align-content:start;gap:24px}.variant-c .captcha-panel{border-left:1px solid #e7ecf3;padding-left:24px;display:flex;flex-direction:column;min-width:0}.variant-c .captcha-head{margin-bottom:2px}.variant-c .captcha-head>span{background:#eef3f9;color:#8a9db5}.variant-c .captcha-line{grid-template-columns:1fr;gap:12px}.variant-c .challenge-button{grid-row:1;justify-content:space-between;border:1px solid #e1e7ed;border-radius:6px;background:#f7fafc;padding:10px 7px;gap:5px}.variant-c .challenge-button img{width:100px;height:40px}.variant-c .challenge-button>span{font-size:8px;width:30px;color:#8a9cb1}.variant-c .captcha-hint{color:#7f90a4;font-size:10px;line-height:1.8}.variant-c .form-options{margin-top:26px}.variant-c .checkbox-label{color:#8b9aad}.variant-c .forgot-link,.variant-c .password-toggle{color:#8195ac}.variant-c .primary-action{background:#385879;border-radius:6px;margin-top:25px}.variant-c .form-footnote{color:#99a6b7}.variant-c .is-register .form-body{grid-template-columns:1fr}.variant-c .is-register .credentials{grid-template-columns:1fr 1fr;gap:20px}.variant-c .success-panel{min-height:420px}.variant-c .success-panel h3{color:#375777}
@media(min-width:761px) and (max-width:1050px){.variant-c .form-body{grid-template-columns:1fr;gap:20px}.variant-c .captcha-panel{border-left:0;border-top:1px solid #e7ecf3;padding:18px 0 0}.variant-c .captcha-line{grid-template-columns:1fr 155px;align-items:center}.variant-c .challenge-button{grid-column:2;padding:0;border:0;background:none}.variant-c #prototype-captcha{grid-column:1;grid-row:1}}
@media(max-width:760px){.prototype-shell{padding:0 0 80px}.role-options{grid-template-columns:repeat(3,minmax(0,1fr));gap:7px}.role-option{padding:11px 6px;gap:7px;justify-content:center;flex-wrap:wrap}.role-avatar{width:27px;height:27px;font-size:11px}.role-text strong{font-size:12px}.role-text small,.role-check{display:none}.role-text{gap:0}.form-heading h3{font-size:26px}.auth-panel input{font-size:16px;height:46px}.auth-panel label{font-size:12px}.captcha-line{grid-template-columns:minmax(0,1fr) 145px;gap:8px}.challenge-button{gap:5px}.challenge-button img{width:100px}.challenge-button>span{font-size:9px}.form-options{margin-top:17px}.prototype-caption{flex-direction:column;gap:1px;font-size:8px}.variant-b .role-options{gap:10px}.variant-b .role-option{padding:12px 5px;justify-content:flex-start;gap:7px}.variant-b .role-number{font-size:8px}.variant-b .role-text strong{font-size:12px}.variant-b .role-check{display:none}.variant-b .role-text small{display:none}.variant-b .captcha-line{grid-template-columns:140px minmax(0,1fr)}.variant-c .role-option{padding:10px 6px;gap:6px}.variant-c .role-avatar{width:23px;height:23px;font-size:10px}.variant-c .role-text strong{font-size:12px}.variant-c .role-text small,.variant-c .role-check{display:none}.variant-c .auth-tabs{margin-bottom:26px}.variant-c .form-body{grid-template-columns:1fr;gap:20px}.variant-c .credentials{gap:18px}.variant-c .captcha-panel{border-left:0;border-top:1px solid #e7ecf3;padding:18px 0 0}.variant-c .captcha-line{grid-template-columns:minmax(0,1fr) 145px}.variant-c .challenge-button{grid-column:2;padding:0;border:0;background:none}.variant-c #prototype-captcha{grid-column:1;grid-row:1}.variant-c .is-register .credentials{grid-template-columns:1fr}.success-panel,.variant-b .success-panel,.variant-c .success-panel{min-height:330px}.success-panel h3{font-size:23px}.primary-action{font-size:13px!important}}
</style>
