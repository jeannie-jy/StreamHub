<script setup lang="ts">
import { inject, onMounted, ref } from 'vue'
import { NButton, NInput, NSpin, NTabs, NTabPane, NTag } from 'naive-ui'
import { opsApi } from '@/api'
import type { Activity, AuditLog, LiveRoom, ModerationLog, OpsSummary, PageResult, SensitiveWord, UserProfile } from '@/types/api'
import StatCard from '@/components/StatCard.vue'

const messageApi = inject<any>('message')
const summary = ref<OpsSummary | null>(null)
const rooms = ref<PageResult<LiveRoom> | null>(null)
const users = ref<PageResult<UserProfile> | null>(null)
const activities = ref<PageResult<Activity> | null>(null)
const words = ref<SensitiveWord[]>([])
const auditLogs = ref<PageResult<AuditLog> | null>(null)
const moderationLogs = ref<PageResult<ModerationLog> | null>(null)
const word = ref('')
const muteRoomId = ref('')
const muteUserId = ref('')
const muteReason = ref('运营禁言')
const muteMinutes = ref('60')
const loading = ref(true)
const error = ref('')
const activeTab = ref('overview')

onMounted(loadAll)

async function loadAll() {
  loading.value = true
  error.value = ''
  const results = await Promise.allSettled([
    opsApi.summary(),
    opsApi.rooms({ page: 1, pageSize: 50 }),
    opsApi.users({ page: 1, pageSize: 50 }),
    opsApi.activities({ page: 1, pageSize: 50 }),
    opsApi.sensitiveWords(),
    opsApi.auditLogs({ page: 1, pageSize: 50 }),
    opsApi.moderationLogs({ page: 1, pageSize: 50 }),
  ])
  if (results[0].status === 'fulfilled') summary.value = results[0].value
  if (results[1].status === 'fulfilled') rooms.value = results[1].value
  if (results[2].status === 'fulfilled') users.value = results[2].value
  if (results[3].status === 'fulfilled') activities.value = results[3].value
  if (results[4].status === 'fulfilled') words.value = results[4].value
  if (results[5].status === 'fulfilled') auditLogs.value = results[5].value
  if (results[6].status === 'fulfilled') moderationLogs.value = results[6].value
  if (results.every((result) => result.status === 'rejected')) error.value = '运营数据加载失败'
  loading.value = false
}

async function stopRoom(room: LiveRoom) { try { await opsApi.stopRoom(room.id, '运营审核停止'); room.status = 'ENDED'; messageApi?.success('直播间已停止') } catch (err) { messageApi?.error(err instanceof Error ? err.message : '操作失败') } }
async function toggleBan(user: UserProfile) { try { const next = user.status === 'BANNED' ? await opsApi.unbanUser(user.id) : await opsApi.banUser(user.id, '运营治理'); user.status = next.status; messageApi?.success(user.status === 'BANNED' ? '用户已封禁' : '用户已解封') } catch (err) { messageApi?.error(err instanceof Error ? err.message : '操作失败') } }
async function addWord() { if (!word.value.trim()) return; try { words.value.unshift(await opsApi.addSensitiveWord(word.value.trim())); word.value = ''; messageApi?.success('敏感词已添加') } catch (err) { messageApi?.error(err instanceof Error ? err.message : '添加失败') } }
async function removeWord(item: SensitiveWord) { try { await opsApi.removeSensitiveWord(item.id); words.value = words.value.filter((entry) => entry.id !== item.id); messageApi?.success('敏感词已删除') } catch (err) { messageApi?.error(err instanceof Error ? err.message : '删除失败') } }
async function stopActivity(activity: Activity) { try { const updated = await opsApi.stopActivity(activity.id, '运营停止'); activity.status = updated.status; messageApi?.success('活动已停止') } catch (err) { messageApi?.error(err instanceof Error ? err.message : '操作失败') } }
async function mute() { const roomId = Number(muteRoomId.value); const userId = Number(muteUserId.value); if (!Number.isInteger(roomId) || roomId <= 0 || !Number.isInteger(userId) || userId <= 0 || !muteReason.value.trim()) return; try { await opsApi.muteUser(roomId, { userId, reason: muteReason.value.trim(), expiresMinutes: Number(muteMinutes.value) || null }); messageApi?.success('房间禁言已生效') } catch (err) { messageApi?.error(err instanceof Error ? err.message : '禁言失败') } }
async function unmute() { const roomId = Number(muteRoomId.value); const userId = Number(muteUserId.value); if (!Number.isInteger(roomId) || roomId <= 0 || !Number.isInteger(userId) || userId <= 0) return; try { await opsApi.unmuteUser(roomId, userId); messageApi?.success('房间禁言已解除') } catch (err) { messageApi?.error(err instanceof Error ? err.message : '解除禁言失败') } }
</script>

