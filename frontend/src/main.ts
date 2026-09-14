import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createDiscreteApi, lightTheme } from 'naive-ui'
import App from './App.vue'
import router from './router'
import { i18n } from './locales'
import './styles/index.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(i18n)

const { message, dialog, notification } = createDiscreteApi(['message', 'dialog', 'notification'], { configProviderProps: { theme: lightTheme } })
app.provide('message', message)
app.provide('dialog', dialog)
app.provide('notification', notification)
app.mount('#app')
