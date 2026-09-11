<script setup lang="ts">
import { inject, onMounted, ref } from 'vue'
import { NButton, NCard, NEmpty, NInput, NSpin, NTabs, NTabPane } from 'naive-ui'
import { activityApi, giftApi, userApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useUserStore } from '@/stores/user'
import type { GiftOrder, ActivityOrder, UserProfile, Wallet } from '@/types/api'

const auth = useAuthStore()
const users = useUserStore()
const messageApi = inject<any>('message')
const profile = ref<UserProfile | null>(null)
const wallet = ref<Wallet | null>(null)
const nickname = ref('')
const giftOrders = ref<GiftOrder[]>([])
const activityOrders = ref<ActivityOrder[]>([])
const loading = ref(true)
const saving = ref(false)

onMounted(async () => {
  try { profile.value = await users.loadProfile(auth.userId); auth.setProfile(profile.value); nickname.value = profile.value.nickname; wallet.value = await giftApi.wallet() } finally { loading.value = false }
})

async function save() {
  saving.value = true
  try { profile.value = await userApi.updateMe({ nickname: nickname.value }); auth.setProfile(profile.value); users.cacheProfile(profile.value); messageApi?.success('资料已更新') } catch (error) { messageApi?.error(error instanceof Error ? error.message : '资料更新失败') } finally { saving.value = false }
}

async function lookupGiftOrder(orderNo: string) { if (!orderNo.trim()) return; try { giftOrders.value.unshift(await giftApi.order(orderNo.trim())) } catch (error) { messageApi?.error(error instanceof Error ? error.message : '订单不存在') } }
async function lookupActivityOrder(orderNo: string) { if (!orderNo.trim()) return; try { activityOrders.value.unshift(await activityApi.order(orderNo.trim())) } catch (error) { messageApi?.error(error instanceof Error ? error.message : '订单不存在') } }
const giftOrderNo = ref('')
const activityOrderNo = ref('')
</script>

<template>
  <main class="page-container profile-page"><NSpin v-if="loading" size="medium" class="loading-block" /><template v-else><div class="profile-hero surface"><div class="profile-avatar">{{ (profile?.nickname || 'U').slice(0, 1) }}</div><div class="profile-copy"><p class="eyebrow">YOUR SPACE</p><h1>{{ profile?.nickname }}</h1><p class="muted">@{{ profile?.username }} · {{ profile?.followerCount || 0 }} 位关注者</p></div><NButton type="primary" secondary tag="a" href="/creator">进入创作中心</NButton></div><div class="profile-grid"><NCard title="个人资料" class="surface-card"><NInput v-model:value="nickname" placeholder="昵称" /><NButton type="primary" :loading="saving" style="margin-top: 12px" @click="save">保存修改</NButton></NCard><NCard title="虚拟钱包" class="surface-card"><div class="wallet-number">{{ wallet?.balance ?? 0 }} <small>金币</small></div><p class="muted">虚拟充值仅用于项目演示</p></NCard></div><div class="section-heading"><div><h2>订单查询</h2><p>输入订单号查看异步处理结果</p></div></div><NTabs type="line" animated class="orders surface"><NTabPane name="gift" tab="礼物订单"><div class="order-search"><NInput v-model:value="giftOrderNo" placeholder="gift-order-001" @keyup.enter="lookupGiftOrder(giftOrderNo)" /><NButton @click="lookupGiftOrder(giftOrderNo)">查询</NButton></div><NEmpty v-if="!giftOrders.length" description="还没有查询过礼物订单" /><div v-for="order in giftOrders" :key="order.orderNo" class="order-row"><span class="mono">{{ order.orderNo }}</span><strong>{{ order.status }}</strong><span>{{ order.giftCode }} × {{ order.quantity }}</span><span>{{ order.totalAmount }} 金币</span></div></NTabPane><NTabPane name="activity" tab="活动订单"><div class="order-search"><NInput v-model:value="activityOrderNo" placeholder="activity-order-001" @keyup.enter="lookupActivityOrder(activityOrderNo)" /><NButton @click="lookupActivityOrder(activityOrderNo)">查询</NButton></div><NEmpty v-if="!activityOrders.length" description="还没有查询过活动订单" /><div v-for="order in activityOrders" :key="order.orderNo" class="order-row"><span class="mono">{{ order.orderNo }}</span><strong>{{ order.status }}</strong><span>活动 #{{ order.activityId }}</span></div></NTabPane></NTabs></template></main>
</template>

<style scoped>
.profile-hero { display: flex; align-items: center; gap: 18px; padding: 28px; } .profile-hero .n-button { margin-left: auto; } .profile-avatar { display: grid; place-items: center; width: 72px; height: 72px; border-radius: 22px; color: white; background: linear-gradient(135deg,#ff8faf,#7568ff); font-size: 30px; font-weight: 800; } .profile-copy h1 { margin: 0 0 5px; font-size: 30px; } .profile-copy .eyebrow { margin-bottom: 8px; }
.profile-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-top: 18px; } .surface-card :deep(.n-card__content) { background: transparent; } .wallet-number { color: #f8d079; font-size: 38px; font-weight: 800; } .wallet-number small { font-size: 14px; font-weight: 500; }
.orders { padding: 0 18px 18px; } .order-search { display: flex; gap: 8px; max-width: 500px; margin: 12px 0 20px; } .order-search .n-input { flex: 1; } .order-row { display: grid; grid-template-columns: 1.4fr .7fr 1fr .8fr; gap: 10px; padding: 12px 0; border-bottom: 1px solid var(--sh-border); color: var(--sh-muted); font-size: 13px; } .order-row strong { color: #71e5bd; }
@media (max-width: 640px) { .profile-hero { align-items: start; flex-wrap: wrap; } .profile-hero .n-button { width: 100%; margin-left: 0; } .profile-grid { grid-template-columns: 1fr; } .order-row { grid-template-columns: 1fr 1fr; } }
</style>
