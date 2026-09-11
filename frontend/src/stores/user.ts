import { ref } from 'vue'
import { defineStore } from 'pinia'
import { userApi } from '@/api'
import type { UserProfile } from '@/types/api'

export const useUserStore = defineStore('user', () => {
  const profiles = ref<Record<number, UserProfile>>({})
  const following = ref<Record<number, boolean>>({})

  async function loadProfile(userId: number) {
    if (!profiles.value[userId]) profiles.value[userId] = await userApi.profile(userId)
    return profiles.value[userId]
  }

  async function loadFollowStatus(anchorId: number) {
    following.value[anchorId] = (await userApi.followStatus(anchorId)).following
    return following.value[anchorId]
  }

  async function toggleFollow(anchorId: number) {
    following.value[anchorId] = following.value[anchorId]
      ? (await userApi.unfollow(anchorId)).following
      : (await userApi.follow(anchorId)).following
    return following.value[anchorId]
  }

  function cacheProfile(profile: UserProfile) {
    profiles.value[profile.id] = profile
  }

  return { profiles, following, loadProfile, loadFollowStatus, toggleFollow, cacheProfile }
})
