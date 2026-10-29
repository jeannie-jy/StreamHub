import { defineComponent, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { liveApi } from '@/api'
import type { LiveRoom } from '@/types/api'
import { useRoomOnlineCount } from './useRoomOnlineCount'

vi.mock('@/api', () => ({ liveApi: { onlineCount: vi.fn() } }))

describe('room online count polling', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(false)
    vi.mocked(liveApi.onlineCount).mockReset()
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  function setup() {
    const room = ref({ onlineCount: 10 } as LiveRoom)
    const wrapper = mount(defineComponent({
      setup() { useRoomOnlineCount(88, room); return () => null },
    }))
    return { room, wrapper }
  }

  it('refreshes, keeps the last count on failure, and retries', async () => {
    vi.mocked(liveApi.onlineCount)
      .mockResolvedValueOnce({ onlineCount: 5 })
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce({ onlineCount: 7 })
    const { room, wrapper } = setup()
    await vi.advanceTimersByTimeAsync(15_000)
    expect(liveApi.onlineCount).toHaveBeenCalledWith(88)
    expect(room.value.onlineCount).toBe(5)
    await vi.advanceTimersByTimeAsync(15_000)
    expect(room.value.onlineCount).toBe(5)
    await vi.advanceTimersByTimeAsync(15_000)
    expect(room.value.onlineCount).toBe(7)
    wrapper.unmount()
  })

  it('pauses while hidden, refreshes when visible, and stops after unmount', async () => {
    vi.mocked(liveApi.onlineCount).mockResolvedValue({ onlineCount: 3 })
    const { wrapper } = setup()
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(true)
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(30_000)
    expect(liveApi.onlineCount).not.toHaveBeenCalled()
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(false)
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()
    expect(liveApi.onlineCount).toHaveBeenCalledTimes(1)
    wrapper.unmount()
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(30_000)
    expect(liveApi.onlineCount).toHaveBeenCalledTimes(1)
  })

  it('does not overlap slow requests or update an unmounted page', async () => {
    let resolve!: (value: { onlineCount: number }) => void
    vi.mocked(liveApi.onlineCount).mockReturnValue(new Promise(done => { resolve = done }))
    const { room, wrapper } = setup()
    await vi.advanceTimersByTimeAsync(45_000)
    expect(liveApi.onlineCount).toHaveBeenCalledTimes(1)
    wrapper.unmount()
    resolve({ onlineCount: 99 })
    await flushPromises()
    expect(room.value.onlineCount).toBe(10)
    expect(vi.getTimerCount()).toBe(0)
  })
})
