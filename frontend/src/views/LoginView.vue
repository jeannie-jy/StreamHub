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

async function submit() {
  error.value = ''
  try {
    await auth.login({ username: username.value, password: password.value })
    messageApi?.success('登录成功')
    await router.push(typeof route.query.redirect === 'string' ? route.query.redirect : '/')
  } catch (err) { error.value = err instanceof ApiError ? err.message : '登录失败，请稍后重试' }
}
</script>

<template>
  <main class="auth-page"><div class="auth-decoration"><span class="auth-orb orb-a" /><span class="auth-orb orb-b" /></div><div class="form-card surface"><p class="eyebrow">WELCOME BACK</p><h1>回到直播现场</h1><p class="form-intro">登录 StreamHub，继续和喜欢的主播互动。</p><NAlert v-if="error" type="error" :show-icon="false" class="form-alert">{{ error }}</NAlert><NSpace vertical size="large"><NFormItem label="用户名"><NInput v-model:value="username" placeholder="输入用户名" maxlength="64" @keyup.enter="submit" /></NFormItem><NFormItem label="密码"><NInput v-model:value="password" type="password" show-password-on="click" placeholder="输入密码" @keyup.enter="submit" /></NFormItem><NButton type="primary" block size="large" :loading="auth.loading" :disabled="!username || !password" @click="submit">登录</NButton></NSpace><p class="form-footer">还没有账号？ <RouterLink to="/auth/register">立即注册</RouterLink></p></div></main>
</template>

<style scoped>
.auth-page { position: relative; display: grid; min-height: calc(100vh - 65px); place-items: center; overflow: hidden; }
.auth-decoration { position: absolute; inset: 0; pointer-events: none; } .auth-orb { position: absolute; display: block; border-radius: 50%; filter: blur(1px); } .orb-a { top: 12%; left: 18%; width: 300px; height: 300px; background: rgba(115,96,255,.12); } .orb-b { right: 18%; bottom: 8%; width: 220px; height: 220px; background: rgba(66,217,208,.08); }
.form-card { position: relative; z-index: 1; } .form-alert { margin-bottom: 18px; }
</style>
