import { createI18n } from 'vue-i18n'

export const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': {
      appName: 'StreamHub',
      nav: { discover: '发现', creator: '创作中心', profile: '个人中心', ops: '运营后台' },
    },
  },
})
