<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from './PrototypeIcon.vue'
import { departments, type HomeAction } from './data'
import { departmentDoctors, departmentProfiles, type DepartmentMode } from './department-data'

const emit = defineEmits<{ action: [action: HomeAction]; back: []; select: [department: string, doctor?: string] }>()
const selectedName = ref(departments[0]!.name)
const mode = ref<DepartmentMode>('ONLINE')
const selected = computed(() => departments.find(item => item.name === selectedName.value) ?? departments[0]!)
const profile = computed(() => departmentProfiles[selected.value.name]!)
const doctors = computed(() => departmentDoctors[selected.value.name] ?? [])
const todayDoctors = computed(() => doctors.value.filter(item => item.today && item.mode === mode.value))
const otherDoctors = computed(() => doctors.value.filter(item => !item.today))
function choose(name: string) { selectedName.value = name; mode.value = 'ONLINE' }
function register(doctor?: string) { emit('select', selected.value.name, doctor); emit('action', 'registration') }
</script>

<template>
  <section class="ph-dept-v-a" data-testid="department-page" aria-labelledby="department-page-title">
    <header class="ph-dept-a-intro"><div><span class="ph-eyebrow">DEPARTMENT DIRECTORY · A</span><h1 id="department-page-title">科室信息</h1><p>先了解服务方向，再选择适合自己的医生与就诊方式。</p></div><div class="ph-dept-a-context"><span class="ph-dept-a-context-icon"><Icon name="grid" /></span><span><strong>{{ departments.length }} 个科室</strong><small>今日门诊服务开放中</small></span></div></header>
    <div class="ph-dept-a-layout">
      <aside class="ph-dept-a-sidebar" aria-label="科室列表"><div class="ph-dept-a-sidebar-title"><span>全部科室</span><small>Department</small></div><button v-for="item in departments" :key="item.name" :class="{ active: selectedName === item.name }" @click="choose(item.name)"><span :class="['ph-dept-a-icon', selectedName === item.name ? 'active' : '']"><Icon :name="item.icon" /></span><span><strong>{{ item.name }}</strong><small>{{ item.detail }}</small></span><Icon name="chevron" /></button></aside>
      <main class="ph-dept-a-main">
        <section class="ph-dept-a-profile"><div class="ph-dept-a-profile-heading"><span class="ph-dept-a-profile-icon"><Icon :name="selected.icon" /></span><div><span class="ph-eyebrow">SELECTED DEPARTMENT</span><h2>{{ selected.name }}</h2><p>{{ selected.detail }}</p></div></div><p class="ph-dept-a-description">{{ profile.description }}</p><div class="ph-dept-a-tags"><span v-for="item in profile.specialties" :key="item">{{ item }}</span></div><div class="ph-dept-a-facts"><span><strong>{{ selected.count }}</strong><small>今日出诊医生</small></span><span><strong>线上 · 线下</strong><small>可选服务方式</small></span><span><strong>按时段</strong><small>预约与签到</small></span></div><p class="ph-dept-a-hint"><Icon name="shield" /> {{ profile.hint }}</p></section>
        <section class="ph-dept-a-today"><div class="ph-dept-a-section-head"><div><span class="ph-eyebrow">TODAY'S CLINIC</span><h2>今日出诊</h2></div><div class="ph-dept-mode-switch" role="tablist" aria-label="今日出诊方式"><button role="tab" :aria-selected="mode === 'ONLINE'" @click="mode = 'ONLINE'"><Icon name="chat" /> 线上</button><button role="tab" :aria-selected="mode === 'OFFLINE'" @click="mode = 'OFFLINE'"><Icon name="hospital" /> 线下</button></div></div><div v-if="todayDoctors.length" class="ph-dept-doctor-grid"> <article v-for="doctor in todayDoctors" :key="doctor.id" class="ph-dept-doctor-card"><div class="ph-dept-doctor-top"><span class="ph-dept-doctor-avatar">{{ doctor.name[0] }}</span><span class="ph-dept-doctor-status">可挂号</span></div><h3>{{ doctor.name }} <small>{{ doctor.title }}</small></h3><p>{{ doctor.specialty }}</p><dl><div><dt>出诊时间</dt><dd>{{ doctor.time }}</dd></div><div><dt>就诊方式</dt><dd>{{ doctor.place }}</dd></div><div><dt>剩余号源</dt><dd>{{ doctor.slots }} 个</dd></div></dl><button class="ph-primary ph-full" @click="register(doctor.name)">立即挂号 <Icon name="arrow" /></button></article></div><div v-else class="ph-dept-empty"><Icon name="calendar" /><p>当前方式暂无今日出诊医生</p><small>可以切换线上/线下，或查看下方医生团队。</small></div></section>
        <section class="ph-dept-a-team"><div class="ph-dept-a-section-head"><div><span class="ph-eyebrow">MEDICAL TEAM</span><h2>医生列表</h2></div><button class="ph-text-button" @click="emit('action', 'doctors')">查看完整列表 <Icon name="arrow" /></button></div><div class="ph-dept-team-grid"><article v-for="doctor in otherDoctors" :key="doctor.id" class="ph-dept-doctor-card secondary"><div class="ph-dept-doctor-top"><span class="ph-dept-doctor-avatar muted">{{ doctor.name[0] }}</span><span class="ph-dept-next-time">{{ doctor.time }}</span></div><h3>{{ doctor.name }} <small>{{ doctor.title }}</small></h3><p>{{ doctor.specialty }}</p><div class="ph-dept-secondary-meta"><span>{{ doctor.place }}</span><span>预约后确认号源</span></div><button class="ph-dept-appointment" @click="register(doctor.name)">预约 <Icon name="arrow" /></button></article></div></section>
      </main>
    </div>
  </section>
</template>
