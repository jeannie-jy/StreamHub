<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { darkTheme, NButton, NConfigProvider, NDropdown, NLayout, NLayoutHeader, NLayoutContent, NMenu, NSpace, NTag } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'
import { useUserStore } from '@/stores/user'

const auth = useAuthStore()
const users = useUserStore()
const route = useRoute()
const router = useRouter()
const profileLoading = ref(false)

const isAuthPage = computed(() => Boolean(route.meta.authPage))
const menuOptions = computed(() => [
  { label: '发现直播', key: '/' },
  ...(auth.isAuthenticated ? [{ label: '创作中心', key: '/creator' }] : []),
  ...(auth.isOperator ? [{ label: '运营后台', key: '/ops' }] : []),
])

onMounted(async () => {
  if (auth.isAuthenticated && !auth.profile && auth.userId) {
    profileLoading.value = true
    try {
      auth.setProfile(await users.loadProfile(auth.userId))
    } finally {
      profileLoading.value = false
    }
  }
})

async function selectMenu(key: string) {
  await router.push(key)
}

async function logout() {
  await auth.logout()
  await router.push('/')
}
</script>

<template>
  <NConfigProvider :theme="darkTheme" :theme-overrides="{ common: { primaryColor: '#8b7cff', primaryColorHover: '#a398ff', borderRadius: '12px' } }">
    <NLayout class="app-shell">
      <NLayoutHeader bordered class="topbar">
        <div class="brand" @click="router.push('/')">
          <span class="brand-mark">S</span>
          <span>StreamHub</span>
          <NTag size="small" round type="info">LIVE</NTag>
        </div>
        <NMenu v-if="!isAuthPage" mode="horizontal" :value="route.path === '/' ? '/' : (route.path.startsWith('/ops') ? '/ops' : route.path.startsWith('/creator') ? '/creator' : '')" :options="menuOptions" class="main-nav" @update:value="selectMenu" />
        <NSpace align="center" class="topbar-actions">
          <template v-if="auth.isAuthenticated">
            <NButton quaternary circle :loading="profileLoading" @click="router.push('/me')">
              <template #icon><span class="avatar-dot">{{ (auth.profile?.nickname || 'U').slice(0, 1) }}</span></template>
            </NButton>
            <NDropdown :options="[{ label: '个人中心', key: 'profile' }, { label: '退出登录', key: 'logout' }]" @select="(key) => key === 'logout' ? logout() : router.push('/me')">
              <NButton text>{{ auth.profile?.nickname || `用户 ${auth.userId}` }}</NButton>
            </NDropdown>
          </template>
          <template v-else>
            <RouterLink to="/auth/login"><NButton quaternary>登录</NButton></RouterLink>
            <RouterLink to="/auth/register"><NButton type="primary">开始使用</NButton></RouterLink>
          </template>
        </NSpace>
      </NLayoutHeader>
      <NLayoutContent content-style="min-height: calc(100vh - 65px)">
        <RouterView />
      </NLayoutContent>
    </NLayout>
  </NConfigProvider>
</template>
