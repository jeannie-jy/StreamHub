<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  name: 'arrow-left' | 'arrow-right' | 'bookmark' | 'bookmark-filled' | 'chat' | 'dashboard' | 'gift' | 'home' | 'menu' | 'play' | 'refresh' | 'search' | 'send' | 'user' | 'users'
  size?: number
  strokeWidth?: number
}>()

const paths: Record<typeof props.name, string> = {
  'arrow-left': 'M19 12H5M12 19l-7-7 7-7',
  'arrow-right': 'M5 12h14M12 5l7 7-7 7',
  bookmark: 'M6 4.5A2.5 2.5 0 0 1 8.5 2h7A2.5 2.5 0 0 1 18 4.5V21l-6-3.5L6 21V4.5Z',
  'bookmark-filled': 'M6 4.5A2.5 2.5 0 0 1 8.5 2h7A2.5 2.5 0 0 1 18 4.5V21l-6-3.5L6 21V4.5Z',
  chat: 'M4 5.5A2.5 2.5 0 0 1 6.5 3h11A2.5 2.5 0 0 1 20 5.5v7a2.5 2.5 0 0 1-2.5 2.5H11l-5.5 4v-4.15A2.5 2.5 0 0 1 4 12.5v-7Z',
  dashboard: 'M4 4h6v6H4V4Zm10 0h6v6h-6V4ZM4 14h6v6H4v-6Zm10 0h6v6h-6v-6Z',
  gift: 'M4 9h16v11H4V9Zm-1-4h18v4H3V5Zm9 0v15M12 5c-3.2 0-4.5-1.1-4.5-2.5S9 1 10.3 2.1C11.3 3 12 5 12 5Zm0 0c3.2 0 4.5-1.1 4.5-2.5S15 1 13.7 2.1C12.7 3 12 5 12 5Z',
  home: 'm3 10 9-7 9 7v10a2 2 0 0 1-2 2h-4v-6H9v6H5a2 2 0 0 1-2-2V10Z',
  menu: 'M4 6h16M4 12h16M4 18h16',
  play: 'm8 5 11 7-11 7V5Z',
  refresh: 'M20 11a8 8 0 0 0-14.8-4L3 10m0-4v4h4M4 13a8 8 0 0 0 14.8 4L21 14m0 4v-4h-4',
  search: 'm20 20-4.5-4.5M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z',
  send: 'm21 3-7.2 18-3.4-7.4L3 10.2 21 3Zm0 0-10.6 10.6',
  user: 'M20 21a8 8 0 0 0-16 0M12 13a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z',
  users: 'M16 21v-1.5a4.5 4.5 0 0 0-4.5-4.5h-3A4.5 4.5 0 0 0 4 19.5V21m6-10a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Zm5-6.5a3 3 0 0 1 0 5.8M16 15h1.5a4.5 4.5 0 0 1 4.5 4.5V21',
}

const path = computed(() => paths[props.name])
const fill = computed(() => props.name === 'bookmark-filled' ? 'currentColor' : 'none')
</script>

<template>
  <svg :width="size || 18" :height="size || 18" viewBox="0 0 24 24" fill="none" stroke="currentColor" :stroke-width="strokeWidth || 1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path :d="path" :fill="fill" />
  </svg>
</template>
