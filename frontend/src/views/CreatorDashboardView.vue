<script setup lang="ts">
import { computed, inject, onMounted, ref } from 'vue'
import { NButton, NFormItem, NInput, NModal, NSelect, NSpin, NTag } from 'naive-ui'
import { liveApi } from '@/api'
import { ApiError } from '@/api/client'
import Icon from '@/components/Icon.vue'
import type { LiveRoom } from '@/types/api'

const messageApi = inject<any>('message')
const rooms = ref<LiveRoom[]>([])
const loading = ref(true)
const error = ref('')
const showCreate = ref(false)
const creating = ref(false)
const title = ref('')
const category = ref('游戏')
const coverUrl = ref('')
const categoryOptions = ['游戏', '音乐', '聊天', '知识', '生活'].map((value) => ({ label: value, value }))
const liveCount = computed(() => rooms.value.filter((room) => room.status === 'LIVE').length)

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    rooms.value = (await liveApi.mine({ page: 1, pageSize: 50 })).items
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '房间加载失败'
  } finally {
    loading.value = false
  }
}

async function create() {
  if (!title.value.trim()) return
  creating.value = true
  try {
    const room = await liveApi.createRoom({ title: title.value.trim(), category: category.value, coverUrl: coverUrl.value.trim() || null })
    rooms.value.unshift(room)
    showCreate.value = false
    title.value = ''
    coverUrl.value = ''
    messageApi?.success('直播间创建成功')
  } catch (err) {
    messageApi?.error(err instanceof Error ? err.message : '创建失败')
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <main class="page-container creator-page">
    <div class="creator-heading"><div><p class="eyebrow">CREATOR STUDIO</p><h1 class="page-title">把你的现场，<span class="gradient-text">交给观众。</span></h1><p class="page-subtitle">管理直播间、查看互动数据，随时开始一场属于你的直播。</p></div><NButton type="primary" size="large" @click="showCreate = true">创建直播间</NButton></div>
    <div class="metric-grid creator-metrics"><div class="metric-card surface"><span class="metric-label">我的直播间</span><span class="metric-value">{{ rooms.length }}</span></div><div class="metric-card surface"><span class="metric-label">正在直播</span><span class="metric-value text-success">{{ liveCount }}</span></div><div class="metric-card surface"><span class="metric-label">累计观众</span><span class="metric-value">暂未统计</span></div><div class="metric-card surface"><span class="metric-label">累计收益</span><span class="metric-value">暂未统计</span></div></div>
    <div class="section-heading"><div><p class="eyebrow">YOUR ROOMS</p><h2>我的直播间</h2><p>选择一个房间进入控制台</p></div></div>
    <NSpin v-if="loading" class="loading-block" />
    <div v-else-if="error" class="empty-state surface error-state"><p>{{ error }}</p><NButton secondary @click="load">重新加载</NButton></div>
    <div v-else-if="!rooms.length" class="empty-state surface"><div class="empty-mark"><Icon name="dashboard" :size="24" /></div><p>还没有直播间，创建一个开始你的第一场直播。</p><NButton type="primary" @click="showCreate = true">创建第一个房间</NButton></div>
    <div v-else class="creator-room-list"><RouterLink v-for="room in rooms" :key="room.id" :to="`/creator/rooms/${room.id}`" class="creator-room surface"><div class="creator-room-art" :class="{ active: room.status === 'LIVE' }"><span>{{ room.status === 'LIVE' ? 'LIVE' : 'OFFLINE' }}</span></div><div class="creator-room-copy"><div class="creator-room-title"><h3>{{ room.title }}</h3><NTag :type="room.status === 'LIVE' ? 'success' : 'default'" size="small" round>{{ room.status }}</NTag></div><p class="muted">{{ room.category || '综合' }} / {{ room.onlineCount }} 人在线</p><span class="room-link">进入控制台</span></div></RouterLink></div>
    <NModal v-model:show="showCreate" preset="card" title="创建直播间" style="width: min(480px, calc(100vw - 32px))"><div class="create-form"><NFormItem label="直播标题"><NInput v-model:value="title" maxlength="128" placeholder="例如：今晚一起聊聊新游戏" /></NFormItem><NFormItem label="分类"><NSelect v-model:value="category" :options="categoryOptions" /></NFormItem><NFormItem label="封面地址（可选）"><NInput v-model:value="coverUrl" placeholder="输入可访问的图片地址" /></NFormItem><NButton type="primary" block :loading="creating" :disabled="!title.trim()" @click="create">创建直播间</NButton></div></NModal>
  </main>
</template>

<style scoped>
.creator-heading { display: flex; align-items: end; justify-content: space-between; gap: 20px; }
.creator-metrics { margin-top: 46px; }
.creator-room-list { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; }
.creator-room { display: flex; align-items: stretch; overflow: hidden; transition: border-color .2s, box-shadow .2s; }
.creator-room:hover { border-color: var(--sh-primary); box-shadow: var(--sh-shadow); }
.creator-room-art { display: grid; place-items: center; width: 150px; min-height: 150px; color: rgba(255, 255, 255, .72); background: #6e8175; font: 11px monospace; letter-spacing: .12em; }
.creator-room-art.active { background: #356750; color: #fff; }
.creator-room-copy { flex: 1; padding: 18px; }
.creator-room-title { display: flex; align-items: start; justify-content: space-between; gap: 8px; }
.creator-room-title h3 { margin: 0; color: var(--sh-ink); font-size: 16px; }
.creator-room-copy p { margin: 10px 0 24px; font-size: 12px; }
.room-link { color: var(--sh-primary); font-size: 12px; font-weight: 700; }
.empty-mark { display: grid; place-items: center; width: 48px; height: 48px; margin: 0 auto 14px; border-radius: 50%; color: var(--sh-primary); background: var(--sh-primary-soft); }
.create-form { display: grid; gap: 2px; }
@media (max-width: 680px) { .creator-heading { align-items: start; flex-direction: column; } .creator-room-list { grid-template-columns: 1fr; } .creator-room-art { width: 110px; min-height: 125px; } }
</style>
