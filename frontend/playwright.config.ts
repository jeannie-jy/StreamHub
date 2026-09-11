import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? 'github' : 'list',
  use: {
    baseURL: process.env.E2E_START_WEB === 'true' ? 'http://localhost:5173' : (process.env.WEB_BASE_URL || 'http://localhost:8089'),
    trace: 'on-first-retry',
  },
  webServer: process.env.E2E_START_WEB === 'true'
    ? { command: 'npm run dev -- --host 0.0.0.0', url: 'http://localhost:5173', reuseExistingServer: !process.env.CI }
    : undefined,
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile', use: { ...devices['iPhone 13'] } },
  ],
})
