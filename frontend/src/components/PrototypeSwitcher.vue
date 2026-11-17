<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
const props = defineProps<{ variants: { key: string; name: string }[]; state: string }>()
const route = useRoute(), router = useRouter()
const development = import.meta.env.DEV
const current = computed(() => props.variants.findIndex(item => item.key === route.query.variant))
const index = computed(() => Math.max(0, current.value))
function change(step: number) { const item = props.variants[(index.value + step + props.variants.length) % props.variants.length]!; void router.replace({ query: { ...route.query, variant: item.key } }) }
function keydown(event: KeyboardEvent) {
  if (event.altKey || event.ctrlKey || event.metaKey || (event.target instanceof Element && event.target.closest('input, textarea, select, [contenteditable], dialog'))) return
  if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') { event.preventDefault(); change(event.key === 'ArrowLeft' ? -1 : 1) }
}
onMounted(() => window.addEventListener('keydown', keydown))
onUnmounted(() => window.removeEventListener('keydown', keydown))
</script>
<template><aside v-if="development" class="prototype-switcher" aria-label="原型方案切换"><div class="prototype-switcher-row"><span class="prototype-mark">PROTOTYPE</span><button type="button" aria-label="上一个方案" @click="change(-1)">←</button><strong aria-live="polite">{{ variants[index]!.key }} · {{ variants[index]!.name }}</strong><button type="button" aria-label="下一个方案" @click="change(1)">→</button><span class="prototype-index">{{ index + 1 }} / {{ variants.length }}</span></div><output aria-label="原型当前状态">{{ state }}</output></aside></template>
<style scoped>
.prototype-switcher{position:fixed;bottom:14px;left:50%;transform:translateX(-50%);z-index:20;padding:9px 14px;background:#182c43;color:#fff;border:1px solid #ffffff24;border-radius:15px;box-shadow:0 8px 28px #132b4438;max-width:calc(100vw - 24px);white-space:nowrap;font:12px system-ui,"Microsoft YaHei",sans-serif}.prototype-switcher-row{display:flex;align-items:center;gap:12px}.prototype-switcher button{background:#ffffff15;border:1px solid #ffffff30;color:#fff;border-radius:7px;min-height:28px;padding:2px 9px;font-size:17px}.prototype-switcher button:focus-visible{outline:3px solid #8edbff}.prototype-switcher strong{font-size:13px;min-width:130px;text-align:center}.prototype-mark{font-size:9px;letter-spacing:.12em;color:#a9bfd4}.prototype-index{font-size:10px;color:#a9bfd4}.prototype-switcher output{display:block;text-align:center;font-size:10px;color:#b9cad9;margin-top:5px}@media(max-width:600px){.prototype-mark,.prototype-index{display:none}.prototype-switcher{bottom:8px}.prototype-switcher-row{gap:12px}.prototype-switcher output{font-size:9px}}
</style>