<template>
  <main class="page-container ops-page">
    <div class="ops-heading"><div><p class="eyebrow">OPERATIONS CENTER</p><h1 class="page-title">把每个房间，<span class="gradient-text">管理得更好。</span></h1><p class="page-subtitle">监控平台状态，处理内容和用户治理，让直播现场保持健康。</p></div><NTag :type="error ? 'warning' : 'success'" round>{{ error ? '部分数据不可用' : '系统运行正常' }}</NTag></div>
    <NSpin v-if="loading" class="loading-block" />
    <div v-else-if="error && !summary && !rooms" class="empty-state surface error-state"><p>{{ error }}</p><NButton secondary @click="loadAll">重新加载</NButton></div>
    <template v-else>
      <div class="metric-grid ops-metrics"><StatCard label="直播间" :value="summary?.liveRoomCount ?? 0" accent="purple" /><StatCard label="在线人数" :value="summary?.onlineCount ?? 0" accent="cyan" /><StatCard label="礼物订单" :value="summary?.giftOrderCount ?? 0" accent="pink" /><StatCard label="待处理订单" :value="summary?.pendingOrderCount ?? 0" accent="green" /></div>
      <p v-if="error" class="partial-warning">部分运营模块加载失败，已保留可用数据。</p>
      <NTabs v-model:value="activeTab" type="line" animated class="ops-tabs">
        <NTabPane name="overview" tab="概览"><div class="ops-overview surface"><h2>治理工作台</h2><p class="muted">从下方模块进入具体管理。所有运营动作都会记录审计日志。</p><div class="overview-links"><button type="button" @click="activeTab = 'rooms'"><strong>直播审核</strong><span>{{ rooms?.total ?? 0 }} 个房间</span></button><button type="button" @click="activeTab = 'users'"><strong>用户治理</strong><span>{{ summary?.bannedUserCount ?? 0 }} 个封禁用户</span></button><button type="button" @click="activeTab = 'content'"><strong>内容治理</strong><span>{{ moderationLogs?.total ?? 0 }} 条拦截记录</span></button><button type="button" @click="activeTab = 'words'"><strong>敏感词库</strong><span>{{ words.length }} 个词</span></button><button type="button" @click="activeTab = 'activities'"><strong>活动管理</strong><span>{{ activities?.total ?? 0 }} 个活动</span></button><button type="button" @click="activeTab = 'audit'"><strong>操作日志</strong><span>{{ auditLogs?.total ?? 0 }} 条记录</span></button></div></div></NTabPane>
        <NTabPane name="rooms" tab="直播审核"><div class="table-card surface"><div class="table-head"><h2>直播间列表</h2><NButton secondary size="small" @click="loadAll">刷新</NButton></div><div class="data-table"><div class="data-row data-header"><span>房间</span><span>主播</span><span>状态</span><span>操作</span></div><div v-for="room in rooms?.items" :key="room.id" class="data-row"><span>{{ room.title }} <small>#{{ room.id }}</small></span><span>用户 {{ room.anchorId }}</span><span><NTag size="small" round :type="room.status === 'LIVE' ? 'success' : 'default'">{{ room.status }}</NTag></span><span><NButton v-if="room.status === 'LIVE'" size="small" type="error" secondary @click="stopRoom(room)">停止直播</NButton></span></div></div></div></NTabPane>
        <NTabPane name="users" tab="用户治理"><div class="table-card surface"><div class="table-head"><h2>用户列表</h2></div><div class="data-table"><div class="data-row data-header"><span>用户</span><span>角色</span><span>状态</span><span>操作</span></div><div v-for="user in users?.items" :key="user.id" class="data-row"><span>{{ user.nickname }} <small>#{{ user.id }}</small></span><span>{{ user.role }}</span><span><NTag size="small" round :type="user.status === 'BANNED' ? 'error' : 'success'">{{ user.status }}</NTag></span><span><NButton size="small" :type="user.status === 'BANNED' ? 'success' : 'error'" secondary @click="toggleBan(user)">{{ user.status === 'BANNED' ? '解封' : '封禁' }}</NButton></span></div></div></div></NTabPane>
        <NTabPane name="activities" tab="活动管理"><div class="table-card surface"><div class="table-head"><h2>活动列表</h2></div><div class="data-table"><div class="data-row data-header"><span>活动</span><span>房间</span><span>状态</span><span>操作</span></div><div v-for="activity in activities?.items" :key="activity.id" class="data-row"><span>{{ activity.name }} <small>#{{ activity.id }}</small></span><span>#{{ activity.roomId }}</span><span>{{ activity.status }}</span><span><NButton v-if="activity.status === 'ACTIVE'" size="small" type="error" secondary @click="stopActivity(activity)">停止</NButton></span></div></div></div></NTabPane>
        <NTabPane name="content" tab="内容治理"><div class="table-card surface"><div class="table-head"><div><h2>弹幕拦截记录</h2><p class="muted">敏感词命中后会保留记录，便于复核。</p></div><NButton secondary size="small" @click="loadAll">刷新</NButton></div><div class="data-table"><div class="data-row data-header"><span>房间 / 用户</span><span>命中词</span><span>内容</span><span>时间</span></div><div v-for="item in moderationLogs?.items" :key="item.id" class="data-row"><span>#{{ item.roomId }} / 用户 {{ item.userId }}</span><span><NTag type="error" size="small">{{ item.matchedWord }}</NTag></span><span class="content-preview">{{ item.content }}</span><span>{{ item.createdAt }}</span></div></div><div class="mute-form"><h3>房间禁言</h3><div class="mute-fields"><NInput v-model:value="muteRoomId" placeholder="房间 ID" /><NInput v-model:value="muteUserId" placeholder="用户 ID" /><NInput v-model:value="muteReason" placeholder="禁言原因" /><NInput v-model:value="muteMinutes" placeholder="时长（分钟，可空）" /></div><div class="mute-actions"><NButton type="error" secondary @click="mute">禁言用户</NButton><NButton secondary @click="unmute">解除禁言</NButton></div></div></div></NTabPane>
        <NTabPane name="words" tab="敏感词库"><div class="table-card surface"><div class="table-head"><h2>敏感词管理</h2><div class="word-add"><NInput v-model:value="word" size="small" placeholder="添加敏感词" @keyup.enter="addWord" /><NButton size="small" type="primary" @click="addWord">添加</NButton></div></div><div class="word-list"><div v-for="item in words" :key="item.id" class="word-row"><span>{{ item.word }}</span><NButton text type="error" size="small" @click="removeWord(item)">删除</NButton></div></div></div></NTabPane>
        <NTabPane name="audit" tab="操作日志"><div class="table-card surface"><div class="table-head"><h2>运营操作日志</h2><NButton secondary size="small" @click="loadAll">刷新</NButton></div><div class="data-table"><div class="data-row data-header"><span>操作</span><span>对象</span><span>原因</span><span>时间</span></div><div v-for="item in auditLogs?.items" :key="item.id" class="data-row"><span>{{ item.action }}</span><span>{{ item.targetType }} #{{ item.targetId }} / 用户 {{ item.operatorId }}</span><span>{{ item.reason || '无' }}</span><span>{{ item.createdAt }}</span></div></div></div></NTabPane>
      </NTabs>
    </template>
  </main>
