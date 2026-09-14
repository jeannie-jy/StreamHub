<script setup lang="ts">
import { inject, onMounted, ref } from 'vue'
import { NButton, NCard, NEmpty, NInput, NSpin, NTabs, NTabPane } from 'naive-ui'
import { activityApi, giftApi, liveApi, userApi } from '@/api'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useUserStore } from '@/stores/user'
import RoomCard from '@/components/RoomCard.vue'
import type { ActivityOrder, GiftOrder, LiveRoom, UserProfile, Wallet } from '@/types/api'

const auth = useAuthStore()
const users = useUserStore()
const messageApi = inject<any>('message')
const profile = ref<UserProfile | null>(null)
const wallet = ref<Wallet | null>(null)
const nickname = ref('')
const giftOrders = ref<GiftOrder[]>([])
const activityOrders = ref<ActivityOrder[]>([])
const followingUsers = ref<UserProfile[]>([])
const favoriteRooms = ref<LiveRoom[]>([])
const loading = ref(true)
const error = ref('')
const saving = ref(false)
const activeTab = ref('profile')
const giftOrderNo = ref('')
const activityOrderNo = ref('')

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    profile.value = await users.loadProfile(auth.userId)
    auth.setProfile(profile.value)
    nickname.value = profile.value.nickname
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '个人资料加载失败'
    loading.value = false
    return
  }
  const results = await Promise.allSettled([
    giftApi.wallet(),
    giftApi.myOrders({ page: 1, pageSize: 20 }),
    activityApi.myOrders({ page: 1, pageSize: 20 }),
    userApi.following({ page: 1, pageSize: 20 }),
    liveApi.favorites({ page: 1, pageSize: 20 }),
  ])
  wallet.value = results[0].status === 'fulfilled' ? results[0].value : null
  giftOrders.value = results[1].status === 'fulfilled' ? results[1].value.items : []
  activityOrders.value = results[2].status === 'fulfilled' ? results[2].value.items : []
  followingUsers.value = results[3].status === 'fulfilled' ? results[3].value.items : []
  favoriteRooms.value = results[4].status === 'fulfilled' ? results[4].value.items : []
  loading.value = false
}

async function save() {
  const nextNickname = nickname.value.trim()
  if (!nextNickname) { messageApi?.error('昵称不能为空'); return }
  saving.value = true
  try {
    profile.value = await userApi.updateMe({ nickname: nextNickname })
    auth.setProfile(profile.value)
    users.cacheProfile(profile.value)
    messageApi?.success('资料已更新')
  } catch (err) {
    messageApi?.error(err instanceof Error ? err.message : '资料更新失败')
  } finally {
    saving.value = false
  }
}

async function lookupGiftOrder(value: string) {
  if (!value.trim()) return
  try { giftOrders.value.unshift(await giftApi.order(value.trim())) } catch (err) { messageApi?.error(err instanceof Error ? err.message : '订单不存在') }
}

async function lookupActivityOrder(value: string) {
  if (!value.trim()) return
  try { activityOrders.value.unshift(await activityApi.order(value.trim())) } catch (err) { messageApi?.error(err instanceof Error ? err.message : '订单不存在') }
}
</script>

