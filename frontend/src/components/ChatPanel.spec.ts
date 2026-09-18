import { flushPromises, shallowMount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ChatPanel from './ChatPanel.vue'
import { scrollScrollbarToBottom } from './scrollbar'

const apiMocks = vi.hoisted(() => ({
  messages: vi.fn(),
  wsTicket: vi.fn(),
}))

vi.mock('@/api', () => ({
  authApi: { wsTicket: apiMocks.wsTicket },
  liveApi: { messages: apiMocks.messages },
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ fullPath: '/rooms/1' }),
  useRouter: () => ({ push: vi.fn() }),
}))

class WebSocketStub {
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSED = 3
  static instances: WebSocketStub[] = []

  readyState = WebSocketStub.CONNECTING
  sent: string[] = []
  close = vi.fn((code?: number, reason?: string) => {
    void code
    void reason
    this.readyState = WebSocketStub.CLOSED
  })
  onopen: (() => void) | null = null
  onmessage: ((event: { data: string }) => void) | null = null
  onerror: (() => void) | null = null
  onclose: (() => void) | null = null

  constructor(readonly url: string) {
    WebSocketStub.instances.push(this)
  }

  send(payload: string) {
    this.sent.push(payload)
  }
}

describe('ChatPanel', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    WebSocketStub.instances = []
    apiMocks.messages.mockResolvedValue([])
    apiMocks.wsTicket.mockResolvedValue({ ticket: 'ticket-1' })
    vi.stubGlobal('WebSocket', WebSocketStub)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('scrolls through the Naive UI scrollbar instance API', () => {
    const scrollTo = vi.fn()
    scrollScrollbarToBottom({ scrollTo })

    expect(scrollTo).toHaveBeenCalledWith({ top: Number.MAX_SAFE_INTEGER })
  })

  it('waits for CONNECTED and closes the socket when heartbeat ACKs stop', async () => {
    const wrapper = shallowMount(ChatPanel, {
      props: { roomId: 1, userId: 7 },
      global: { provide: { message: { error: vi.fn(), warning: vi.fn() } } },
    })
    await flushPromises()

    const socket = WebSocketStub.instances[0]
    const state = wrapper.vm as unknown as { connected: boolean }
    expect(socket.url).toContain('roomId=1')
    expect(state.connected).toBe(false)

    socket.readyState = WebSocketStub.OPEN
    socket.onopen?.()
    expect(socket.sent).toContain(JSON.stringify({ type: 'HEARTBEAT' }))
    expect(state.connected).toBe(false)

    socket.onmessage?.({ data: JSON.stringify({ type: 'CONNECTED' }) })
    await nextTick()
    expect(state.connected).toBe(true)

    await vi.advanceTimersByTimeAsync(44_000)
    socket.onmessage?.({ data: JSON.stringify({ type: 'HEARTBEAT_ACK' }) })
    await vi.advanceTimersByTimeAsync(44_999)
    expect(socket.close).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1)
    expect(socket.close).toHaveBeenCalledWith(4000, 'heartbeat timeout')
    wrapper.unmount()
  })
})
