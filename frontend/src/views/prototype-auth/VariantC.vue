<script setup lang="ts">
defineProps<{ roleLabel: string }>()
</script>

<template>
  <div class="layout-c" data-testid="variant-layout">
    <aside class="c-sidebar">
      <div class="c-brand"><span class="c-symbol" aria-hidden="true">+</span><span>校医院<small>健康服务平台</small></span></div>
      <div class="c-navigation"><p>服务空间</p><slot name="roles" /></div>
      <div class="c-bottom"><span class="c-signal" />三端 · 同一服务入口<small>纯前端交互原型</small></div>
    </aside>
    <div class="c-workspace">
      <div class="c-topline"><span>账户中心 <span class="c-slash">/</span> {{ roleLabel }}</span><span class="c-live">演示环境</span></div>
      <div class="c-form"><slot /></div>
      <div class="c-footer"><span>01 选择身份</span><span>02 填写账户</span><span>03 进入服务</span></div>
    </div>
  </div>
</template>

<style scoped>
.layout-c{display:grid;grid-template-columns:235px minmax(0,1fr);min-height:720px;background:#fff;border:1px solid #dbe1ea;border-radius:18px;overflow:hidden;box-shadow:0 20px 65px #273c5c0b}.c-sidebar{display:flex;flex-direction:column;padding:35px 20px 28px;background:#f2f5fa;border-right:1px solid #e0e6ef}.c-brand{display:flex;align-items:center;gap:11px;padding:0 11px;font-size:19px;font-weight:650;letter-spacing:.04em;color:#2c415d}.c-symbol{display:grid;place-items:center;width:39px;height:39px;border-radius:11px;background:#334f77;color:white;font-size:33px;font-weight:300}.c-brand small{display:block;font-size:9px;font-weight:400;letter-spacing:.08em;margin-top:4px;color:#77899f}.c-navigation{margin-top:56px}.c-navigation>p{font-size:10px;color:#8190a4;margin:0 14px 15px}.c-bottom{margin-top:auto;padding:24px 12px 0;font-size:9px;color:#8090a4;line-height:1.8}.c-bottom small{display:block;font-size:9px;margin:6px 0 0 12px;color:#95a0b0}.c-signal{display:inline-block;width:5px;height:5px;background:#678a76;border-radius:50%;margin-right:7px}.c-workspace{padding:30px 36px 26px;display:flex;flex-direction:column;min-width:0}.c-topline{display:flex;align-items:center;justify-content:space-between;font-size:10px;color:#7a8a9d;padding-bottom:25px;border-bottom:1px solid #ecf0f5}.c-slash{padding:0 10px;color:#bfccd7}.c-live{font-size:9px;border:1px solid #dfe6ed;border-radius:4px;padding:4px 7px;color:#8a9aab}.c-form{padding:37px 0 25px;flex:1}.c-footer{display:flex;justify-content:space-between;padding-top:17px;border-top:1px solid #ecf0f5;font-size:9px;color:#8b99ab;letter-spacing:.04em}
@media(max-width:760px){.layout-c{grid-template-columns:1fr;min-height:0}.c-sidebar{padding:23px 20px;border-right:0;border-bottom:1px solid #e0e6ef}.c-brand{padding:0;font-size:16px}.c-navigation{margin-top:23px}.c-navigation>p,.c-bottom{display:none}.c-workspace{padding:23px}.c-topline{padding-bottom:18px}.c-form{padding:25px 0 15px}.c-footer{font-size:8px}}
</style>
