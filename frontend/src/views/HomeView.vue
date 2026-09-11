<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NButton, NEmpty, NInput, NSelect, NSpin, NTag } from 'naive-ui'
import { liveApi } from '@/api'
import { ApiError } from '@/api/client'
import RoomCard from '@/components/RoomCard.vue'
import type { LiveRoom } from '@/types/api'

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
    const result = await liveApi.rooms({ page: 1, pageSize: 24, status: 'LIVE', keyword: keyword.value || undefined, category: category.value || undefined })
    rooms.value = result.items
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '直播列表加载失败'
  } finally { loading.value = false }
}
</script>

<template>
  <main class="page-container home-page">
    <section class="hero-block">
      <div class="hero-copy"><p class="eyebrow">LIVE. CONNECT. PLAY.</p><h1 class="page-title">每一次互动，<br /><span class="gradient-text">都值得被看见。</span></h1><p class="page-subtitle">发现正在发生的直播，和主播一起聊天、送出礼物，在每个热闹的房间里找到属于你的现场。</p><div class="hero-actions"><NButton type="primary" size="large" @click="scrollToRooms">探索直播</NButton><NButton size="large" secondary tag="a" href="/creator">开始直播</NButton></div></div>
      <div class="hero-orbit"><div class="orbit-glow" /><div class="orbit-card orbit-card-one"><span>🔥</span><div><strong>实时互动</strong><small>弹幕正在发生</small></div></div><div class="orbit-card orbit-card-two"><span>✦</span><div><strong>虚拟礼物</strong><small>支持喜欢的主播</small></div></div><div class="hero-center"><span class="hero-play">▶</span><span>STREAM<br />HUB</span></div></div>
    </section>

    <section id="room-list">
      <div class="section-heading"><div><h2>正在直播</h2><p>{{ liveRooms.length }} 个房间正在和观众互动</p></div><NTag type="success" round>实时更新</NTag></div>
      <div class="filter-bar surface"><NInput v-model:value="keyword" clearable placeholder="搜索直播间或主播" @keyup.enter="loadRooms" /><NSelect v-model:value="category" clearable placeholder="全部分类" :options="categories" /><NButton type="primary" secondary @click="loadRooms">搜索</NButton></div>
      <NSpin v-if="loading" size="medium" class="loading-block" />
      <div v-else-if="error" class="empty-state surface"><p>{{ error }}</p><NButton secondary @click="loadRooms">重新加载</NButton></div>
      <NEmpty v-else-if="!rooms.length" description="暂时没有正在直播的房间" class="empty-state surface" />
      <div v-else class="room-grid"><RoomCard v-for="room in rooms" :key="room.id" :room="room" /></div>
    </section>
  </main>
</template>

<style scoped>
.hero-block { display: grid; grid-template-columns: 1fr 1fr; align-items: center; min-height: 510px; gap: 40px; }
.gradient-text { color: #aaa1ff; background: linear-gradient(105deg, #b0a6ff, #64ded5 80%); -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent; }
.hero-actions { display: flex; gap: 12px; margin-top: 30px; }
.hero-orbit { position: relative; min-height: 420px; }
.orbit-glow { position: absolute; top: 50%; left: 50%; width: 380px; height: 380px; border-radius: 50%; transform: translate(-50%, -50%); background: radial-gradient(circle, rgba(124,105,255,.35), rgba(66,217,208,.12) 48%, transparent 70%); filter: blur(3px); }
.hero-center { position: absolute; top: 50%; left: 50%; display: flex; align-items: center; justify-content: center; flex-direction: column; width: 178px; height: 178px; gap: 10px; border: 1px solid rgba(255,255,255,.14); border-radius: 50%; color: #dcd9ff; background: rgba(14,18,37,.8); box-shadow: 0 0 0 26px rgba(139,124,255,.045), 0 30px 90px rgba(80,63,190,.3); transform: translate(-50%, -50%); font-size: 12px; font-weight: 800; letter-spacing: .22em; text-align: center; }
.hero-play { display: grid; place-items: center; width: 54px; height: 54px; padding-left: 3px; border-radius: 50%; color: #fff; background: linear-gradient(135deg,#9c90ff,#5e54d8); box-shadow: 0 10px 30px rgba(124,105,255,.5); }
.orbit-card { position: absolute; display: flex; align-items: center; gap: 10px; padding: 12px 15px; border: 1px solid var(--sh-border); border-radius: 14px; background: rgba(20,24,43,.82); box-shadow: var(--sh-shadow); backdrop-filter: blur(12px); }
.orbit-card > span { font-size: 22px; } .orbit-card strong, .orbit-card small { display: block; } .orbit-card strong { font-size: 12px; } .orbit-card small { margin-top: 4px; color: var(--sh-muted); font-size: 11px; }
.orbit-card-one { top: 15%; left: 7%; } .orbit-card-two { right: 4%; bottom: 18%; }
.filter-bar { display: flex; gap: 10px; padding: 12px; margin-bottom: 18px; } .filter-bar .n-input { flex: 1; } .filter-bar .n-select { width: 180px; }
.loading-block { display: block; margin: 80px auto; }
@media (max-width: 760px) { .hero-block { grid-template-columns: 1fr; min-height: auto; padding: 38px 0 52px; } .hero-orbit { min-height: 300px; transform: scale(.84); } .orbit-card-one { left: 0; } .orbit-card-two { right: 0; } .filter-bar { flex-wrap: wrap; } .filter-bar .n-select { width: calc(50% - 5px); } }
</style>
