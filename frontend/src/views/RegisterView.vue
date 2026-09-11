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
  if (password.value !== confirmPassword.value) { error.value = '两次输入的密码不一致'; return }
  if (password.value.length < 8) { error.value = '密码至少需要 8 个字符'; return }
  try { await auth.register({ username: username.value, nickname: nickname.value || username.value, password: password.value }); messageApi?.success('注册成功'); await router.push('/') } catch (err) { error.value = err instanceof ApiError ? err.message : '注册失败，请稍后重试' }
}
</script>

<template>
  <main class="auth-page"><div class="form-card surface"><p class="eyebrow">JOIN THE ROOM</p><h1>创建你的账号</h1><p class="form-intro">注册后即可观看直播、发送弹幕，也可以直接开启自己的直播间。</p><NAlert v-if="error" type="error" :show-icon="false" class="form-alert">{{ error }}</NAlert><NSpace vertical size="large"><NFormItem label="用户名"><NInput v-model:value="username" placeholder="3-64 个字符" maxlength="64" /></NFormItem><NFormItem label="昵称"><NInput v-model:value="nickname" placeholder="直播间里显示的名字" maxlength="64" /></NFormItem><NFormItem label="密码"><NInput v-model:value="password" type="password" show-password-on="click" placeholder="至少 8 个字符" /></NFormItem><NFormItem label="确认密码"><NInput v-model:value="confirmPassword" type="password" show-password-on="click" placeholder="再次输入密码" @keyup.enter="submit" /></NFormItem><NButton type="primary" block size="large" :loading="auth.loading" :disabled="!username || !password || !confirmPassword" @click="submit">创建账号</NButton></NSpace><p class="form-footer">已经有账号？ <RouterLink to="/auth/login">返回登录</RouterLink></p></div></main>
</template>

<style scoped>.auth-page { display: grid; min-height: calc(100vh - 65px); place-items: center; } .form-card { margin: 34px auto; } .form-alert { margin-bottom: 18px; }</style>
