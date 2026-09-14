<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NButton, NEmpty, NInput, NSelect, NSpin } from 'naive-ui'
import { useRouter } from 'vue-router'
import { liveApi } from '@/api'
import { ApiError } from '@/api/client'
import Icon from '@/components/Icon.vue'
import RoomCard from '@/components/RoomCard.vue'
import type { LiveRoom } from '@/types/api'

const router = useRouter()
const rooms = ref<LiveRoom[]>([])
const loading = ref(true)
const error = ref('')
const keyword = ref('')
const category = ref<string | null>(null)
const categories = computed(() => Array.from(new Set(rooms.value.map((room) => room.category).filter((value): value is string => Boolean(value)))).map((value) => ({ label: value, value })))
const liveRooms = computed(() => rooms.value.filter((room) => room.status === 'LIVE'))

onMounted(loadRooms)

function scrollToRooms() {
  document.getElementById('room-list')?.scrollIntoView({ behavior: 'smooth' })
}

async function loadRooms() {
  loading.value = true
  error.value = ''
  try {
    const result = await liveApi.rooms({ page: 1, pageSize: 24, status: 'LIVE', keyword: keyword.value.trim() || undefined, category: category.value || undefined })
    rooms.value = result.items
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '直播列表加载失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="page-container home-page">
    <section class="hero-block">
      <div class="hero-copy">
        <p class="eyebrow">LIVE CONTENT, REAL PEOPLE</p>
        <h1 class="page-title">把时间留给<br /><span class="gradient-text">正在发生的事。</span></h1>
        <div class="hero-rule" />
        <p class="page-subtitle">发现正在直播的内容，和主播一起聊天、关注与收藏。每个房间都有自己的节奏。</p>
        <div class="hero-actions">
          <NButton type="primary" size="large" @click="scrollToRooms">浏览直播</NButton>
          <NButton size="large" secondary @click="router.push('/creator')">开始直播</NButton>
        </div>
      </div>
      <aside class="hero-aside surface" aria-label="直播概览">
        <div class="hero-aside-head"><div><p class="eyebrow">NOW ON AIR</p><h2>现在有人正在直播</h2><p>选择一个房间，加入现场。</p></div><span class="status-dot" aria-label="实时状态" /></div>
        <div class="hero-stat-list">
          <div class="hero-stat"><span>当前房间</span><strong>{{ liveRooms.length }}</strong></div>
          <div class="hero-stat"><span>内容分类</span><strong>{{ categories.length }}</strong></div>
          <div class="hero-stat"><span>进入方式</span><strong>直接观看</strong></div>
        </div>
      </aside>
    </section>

    <section id="room-list">
      <div class="section-heading"><div><p class="eyebrow">DISCOVER</p><h2>正在直播</h2><p>{{ liveRooms.length }} 个房间正在和观众互动</p></div></div>
      <div class="filter-bar surface">
        <NInput v-model:value="keyword" clearable placeholder="搜索直播标题或主播" @keyup.enter="loadRooms"><template #prefix><Icon name="search" :size="17" /></template></NInput>
        <NSelect v-model:value="category" clearable placeholder="全部分类" :options="categories" />
        <NButton type="primary" secondary @click="loadRooms">搜索</NButton>
      </div>
      <NSpin v-if="loading" size="medium" class="loading-block" />
      <div v-else-if="error" class="empty-state surface error-state"><p>{{ error }}</p><NButton secondary @click="loadRooms">重新加载</NButton></div>
      <NEmpty v-else-if="!rooms.length" description="暂时没有正在直播的房间" class="empty-state surface" />
      <div v-else class="room-grid"><RoomCard v-for="room in rooms" :key="room.id" :room="room" /></div>
    </section>
  </main>
</template>

<style scoped>
.status-dot { display: block; width: 10px; height: 10px; margin-top: 6px; border-radius: 50%; background: var(--sh-primary); box-shadow: 0 0 0 5px var(--sh-primary-soft); }
</style>
