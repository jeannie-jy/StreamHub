import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/HomeView.vue'), meta: { public: true } },
    { path: '/discover', redirect: '/' },
    { path: '/live/:roomId(\\d+)', name: 'room', component: () => import('@/views/RoomView.vue'), meta: { public: true } },
    { path: '/auth/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true, authPage: true } },
    { path: '/auth/register', name: 'register', component: () => import('@/views/RegisterView.vue'), meta: { public: true, authPage: true } },
    { path: '/me', name: 'profile', component: () => import('@/views/ProfileView.vue'), meta: { requiresAuth: true } },
    { path: '/creator', name: 'creator', component: () => import('@/views/CreatorDashboardView.vue'), meta: { requiresAuth: true } },
    { path: '/creator/rooms/:roomId(\\d+)', name: 'creator-room', component: () => import('@/views/CreatorRoomView.vue'), meta: { requiresAuth: true } },
    { path: '/ops', name: 'ops', component: () => import('@/views/OpsDashboardView.vue'), meta: { requiresAuth: true, operator: true } },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue'), meta: { public: true } },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  await auth.hydrate()
  if (to.meta.authPage && auth.isAuthenticated) return { name: 'home' }
  if (to.meta.requiresAuth && !auth.isAuthenticated) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.meta.operator && !auth.isOperator) return { name: 'home' }
  return true
})

export default router
