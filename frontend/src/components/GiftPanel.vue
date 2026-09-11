<script setup lang="ts">
import { inject, onMounted, ref } from 'vue'
import { NButton, NInputNumber, NModal, NSpace, NTag } from 'naive-ui'
import { giftApi } from '@/api'
import { useWalletStore } from '@/stores/wallet'
import type { GiftCatalog } from '@/types/api'

const props = defineProps<{ roomId: number; enabled?: boolean }>()
const messageApi = inject<any>('message')
const wallet = useWalletStore()
const selected = ref<GiftCatalog | null>(null)
const quantity = ref(1)
const showRecharge = ref(false)
const rechargeAmount = ref(1000)
const sending = ref(false)

onMounted(async () => {
  if (!wallet.gifts.length) await wallet.load()
})

async function send() {
  if (!selected.value) return
  sending.value = true
  try {
    const order = await giftApi.send(props.roomId, { giftCode: selected.value.code, quantity: quantity.value, clientOrderNo: `gift-${Date.now()}-${crypto.randomUUID().slice(0, 8)}` })
    messageApi?.success(`礼物已送出，订单状态：${order.status}`)
    await pollOrder(order.orderNo)
  } catch (error) { messageApi?.error(error instanceof Error ? error.message : '送礼失败') } finally { sending.value = false }
}

async function pollOrder(orderNo: string) {
  for (let i = 0; i < 8; i += 1) {
    await new Promise((resolve) => window.setTimeout(resolve, 800))
    const order = await giftApi.order(orderNo)
    if (order.status !== 'PENDING') { messageApi?.[order.status === 'SUCCESS' ? 'success' : 'error'](order.status === 'SUCCESS' ? '礼物订单处理成功' : order.failureReason || '礼物订单处理失败'); await wallet.load(); return }
  }
}

async function recharge() {
  try { await wallet.recharge(rechargeAmount.value); showRecharge.value = false; messageApi?.success('金币充值成功') } catch (error) { messageApi?.error(error instanceof Error ? error.message : '充值失败') }
}
</script>

<template>
  <div class="gift-panel surface">
    <div class="panel-head"><div><strong>送出礼物</strong><div class="panel-caption">用虚拟金币支持主播</div></div><NButton text size="small" @click="showRecharge = true">充值金币</NButton></div>
    <div class="balance-row"><span>我的余额</span><strong>{{ wallet.wallet?.balance ?? '—' }}</strong><span class="coin">金币</span></div>
    <div class="gift-list"><button v-for="gift in wallet.gifts" :key="gift.code" class="gift-option" :class="{ selected: selected?.code === gift.code }" :disabled="enabled === false" @click="selected = gift"><span class="gift-emoji">{{ gift.code === 'rocket' ? '🚀' : gift.code === 'crown' ? '👑' : '🌹' }}</span><span>{{ gift.name }}</span><small>{{ gift.price }}</small></button></div>
    <div class="gift-actions"><NInputNumber v-model:value="quantity" :min="1" :max="99" size="small" /><NButton type="primary" :loading="sending" :disabled="!selected || enabled === false" @click="send">送出 {{ selected?.name || '礼物' }}</NButton></div>
    <NTag v-if="enabled === false" type="warning" size="small">登录后即可送礼</NTag>
    <NModal v-model:show="showRecharge" preset="card" title="充值虚拟金币" style="width: min(380px, calc(100vw - 32px))"><NSpace vertical><p class="muted">这是项目演示用的虚拟充值，不会产生真实扣款。</p><NInputNumber v-model:value="rechargeAmount" :min="1" :step="100" /><NButton type="primary" block @click="recharge">确认充值</NButton></NSpace></NModal>
  </div>
</template>

<style scoped>
.gift-panel { padding: 18px; }
.panel-head { display: flex; align-items: start; justify-content: space-between; gap: 10px; }
.panel-caption { margin-top: 4px; color: var(--sh-muted); font-size: 12px; }
.balance-row { display: flex; align-items: baseline; gap: 7px; margin: 18px 0 14px; color: var(--sh-muted); font-size: 12px; }
.balance-row strong { margin-left: auto; color: #f8d079; font-size: 23px; }
.coin { color: #f8d079; }
.gift-list { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.gift-option { display: flex; align-items: center; flex-direction: column; gap: 4px; padding: 10px 6px; border: 1px solid var(--sh-border); border-radius: 12px; color: #dce0ec; background: rgba(255,255,255,.03); cursor: pointer; }
.gift-option:hover, .gift-option.selected { border-color: rgba(139,124,255,.8); background: rgba(139,124,255,.14); }
.gift-option small { color: #f8d079; }
.gift-emoji { font-size: 24px; }
.gift-actions { display: grid; grid-template-columns: 90px 1fr; gap: 8px; margin-top: 14px; }
.gift-panel > .n-tag { margin-top: 12px; }
</style>
