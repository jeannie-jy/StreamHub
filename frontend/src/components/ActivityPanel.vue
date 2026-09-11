<script setup lang="ts">
import { computed, inject, onBeforeUnmount, ref } from 'vue'
import { NButton, NProgress, NTag } from 'naive-ui'
import { activityApi } from '@/api'
import type { Activity, ActivityOrder } from '@/types/api'

const props = defineProps<{ activity: Activity; enabled?: boolean }>()
const messageApi = inject<any>('message')
const current = ref({ ...props.activity })
const order = ref<ActivityOrder | null>(null)
const loading = ref(false)
let timer: number | undefined

const progress = computed(() => current.value.stock ? Math.round(((current.value.stock - current.value.remainingStock) / current.value.stock) * 100) : 0)
const active = computed(() => current.value.status === 'ACTIVE' || current.value.status === 'RUNNING')

async function seckill() {
  loading.value = true
  try {
    order.value = await activityApi.seckill(current.value.id, `activity-${Date.now()}-${crypto.randomUUID().slice(0, 8)}`)
    messageApi?.success('已进入抢购队列')
    poll()
  } catch (error) { messageApi?.error(error instanceof Error ? error.message : '抢购失败') } finally { loading.value = false }
}

function poll() {
  if (!order.value) return
  timer = window.setInterval(async () => {
    if (!order.value) return
    order.value = await activityApi.order(order.value.orderNo)
    if (order.value.status !== 'PENDING') { window.clearInterval(timer); messageApi?.[order.value.status === 'SUCCESS' ? 'success' : 'error'](order.value.status === 'SUCCESS' ? '抢购成功' : '抢购未成功') }
  }, 1_200)
}

onBeforeUnmount(() => window.clearInterval(timer))
</script>

<template>
  <div class="activity-card surface">
    <div class="activity-head"><div><NTag type="warning" size="small" round>限时活动</NTag><h3>{{ current.name }}</h3></div><span class="activity-stock">剩余 {{ current.remainingStock }}</span></div>
    <NProgress type="line" :percentage="progress" :show-indicator="false" color="#ff6fae" rail-color="rgba(255,255,255,.08)" />
    <div class="activity-foot"><span class="muted">{{ current.unitPrice }} 金币 / 份</span><NButton type="primary" size="small" :loading="loading" :disabled="!active || current.remainingStock <= 0 || enabled === false" @click="seckill">{{ current.remainingStock <= 0 ? '已售罄' : order?.status === 'PENDING' ? '排队中' : '立即抢购' }}</NButton></div>
    <p v-if="order" class="order-status">订单 {{ order.orderNo }} · {{ order.status }}</p>
  </div>
</template>

<style scoped>
.activity-card { padding: 18px; }
.activity-head { display: flex; justify-content: space-between; gap: 12px; align-items: start; }
.activity-head h3 { margin: 10px 0 16px; font-size: 17px; }
.activity-stock { color: #ff9cbd; font-size: 13px; }
.activity-foot { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: 15px; }
.order-status { margin: 13px 0 0; color: var(--sh-muted); font-family: monospace; font-size: 11px; }
</style>
