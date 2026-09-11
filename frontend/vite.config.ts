import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')
  const apiTarget = env.VITE_DEV_API_TARGET || 'http://localhost:8088'
  const realtimeTarget = env.VITE_DEV_REALTIME_TARGET || 'http://localhost:8090'

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
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
        '/rtc': {
          target: 'http://localhost:1985',
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
