<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { NButton, NInput, NScrollbar, NTag, type ScrollbarInst } from 'naive-ui'
import { useRoute, useRouter } from 'vue-router'
import { authApi, liveApi } from '@/api'
import { ApiError } from '@/api/client'
import Icon from './Icon.vue'
import { scrollScrollbarToBottom } from './scrollbar'
import type { ChatMessage } from '@/types/api'

const props = defineProps<{ roomId: number; userId?: number; initialMessages?: ChatMessage[] }>()
const messageApi = inject<any>('message')
const router = useRouter()
const route = useRoute()
const messages = ref<ChatMessage[]>(props.initialMessages ? [...props.initialMessages] : [])
const content = ref('')
const connected = ref(false)
const connecting = ref(false)
const reconnectCount = ref(0)
const scrollRef = ref<ScrollbarInst | null>(null)
let socket: WebSocket | null = null
let heartbeatTimer: number | undefined
let heartbeatTimeoutTimer: number | undefined
let reconnectTimer: number | undefined
let stopped = false
let shouldReconnect = true

const HEARTBEAT_INTERVAL_MS = 20_000
const HEARTBEAT_ACK_TIMEOUT_MS = 45_000

const statusText = computed(() => {
  if (!props.userId) return '登录后参与'
  if (connected.value) return '实时连接正常'
  if (connecting.value) return '正在连接'
  return reconnectCount.value ? `等待重连 ${reconnectCount.value}` : '未连接'
})

onMounted(async () => {
  await loadHistory()
  if (props.userId) connect()
})

watch(() => props.userId, (userId) => {
  closeSocket()
  shouldReconnect = true
  reconnectCount.value = 0
  if (userId) connect()
})

onBeforeUnmount(() => {
  stopped = true
  shouldReconnect = false
  closeSocket()
})

async function loadHistory(afterId = 0) {
  try {
    const history = await liveApi.messages(props.roomId, afterId, 100)
    if (afterId === 0) messages.value = history
    else appendMessages(history)
    await scrollToBottom()
  } catch (err) {
    if (!(err instanceof ApiError) || err.status !== 404) messageApi?.error(err instanceof Error ? err.message : '弹幕历史加载失败')
  }
}

async function connect() {
  if (stopped || !props.userId || connected.value || connecting.value) return
  const userId = props.userId
  connecting.value = true
  shouldReconnect = true
  try {
    const ticket = await authApi.wsTicket()
    if (stopped || props.userId !== userId) return
    const base = import.meta.env.VITE_WS_BASE_URL || '/ws'
    const wsBase = base.startsWith('http') ? base.replace(/^http/, 'ws') : `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}${base}`
    const params = new URLSearchParams({ roomId: String(props.roomId), ticket: ticket.ticket })
    const current = new WebSocket(`${wsBase}/chat?${params}`)
    socket = current
    current.onopen = () => {
      if (socket !== current) return
      connecting.value = false
      armHeartbeatTimeout(current)
      current.send(JSON.stringify({ type: 'HEARTBEAT' }))
      heartbeatTimer = window.setInterval(() => {
        if (socket === current && current.readyState === WebSocket.OPEN) {
          current.send(JSON.stringify({ type: 'HEARTBEAT' }))
        }
      }, HEARTBEAT_INTERVAL_MS)
    }
    current.onmessage = (event) => {
      if (socket !== current) return
      try { handleEvent(JSON.parse(event.data), current) } catch { messageApi?.warning('收到无法识别的实时消息') }
    }
    current.onerror = () => current.close()
    current.onclose = async () => {
      if (socket !== current) return
      socket = null
      connected.value = false
      connecting.value = false
      window.clearInterval(heartbeatTimer)
      window.clearTimeout(heartbeatTimeoutTimer)
      if (stopped || !shouldReconnect || props.userId !== userId) return
      await loadHistory(messages.value.at(-1)?.id || 0)
      scheduleReconnect()
    }
  } catch (err) {
    connecting.value = false
    if (!stopped && shouldReconnect && props.userId === userId) {
      if (err instanceof ApiError && err.status === 401) messageApi?.warning('登录状态已过期，请重新登录')
      scheduleReconnect()
    }
  }
}

