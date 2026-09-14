import { describe, expect, it, vi } from 'vitest'
import { scrollScrollbarToBottom } from './scrollbar'

describe('ChatPanel', () => {
  it('scrolls through the Naive UI scrollbar instance API', () => {
    const scrollTo = vi.fn()
    scrollScrollbarToBottom({ scrollTo })

    expect(scrollTo).toHaveBeenCalledWith({ top: Number.MAX_SAFE_INTEGER })
  })
})
