import type { ScrollbarInst } from 'naive-ui'

export function scrollScrollbarToBottom(scrollbar: Pick<ScrollbarInst, 'scrollTo'> | null) {
  scrollbar?.scrollTo({ top: Number.MAX_SAFE_INTEGER })
}
