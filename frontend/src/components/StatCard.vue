<script setup lang="ts">
defineProps<{ label: string; value: string | number; accent?: 'purple' | 'cyan' | 'pink' | 'green' }>()
</script>

<template>
  <div class="metric-card surface" :class="`accent-${accent || 'purple'}`">
    <div class="metric-label">{{ label }}</div>
    <div class="metric-value">{{ value }}</div>
  </div>
</template>

<style scoped>
.metric-card { position: relative; overflow: hidden; }
.metric-card::after { position: absolute; right: -28px; bottom: -42px; width: 110px; height: 110px; border-radius: 50%; background: currentColor; opacity: .08; content: ''; }
.accent-purple { color: #b1a8ff; } .accent-cyan { color: #70ebe1; } .accent-pink { color: #ff91bd; } .accent-green { color: #71e5bd; }
.metric-label, .metric-value { color: inherit; }
</style>
