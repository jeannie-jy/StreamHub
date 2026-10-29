import { onMounted, onUnmounted, type Ref } from 'vue'
import { liveApi } from '@/api'
import type { LiveRoom } from '@/types/api'

export function useRoomOnlineCount(roomId: number, room: Ref<LiveRoom | null>) {
  let timer: ReturnType<typeof setTimeout> | undefined
  let disposed = false
  let pending = false

  function stopTimer() {
    clearTimeout(timer)
    timer = undefined
  }

  function schedule() {
    stopTimer()
    if (!disposed && !document.hidden) timer = setTimeout(refresh, 15_000)
  }

  async function refresh() {
    if (disposed || document.hidden || pending) return
    pending = true
    try {
      if (room.value) {
        const result = await liveApi.onlineCount(roomId)
        if (!disposed && room.value) room.value.onlineCount = result.onlineCount
      }
    } catch {
      // Keep the last known count and retry on the next polling cycle.
    } finally {
      pending = false
      schedule()
    }
  }

  function visibilityChanged() {
    stopTimer()
    if (!document.hidden) void refresh()
  }

  onMounted(() => {
    document.addEventListener('visibilitychange', visibilityChanged)
    schedule()
  })
  onUnmounted(() => {
    disposed = true
    stopTimer()
    document.removeEventListener('visibilitychange', visibilityChanged)
  })
}
