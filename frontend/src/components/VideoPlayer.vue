<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { NButton, NSpin, NTag } from 'naive-ui'
import mpegts from 'mpegts.js'

const props = defineProps<{ webrtcUrl?: string | null; flvUrl?: string | null; title?: string }>()
const video = ref<HTMLVideoElement | null>(null)
const mode = ref<'webrtc' | 'flv' | 'idle' | 'error'>('idle')
const errorMessage = ref('')
let peer: RTCPeerConnection | null = null
let flvPlayer: ReturnType<typeof mpegts.createPlayer> | null = null

const hasVideo = computed(() => Boolean(props.webrtcUrl || props.flvUrl))

watch(() => [props.webrtcUrl, props.flvUrl], () => void start(), { immediate: true })

async function start() {
  cleanup()
  errorMessage.value = ''
  if (!hasVideo.value) {
    mode.value = 'idle'
    return
  }
  if (props.webrtcUrl && 'RTCPeerConnection' in window) {
    try {
      await startWebRtc(props.webrtcUrl)
      return
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'WebRTC 播放失败，正在切换兼容模式'
    }
  }
  if (props.flvUrl && mpegts.isSupported() && video.value) {
    try {
      mode.value = 'flv'
      flvPlayer = mpegts.createPlayer({ type: 'flv', url: props.flvUrl, isLive: true }, { enableStashBuffer: false, stashInitialSize: 128 })
      flvPlayer.attachMediaElement(video.value)
      flvPlayer.load()
      await flvPlayer.play()
      return
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'HTTP-FLV 播放失败'
    }
  }
  mode.value = 'error'
}

async function startWebRtc(url: string) {
  mode.value = 'webrtc'
  peer = new RTCPeerConnection()
  peer.addTransceiver('video', { direction: 'recvonly' })
  peer.ontrack = (event) => {
    if (video.value && event.streams[0]) {
      video.value.srcObject = event.streams[0]
      void video.value.play()
    }
  }
  const offer = await peer.createOffer()
  await peer.setLocalDescription(offer)
  const response = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/sdp', Accept: 'application/sdp' }, body: offer.sdp })
  if (!response.ok) throw new Error(`WebRTC 信令失败 (${response.status})`)
  const answer = await response.text()
  await peer.setRemoteDescription({ type: 'answer', sdp: answer })
}

function cleanup() {
  peer?.close()
  peer = null
  if (flvPlayer) {
    flvPlayer.pause()
    flvPlayer.unload()
    flvPlayer.detachMediaElement()
    flvPlayer.destroy()
    flvPlayer = null
  }
  if (video.value) video.value.srcObject = null
}

onBeforeUnmount(cleanup)
</script>

<template>
  <div class="video-frame surface">
    <video ref="video" controls playsinline muted class="video-element" :aria-label="title || '直播视频'" />
    <div v-if="mode === 'idle'" class="video-placeholder"><span class="play-symbol">▶</span><span>{{ hasVideo ? '正在连接直播…' : '主播暂未开播' }}</span></div>
    <div v-if="mode === 'error'" class="video-placeholder"><span class="play-symbol">◌</span><span>{{ errorMessage || '暂时无法播放直播' }}</span><NButton v-if="hasVideo" size="small" secondary @click="start">重试播放</NButton></div>
    <div v-if="mode === 'idle' && hasVideo" class="video-loading"><NSpin size="small" /></div>
    <NTag v-if="mode === 'flv'" size="small" round class="fallback-tag">兼容播放</NTag>
  </div>
</template>

<style scoped>
.video-frame { position: relative; overflow: hidden; aspect-ratio: 16 / 9; background: #05060b; }
.video-element { display: block; width: 100%; height: 100%; object-fit: contain; background: #05060b; }
.video-placeholder { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 12px; color: var(--sh-muted); background: radial-gradient(circle at 50% 30%, rgba(139,124,255,.16), transparent 42%), #070912; font-size: 14px; }
.play-symbol { display: grid; place-items: center; width: 54px; height: 54px; border-radius: 50%; color: white; background: rgba(139,124,255,.26); font-size: 18px; }
.video-loading { position: absolute; inset: 0; display: grid; place-items: center; pointer-events: none; }
.fallback-tag { position: absolute; top: 12px; right: 12px; }
</style>
