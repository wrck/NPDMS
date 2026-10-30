import { expect, test } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/runtime-config', route => route.fulfill({ json: { authMode: 'local', apiBaseUrl: '/api/v1/', telnetEnabled: false, serialEnabled: false } }))
  await page.route('**/api/v1/parser-runtime/status', route => route.fulfill({ status: 403, json: {} }))
})

test('overview authorized totals and failed-record navigation', async ({ page }) => {
  await page.route('**/api/v1/management/overview**', route => route.fulfill({ json: { total: 4, byStatus: { FAILED: 1, SUCCEEDED: 3 } } }))
  await page.route('**/api/v1/management/collections**', route => route.fulfill({ json: { items: [], total: 0, page: 0, size: 20 } }))
  await page.goto('/overview')
  await expect(page.getByRole('heading', { name: '设备运维总览' })).toBeVisible()
  await page.getByRole('link', { name: '失败 1' }).click()
  await expect(page).toHaveURL(/records\?status=FAILED/)
  await expect(page.getByText('暂无采集记录')).toBeVisible()
})

test('records forbidden state supports real retry', async ({ page }) => {
  let calls = 0
  await page.route('**/api/v1/management/collections**', route => {
    calls++
    return calls === 1 ? route.fulfill({ status: 403, json: {} }) : route.fulfill({ json: { items: [], total: 0, page: 0, size: 20 } })
  })
  await page.goto('/records')
  await expect(page.getByRole('alert')).toContainText('403')
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.getByText('暂无采集记录')).toBeVisible()
  expect(calls).toBe(2)
})

test('parser empty registry does not conceal runtime permission error', async ({ page }) => {
  await page.route('**/api/v1/parser-log-types', route => route.fulfill({ json: [] }))
  await page.route('**/api/v1/management/settings', route => route.fulfill({ status: 403, json: {} }))
  await page.goto('/parser')
  await expect(page.getByText('暂无日志类型')).toBeVisible()
  await expect(page.getByRole('alert')).toContainText('403')
  await expect(page.getByRole('button', { name: '重试', exact: true })).toBeVisible()
})
