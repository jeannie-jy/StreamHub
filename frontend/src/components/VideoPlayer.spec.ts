import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import VideoPlayer from './VideoPlayer.vue'

class PeerConnectionStub {
  ontrack: ((event: { streams: MediaStream[] }) => void) | null = null

  addTransceiver() {}
  async createOffer() { return { type: 'offer' as const, sdp: 'offer' } }
  async setLocalDescription() {}
  async setRemoteDescription() {}
  close() {}
}

describe('VideoPlayer', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.stubGlobal('RTCPeerConnection', PeerConnectionStub)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => 'answer' }))
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('reports a missing publisher when signaling succeeds but no media arrives', async () => {
    const wrapper = mount(VideoPlayer, { props: { webrtcUrl: '/rtc/whep', title: '测试直播' } })
    await flushPromises()
    expect(wrapper.text()).toContain('正在连接直播')

    await vi.advanceTimersByTimeAsync(6000)
    await flushPromises()

    expect(wrapper.text()).toContain('当前没有可用的视频流，主播可能尚未推流')
    wrapper.unmount()
  })
})
