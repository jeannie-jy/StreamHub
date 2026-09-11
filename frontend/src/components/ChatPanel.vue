<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { NButton, NInput, NScrollbar, NTag } from 'naive-ui'
import { authApi, liveApi } from '@/api'
import { ApiError } from '@/api/client'
import type { ChatMessage } from '@/types/api'

const props = defineProps<{ roomId: number; userId?: number; initialMessages?: ChatMessage[] }>()
const messageApi = inject<any>('message')
const messages = ref<ChatMessage[]>(props.initialMessages ? [...props.initialMessages] : [])
const content = ref('')
const connected = ref(false)
const connecting = ref(false)
const reconnectCount = ref(0)
const scrollRef = ref<HTMLElement | null>(null)
let socket: WebSocket | null = null
let heartbeatTimer: number | undefined
let reconnectTimer: number | undefined
let stopped = false

const statusText = computed(() => connected.value ? '实时连接正常' : connecting.value ? '正在连接…' : reconnectCount.value ? `等待重连 (${reconnectCount.value})` : '未连接')

onMounted(async () => {
  await loadHistory()
  if (props.userId) connect()
})

onBeforeUnmount(() => {
  stopped = true
  window.clearInterval(heartbeatTimer)
  window.clearTimeout(reconnectTimer)
  socket?.close()
})

async function loadHistory(afterId = 0) {
  try {
    const history = await liveApi.messages(props.roomId, afterId, 100)
    if (afterId === 0) messages.value = history
    else appendMessages(history)
    await scrollToBottom()
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 404) messageApi?.error(error instanceof Error ? error.message : '弹幕历史加载失败')
  }
}

async function connect() {
  if (stopped || !props.userId || connected.value || connecting.value) return
  connecting.value = true
  let ticket: string | null = null
  try {
    ticket = await createTicket()
  } catch {
    connecting.value = false
    scheduleReconnect()
    return
  }
  const base = import.meta.env.VITE_WS_BASE_URL || '/ws'
  const wsBase = base.startsWith('http') ? base.replace(/^http/, 'ws') : `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}${base}`
  const params = new URLSearchParams({ roomId: String(props.roomId) })
  if (ticket) params.set('ticket', ticket)
  else if (import.meta.env.DEV && import.meta.env.VITE_WS_LEGACY_USER_ID === 'true') params.set('userId', String(props.userId))
  else {
    connecting.value = false
    scheduleReconnect()
    return
  }
  socket = new WebSocket(`${wsBase}/chat?${params}`)
  socket.onopen = () => {
    connected.value = true
    connecting.value = false
    reconnectCount.value = 0
    heartbeatTimer = window.setInterval(() => socket?.send(JSON.stringify({ type: 'HEARTBEAT' })), 20_000)
  }
  socket.onmessage = (event) => handleEvent(JSON.parse(event.data))
  socket.onerror = () => socket?.close()
  socket.onclose = async () => {
    connected.value = false
    connecting.value = false
    window.clearInterval(heartbeatTimer)
    if (stopped) return
    await loadHistory(messages.value.at(-1)?.id || 0)
    scheduleReconnect()
  }
}

async function createTicket() {
  try {
    return (await authApi.wsTicket()).ticket
  } catch {
    if (import.meta.env.DEV && import.meta.env.VITE_WS_LEGACY_USER_ID === 'true') return null
    throw new Error('实时连接鉴权失败')
  }
}

function scheduleReconnect() {
  if (stopped) return
  reconnectCount.value += 1
  const delay = Math.min(15_000, 1_000 * 2 ** Math.min(reconnectCount.value - 1, 4))
  reconnectTimer = window.setTimeout(connect, delay)
}

function handleEvent(event: Record<string, any>) {
  if (event.type === 'CHAT') {
    appendMessages([event as ChatMessage])
    void scrollToBottom()
  } else if (event.type === 'ERROR') {
    messageApi?.warning(event.message || '弹幕发送失败')
  }
}

function appendMessages(incoming: ChatMessage[]) {
  const known = new Set(messages.value.map((item) => item.id))
  messages.value.push(...incoming.filter((item) => !known.has(item.id)))
  if (messages.value.length > 200) messages.value.splice(0, messages.value.length - 200)
}

function sendMessage() {
  const text = content.value.trim()
  if (!text) return
  if (!socket || socket.readyState !== WebSocket.OPEN) {
    messageApi?.warning('实时连接尚未建立')
    return
  }
  socket.send(JSON.stringify({ type: 'CHAT', clientMessageId: crypto.randomUUID(), content: text }))
  content.value = ''
}

async function scrollToBottom() {
  await nextTick()
  const element = scrollRef.value?.querySelector('.n-scrollbar-container') as HTMLElement | null
  if (element) element.scrollTop = element.scrollHeight
}
</script>

<template>
  <div class="chat-panel surface">
    <div class="panel-head"><div><strong>实时弹幕</strong><div class="panel-caption">和房间里的观众聊聊</div></div><NTag :type="connected ? 'success' : 'warning'" size="small" round>{{ statusText }}</NTag></div>
    <NScrollbar ref="scrollRef" class="chat-scroll">
      <div v-if="!messages.length" class="chat-empty">还没有弹幕，发出第一句话吧</div>
      <div v-for="item in messages" :key="`${item.id}-${item.clientMessageId}`" class="chat-line">
        <span class="chat-user">用户 {{ item.userId }}</span><span class="chat-content">{{ item.content }}</span>
      </div>
    </NScrollbar>
    <div class="chat-input"><NInput v-model:value="content" placeholder="说点什么…" maxlength="512" @keyup.enter="sendMessage" /><NButton type="primary" :disabled="!connected || !content.trim()" @click="sendMessage">发送</NButton></div>
  </div>
</template>

<style scoped>
.chat-panel { display: flex; min-height: 420px; flex-direction: column; }
.panel-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 18px 18px 14px; border-bottom: 1px solid var(--sh-border); }
.panel-caption { margin-top: 4px; color: var(--sh-muted); font-size: 12px; }
.chat-scroll { flex: 1; min-height: 260px; padding: 8px 16px; }
.chat-line { padding: 7px 0; font-size: 13px; line-height: 1.5; }
.chat-user { margin-right: 8px; color: #a9a0ff; }
.chat-content { color: #e5e7f0; word-break: break-word; }
.chat-empty { padding: 80px 20px; color: var(--sh-muted); text-align: center; font-size: 13px; }
.chat-input { display: grid; grid-template-columns: 1fr auto; gap: 8px; padding: 14px 16px 16px; border-top: 1px solid var(--sh-border); }
</style>
