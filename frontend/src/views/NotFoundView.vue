<template><main class="page-container not-found"><div class="not-found-code">404</div><h1>这个房间还不存在</h1><p class="muted">页面可能已经离开直播，或者地址输入有误。</p><RouterLink to="/"><NButton type="primary">回到发现页</NButton></RouterLink></main></template>

<script setup lang="ts">
import { NButton } from 'naive-ui'
</script>
<style scoped>.not-found { display: grid; min-height: 70vh; place-items: center; align-content: center; gap: 14px; text-align: center; } .not-found-code { color: #968bff; font-size: 110px; font-weight: 900; letter-spacing: -.1em; line-height: 1; }</style>
