<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { lightTheme, NButton, NConfigProvider, NDropdown, NLayout, NLayoutContent, NLayoutHeader, NMenu, NSpace } from 'naive-ui'
import Icon from '@/components/Icon.vue'
import { useAuthStore } from '@/stores/auth'
import { useUserStore } from '@/stores/user'

const auth = useAuthStore()
const users = useUserStore()
const route = useRoute()
const router = useRouter()
const profileLoading = ref(false)

const isAuthPage = computed(() => Boolean(route.meta.authPage))
const menuOptions = computed(() => [
  { label: '发现', key: '/' },
  ...(auth.isAuthenticated ? [{ label: '关注', key: '/following' }] : []),
  ...(auth.isAuthenticated ? [{ label: '创作中心', key: '/creator' }] : []),
  ...(auth.isOperator ? [{ label: '运营后台', key: '/ops' }] : []),
])
const mobileItems = computed(() => [
  { label: '发现', key: '/', icon: 'home' as const },
  { label: '关注', key: '/following', icon: 'users' as const },
  { label: '创作', key: '/creator', icon: 'dashboard' as const },
  { label: '我的', key: '/me', icon: 'user' as const },
])
const activeMenu = computed(() => {
  if (route.path.startsWith('/following')) return '/following'
  if (route.path.startsWith('/creator')) return '/creator'
  if (route.path.startsWith('/ops')) return '/ops'
  return route.path === '/' ? '/' : ''
})

onMounted(async () => {
  if (auth.isAuthenticated && !auth.profile && auth.userId) {
    profileLoading.value = true
    try {
      auth.setProfile(await users.loadProfile(auth.userId))
    } catch {
      // The shell remains usable when profile enrichment is temporarily unavailable.
    } finally {
      profileLoading.value = false
    }
  }
})

async function selectMenu(key: string) {
  if (key === '/creator' && !auth.isAuthenticated) {
    await router.push({ name: 'login', query: { redirect: '/creator' } })
    return
  }
  await router.push(key)
}

async function logout() {
  await auth.logout()
  await router.push('/')
}
</script>

<template>
  <NConfigProvider :theme="lightTheme" :theme-overrides="{ common: { primaryColor: '#236b52', primaryColorHover: '#18513e', borderRadius: '10px' } }">
    <NLayout class="app-shell">
      <NLayoutHeader bordered class="topbar">
        <div class="brand" role="button" tabindex="0" aria-label="返回发现" @click="router.push('/')" @keyup.enter="router.push('/')">
          <span class="brand-mark">S</span>
          <span class="brand-name">StreamHub</span>
        </div>
        <NMenu v-if="!isAuthPage" mode="horizontal" :value="activeMenu" :options="menuOptions" class="main-nav" @update:value="selectMenu" />
        <NSpace align="center" class="topbar-actions">
          <template v-if="auth.isAuthenticated">
            <NDropdown :options="[{ label: '个人中心', key: 'profile' }, { label: '退出登录', key: 'logout' }]" @select="(key) => key === 'logout' ? logout() : router.push('/me')">
              <NButton quaternary class="profile-trigger" :loading="profileLoading">
                <span class="avatar-dot">{{ (auth.profile?.nickname || 'U').slice(0, 1) }}</span>
                <span class="profile-name">{{ auth.profile?.nickname || `用户 ${auth.userId}` }}</span>
              </NButton>
            </NDropdown>
          </template>
          <template v-else-if="!isAuthPage">
            <RouterLink to="/auth/login"><NButton quaternary>登录</NButton></RouterLink>
            <RouterLink to="/auth/register"><NButton type="primary">注册</NButton></RouterLink>
          </template>
        </NSpace>
      </NLayoutHeader>
      <NLayoutContent content-style="min-height: calc(100vh - 72px)">
        <RouterView />
      </NLayoutContent>
      <nav v-if="!isAuthPage" class="mobile-nav" aria-label="移动端导航">
        <button v-for="item in mobileItems" :key="item.key" class="mobile-nav-item" :class="{ active: activeMenu === item.key }" type="button" @click="selectMenu(item.key)">
          <Icon :name="item.icon" :size="19" />
          <span>{{ item.label }}</span>
        </button>
      </nav>
    </NLayout>
  </NConfigProvider>
</template>
