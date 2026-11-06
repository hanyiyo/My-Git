<template>
  <form @submit.prevent="login">
    <input v-model="identifier" autocomplete="username" placeholder="账号" required />
    <input v-model="password" type="password" autocomplete="current-password" placeholder="密码" required />
    <img v-if="captchaImage" :src="captchaImage" alt="图形验证码" />
    <input v-model="captcha" placeholder="图形验证码" required />
    <button type="button" @click="refreshCaptcha">刷新验证码</button>
    <button type="submit">登录</button>
    <p v-if="message" role="status">{{ message }}</p>
  </form>
</template>
<script setup>
import { onMounted, ref } from "vue";
const identifier = ref(""); const password = ref(""); const captcha = ref("");
const challengeId = ref(""); const captchaImage = ref(""); const message = ref("");
const browserId = localStorage.getItem("browserId") || crypto.randomUUID(); localStorage.setItem("browserId", browserId);
async function refreshCaptcha() { const response = await fetch("/api/v1/auth/captcha", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ browserId }) }); const data = await response.json(); if (response.ok) { challengeId.value = data.challengeId; captchaImage.value = data.image; captcha.value = ""; } else message.value = data.error?.message ?? "验证码暂不可用"; }
async function login() { const response = await fetch("/api/v1/auth/login", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ identifier: identifier.value, password: password.value, captcha: captcha.value, challengeId: challengeId.value, browserId }) }); const data = await response.json(); message.value = response.ok ? "登录成功" : (data.error?.message ?? "登录失败"); if (response.ok) { sessionStorage.setItem("accessToken", data.accessToken); sessionStorage.setItem("refreshToken", data.refreshToken); } }
onMounted(refreshCaptcha);
</script>
