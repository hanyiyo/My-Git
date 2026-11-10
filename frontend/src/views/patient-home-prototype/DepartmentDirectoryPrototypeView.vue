<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from './PrototypeIcon.vue'
import { departments, type HomeAction } from './data'

const emit = defineEmits<{ action: [action: HomeAction]; back: []; select: [department: string] }>()
const selectedName = ref(departments[0]!.name)
const selectedDepartment = computed(() => departments.find(item => item.name === selectedName.value) ?? departments[0]!)
const descriptions: Record<string, string> = {
  '全科医学科': '覆盖常见不适、健康咨询与初次就诊导诊，适合还不确定挂什么科的情况。',
  '内科': '关注呼吸、消化和慢病管理等常见内科问题，提供线上与线下就诊安排。',
  '口腔科': '提供牙齿保健、口腔检查和常见口腔问题咨询，按预约时段到院就诊。',
  '皮肤科': '针对常见皮肤问题提供专业咨询，具体诊疗与检查以医生判断为准。',
}

function chooseDepartment(name: string) { selectedName.value = name }
</script>

<template>
  <section class="ph-department-page" data-testid="department-page" aria-labelledby="department-page-title">
    <div class="ph-directory-breadcrumb"><button class="ph-text-button" @click="emit('back')"><Icon name="arrow" /> 返回患者首页</button><span>/</span><strong>科室信息</strong></div>
    <header class="ph-directory-hero">
      <div><span class="ph-eyebrow">FIND YOUR CARE</span><h1 id="department-page-title">科室信息</h1><p>先了解服务方向，再选择适合自己的医生与就诊方式。</p></div>
      <button class="ph-directory-ai" @click="emit('action', 'ai')"><span class="ph-icon-tile teal"><Icon name="sparkles" /></span><span><strong>不确定挂什么科？</strong><small>让 AI 助手帮你梳理方向</small></span><Icon name="arrow" /></button>
    </header>
    <div class="ph-directory-layout">
      <section class="ph-directory-list" aria-label="科室列表"><div class="ph-section-title"><h2>全部科室 <span>{{ departments.length }} 个服务方向</span></h2></div><div class="ph-directory-cards"><button v-for="item in departments" :key="item.name" class="ph-directory-card" :class="{ active: selectedName === item.name }" @click="chooseDepartment(item.name)"><span :class="['ph-icon-tile', item.name === selectedDepartment.name ? 'teal' : 'blue']"><Icon :name="item.icon" /></span><span><strong>{{ item.name }}</strong><small>{{ item.detail }}</small></span><span class="ph-directory-count">{{ item.count }} 位医生<Icon name="chevron" /></span></button></div></section>
      <aside class="ph-directory-detail" aria-label="科室详情"><span class="ph-eyebrow">DEPARTMENT PROFILE</span><div class="ph-directory-detail-icon"><Icon :name="selectedDepartment.icon" /></div><h2>{{ selectedDepartment.name }}</h2><p>{{ descriptions[selectedDepartment.name] }}</p><dl><div><dt>今日出诊</dt><dd>{{ selectedDepartment.count }} 位医生</dd></div><div><dt>服务方式</dt><dd>线上 · 线下</dd></div><div><dt>预约提示</dt><dd>按时段预约，提前签到</dd></div></dl><button class="ph-primary ph-full" @click="emit('select', selectedDepartment.name); emit('action', 'registration')">立即挂号 <Icon name="arrow" /></button><button class="ph-directory-doctors" @click="emit('action', 'doctors')">查看医生团队 <Icon name="arrow" /></button></aside>
    </div>
    <p class="ph-directory-note"><Icon name="shield" /> 科室与医生信息均为当前原型演示数据，实际号源以系统发布为准。</p>
  </section>
</template>
