<script setup lang="ts">
import { computed } from 'vue'
import type { HomeAction } from './data'
import DepartmentVariantA from './DepartmentVariantA.vue'
import DepartmentVariantB from './DepartmentVariantB.vue'
import DepartmentVariantC from './DepartmentVariantC.vue'

const props = defineProps<{ variant: string }>()
const emit = defineEmits<{ action: [action: HomeAction]; back: []; select: [department: string, doctor?: string] }>()
const variantComponent = computed(() => props.variant === 'B' ? DepartmentVariantB : props.variant === 'C' ? DepartmentVariantC : DepartmentVariantA)
function selectDepartment(department: string, doctor?: string) { emit('select', department, doctor) }
</script>

<template><component :is="variantComponent" @action="emit('action', $event)" @back="emit('back')" @select="selectDepartment" /></template>
