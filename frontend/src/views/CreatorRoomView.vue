<script setup lang="ts">
import { inject, onMounted, ref } from 'vue'
import { NButton, NFormItem, NInput, NInputNumber, NModal, NSpin, NSpace, NTag } from 'naive-ui'
import { activityApi, liveApi } from '@/api'
import { ApiError } from '@/api/client'
import ActivityPanel from '@/components/ActivityPanel.vue'
import Icon from '@/components/Icon.vue'
import StatCard from '@/components/StatCard.vue'
import VideoPlayer from '@/components/VideoPlayer.vue'
import { useRoute, useRouter } from 'vue-router'
import type { Activity, LiveRoom, RoomDashboard } from '@/types/api'

const route = useRoute()
const router = useRouter()
const messageApi = inject<any>('message')
const room = ref<LiveRoom | null>(null)
const dashboard = ref<RoomDashboard | null>(null)
const activities = ref<Activity[]>([])
const loading = ref(true)
const error = ref('')
const busy = ref(false)
const showActivity = ref(false)
const activityName = ref('')
const stock = ref(100)
const unitPrice = ref(0)
const roomId = Number(route.params.roomId)

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    room.value = await liveApi.room(roomId)
    const [nextDashboard, nextActivities] = await Promise.allSettled([liveApi.dashboard(roomId), liveApi.activities(roomId)])
    dashboard.value = nextDashboard.status === 'fulfilled' ? nextDashboard.value : null
    activities.value = nextActivities.status === 'fulfilled' ? nextActivities.value : []
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '控制台加载失败'
  } finally {
    loading.value = false
  }
}

async function toggleRoom() {
  if (!room.value) return
  busy.value = true
  try {
    room.value = room.value.status === 'LIVE' ? await liveApi.endRoom(roomId) : await liveApi.startRoom(roomId)
    messageApi?.success(room.value.status === 'LIVE' ? '推流通道已开启，请使用 OBS 向下方地址推流' : '直播已结束')
    dashboard.value = await liveApi.dashboard(roomId)
  } catch (err) {
    messageApi?.error(err instanceof Error ? err.message : '操作失败')
  } finally {
    busy.value = false
  }
}

async function copy(value?: string | null) {
  if (!value) return
  try {
    await navigator.clipboard.writeText(value)
    messageApi?.success('已复制到剪贴板')
  } catch {
    messageApi?.error('复制失败，请手动复制')
  }
}

async function createActivity() {
  if (!activityName.value.trim()) return
  try {
    const now = new Date()
    const end = new Date(now.getTime() + 60 * 60 * 1000)
    const created = await activityApi.create(roomId, { name: activityName.value.trim(), stock: stock.value, unitPrice: unitPrice.value, startsAt: now.toISOString(), endsAt: end.toISOString() })
    activities.value.unshift(created)
    showActivity.value = false
    activityName.value = ''
    messageApi?.success('活动已创建')
  } catch (err) {
    messageApi?.error(err instanceof Error ? err.message : '活动创建失败')
  }
}

async function startActivity(activity: Activity) {
  try {
    const updated = await activityApi.start(activity.id)
    const index = activities.value.findIndex((item) => item.id === activity.id)
    if (index >= 0) activities.value[index] = updated
    messageApi?.success('活动已启动')
  } catch (err) {
    messageApi?.error(err instanceof Error ? err.message : '活动启动失败')
  }
}
</script>

