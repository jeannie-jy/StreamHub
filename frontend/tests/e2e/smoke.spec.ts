import { expect, test } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/live/rooms**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: { items: [{ id: 101, anchorId: 7, title: '夜航电台', category: '音乐', status: 'LIVE', onlineCount: 128, coverUrl: null, playbackUrl: null, webrtcPlaybackUrl: null, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }], page: 1, pageSize: 20, total: 1, hasNext: false },
        code: 'OK', message: 'success', traceId: 'e2e', timestamp: new Date().toISOString(),
      }),
    })
  })
})

test('discover page renders a live room', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByText('夜航电台')).toBeVisible()
  await expect(page.getByText('每一次互动')).toBeVisible()
})

test('mobile viewport keeps the discover page usable', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: /每一次互动/ })).toBeVisible()
  await expect(page.locator('.room-grid')).toBeVisible()
})
