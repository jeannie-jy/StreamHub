<script setup lang="ts">
import { computed, inject, onMounted, ref } from 'vue'
import { NButton, NFormItem, NInput, NModal, NSelect, NSpin, NTag } from 'naive-ui'
import { liveApi } from '@/api'
import type { LiveRoom } from '@/types/api'

const messageApi = inject<any>('message')
const rooms = ref<LiveRoom[]>([])
const loading = ref(true)
const showCreate = ref(false)
const creating = ref(false)
const title = ref('')
const category = ref('游戏')
const coverUrl = ref('')
const categoryOptions = ['游戏', '音乐', '聊天', '知识', '生活'].map((value) => ({ label: value, value }))
const liveCount = computed(() => rooms.value.filter((room) => room.status === 'LIVE').length)

onMounted(load)
async function load() { try { rooms.value = (await liveApi.mine({ page: 1, pageSize: 50 })).items } catch (error) { messageApi?.error(error instanceof Error ? error.message : '房间加载失败') } finally { loading.value = false } }
async function create() { creating.value = true; try { const room = await liveApi.createRoom({ title: title.value, category: category.value, coverUrl: coverUrl.value || null }); rooms.value.unshift(room); showCreate.value = false; title.value = ''; coverUrl.value = ''; messageApi?.success('直播间创建成功') } catch (error) { messageApi?.error(error instanceof Error ? error.message : '创建失败') } finally { creating.value = false } }
</script>

<template>
  <main class="page-container creator-page"><div class="creator-heading"><div><p class="eyebrow">CREATOR STUDIO</p><h1 class="page-title">把你的现场，<span class="gradient-text">交给观众。</span></h1><p class="page-subtitle">管理直播间、查看互动数据，随时开始一场属于你的直播。</p></div><NButton type="primary" size="large" @click="showCreate = true">＋ 创建直播间</NButton></div><div class="metric-grid creator-metrics"><div class="metric-card surface"><span class="metric-label">我的直播间</span><span class="metric-value">{{ rooms.length }}</span></div><div class="metric-card surface"><span class="metric-label">正在直播</span><span class="metric-value text-success">{{ liveCount }}</span></div><div class="metric-card surface"><span class="metric-label">累计观众</span><span class="metric-value">—</span></div><div class="metric-card surface"><span class="metric-label">累计收益</span><span class="metric-value">—</span></div></div><div class="section-heading"><div><h2>我的直播间</h2><p>选择一个房间进入控制台</p></div></div><NSpin v-if="loading" class="loading-block" /><div v-else-if="!rooms.length" class="empty-state surface"><div class="empty-icon">✦</div><p>还没有直播间，创建一个开始你的第一场直播吧。</p><NButton type="primary" @click="showCreate = true">创建第一个房间</NButton></div><div v-else class="creator-room-list"><RouterLink v-for="room in rooms" :key="room.id" :to="`/creator/rooms/${room.id}`" class="creator-room surface"><div class="creator-room-art" :class="{ active: room.status === 'LIVE' }"><span>{{ room.status === 'LIVE' ? 'LIVE' : 'OFFLINE' }}</span></div><div class="creator-room-copy"><div class="creator-room-title"><h3>{{ room.title }}</h3><NTag :type="room.status === 'LIVE' ? 'success' : 'default'" size="small" round>{{ room.status }}</NTag></div><p class="muted">{{ room.category || '综合' }} · {{ room.onlineCount }} 人在线</p><span class="room-link">进入控制台 →</span></div></RouterLink></div><NModal v-model:show="showCreate" preset="card" title="创建直播间" style="width: min(480px, calc(100vw - 32px))"><div class="create-form"><NFormItem label="直播标题"><NInput v-model:value="title" maxlength="128" placeholder="例如：今晚一起聊聊新游戏" /></NFormItem><NFormItem label="分类"><NSelect v-model:value="category" :options="categoryOptions" /></NFormItem><NFormItem label="封面地址（可选）"><NInput v-model:value="coverUrl" placeholder="后续可接入 MinIO 上传" /></NFormItem><NButton type="primary" block :loading="creating" :disabled="!title.trim()" @click="create">创建直播间</NButton></div></NModal></main>
</template>

<style scoped>
.creator-heading { display: flex; align-items: end; justify-content: space-between; gap: 20px; } .gradient-text { color: #aaa1ff; background: linear-gradient(105deg,#b0a6ff,#64ded5 80%); -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent; } .creator-metrics { margin-top: 46px; } .creator-room-list { display: grid; grid-template-columns: repeat(2,1fr); gap: 14px; } .creator-room { display: flex; align-items: stretch; overflow: hidden; transition: border-color .2s; } .creator-room:hover { border-color: rgba(139,124,255,.6); } .creator-room-art { display: grid; place-items: center; width: 150px; min-height: 150px; color: rgba(255,255,255,.72); background: linear-gradient(135deg,#273254,#171c3c); font: 11px monospace; } .creator-room-art.active { background: linear-gradient(135deg,#4e315b,#281c55); color: #ff91bd; } .creator-room-copy { flex: 1; padding: 18px; } .creator-room-title { display: flex; align-items: start; justify-content: space-between; gap: 8px; } .creator-room-title h3 { margin: 0; font-size: 16px; } .creator-room-copy p { margin: 10px 0 24px; font-size: 12px; } .room-link { color: #aaa1ff; font-size: 12px; } .empty-icon { color: #aaa1ff; font-size: 38px; } .create-form { display: grid; gap: 2px; }
@media (max-width: 680px) { .creator-heading { align-items: start; flex-direction: column; } .creator-room-list { grid-template-columns: 1fr; } .creator-room-art { width: 110px; min-height: 125px; } }
</style>
