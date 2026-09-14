import { expect, test } from '@playwright/test'

const room = {
  id: 101,
  anchorId: 7,
  anchorNickname: '小溪',
  title: '夜航电台',
  category: '音乐',
  status: 'LIVE',
  onlineCount: 128,
  coverUrl: null,
  playbackUrl: null,
  webrtcPlaybackUrl: null,
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
}

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/auth/refresh', async (route) => {
    await route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ success: false, data: null, code: 'COMMON-401', message: '未登录', traceId: 'e2e', timestamp: new Date().toISOString() }) })
  })
  await page.route('**/api/v1/live/rooms**', async (route) => {
    const path = new URL(route.request().url()).pathname
    let data: unknown
    if (path.endsWith('/messages') || path.endsWith('/activities') || path.endsWith('/gift-rank') || path.endsWith('/gift-income-rank')) data = []
    else if (path.endsWith('/101')) data = room
    else data = { items: [room], page: 1, pageSize: 20, total: 1, hasNext: false }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data,
        code: 'OK', message: 'success', traceId: 'e2e', timestamp: new Date().toISOString(),
      }),
    })
  })
  await page.route('**/api/v1/users/7', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: { id: 7, username: 'xiaoxi', nickname: '小溪', role: 'USER', status: 'ACTIVE', followerCount: 12 }, code: 'OK', message: 'success', traceId: 'e2e', timestamp: new Date().toISOString() }) })
  })
  await page.route('**/api/v1/gifts', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: [], code: 'OK', message: 'success', traceId: 'e2e', timestamp: new Date().toISOString() }) })
  })
})

test('discover page renders a live room', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByText('夜航电台')).toBeVisible()
  await expect(page.getByText('把时间留给正在发生的事')).toBeVisible()
})

test('mobile viewport keeps the discover page usable', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: /把时间留给\s*正在发生的事/ })).toBeVisible()
  await expect(page.locator('.room-grid')).toBeVisible()
})

test('room route renders the public interaction panels', async ({ page }) => {
  await page.goto('/live/101')
  await expect(page.getByRole('heading', { name: '夜航电台' })).toBeVisible()
  await expect(page.getByText('实时弹幕')).toBeVisible()
  await expect(page.getByText('支持主播')).toBeVisible()
  await expect(page.getByText('房间榜单')).toBeVisible()
  await expect(page.getByText(/querySelector is not a function/)).toHaveCount(0)
})
