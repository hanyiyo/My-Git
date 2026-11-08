<script setup lang="ts">
defineProps<{ roleLabel: string }>()
</script>

<template>
  <div class="layout-a" data-testid="variant-layout">
    <aside class="a-aside">
      <div class="a-brand"><span class="a-mark" aria-hidden="true">+</span><span>校医院健康服务<small>CAMPUS HEALTH</small></span></div>
      <div class="a-intro"><span class="a-overline">CARE BEGINS HERE</span><h2>每一次就诊，<br>从这里开始。</h2><p>让预约更从容，让沟通更简单。<br>选择你的身份，进入专属服务空间。</p></div>
      <div class="a-roles"><p class="a-section">选择你的身份 <span>01 / 02</span></p><slot name="roles" /></div>
      <p class="a-note"><span aria-hidden="true">◈</span> 一个入口，连接患者、医生与管理员</p>
    </aside>
    <div class="a-main"><div class="a-context"><span class="a-dot" />{{ roleLabel }}入口 <span class="a-demo">交互原型</span></div><slot /><p class="a-footer">温暖的服务，从清晰的第一步开始。</p></div>
  </div>
</template>

<style scoped>
.layout-a{display:grid;grid-template-columns:46% 54%;min-height:720px;overflow:hidden;border:1px solid #dbe5df;border-radius:26px;background:#fff;box-shadow:0 20px 70px #183c3410}.a-aside{padding:38px 42px 28px;display:flex;flex-direction:column;background:#174c43;color:#f2f6ef;position:relative}.a-brand{display:flex;gap:12px;align-items:center;font-size:14px;font-weight:650;letter-spacing:.06em}.a-mark{width:36px;height:36px;display:grid;place-items:center;border:1px solid #9ebfb155;border-radius:11px;color:#cbe4c0;font-size:31px;font-weight:300}.a-brand small{display:block;font-size:8px;letter-spacing:.18em;margin-top:5px;color:#aec9be}.a-intro{padding:55px 0 34px}.a-overline{font-size:9px;letter-spacing:.22em;color:#afcbb8}.a-intro h2{font-size:39px;line-height:1.4;font-weight:550;letter-spacing:-.05em;margin:17px 0 20px}.a-intro p{font-size:12px;line-height:1.9;color:#bed5ca;margin:0}.a-section{display:flex;justify-content:space-between;align-items:center;font-size:11px;color:#ccded5;margin:0 0 13px}.a-section span{font-size:9px;letter-spacing:.1em;color:#8eafa0}.a-note{font-size:10px;color:#a4c4b4;margin:25px 0 0;line-height:1.7}.a-note span{margin-right:5px}.a-main{padding:38px 52px 24px;display:flex;flex-direction:column;min-width:0}.a-context{display:flex;align-items:center;gap:7px;font-size:11px;color:#698277;margin-bottom:35px}.a-dot{width:6px;height:6px;border-radius:50%;background:#4c856b}.a-demo{margin-left:auto;font-size:9px;padding:4px 8px;background:#f1f5ef;border:1px solid #e1e9df;border-radius:5px;color:#74876c}.a-footer{margin:22px 0 0;padding-top:16px;border-top:1px solid #edf1ed;font-size:10px;color:#8b9b91;text-align:center}
@media(max-width:760px){.layout-a{grid-template-columns:1fr;min-height:0;border-radius:18px}.a-aside{padding:25px}.a-intro{padding:25px 0 20px}.a-intro h2{font-size:30px;margin:10px 0}.a-intro p,.a-note{display:none}.a-main{padding:25px}.a-context{margin-bottom:23px}.a-section{margin-bottom:10px}}
</style>
