<script setup lang="ts">
defineProps<{ label: string; value: string | number; accent?: 'purple' | 'cyan' | 'pink' | 'green' }>()
</script>

<template>
  <div class="metric-card surface" :class="`accent-${accent || 'purple'}`"><div class="metric-label">{{ label }}</div><div class="metric-value">{{ value }}</div></div>
</template>

<style scoped>
.metric-card { position: relative; overflow: hidden; }
.metric-card::after { position: absolute; right: -28px; bottom: -42px; width: 110px; height: 110px; border-radius: 50%; background: currentColor; opacity: .08; content: ''; }
.accent-purple { color: #526d9e; }
.accent-cyan { color: #2d806f; }
.accent-pink { color: #a95b50; }
.accent-green { color: #236b52; }
.metric-label { color: var(--sh-muted); font-size: 13px; }
.metric-value { margin-top: 10px; color: var(--sh-ink); font-size: 30px; font-weight: 800; letter-spacing: -.04em; }
</style>
