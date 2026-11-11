<script setup lang="ts">
defineProps<{ name: string }>()
const paths: Record<string, string> = {
  home: 'M3 10 12 3l9 7M5 9v12h5v-7h4v7h5V9',
  cross: 'M9 3h6v6h6v6h-6v6H9v-6H3V9h6Z',
  grid: 'M3 3h7v7H3ZM14 3h7v7h-7ZM3 14h7v7H3ZM14 14h7v7h-7Z',
  doctor: 'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0ZM4 21v-3a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v3M8 13v5h3m5-5v4m-1 2a1 1 0 1 0 2 0 1 1 0 0 0-2 0',
  calendar: 'M4 5h16v16H4ZM8 2v6m8-6v6M4 11h16M8 15h2m4 0h2m-8 3h2',
  file: 'M5 3h9l5 5v13H5ZM14 3v6h5M8 13h8m-8 4h6',
  bell: 'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9ZM9 21h6',
  user: 'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0ZM4 21v-2a8 8 0 0 1 16 0v2',
  chat: 'M21 11a9 9 0 0 1-9 9H4l-2 2v-9A9 9 0 1 1 21 11ZM7 10h10M7 14h6',
  hospital: 'M6 21V4h12v17M3 21h18M10 21v-5h4v5M9 7h6m-3-3v6M9 12h1m4 0h1',
  scan: 'M3 8V3h5m8 0h5v5M3 16v5h5m8 0h5v-5M7 12l3 3 7-7',
  wallet: 'M3 6h17v15H3ZM3 6V3h14v3m-2 6h6v5h-6Zm3 2h.01',
  heart: 'M12 21 3.5 12.5a6 6 0 0 1 8.5-8 6 6 0 0 1 8.5 8ZM3 12h5l2-4 4 8 2-4h5',
  smile: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM8 14s1 3 4 3 4-3 4-3M8 9h.01M16 9h.01',
  leaf: 'M20 3S4 2 4 12a7 7 0 0 0 12 5c5-5 4-14 4-14ZM3 21 15 9',
  arrow: 'M4 12h16m-6-6 6 6-6 6',
  chevron: 'm9 5 7 7-7 7',
  menu: 'M4 6h16M4 12h16M4 18h16',
  clock: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM12 7v5l3 2',
  pin: 'M18 9c0 5-6 12-6 12S6 14 6 9a6 6 0 0 1 12 0ZM14 9a2 2 0 1 1-4 0 2 2 0 0 1 4 0',
  shield: 'M12 2 3 6v6c0 6 9 10 9 10s9-4 9-10V6ZM8 12l3 3 5-6',
  sparkles: 'm12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5ZM20 2v4m-2-2h4',
  close: 'm6 6 12 12M6 18 18 6',
  image: 'M3 3h18v18H3Zm0 14 5-5 5 5 4-4 4 4M14 8h.01',
  search: 'm21 21-4.5-4.5m2.5-5.5a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z',
  refresh: 'M20 11a8 8 0 1 0 1 4m-1-9v5h-5',
  info: 'M12 16v-4m0-4h.01M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z',
  warning: 'M12 3 2.5 20h19L12 3Zm0 6v5m0 3h.01',
}
</script>
<template><svg class="ph-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="paths[name] ?? paths.cross" /></svg></template>
