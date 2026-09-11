<script setup lang="ts">
import { computed } from 'vue'
import { NTag } from 'naive-ui'
import type { LiveRoom } from '@/types/api'

const props = defineProps<{ room: LiveRoom }>()
const cover = computed(() => props.room.coverUrl || `https://images.unsplash.com/photo-${['1516321318423-f06f85e504b3', '1492684223066-81342ee5ff30', '1531058020387-3be344556be6', '1519389950473-47ba0277781c'][props.room.id % 4]}?auto=format&fit=crop&w=900&q=80`)
</script>

<template>
  <RouterLink :to="`/live/${room.id}`" class="room-card surface">
    <div class="room-cover" :style="{ backgroundImage: `url(${cover})` }">
      <div class="cover-gradient" />
      <NTag v-if="room.status === 'LIVE'" size="small" type="error" round class="live-tag">LIVE</NTag>
      <span class="room-online">● {{ room.onlineCount || 0 }} 人在线</span>
    </div>
    <div class="room-card-body">
      <h3>{{ room.title }}</h3>
      <div class="room-meta">
        <span class="room-avatar">{{ (room.anchorNickname || `主播 ${room.anchorId}`).slice(0, 1) }}</span>
        <span class="room-anchor">{{ room.anchorNickname || `主播 ${room.anchorId}` }}</span>
        <span class="room-category">{{ room.category || '综合' }}</span>
      </div>
    </div>
  </RouterLink>
</template>

<style scoped>
.room-card { display: block; overflow: hidden; transition: transform .24s ease, border-color .24s ease; }
.room-card:hover { transform: translateY(-5px); border-color: rgba(139, 124, 255, .55); }
.room-cover { position: relative; height: 170px; background-position: center; background-size: cover; }
.cover-gradient { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(0,0,0,.08), rgba(0,0,0,.7)); }
.live-tag { position: absolute; top: 12px; left: 12px; }
.room-online { position: absolute; right: 12px; bottom: 11px; color: rgba(255,255,255,.84); font-size: 12px; }
.room-online::first-letter { color: #ff6f91; }
.room-card-body { padding: 15px 16px 17px; }
.room-card h3 { overflow: hidden; margin: 0 0 13px; font-size: 15px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.room-meta { display: flex; align-items: center; gap: 8px; color: var(--sh-muted); font-size: 12px; }
.room-avatar { display: grid; place-items: center; width: 24px; height: 24px; border-radius: 50%; color: white; background: linear-gradient(135deg,#f89ab6,#816fff); }
.room-anchor { overflow: hidden; max-width: 110px; text-overflow: ellipsis; white-space: nowrap; }
.room-category { margin-left: auto; padding: 4px 8px; border-radius: 6px; background: rgba(255,255,255,.06); }
</style>