<template>
  <main class="page-container creator-room-page">
    <NSpin v-if="loading" class="loading-block" />
    <div v-else-if="error" class="empty-state surface error-state"><p>{{ error }}</p><NButton secondary @click="load">重新加载</NButton></div>
    <template v-else-if="room">
      <button class="back-link" type="button" @click="router.push('/creator')"><Icon name="arrow-left" :size="16" />我的直播间</button>
      <div class="creator-room-head"><div><p class="eyebrow">ROOM CONTROL / #{{ room.id }}</p><h1>{{ room.title }}</h1><NTag :type="room.status === 'LIVE' ? 'success' : 'default'" round>{{ room.status }}</NTag></div><NSpace><NButton v-if="room.status === 'LIVE' && room.pushUrl" secondary @click="copy(room.pushUrl)">复制推流地址</NButton><NButton :type="room.status === 'LIVE' ? 'error' : 'primary'" :loading="busy" @click="toggleRoom">{{ room.status === 'LIVE' ? '结束直播' : '开始直播' }}</NButton></NSpace></div>
      <div class="two-column creator-layout"><section><VideoPlayer :webrtc-url="room.status === 'LIVE' ? room.webrtcPlaybackUrl : null" :flv-url="room.status === 'LIVE' ? room.playbackUrl : null" :title="room.title" /><div v-if="room.pushUrl" class="push-tip surface"><strong>OBS 推流地址</strong><span class="mono">{{ room.pushUrl }}</span><span>启动直播仅开启推流通道；请在 OBS 中开始推流，画面出现后观众才能观看。</span><NButton text size="small" @click="copy(room.pushUrl)">复制</NButton></div></section><aside class="metric-stack"><StatCard label="当前在线" :value="dashboard?.onlineCount ?? room.onlineCount" accent="cyan" /><StatCard label="礼物收入" :value="dashboard?.giftAmount ?? 0" accent="pink" /><StatCard label="礼物订单" :value="dashboard?.giftOrderCount ?? 0" accent="purple" /><StatCard label="弹幕数量" :value="dashboard?.chatMessageCount ?? 0" accent="green" /></aside></div>
      <div class="section-heading"><div><p class="eyebrow">ROOM ACTIVITIES</p><h2>活动管理</h2><p>创建限时活动，和观众一起提升房间热度。</p></div><NButton type="primary" secondary @click="showActivity = true">新建活动</NButton></div>
      <div v-if="!activities.length" class="empty-state surface">还没有活动，创建一个限时活动吧。</div>
      <div v-else class="activity-admin-list"><div v-for="activity in activities" :key="activity.id" class="activity-admin surface"><ActivityPanel :activity="activity" readonly /><div class="activity-admin-actions"><NButton v-if="activity.status === 'DRAFT'" type="primary" size="small" @click="startActivity(activity)">启动活动</NButton><span class="muted">状态：{{ activity.status }}</span></div></div></div>
      <NModal v-model:show="showActivity" preset="card" title="创建限时活动" style="width: min(460px, calc(100vw - 32px))"><NFormItem label="活动名称"><NInput v-model:value="activityName" placeholder="例如：直播间限时活动" /></NFormItem><NFormItem label="库存"><NInputNumber v-model:value="stock" :min="1" /></NFormItem><NFormItem label="单价（虚拟金币）"><NInputNumber v-model:value="unitPrice" :min="0" /></NFormItem><NButton type="primary" block :disabled="!activityName.trim()" @click="createActivity">创建活动</NButton></NModal>
    </template>
    <div v-else class="empty-state surface">直播间不存在</div>
  </main>
</template>

<style scoped>
.back-link { display: inline-flex; align-items: center; gap: 6px; margin-bottom: 25px; padding: 0; border: 0; color: var(--sh-muted); background: transparent; cursor: pointer; font-size: 13px; }
.creator-room-head { display: flex; align-items: end; justify-content: space-between; gap: 18px; margin-bottom: 24px; }
.creator-room-head h1 { margin: 0 0 12px; color: var(--sh-ink); font-size: 34px; letter-spacing: -.04em; }
.creator-room-head .eyebrow { margin-bottom: 10px; }
.metric-stack { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.push-tip { display: flex; align-items: center; gap: 10px; padding: 13px 15px; margin-top: 12px; font-size: 12px; }
.push-tip .mono { overflow: hidden; flex: 1; color: var(--sh-muted); text-overflow: ellipsis; white-space: nowrap; }
.activity-admin-list { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; }
.activity-admin { overflow: hidden; }
.activity-admin-actions { display: flex; align-items: center; gap: 10px; padding: 0 18px 15px; font-size: 12px; }
@media (max-width: 700px) { .creator-room-head { align-items: start; flex-direction: column; } .metric-stack { grid-template-columns: 1fr 1fr; } .activity-admin-list { grid-template-columns: 1fr; } }
</style>