<template>
  <main class="page-container profile-page">
    <NSpin v-if="loading" size="medium" class="loading-block" />
    <div v-else-if="error" class="empty-state surface error-state"><p>{{ error }}</p><NButton secondary @click="load">重新加载</NButton></div>
    <template v-else>
      <div class="profile-hero surface"><div class="profile-avatar">{{ (profile?.nickname || 'U').slice(0, 1) }}</div><div class="profile-copy"><p class="eyebrow">YOUR SPACE</p><h1>{{ profile?.nickname }}</h1><p class="muted">@{{ profile?.username }} / {{ profile?.followerCount || 0 }} 位关注者</p></div><NButton type="primary" secondary @click="$router.push('/creator')">进入创作中心</NButton></div>
      <div class="profile-grid"><NCard title="个人资料" class="surface-card"><NInput v-model:value="nickname" maxlength="64" placeholder="昵称" /><NButton type="primary" :loading="saving" style="margin-top: 12px" @click="save">保存修改</NButton></NCard><NCard title="虚拟钱包" class="surface-card"><div class="wallet-number">{{ wallet?.balance ?? 0 }} <small>金币</small></div><p class="muted">虚拟充值仅用于项目演示。</p></NCard></div>
      <div class="section-heading"><div><p class="eyebrow">YOUR ACTIVITY</p><h2>我的内容</h2><p>查看关注、收藏和订单。</p></div></div>
      <NTabs v-model:value="activeTab" type="line" animated class="profile-tabs">
        <NTabPane name="profile" tab="关注主播"><div v-if="!followingUsers.length" class="empty-state surface"><NEmpty description="还没有关注主播" /></div><div v-else class="following-profile-list"><div v-for="user in followingUsers" :key="user.id" class="following-profile surface"><span class="profile-avatar small">{{ user.nickname.slice(0, 1) }}</span><div><strong>{{ user.nickname }}</strong><p>@{{ user.username }} / {{ user.followerCount }} 位关注者</p></div></div></div></NTabPane>
        <NTabPane name="favorites" tab="收藏直播间"><div v-if="!favoriteRooms.length" class="empty-state surface"><NEmpty description="还没有收藏直播间" /></div><div v-else class="room-grid"><RoomCard v-for="room in favoriteRooms" :key="room.id" :room="room" /></div></NTabPane>
        <NTabPane name="orders" tab="订单查询"><div class="orders surface"><div class="order-block"><h3>礼物订单</h3><div class="order-search"><NInput v-model:value="giftOrderNo" placeholder="输入订单号" @keyup.enter="lookupGiftOrder(giftOrderNo)" /><NButton @click="lookupGiftOrder(giftOrderNo)">查询</NButton></div><NEmpty v-if="!giftOrders.length" description="还没有礼物订单" /><div v-for="order in giftOrders" :key="order.orderNo" class="order-row"><span class="mono">{{ order.orderNo }}</span><strong>{{ order.status }}</strong><span>{{ order.giftCode }} / {{ order.quantity }}</span><span>{{ order.totalAmount }} 金币</span></div></div><div class="order-block"><h3>活动订单</h3><div class="order-search"><NInput v-model:value="activityOrderNo" placeholder="输入订单号" @keyup.enter="lookupActivityOrder(activityOrderNo)" /><NButton @click="lookupActivityOrder(activityOrderNo)">查询</NButton></div><NEmpty v-if="!activityOrders.length" description="还没有活动订单" /><div v-for="order in activityOrders" :key="order.orderNo" class="order-row"><span class="mono">{{ order.orderNo }}</span><strong>{{ order.status }}</strong><span>活动 #{{ order.activityId }}</span></div></div></div></NTabPane>
      </NTabs>
    </template>
  </main>
</template>

<style scoped>
.profile-hero { display: flex; align-items: center; gap: 18px; padding: 28px; }
.profile-hero .n-button { margin-left: auto; }
.profile-avatar { display: grid; place-items: center; width: 72px; height: 72px; border: 1px solid var(--sh-border-strong); border-radius: 22px; color: var(--sh-primary-strong); background: var(--sh-primary-soft); font-size: 30px; font-weight: 800; }
.profile-avatar.small { width: 44px; height: 44px; border-radius: 50%; font-size: 18px; }
.profile-copy h1 { margin: 0 0 5px; color: var(--sh-ink); font-size: 30px; }
.profile-copy .eyebrow { margin-bottom: 8px; }
.profile-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-top: 18px; }
.surface-card :deep(.n-card__content), .surface-card :deep(.n-card-header) { background: transparent; }
.wallet-number { color: var(--sh-ink); font-size: 38px; font-weight: 800; }
.wallet-number small { color: var(--sh-muted); font-size: 14px; font-weight: 500; }
.profile-tabs :deep(.n-tabs-pane-wrapper) { padding-top: 20px; }
.following-profile-list { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.following-profile { display: flex; align-items: center; gap: 12px; padding: 16px; }
.following-profile strong { color: var(--sh-ink); }
.following-profile p { margin: 5px 0 0; color: var(--sh-muted); font-size: 12px; }
.orders { display: grid; gap: 24px; padding: 20px; }
.order-block h3 { margin: 0 0 12px; color: var(--sh-ink); }
.order-search { display: flex; gap: 8px; max-width: 500px; margin: 0 0 18px; }
.order-search .n-input { flex: 1; }
.order-row { display: grid; grid-template-columns: 1.4fr .7fr 1fr .8fr; gap: 10px; padding: 12px 0; border-bottom: 1px solid var(--sh-border); color: var(--sh-muted); font-size: 13px; }
.order-row strong { color: var(--sh-primary); }
@media (max-width: 640px) { .profile-hero { align-items: start; flex-wrap: wrap; } .profile-hero .n-button { width: 100%; margin-left: 0; } .profile-grid, .following-profile-list { grid-template-columns: 1fr; } .order-row { grid-template-columns: 1fr 1fr; } }
</style>
