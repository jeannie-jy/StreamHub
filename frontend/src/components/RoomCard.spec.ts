import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import RoomCard from './RoomCard.vue'

describe('RoomCard', () => {
  it('shows live status, title, anchor and online count', () => {
    const wrapper = mount(RoomCard, {
      props: {
        room: {
          id: 1,
          anchorId: 7,
          anchorNickname: '小溪',
          title: '夜航电台',
          category: '音乐',
          status: 'LIVE',
          onlineCount: 128,
          coverUrl: null,
          playbackUrl: null,
          webrtcPlaybackUrl: null,
          createdAt: '',
          updatedAt: '',
        },
      },
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    expect(wrapper.text()).toContain('夜航电台')
    expect(wrapper.text()).toContain('小溪')
    expect(wrapper.text()).toContain('128 人在线')
    expect(wrapper.text()).toContain('LIVE')
  })
})
