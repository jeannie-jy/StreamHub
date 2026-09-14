<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { NButton, NSpin, NTag } from 'naive-ui'
import Icon from './Icon.vue'
import mpegts from 'mpegts.js'

const props = defineProps<{ webrtcUrl?: string | null; flvUrl?: string | null; title?: string }>()
const video = ref<HTMLVideoElement | null>(null)
const mode = ref<'webrtc' | 'flv' | 'idle' | 'error'>('idle')
const errorMessage = ref('')
const needsManualPlay = ref(false)
const mediaReady = ref(false)
let peer: RTCPeerConnection | null = null
let flvPlayer: ReturnType<typeof mpegts.createPlayer> | null = null
let controller: InstanceType<typeof globalThis.AbortController> | null = null
let runId = 0
let cancelMediaWait: (() => void) | null = null

const MEDIA_READY_TIMEOUT_MS = 6000
const HAVE_CURRENT_DATA = 2

class MediaUnavailableError extends Error {}

const hasVideo = computed(() => Boolean(props.webrtcUrl || props.flvUrl))

watch(() => [props.webrtcUrl, props.flvUrl], () => void start(), { immediate: true })

async function start() {
  const currentRun = ++runId
  await nextTick()
  cleanup()
  errorMessage.value = ''
  needsManualPlay.value = false
  mediaReady.value = false
  if (!hasVideo.value) { mode.value = 'idle'; return }
  if (props.webrtcUrl && 'RTCPeerConnection' in window) {
    try {
      await startWebRtc(props.webrtcUrl, currentRun)
      return
    } catch (err) {
      if (currentRun !== runId) return
      cleanup()
      errorMessage.value = err instanceof Error ? err.message : 'WebRTC 播放失败'
    }
  }
  if (props.flvUrl && mpegts.isSupported() && video.value && currentRun === runId) {
    try {
      mode.value = 'flv'
      flvPlayer = mpegts.createPlayer({ type: 'flv', url: props.flvUrl, isLive: true }, { enableStashBuffer: false, stashInitialSize: 128 })
      flvPlayer.attachMediaElement(video.value)
      flvPlayer.load()
      const playAttempt = flvPlayer.play()
      if (playAttempt) {
        void playAttempt.catch(() => {
          if (currentRun === runId) needsManualPlay.value = true
        })
      }
      await waitForMediaData(currentRun)
      return
    } catch (err) {
      if (currentRun !== runId) return
      errorMessage.value = err instanceof Error ? err.message : 'HTTP-FLV 播放失败'
    }
  }
  if (currentRun === runId) mode.value = 'error'
}

async function startWebRtc(url: string, currentRun: number) {
  mode.value = 'webrtc'
  peer = new RTCPeerConnection()
  peer.addTransceiver('video', { direction: 'recvonly' })
  peer.ontrack = (event) => {
    if (video.value && event.streams[0] && currentRun === runId) {
      video.value.srcObject = event.streams[0]
      void playVideo()
    }
  }
  const offer = await peer.createOffer()
  await peer.setLocalDescription(offer)
  controller = new globalThis.AbortController()
  const timeout = window.setTimeout(() => controller?.abort(), 10_000)
  try {
    const response = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/sdp', Accept: 'application/sdp' }, body: offer.sdp, signal: controller.signal })
    if (!response.ok) throw new Error(`WebRTC 信令失败 (${response.status})`)
    const answer = await response.text()
    if (currentRun !== runId || !peer) return
    await peer.setRemoteDescription({ type: 'answer', sdp: answer })
    await waitForMediaData(currentRun)
  } finally {
    window.clearTimeout(timeout)
  }
}

