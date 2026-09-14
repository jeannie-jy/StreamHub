import { ref } from 'vue'
import { defineStore } from 'pinia'
import { giftApi } from '@/api'
import type { GiftCatalog, Wallet } from '@/types/api'

export const useWalletStore = defineStore('wallet', () => {
  const wallet = ref<Wallet | null>(null)
  const gifts = ref<GiftCatalog[]>([])

  async function loadCatalog() {
    if (!gifts.value.length) gifts.value = await giftApi.catalog()
    return gifts.value
  }

  async function loadBalance() {
    wallet.value = await giftApi.wallet()
    return wallet.value
  }

  async function load() {
    await Promise.all([loadBalance(), loadCatalog()])
  }

  async function recharge(amount: number) {
    const result = await giftApi.recharge({ bizNo: `recharge-${Date.now()}`, amount })
    wallet.value = result
    return result
  }

  return { wallet, gifts, load, loadCatalog, loadBalance, recharge }
})
