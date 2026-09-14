<script setup lang="ts">
import { inject, ref } from 'vue'
import { NAlert, NButton, NFormItem, NInput, NSpace } from 'naive-ui'
import { useRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const messageApi = inject<any>('message')
const username = ref('')
const nickname = ref('')
const password = ref('')
const confirmPassword = ref('')
const error = ref('')

async function submit() {
  error.value = ''
  const nextUsername = username.value.trim()
  const nextNickname = nickname.value.trim() || nextUsername
  if (nextUsername.length < 3 || nextUsername.length > 64) { error.value = '用户名需要 3 到 64 个字符'; return }
  if (nextNickname.length < 1 || nextNickname.length > 64) { error.value = '昵称需要 1 到 64 个字符'; return }
  if (password.value.length < 8 || password.value.length > 128) { error.value = '密码需要 8 到 128 个字符'; return }
  if (password.value !== confirmPassword.value) { error.value = '两次输入的密码不一致'; return }
  try {
    await auth.register({ username: nextUsername, nickname: nextNickname, password: password.value })
    messageApi?.success('注册成功')
    await router.push('/')
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '注册失败，请稍后重试'
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-intro">
      <p class="eyebrow">JOIN STREAMHUB</p>
      <h1>找到你的下一场现场。</h1>
      <div class="hero-rule" />
      <p>创建账号，关注喜欢的主播，收藏值得回看的直播间。</p>
    </section>
    <section class="form-card surface" aria-labelledby="register-title">
      <p class="eyebrow">创建账号</p>
      <h2 id="register-title">加入 StreamHub</h2>
      <p class="form-intro">注册后即可观看直播、发送弹幕并开始创作。</p>
      <NAlert v-if="error" type="error" :show-icon="false" class="form-alert">{{ error }}</NAlert>
      <NSpace vertical size="large">
        <NFormItem label="用户名"><NInput v-model:value="username" placeholder="3 到 64 个字符" maxlength="64" /></NFormItem>
        <NFormItem label="昵称"><NInput v-model:value="nickname" placeholder="直播间里显示的名字" maxlength="64" /></NFormItem>
        <NFormItem label="密码"><NInput v-model:value="password" type="password" show-password-on="click" placeholder="8 到 128 个字符" /></NFormItem>
        <NFormItem label="确认密码"><NInput v-model:value="confirmPassword" type="password" show-password-on="click" placeholder="再次输入密码" @keyup.enter="submit" /></NFormItem>
        <NButton type="primary" block size="large" :loading="auth.loading" @click="submit">创建账号</NButton>
      </NSpace>
      <p class="form-footer">已经有账号？ <RouterLink to="/auth/login">返回登录</RouterLink></p>
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