function waitForMediaData(currentRun: number): Promise<void> {
  const element = video.value
  if (!element) return Promise.reject(new Error('播放器尚未就绪'))

  if (element.readyState >= HAVE_CURRENT_DATA) {
    mediaReady.value = true
    return Promise.resolve()
  }

  return new Promise((resolve, reject) => {
    let settled = false
    let timer = 0

    const finish = (callback: () => void) => {
      if (settled) return
      settled = true
      window.clearTimeout(timer)
      element.removeEventListener('loadeddata', onReady)
      element.removeEventListener('playing', onReady)
      element.removeEventListener('error', onError)
      if (cancelMediaWait === cancel) cancelMediaWait = null
      callback()
    }
    const onReady = () => finish(() => {
      if (currentRun === runId) mediaReady.value = true
      resolve()
    })
    const onError = () => finish(() => reject(new Error('播放流加载失败')))
    const cancel = () => finish(() => {
      const error = new Error('已取消')
      error.name = 'AbortError'
      reject(error)
    })

    element.addEventListener('loadeddata', onReady)
    element.addEventListener('playing', onReady)
    element.addEventListener('error', onError)
    timer = window.setTimeout(() => {
      finish(() => reject(new MediaUnavailableError('当前没有可用的视频流，主播可能尚未推流')))
    }, MEDIA_READY_TIMEOUT_MS)
    cancelMediaWait = cancel
  })
}

async function playVideo() {
  if (!video.value) return
  try {
    await video.value.play()
    needsManualPlay.value = false
  } catch {
    needsManualPlay.value = true
  }
}

function cleanup() {
  cancelMediaWait?.()
  cancelMediaWait = null
  mediaReady.value = false
  controller?.abort()
  controller = null
  peer?.close()
  peer = null
  if (flvPlayer) {
    flvPlayer.pause()
    flvPlayer.unload()
    flvPlayer.detachMediaElement()
    flvPlayer.destroy()
    flvPlayer = null
  }
  if (video.value) { video.value.pause(); video.value.srcObject = null; video.value.removeAttribute('src') }
}

onBeforeUnmount(() => { runId += 1; cleanup() })
</script>

<template>
  <div class="video-frame surface">
    <video ref="video" controls playsinline muted class="video-element" :aria-label="title || '直播视频'" @click="playVideo" />
    <div v-if="mode === 'idle' && !hasVideo" class="video-placeholder"><span class="placeholder-title">主播暂未开播</span><span class="muted">开播后这里会出现直播画面</span></div>
    <div v-else-if="hasVideo && mode !== 'error' && !mediaReady" class="video-placeholder"><NSpin size="small" /><span class="placeholder-title">正在连接直播</span><span class="muted">若主播尚未推流，这里会显示明确提示</span></div>
    <div v-if="mode === 'error'" class="video-placeholder"><span class="placeholder-title">暂时无法播放直播</span><span class="muted">{{ errorMessage || '请检查直播是否仍在进行' }}</span><NButton v-if="hasVideo" size="small" secondary @click="start">重新播放</NButton></div>
    <button v-if="needsManualPlay" class="manual-play" type="button" aria-label="播放直播" @click="playVideo"><span class="play-circle"><Icon name="play" :size="18" /></span><span>点击播放</span></button>
    <NTag v-if="mode === 'flv'" size="small" round class="fallback-tag">兼容播放</NTag>
  </div>
</template>

<style scoped>
.video-frame { position: relative; overflow: hidden; aspect-ratio: 16 / 9; background: #18201c; }
.video-element { display: block; width: 100%; height: 100%; object-fit: contain; background: #18201c; }
.video-placeholder { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 9px; color: #fff; background: #18201c; font-size: 14px; }
.placeholder-title { font-size: 17px; font-weight: 700; }
.video-placeholder .muted { color: rgba(255, 255, 255, .65); }
.manual-play { position: absolute; inset: 0; display: grid; place-items: center; align-content: center; gap: 8px; border: 0; color: #fff; background: rgba(24, 32, 28, .28); cursor: pointer; }
.play-circle { display: grid; place-items: center; width: 52px; height: 52px; border: 1px solid rgba(255, 255, 255, .7); border-radius: 50%; }
.fallback-tag { position: absolute; top: 12px; right: 12px; }
</style>
