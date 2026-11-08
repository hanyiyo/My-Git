<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'

const props = defineProps<{
  variants: ReadonlyArray<{ id: string; name: string }>
  current: string
  state: string
}>()
const emit = defineEmits<{ select: [id: string] }>()
function cycle(direction: number) {
  const index = props.variants.findIndex(item => item.id === props.current)
  emit('select', props.variants[(index + direction + props.variants.length) % props.variants.length]!.id)
}
function onKey(event: KeyboardEvent) {
  if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey || event.defaultPrevented) return
  if (event.target instanceof Element && event.target.closest('input, textarea, select, [contenteditable]:not([contenteditable="false"])')) return
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
  event.preventDefault()
  cycle(event.key === 'ArrowLeft' ? -1 : 1)
}
onMounted(() => window.addEventListener('keydown', onKey))
onUnmounted(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <nav class="prototype-switcher" aria-label="原型方案切换">
    <span class="switcher-caption">布局实验 <small>{{ state }}</small></span>
    <button class="arrow" type="button" aria-label="上一方案" @click="cycle(-1)">←</button>
    <div class="variant-choices">
      <button v-for="item in variants" :key="item.id" type="button" :aria-pressed="current === item.id" :aria-label="`方案 ${item.id}：${item.name}`" @click="emit('select', item.id)">
        <span>{{ item.id }}</span><strong>{{ item.name }}</strong>
      </button>
    </div>
    <button class="arrow" type="button" aria-label="下一方案" @click="cycle(1)">→</button>
  </nav>
</template>

<style scoped>
.prototype-switcher{position:fixed;bottom:20px;left:50%;transform:translateX(-50%);z-index:50;display:flex;align-items:center;gap:8px;padding:9px 12px;border:1px solid #ffffff35;border-radius:19px;background:#172c31;color:#fff;box-shadow:0 10px 35px #122a3130;font-family:inherit;max-width:calc(100vw - 24px)}
.switcher-caption{padding:0 10px;font-size:11px;white-space:nowrap;color:#c6d8d6;line-height:1.7}.switcher-caption small{display:block;font-size:10px;color:#9eb5b3}.variant-choices{display:flex;gap:5px}.prototype-switcher button{display:flex;align-items:center;justify-content:center;gap:7px;min-height:38px;border-radius:10px;padding:8px 12px;background:transparent;color:#cad8d7;white-space:nowrap;font-size:12px;font-weight:500}.variant-choices button[aria-pressed=true]{background:#e6f3ea;color:#173d34}.variant-choices span{font-family:monospace;opacity:.7}.variant-choices strong{font-weight:600}.prototype-switcher .arrow{padding:8px;width:33px;flex:none}.prototype-switcher button:hover{background:#ffffff16}.variant-choices button[aria-pressed=true]:hover{background:#fff}.prototype-switcher button:focus-visible{outline:3px solid #9cdbbd;outline-offset:2px}
@media(max-width:650px){.prototype-switcher{bottom:12px;padding:7px;gap:3px}.switcher-caption{display:none}.variant-choices button{padding:9px 10px;font-size:11px;gap:4px}.prototype-switcher .arrow{width:28px;padding:4px}}
</style>
