<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NEmpty, NSpin, NTabs, NTabPane } from 'naive-ui'
import { giftApi } from '@/api'
import type { RankEntry } from '@/types/api'

const props = defineProps<{ roomId: number }>()
const contributors = ref<RankEntry[]>([])
const income = ref<RankEntry[]>([])
const loading = ref(true)

onMounted(async () => {
  try { [contributors.value, income.value] = await Promise.all([giftApi.contributorRank(props.roomId), giftApi.incomeRank(props.roomId)]) } finally { loading.value = false }
})
</script>

<template>
  <div class="rank-panel surface">
    <div class="panel-title">榜单</div>
    <NSpin v-if="loading" size="small" />
    <NTabs v-else type="line" animated>
      <NTabPane name="contributors" tab="贡献榜"><div v-if="!contributors.length" class="rank-empty"><NEmpty description="暂无礼物记录" size="small" /></div><div v-for="(item, index) in contributors" :key="item.userId" class="rank-row"><span class="rank-number" :class="`rank-${index + 1}`">{{ index + 1 }}</span><span class="rank-name">用户 {{ item.userId }}</span><strong>{{ item.amount }} 金币</strong></div></NTabPane>
      <NTabPane name="income" tab="收益榜"><div v-if="!income.length" class="rank-empty"><NEmpty description="暂无收益记录" size="small" /></div><div v-for="(item, index) in income" :key="item.userId" class="rank-row"><span class="rank-number" :class="`rank-${index + 1}`">{{ index + 1 }}</span><span class="rank-name">用户 {{ item.userId }}</span><strong>{{ item.amount }} 金币</strong></div></NTabPane>
    </NTabs>
  </div>
</template>

<style scoped>
.rank-panel { padding: 18px; }
.panel-title { margin-bottom: 12px; font-weight: 700; }
.rank-row { display: flex; align-items: center; gap: 10px; padding: 10px 0; border-bottom: 1px solid rgba(255,255,255,.05); font-size: 13px; }
.rank-row:last-child { border-bottom: 0; }
.rank-number { display: grid; place-items: center; width: 22px; height: 22px; border-radius: 7px; color: var(--sh-muted); background: rgba(255,255,255,.06); font-size: 12px; }
.rank-1 { color: #ffe08a; background: rgba(255, 201, 77, .18); } .rank-2 { color: #d9e5f0; } .rank-3 { color: #efa883; }
.rank-name { flex: 1; color: #d4d8e7; }
.rank-row strong { color: #f0b9ff; font-size: 12px; }
.rank-empty { padding: 22px 0; }
</style>
