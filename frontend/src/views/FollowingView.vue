<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NButton, NEmpty, NSpin, NTabs, NTabPane } from 'naive-ui'
import { liveApi, userApi } from '@/api'
import { ApiError } from '@/api/client'
import RoomCard from '@/components/RoomCard.vue'
import type { LiveRoom, UserProfile } from '@/types/api'

const followingRooms = ref<LiveRoom[]>([])
const favoriteRooms = ref<LiveRoom[]>([])
const followingUsers = ref<UserProfile[]>([])
const loading = ref(true)
const error = ref('')
const activeTab = ref('following')

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  const results = await Promise.allSettled([
    liveApi.following({ page: 1, pageSize: 24 }),
    liveApi.favorites({ page: 1, pageSize: 24 }),
    userApi.following({ page: 1, pageSize: 24 }),
  ])
  const [rooms, favorites, users] = results
  if (rooms.status === 'fulfilled') followingRooms.value = rooms.value.items
  if (favorites.status === 'fulfilled') favoriteRooms.value = favorites.value.items
  if (users.status === 'fulfilled') followingUsers.value = users.value.items
  if (results.every((result) => result.status === 'rejected')) {
    const firstError = results.find((result): result is PromiseRejectedResult => result.status === 'rejected')?.reason
    error.value = firstError instanceof ApiError ? firstError.message : '关注内容加载失败'
  }
  loading.value = false
}
</script>

<template>
  <main class="page-container following-page">
    <div class="page-heading"><div><p class="eyebrow">YOUR FEED</p><h1 class="page-title">关注内容</h1><p class="page-subtitle">把喜欢的主播和直播间放在一起，回来就能继续看。</p></div><NButton secondary @click="load">重新加载</NButton></div>
    <NSpin v-if="loading" class="loading-block" />
    <div v-else-if="error" class="empty-state surface error-state"><p>{{ error }}</p><NButton secondary @click="load">重新加载</NButton></div>
    <NTabs v-else v-model:value="activeTab" type="line" animated class="following-tabs">
      <NTabPane name="following" tab="正在关注">
        <div v-if="!followingRooms.length" class="empty-state surface"><NEmpty description="关注的主播暂时没有直播" /></div>
        <div v-else class="room-grid"><RoomCard v-for="room in followingRooms" :key="room.id" :room="room" /></div>
        <div v-if="followingUsers.length" class="following-users surface"><div class="section-heading compact"><div><h2>关注的主播</h2><p>{{ followingUsers.length }} 位主播</p></div></div><div class="user-list"><div v-for="user in followingUsers" :key="user.id" class="user-row"><span class="avatar-fallback">{{ user.nickname.slice(0, 1) }}</span><div><strong>{{ user.nickname }}</strong><p>@{{ user.username }} · {{ user.followerCount }} 位关注者</p></div></div></div></div>
      </NTabPane>
      <NTabPane name="favorites" tab="收藏的直播间">
        <div v-if="!favoriteRooms.length" class="empty-state surface"><NEmpty description="还没有收藏直播间" /></div>
        <div v-else class="room-grid"><RoomCard v-for="room in favoriteRooms" :key="room.id" :room="room" /></div>
      </NTabPane>
    </NTabs>
  </main>
</template>

<style scoped>
.page-heading { display: flex; align-items: end; justify-content: space-between; gap: 20px; }
.following-tabs { margin-top: 34px; }
.following-tabs :deep(.n-tabs-pane-wrapper) { padding-top: 20px; }
.following-users { margin-top: 28px; padding: 0 20px 20px; }
.compact { margin: 20px 0 14px; }
.compact h2 { font-size: 19px; }
.user-list { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.user-row { display: flex; align-items: center; gap: 10px; padding: 12px; border: 1px solid var(--sh-border); border-radius: 10px; }
.user-row strong { color: var(--sh-ink); font-size: 13px; }
.user-row p { margin: 4px 0 0; color: var(--sh-muted); font-size: 12px; }
@media (max-width: 600px) { .page-heading { align-items: start; flex-direction: column; } .user-list { grid-template-columns: 1fr; } }
</style>
