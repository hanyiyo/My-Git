<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { useSession } from '../session'
const router = useRouter(), session = useSession()
const identifier = ref(''), password = ref(''), captcha = ref(''), challengeId = ref(''), image = ref('')
const role = ref('PATIENT'), register = ref(false), message = ref(''), busy = ref(false)
async function refresh() {
  try { const challenge = await api<{ challengeId: string; image: string }>('/auth/captcha'); challengeId.value = challenge.challengeId; image.value = challenge.image; captcha.value = ''; return true }
  catch (error) { image.value = ''; message.value = error instanceof Error ? error.message : '验证码暂不可用'; return false }
}
async function submit() {
  busy.value = true; message.value = ''
  try {
    if (register.value) {
      await api('/auth/register', 'POST', { identifier: identifier.value, password: password.value, role: role.value })
      register.value = false
      const ready = await refresh()
      if (ready) message.value = '注册成功，请输入图形验证码后登录'
    } else {
      await session.login(identifier.value, password.value, challengeId.value, captcha.value)
      await router.push('/' + session.account!.roles[0]!.toLowerCase())
    }
  } catch (error) { message.value = error instanceof Error ? error.message : '操作失败'; if (!register.value) await refresh() }
  finally { busy.value = false }
}
onMounted(refresh)
</script>
<template>
  <section class="card"><h2>{{ register ? '创建账户' : '账户登录' }}</h2><p class="hint">患者、医生和管理员使用同一安全登录入口。</p>
    <p v-if="message" role="status" class="notice">{{ message }}</p>
    <form @submit.prevent="submit">
      <label for="identifier">账号</label><input id="identifier" v-model="identifier" autocomplete="username" required maxlength="190">
      <label for="password">密码</label><input id="password" v-model="password" type="password" :autocomplete="register ? 'new-password' : 'current-password'" required minlength="12" maxlength="72"><small>12–72 位，仅使用虚构演示账户。</small>
      <template v-if="register"><label for="role">注册身份</label><select id="role" v-model="role"><option value="PATIENT">患者</option><option value="DOCTOR">医生（资质待审核）</option></select></template>
      <template v-else><label for="captcha">图形验证码</label><div class="captcha"><input id="captcha" v-model="captcha" autocomplete="off" required maxlength="8"><img v-if="image" :src="image" alt="图形验证码"><button class="secondary" type="button" @click="refresh">换一张</button></div></template>
      <button :disabled="busy" type="submit">{{ busy ? '处理中…' : register ? '注册' : '登录' }}</button>
    </form><button class="text" type="button" @click="register = !register; message = ''">{{ register ? '已有账户，返回登录' : '患者或医生注册' }}</button>
  </section>
</template>