function closeSocket() {
  window.clearInterval(heartbeatTimer)
  window.clearTimeout(heartbeatTimeoutTimer)
  window.clearTimeout(reconnectTimer)
  connecting.value = false
  connected.value = false
  const current = socket
  socket = null
  if (current) current.close(1000, 'client closed')
}

function scheduleReconnect() {
  if (stopped || !shouldReconnect || !props.userId) return
  window.clearTimeout(reconnectTimer)
  reconnectCount.value += 1
  const baseDelay = Math.min(15_000, 1_000 * 2 ** Math.min(reconnectCount.value - 1, 4))
  const delay = Math.round(baseDelay * (0.8 + Math.random() * 0.4))
  reconnectTimer = window.setTimeout(connect, delay)
}

function armHeartbeatTimeout(current: WebSocket) {
  window.clearTimeout(heartbeatTimeoutTimer)
  heartbeatTimeoutTimer = window.setTimeout(() => {
    if (socket === current && current.readyState === WebSocket.OPEN) {
      current.close(4000, 'heartbeat timeout')
    }
  }, HEARTBEAT_ACK_TIMEOUT_MS)
}

function handleEvent(event: Record<string, any>, current: WebSocket) {
  if (event.type === 'CONNECTED') {
    connected.value = true
    connecting.value = false
    reconnectCount.value = 0
    armHeartbeatTimeout(current)
  } else if (event.type === 'HEARTBEAT_ACK') {
    armHeartbeatTimeout(current)
  } else if (event.type === 'CHAT') {
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
  if (!props.userId) { void router.push({ name: 'login', query: { redirect: route.fullPath } }); return }
  if (!text) return
  if (!socket || socket.readyState !== WebSocket.OPEN) { messageApi?.warning('实时连接尚未建立'); return }
  socket.send(JSON.stringify({ type: 'CHAT', clientMessageId: crypto.randomUUID(), content: text }))
  content.value = ''
}

async function scrollToBottom() {
  await nextTick()
  scrollScrollbarToBottom(scrollRef.value)
}
</script>

<template>
  <div class="chat-panel surface">
    <div class="panel-head"><div><strong>实时弹幕</strong><div class="panel-caption">和房间里的观众聊聊</div></div><NTag :type="connected ? 'success' : 'default'" size="small" round>{{ statusText }}</NTag></div>
    <NScrollbar ref="scrollRef" class="chat-scroll">
      <div v-if="!messages.length" class="chat-empty">还没有弹幕</div>
      <div v-for="item in messages" :key="`${item.id}-${item.clientMessageId}`" class="chat-line"><span class="chat-user">{{ item.nickname || `用户 ${item.userId}` }}</span><span class="chat-content">{{ item.content }}</span></div>
    </NScrollbar>
    <div class="chat-input">
      <NInput v-model:value="content" :disabled="!userId" :placeholder="userId ? '说点什么' : '登录后发送弹幕'" maxlength="512" @keyup.enter="sendMessage" />
      <NButton v-if="userId" type="primary" :disabled="!connected || !content.trim()" @click="sendMessage"><template #icon><Icon name="send" /></template>发送</NButton>
      <NButton v-else type="primary" @click="sendMessage">登录</NButton>
    </div>
  </div>
</template>

<style scoped>
.chat-panel { display: flex; min-height: 420px; flex-direction: column; }
.panel-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 18px 18px 14px; border-bottom: 1px solid var(--sh-border); }
.panel-caption { margin-top: 4px; color: var(--sh-muted); font-size: 12px; }
.chat-scroll { flex: 1; min-height: 260px; padding: 8px 16px; }
.chat-line { padding: 7px 0; font-size: 13px; line-height: 1.5; }
.chat-user { margin-right: 8px; color: var(--sh-primary); }
.chat-content { color: var(--sh-ink); word-break: break-word; }
.chat-empty { padding: 80px 20px; color: var(--sh-muted); text-align: center; font-size: 13px; }
.chat-input { display: grid; grid-template-columns: 1fr auto; gap: 8px; padding: 14px 16px 16px; border-top: 1px solid var(--sh-border); }
</style>
