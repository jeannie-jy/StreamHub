<script setup lang="ts">
defineProps<{
  loading?: boolean
  error?: string
  empty?: boolean
  emptyText?: string
}>()
</script>

<template>
  <div v-if="loading" class="async-state surface" role="status">正在加载</div>
  <div v-else-if="error" class="async-state surface async-error" role="alert">
    <p>{{ error }}</p>
    <slot name="actions" />
  </div>
  <div v-else-if="empty" class="async-state surface"><p>{{ emptyText || '暂时没有内容' }}</p><slot name="empty-action" /></div>
  <slot v-else />
</template>

<style scoped>
.async-state { min-height: 150px; display: grid; place-items: center; align-content: center; gap: 8px; padding: 28px 20px; color: var(--sh-muted); text-align: center; }
.async-state p { margin: 0; }
.async-error { color: var(--sh-danger); }
</style>
