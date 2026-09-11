<script setup lang="ts">
import { computed, inject, onMounted, ref } from 'vue'
import { NButton, NDivider, NEmpty, NSpin, NTag } from 'naive-ui'
import { useRoute, useRouter } from 'vue-router'
import { liveApi } from '@/api'
import { ApiError } from '@/api/client'
import ActivityPanel from '@/components/ActivityPanel.vue'
import ChatPanel from '@/components/ChatPanel.vue'
import GiftPanel from '@/components/GiftPanel.vue'
import RankList from '@/components/RankList.vue'
import VideoPlayer from '@/components/VideoPlayer.vue'
import { useAuthStore } from '@/stores/auth'
import { useUserStore } from '@/stores/user'
import type { Activity, LiveRoom, UserProfile } from '@/types/api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const users = useUserStore()
const messageApi = inject<any>('message')
const room = ref<LiveRoom | null>(null)
const anchor = ref<UserProfile | null>(null)
const activities = ref<Activity[]>([])
const loading = ref(true)
const following = ref(false)
const followLoading = ref(false)
const roomId = computed(() => Number(route.params.roomId))

onMounted(load)

async function load() {
  loading.value = true
  try {
    room.value = await liveApi.room(roomId.value)
    if (room.value.anchorId) anchor.value = await users.loadProfile(room.value.anchorId)
    try { activities.value = await liveApi.activities(roomId.value) } catch { activities.value = [] }
    if (auth.isAuthenticated && room.value.anchorId !== auth.userId) {
      try { following.value = await users.loadFollowStatus(room.value.anchorId) } catch { following.value = false }
    }
  } catch (error) {
    messageApi?.error(error instanceof ApiError ? error.message : '直播间加载失败')
  } finally { loading.value = false }
}

async function toggleFollow() {
  if (!auth.isAuthenticated) { await router.push({ name: 'login', query: { redirect: route.fullPath } }); return }
  if (!room.value) return
  followLoading.value = true
  try { following.value = await users.toggleFollow(room.value.anchorId) } catch (error) { messageApi?.error(error instanceof Error ? error.message : '操作失败') } finally { followLoading.value = false }
}

</script>

<template>
  <main class="page-container room-page">
    <NSpin v-if="loading" size="medium" class="loading-block" />
    <div v-else-if="!room" class="empty-state surface"><NEmpty description="直播间不存在或已下线" /><NButton secondary @click="router.push('/')">返回发现</NButton></div>
    <template v-else>
      <div class="room-topline"><div><NTag v-if="room.status === 'LIVE'" type="error" round size="small">LIVE NOW</NTag><span class="room-id">ROOM #{{ room.id }}</span></div><NButton quaternary @click="router.push('/')">← 返回发现</NButton></div>
      <div class="two-column room-layout"><section><VideoPlayer :webrtc-url="room.webrtcPlaybackUrl" :flv-url="room.playbackUrl" :title="room.title" /><div class="room-info"><div class="room-title-row"><div><h1>{{ room.title }}</h1><div class="room-stats"><span>◉ {{ room.onlineCount }} 人在线</span><span>·</span><span>{{ room.category || '综合' }}</span></div></div><NButton v-if="room.anchorId !== auth.userId" :type="following ? 'default' : 'primary'" secondary @click="toggleFollow">{{ following ? '已关注' : '关注主播' }}</NButton></div><div class="anchor-row"><span class="large-avatar">{{ (anchor?.nickname || `主播 ${room.anchorId}`).slice(0, 1) }}</span><div><strong>{{ anchor?.nickname || `主播 ${room.anchorId}` }}</strong><div class="muted">{{ anchor?.followerCount || 0 }} 位关注者</div></div></div></div><NDivider /><div v-if="activities.length" class="room-activities"><div class="section-heading compact"><div><h2>直播活动</h2><p>参与互动赢取限时福利</p></div></div><div class="activity-stack"><ActivityPanel v-for="activity in activities" :key="activity.id" :activity="activity" :enabled="auth.isAuthenticated" /></div></div></section><aside class="room-sidebar"><ChatPanel :room-id="room.id" :user-id="auth.userId || undefined" /><GiftPanel :room-id="room.id" :enabled="auth.isAuthenticated" /><RankList :room-id="room.id" /></aside></div>
    </template>
  </main>
</template>

<style scoped>
.room-topline { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; } .room-topline > div { display: flex; align-items: center; gap: 10px; } .room-id { color: var(--sh-muted); font: 11px monospace; letter-spacing: .12em; }
.room-layout { align-items: start; } .room-sidebar { display: flex; gap: 16px; flex-direction: column; }
.room-info { padding: 20px 4px 0; } .room-title-row { display: flex; align-items: start; justify-content: space-between; gap: 20px; } .room-title-row h1 { margin: 0 0 8px; font-size: clamp(22px, 3vw, 32px); letter-spacing: -.04em; } .room-stats { display: flex; gap: 8px; color: var(--sh-muted); font-size: 13px; }
.anchor-row { display: flex; align-items: center; gap: 10px; margin-top: 22px; } .large-avatar { display: grid; place-items: center; width: 40px; height: 40px; border-radius: 50%; color: white; background: linear-gradient(135deg,#f89ab6,#816fff); } .anchor-row strong { font-size: 14px; }
.compact { margin: 20px 0 12px; } .compact h2 { font-size: 20px; } .activity-stack { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
@media (max-width: 680px) { .activity-stack { grid-template-columns: 1fr; } .room-title-row { align-items: stretch; flex-direction: column; } }
</style>
