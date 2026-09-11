import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')
  const apiTarget = env.VITE_DEV_API_TARGET || 'http://localhost:8088'
  const realtimeTarget = env.VITE_DEV_REALTIME_TARGET || 'http://localhost:8090'
  const mediaTarget = env.VITE_DEV_MEDIA_TARGET || 'http://localhost:8080'
  const rtcTarget = env.VITE_DEV_RTC_TARGET || 'http://localhost:1985'

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': '/src',
      },
    },
    server: {
      host: '0.0.0.0',
      port: Number(env.VITE_DEV_PORT || 5173),
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true,
        },
        '/ws': {
          target: realtimeTarget,
          changeOrigin: true,
          ws: true,
        },
        '/live': {
          target: mediaTarget,
          changeOrigin: true,
        },
        '/rtc': {
          target: rtcTarget,
          changeOrigin: true,
        },
      },
    },
    build: {
      sourcemap: mode !== 'production',
      target: 'es2022',
    },
  }
})
