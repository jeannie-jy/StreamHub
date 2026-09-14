<script setup lang="ts">
import { computed } from 'vue'
import { NTag } from 'naive-ui'
import type { LiveRoom } from '@/types/api'

const props = defineProps<{ room: LiveRoom }>()
const hasCover = computed(() => Boolean(props.room.coverUrl))
</script>

<template>
  <RouterLink :to="`/live/${room.id}`" class="room-card surface">
    <div class="room-cover" :class="{ 'room-cover-placeholder': !hasCover }" :style="hasCover ? { backgroundImage: `url(${room.coverUrl})` } : undefined">
      <div class="cover-gradient" />
      <span v-if="!hasCover" class="placeholder-label">STREAMHUB</span>
      <NTag v-if="room.status === 'LIVE'" size="small" type="success" round class="live-tag">直播中</NTag>
      <span class="room-online"><span class="online-dot" /> {{ room.onlineCount || 0 }} 人在线</span>
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
.room-card { display: block; overflow: hidden; transition: transform .2s ease, border-color .2s ease, box-shadow .2s ease; }
.room-card:hover { transform: translateY(-3px); border-color: var(--sh-primary); box-shadow: 0 18px 42px rgba(31, 36, 33, .12); }
.room-cover { position: relative; height: 170px; background-position: center; background-size: cover; }
.room-cover-placeholder { display: grid; place-items: center; color: rgba(255, 255, 255, .8); background: linear-gradient(135deg, #244b3c, #88a895); }
.cover-gradient { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(0, 0, 0, .04), rgba(0, 0, 0, .62)); }
.placeholder-label { position: relative; font-size: 11px; font-weight: 800; letter-spacing: .2em; }
.live-tag { position: absolute; top: 12px; left: 12px; }
.room-online { position: absolute; right: 12px; bottom: 11px; display: inline-flex; align-items: center; gap: 6px; color: #fff; font-size: 12px; }
.online-dot { width: 6px; height: 6px; border-radius: 50%; background: #9be0b5; }
.room-card-body { padding: 15px 16px 17px; }
.room-card h3 { overflow: hidden; margin: 0 0 13px; color: var(--sh-ink); font-size: 15px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.room-meta { display: flex; align-items: center; gap: 8px; color: var(--sh-muted); font-size: 12px; }
.room-avatar { display: grid; place-items: center; width: 24px; height: 24px; border: 1px solid var(--sh-border-strong); border-radius: 50%; color: var(--sh-primary-strong); background: var(--sh-primary-soft); }
.room-anchor { overflow: hidden; max-width: 110px; text-overflow: ellipsis; white-space: nowrap; }
.room-category { margin-left: auto; padding: 4px 8px; border-radius: 6px; background: var(--sh-surface-muted); }
</style>
