<script setup lang="ts">
defineProps<{ roleLabel: string }>()
</script>

<template>
  <div class="layout-b" data-testid="variant-layout">
    <div class="b-main">
      <div class="b-brand"><span aria-hidden="true">+</span> 校医院健康服务 <small>ACCOUNT ACCESS</small></div>
      <div class="b-form"><slot /></div>
      <div class="b-footer"><span>校医院 · 三端协作平台</span><span>纯前端交互原型</span></div>
    </div>
    <aside class="b-aside">
      <span class="b-eyebrow">为不同的你，准备不同的服务</span>
      <h2>各就其位，<br><em>安心协作。</em></h2>
      <p class="b-copy">先在右侧找到你的身份，<br>再用左侧表单开启今天的工作与生活。</p>
      <div class="b-roles"><span class="b-label">你希望以什么身份进入？</span><slot name="roles" /></div>
      <div class="b-current"><span>当前入口</span><strong>{{ roleLabel }}</strong><span aria-hidden="true">↖</span></div>
      <p class="b-note">身份切换后，可继续使用同一组表单。</p>
    </aside>
  </div>
</template>

<style scoped>
.layout-b{display:grid;grid-template-columns:57% 43%;min-height:720px;border:1px solid #dddcd3;border-radius:6px;overflow:hidden;background:#fff;box-shadow:0 18px 60px #3e39250a}.b-main{padding:34px 46px 25px;display:flex;flex-direction:column;min-width:0}.b-brand{display:flex;align-items:center;gap:7px;font-size:12px;font-weight:650;letter-spacing:.02em}.b-brand>span{font-size:25px;color:#335e66;font-weight:400}.b-brand small{margin-left:auto;font-size:8px;letter-spacing:.15em;color:#8d9794}.b-form{padding:42px 20px 5px;flex:1}.b-footer{display:flex;justify-content:space-between;border-top:1px solid #efeee8;padding-top:17px;font-size:9px;color:#8a918b}.b-aside{background:#efeee4;padding:59px 38px 30px;color:#343f37;display:flex;flex-direction:column}.b-eyebrow{font-size:10px;letter-spacing:.08em;color:#6e7762}.b-aside h2{font-size:41px;line-height:1.4;font-weight:500;letter-spacing:-.06em;margin:26px 0 20px}.b-aside h2 em{font-style:normal;color:#727b47}.b-copy{font-size:12px;line-height:1.9;margin:0;color:#828875}.b-roles{margin-top:42px}.b-label{display:block;font-size:11px;color:#717960;margin-bottom:16px}.b-current{display:flex;align-items:center;gap:15px;border-top:1px solid #d4d6c5;padding-top:22px;margin-top:auto;font-size:10px;color:#7c836b}.b-current strong{font-size:15px;color:#4b5a39;font-weight:550}.b-current>span:last-child{margin-left:auto;font-size:22px;color:#7c8758}.b-note{font-size:10px;color:#868d79;margin:12px 0 0}
@media(max-width:760px){.layout-b{grid-template-columns:1fr;border-radius:14px}.b-aside{order:-1;padding:25px}.b-aside h2{font-size:28px;margin:12px 0;line-height:1.3}.b-aside h2 br,.b-copy,.b-note,.b-current{display:none}.b-roles{margin-top:20px}.b-main{padding:25px}.b-brand small{font-size:7px}.b-form{padding:25px 0 5px}.b-footer{margin-top:15px}.b-eyebrow{font-size:9px}}
</style>
