<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NButton, NEmpty, NSpin, NTabs, NTabPane } from 'naive-ui'
import { giftApi } from '@/api'
import type { RankEntry } from '@/types/api'

const props = defineProps<{ roomId: number }>()
const contributors = ref<RankEntry[]>([])
const income = ref<RankEntry[]>([])
const loading = ref(true)
const error = ref('')

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [nextContributors, nextIncome] = await Promise.all([giftApi.contributorRank(props.roomId), giftApi.incomeRank(props.roomId)])
    contributors.value = nextContributors
    income.value = nextIncome
  } catch (err) {
    error.value = err instanceof Error ? err.message : '榜单加载失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="rank-panel surface">
    <div class="panel-title">房间榜单</div>
    <NSpin v-if="loading" size="small" />
    <div v-else-if="error" class="rank-error"><span>{{ error }}</span><NButton text size="small" @click="load">重试</NButton></div>
    <NTabs v-else type="line" animated>
      <NTabPane name="contributors" tab="贡献榜"><div v-if="!contributors.length" class="rank-empty"><NEmpty description="暂无礼物记录" size="small" /></div><div v-for="(item, index) in contributors" :key="item.userId" class="rank-row"><span class="rank-number">{{ index + 1 }}</span><span class="rank-name">{{ item.nickname || `用户 ${item.userId}` }}</span><strong>{{ item.amount }} 金币</strong></div></NTabPane>
      <NTabPane name="income" tab="收益榜"><div v-if="!income.length" class="rank-empty"><NEmpty description="暂无收益记录" size="small" /></div><div v-for="(item, index) in income" :key="item.userId" class="rank-row"><span class="rank-number">{{ index + 1 }}</span><span class="rank-name">{{ item.nickname || `用户 ${item.userId}` }}</span><strong>{{ item.amount }} 金币</strong></div></NTabPane>
    </NTabs>
  </div>
</template>

<style scoped>
.rank-panel { padding: 18px; }
.panel-title { margin-bottom: 12px; color: var(--sh-ink); font-weight: 700; }
.rank-row { display: flex; align-items: center; gap: 10px; padding: 10px 0; border-bottom: 1px solid var(--sh-border); font-size: 13px; }
.rank-row:last-child { border-bottom: 0; }
.rank-number { display: grid; place-items: center; width: 22px; height: 22px; border-radius: 7px; color: var(--sh-muted); background: var(--sh-surface-muted); font-size: 12px; }
.rank-name { flex: 1; color: var(--sh-ink); }
.rank-row strong { color: var(--sh-primary); font-size: 12px; }
.rank-empty { padding: 22px 0; }
.rank-error { display: flex; align-items: center; justify-content: center; gap: 10px; color: var(--sh-danger); font-size: 13px; }
</style>
