import { describe, expect, it, vi } from 'vitest'
vi.mock('@/config/runtime', () => ({ loadRuntimeConfig: async () => ({ authMode: 'local' }) }))
import router from '@/router'
import { readFileSync } from 'node:fs'
describe('management navigation', () => {
  it('uses real management routes including parser and restores record query', async () => {
    expect(router.resolve('/parser').name).toBe('parser')
    await router.push('/records/id-1?namespace=n&mode=generic')
    expect(router.currentRoute.value.query.collectionId).toBe('id-1')
    for (const path of ['/overview','/scripts','/tasks','/records','/settings']) {
      expect(String(router.resolve(path).matched[0]?.components?.default)).not.toContain('ManagementPlaceholderView')
    }
  })
  it('uses configurable cross-platform Playwright executables', () => {
    const source = readFileSync(`${process.cwd()}/playwright.config.ts`, 'utf8')
    expect(source).not.toContain('C:\\\\Program Files')
    expect(source).toContain('PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH')
    expect(source).toContain("process.platform === 'win32'")
  })
})
