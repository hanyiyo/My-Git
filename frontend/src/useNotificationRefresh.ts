import { onMounted, onUnmounted } from 'vue'

export function useNotificationRefresh(refresh: () => Promise<void>, appointmentId?: string) {
  let timer: ReturnType<typeof setTimeout> | undefined
  function receive(event: Event) {
    const item = (event as CustomEvent<{ objectId: string }>).detail
    if (appointmentId && item.objectId !== appointmentId) return
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => { void refresh() }, 100)
  }
  onMounted(() => window.addEventListener('medical-notification', receive))
  onUnmounted(() => { window.removeEventListener('medical-notification', receive); if (timer) clearTimeout(timer) })
}
