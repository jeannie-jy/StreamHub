<script setup lang="ts">
import { inject, ref } from 'vue'
import { NAlert, NButton, NFormItem, NInput, NSpace } from 'naive-ui'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const messageApi = inject<any>('message')
const username = ref('')
const password = ref('')
const error = ref('')

function safeRedirect(value: unknown) {
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') ? value : '/'
}

async function submit() {
  error.value = ''
  const nextUsername = username.value.trim()
  if (nextUsername.length < 3) { error.value = '用户名至少需要 3 个字符'; return }
  if (!password.value) { error.value = '请输入密码'; return }
  try {
    await auth.login({ username: nextUsername, password: password.value })
    messageApi?.success('登录成功')
    await router.push(safeRedirect(route.query.redirect))
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '登录失败，请稍后重试'
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-intro">
      <p class="eyebrow">STREAMHUB ACCOUNT</p>
      <h1>回到你关注的现场。</h1>
      <div class="hero-rule" />
      <p>登录后继续观看直播、参与弹幕互动，并管理你的关注与收藏。</p>
    </section>
    <section class="form-card surface" aria-labelledby="login-title">
      <p class="eyebrow">欢迎回来</p>
      <h2 id="login-title">登录账号</h2>
      <p class="form-intro">使用用户名和密码登录 StreamHub。</p>
      <NAlert v-if="error" type="error" :show-icon="false" class="form-alert">{{ error }}</NAlert>
      <NSpace vertical size="large">
        <NFormItem label="用户名">
          <NInput v-model:value="username" placeholder="输入用户名" maxlength="64" clearable @keyup.enter="submit" />
        </NFormItem>
        <NFormItem label="密码">
          <NInput v-model:value="password" type="password" show-password-on="click" placeholder="输入密码" @keyup.enter="submit" />
        </NFormItem>
        <NButton type="primary" block size="large" :loading="auth.loading" @click="submit">登录</NButton>
      </NSpace>
      <p class="form-footer">还没有账号？ <RouterLink to="/auth/register">创建账号</RouterLink></p>
    </section>
  </main>
</template>

<style scoped>
.auth-page { display: grid; grid-template-columns: minmax(0, .9fr) minmax(360px, 440px); align-items: center; justify-content: center; gap: 80px; min-height: calc(100vh - 72px); width: min(980px, calc(100% - 48px)); margin: 0 auto; }
.auth-intro { max-width: 410px; }
.auth-intro h1 { margin: 0; color: var(--sh-ink); font-size: clamp(34px, 5vw, 56px); line-height: 1.08; letter-spacing: -.07em; }
.auth-intro > p:last-child { color: var(--sh-muted); line-height: 1.75; }
.form-card { width: 100%; margin: 34px auto; }
.form-card h2 { margin: 0; color: var(--sh-ink); font-size: 30px; letter-spacing: -.05em; }
@media (max-width: 760px) { .auth-page { grid-template-columns: 1fr; gap: 0; width: min(440px, calc(100% - 32px)); } .auth-intro { display: none; } }
</style>
