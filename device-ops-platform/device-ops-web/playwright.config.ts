import { defineConfig } from '@playwright/test'

const baseURL = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:15174'
const executablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH
const pnpm = process.platform === 'win32' ? 'pnpm.cmd' : 'pnpm'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'line',
  outputDir: 'node_modules/.cache/playwright-results',
  use: {
    baseURL,
    headless: true,
    browserName: 'chromium',
    launchOptions: executablePath ? { executablePath } : {},
    viewport: { width: 1440, height: 1000 }
  },
  webServer: {
    command: process.env.PLAYWRIGHT_WEB_SERVER_COMMAND || `${pnpm} exec vite --host 127.0.0.1 --port ${new URL(baseURL).port || '15174'} --strictPort`,
    url: baseURL,
    reuseExistingServer: process.env.PLAYWRIGHT_REUSE_SERVER === 'true',
    timeout: 120_000,
    stdout: 'pipe',
    stderr: 'pipe'
  }
})