</template>

<style scoped>
.ops-heading { display: flex; align-items: end; justify-content: space-between; gap: 20px; }
.ops-metrics { margin: 46px 0 34px; }
.partial-warning { margin: 0 0 18px; color: var(--sh-warning); font-size: 13px; }
.ops-tabs { padding: 0 4px; }
.ops-overview, .table-card { padding: 22px; }
.ops-overview h2, .table-head h2 { margin: 0; color: var(--sh-ink); font-size: 19px; }
.ops-overview > p { margin: 8px 0 24px; font-size: 13px; }
.overview-links { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.overview-links button { display: flex; align-items: start; justify-content: space-between; gap: 12px; padding: 17px; border: 1px solid var(--sh-border); border-radius: 10px; color: var(--sh-ink); background: var(--sh-surface-muted); cursor: pointer; text-align: left; }
.overview-links button:hover { border-color: var(--sh-primary); }
.overview-links span { color: var(--sh-muted); font-size: 12px; }
.table-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 18px; }
.data-row { display: grid; grid-template-columns: 1.4fr .9fr .8fr .8fr; gap: 10px; align-items: center; min-height: 48px; border-bottom: 1px solid var(--sh-border); color: var(--sh-ink); font-size: 13px; }
.data-header { min-height: 36px; color: var(--sh-muted); font-size: 12px; }
.data-row small { color: var(--sh-muted); }
.content-preview { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mute-form { margin-top: 26px; padding-top: 22px; border-top: 1px solid var(--sh-border); }
.mute-form h3 { margin: 0 0 12px; color: var(--sh-ink); font-size: 15px; }
.mute-fields { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
.mute-actions { display: flex; gap: 8px; margin-top: 10px; }
.word-add { display: flex; gap: 8px; }
.word-add .n-input { width: 160px; }
.word-row { display: flex; align-items: center; justify-content: space-between; padding: 12px 0; border-bottom: 1px solid var(--sh-border); color: var(--sh-ink); font-size: 13px; }
@media (max-width: 680px) { .ops-heading { align-items: start; flex-direction: column; } .overview-links { grid-template-columns: 1fr; } .data-row { grid-template-columns: 1.4fr .8fr .9fr; } .data-row > span:last-child { grid-column: 3; grid-row: 1; justify-self: end; } .data-header > span:last-child { display: none; } .mute-fields { grid-template-columns: 1fr 1fr; } }
</style>
