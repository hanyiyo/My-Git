<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Icon from './PrototypeIcon.vue'
import { addPrototypeDays, formatPrototypeDate, parsePrototypeDate, prototypeDateEnd, prototypeToday } from './prototype-date-options'

const props = withDefaults(defineProps<{
  modelValue: string
  label: string
  minDate?: string
  maxDate?: string
}>(), {
  minDate: prototypeToday,
  maxDate: prototypeDateEnd,
})
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const open = ref(false)
const cursor = ref(parsePrototypeDate(props.modelValue || props.minDate))

const monthTitle = computed(() => `${cursor.value.getFullYear()}年${cursor.value.getMonth() + 1}月`)
const firstDay = computed(() => new Date(cursor.value.getFullYear(), cursor.value.getMonth(), 1))
const calendarDays = computed(() => {
  const total = new Date(cursor.value.getFullYear(), cursor.value.getMonth() + 1, 0).getDate()
  const blanks = Array.from({ length: firstDay.value.getDay() }, () => null)
  return [...blanks, ...Array.from({ length: total }, (_, index) => {
    const date = new Date(cursor.value.getFullYear(), cursor.value.getMonth(), index + 1)
    return { value: formatPrototypeDate(date), day: index + 1 }
  })]
})
const selectedOption = computed(() => {
  const date = parsePrototypeDate(props.modelValue)
  const day = date.getDate()
  const month = date.getMonth() + 1
  const isToday = props.modelValue === props.minDate
  return `${isToday ? '今天 · ' : ''}${month}月${day}日`
})
const canGoPrevious = computed(() => monthKey(cursor.value) > monthKey(parsePrototypeDate(props.minDate)))
const canGoNext = computed(() => monthKey(cursor.value) < monthKey(parsePrototypeDate(props.maxDate)))

function monthKey(date: Date): number {
  return date.getFullYear() * 12 + date.getMonth()
}
function shiftMonth(offset: number) {
  const next = new Date(cursor.value.getFullYear(), cursor.value.getMonth() + offset, 1)
  cursor.value = next
}
function isDisabled(value: string) {
  return value < props.minDate || value > props.maxDate
}
function isSelected(value: string) {
  return value === props.modelValue
}
function select(value: string) {
  if (isDisabled(value)) return
  emit('update:modelValue', value)
  open.value = false
}
function toggle() {
  if (!open.value) cursor.value = parsePrototypeDate(props.modelValue || props.minDate)
  open.value = !open.value
}
function closeOnEscape() {
  open.value = false
}

watch(() => props.modelValue, value => {
  if (!value) emit('update:modelValue', addPrototypeDays(props.minDate, 0))
})
</script>

<template>
  <div class="ph-date-picker" data-testid="prototype-date-picker">
    <span class="ph-date-picker-label">{{ label }}</span>
    <button class="ph-date-picker-trigger" type="button" :aria-label="label" aria-haspopup="dialog" :aria-expanded="open" @click="toggle">
      <Icon name="calendar" /><span>{{ selectedOption }}</span><Icon name="chevron" class="ph-date-picker-chevron" />
    </button>
    <div v-if="open" class="ph-date-picker-popover" role="dialog" :aria-label="`${label}日历`" @keydown.esc="closeOnEscape">
      <header class="ph-date-picker-header"><button type="button" aria-label="上个月" :disabled="!canGoPrevious" @click="shiftMonth(-1)"><Icon name="chevron" /></button><strong>{{ monthTitle }}</strong><button type="button" aria-label="下个月" :disabled="!canGoNext" @click="shiftMonth(1)"><Icon name="chevron" /></button></header>
      <div class="ph-date-picker-weekdays" aria-hidden="true"><span v-for="day in ['日', '一', '二', '三', '四', '五', '六']" :key="day">{{ day }}</span></div>
      <div class="ph-date-picker-days" role="grid">
        <span v-for="(day, index) in calendarDays" :key="day?.value ?? `blank-${index}`" class="ph-date-picker-cell">
          <button v-if="day" type="button" :disabled="isDisabled(day.value)" :aria-label="day.value" :aria-selected="isSelected(day.value)" :class="{ selected: isSelected(day.value), disabled: isDisabled(day.value) }" @click="select(day.value)">{{ day.day }}</button>
        </span>
      </div>
    </div>
  </div>
</template>
