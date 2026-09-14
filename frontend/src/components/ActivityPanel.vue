<script setup lang="ts">
import { computed, inject, onBeforeUnmount, ref, watch } from 'vue'
import { NButton, NProgress, NTag } from 'naive-ui'
import { useRoute, useRouter } from 'vue-router'
import { activityApi } from '@/api'
import type { Activity, ActivityOrder } from '@/types/api'

const props = defineProps<{ activity: Activity; enabled?: boolean; readonly?: boolean }>()
const messageApi = inject<any>('message')
const router = useRouter()
const route = useRoute()
const current = ref({ ...props.activity })
const order = ref<ActivityOrder | null>(null)
const loading = ref(false)
let timer: number | undefined

const progress = computed(() => current.value.stock ? Math.min(100, Math.max(0, Math.round(((current.value.stock - current.value.remainingStock) / current.value.stock) * 100))) : 0)
const active = computed(() => current.value.status === 'ACTIVE' || current.value.status === 'RUNNING')

watch(() => props.activity, (next) => { current.value = { ...next }; order.value = null }, { deep: true })

async function seckill() {
  if (props.enabled === false) { await router.push({ name: 'login', query: { redirect: route.fullPath } }); return }
  if (props.readonly || !active.value || current.value.remainingStock <= 0) return
  loading.value = true
  try {
    order.value = await activityApi.seckill(current.value.id, `activity-${Date.now()}-${crypto.randomUUID().slice(0, 8)}`)
    messageApi?.success('已进入抢购队列')
    schedulePoll()
  } catch (err) {
    messageApi?.error(err instanceof Error ? err.message : '抢购失败')
  } finally {
    loading.value = false
  }
}

function schedulePoll() {
  window.clearTimeout(timer)
  if (!order.value || order.value.status !== 'PENDING') return
  timer = window.setTimeout(async () => {
    if (!order.value) return
    try {
      order.value = await activityApi.order(order.value.orderNo)
      if (order.value.status !== 'PENDING') messageApi?.[order.value.status === 'SUCCESS' ? 'success' : 'error'](order.value.status === 'SUCCESS' ? '抢购成功' : '抢购未成功')
      else schedulePoll()
    } catch { messageApi?.warning('订单查询失败，请稍后在个人中心查看') }
  }, 1_200)
}

onBeforeUnmount(() => window.clearTimeout(timer))
</script>

<template>
  <div class="activity-card surface">
    <div class="activity-head"><div><NTag type="warning" size="small" round>限时活动</NTag><h3>{{ current.name }}</h3></div><span class="activity-stock">剩余 {{ current.remainingStock }}</span></div>
    <NProgress type="line" :percentage="progress" :show-indicator="false" color="#236b52" rail-color="#e1e6e1" />
    <div class="activity-foot"><span class="muted">{{ current.unitPrice }} 金币 / 份</span><NButton type="primary" size="small" :loading="loading" :disabled="readonly || !active || current.remainingStock <= 0" @click="seckill">{{ readonly ? '仅供查看' : current.remainingStock <= 0 ? '已售罄' : order?.status === 'PENDING' ? '排队中' : enabled === false ? '登录后参与' : '立即抢购' }}</NButton></div>
    <p v-if="order" class="order-status">订单 {{ order.orderNo }} / {{ order.status }}</p>
  </div>
</template>

<style scoped>
.activity-card { padding: 18px; }
.activity-head { display: flex; justify-content: space-between; gap: 12px; align-items: start; }
.activity-head h3 { margin: 10px 0 16px; color: var(--sh-ink); font-size: 17px; }
.activity-stock { color: var(--sh-danger); font-size: 13px; }
.activity-foot { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: 15px; }
.order-status { margin: 13px 0 0; color: var(--sh-muted); font-family: monospace; font-size: 11px; }
</style>
