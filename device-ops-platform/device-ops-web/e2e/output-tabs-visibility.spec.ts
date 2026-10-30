import { expect, test, type Locator } from '@playwright/test'

// Real app + real Element Plus layout; only historical HTTP data is mocked.
const authority = 'https://idp.acceptance.example/realms/device-ops'
const coordinate = { logType: 'device-command-output', releaseVersion: '1.3.0', engineVersion: '1.3.0', ruleVersion: '1.3.0', projectionVersion: '1.3.0' }
const commands = Array.from({ length: 24 }, (_, commandIndex) => ({ commandIndex, commandText: `show evidence ${commandIndex}`, status: 'SUCCEEDED', stdout: 'historical output\n'.repeat(60), stderr: 'historical warning', parsedFacts: {}, parseWarnings: [], pageCount: 0, truncated: false, legacy: false }))
const details = { collectionId: 'layout-history', namespace: 'standalone', activityType: 'INSPECTION', status: 'SUCCEEDED', script: { source: 'ADHOC_INLINE', key: 'history', version: '1', sha256: 'a'.repeat(64), parserType: 'NONE' }, targets: [{ targetId: 1, contextSnapshot: { project: {}, device: {}, extensions: {} }, endpointSnapshot: { host: 'historical.invalid', port: 22, username: 'reader' }, status: 'SUCCEEDED', stdout: '', stderr: '', parsedFacts: {}, commandBlocks: commands }] }
const semanticResult = { schemaVersion: '1.1.0', snapshot: {}, projections: {}, quality: {}, observations: commands.map(({ commandIndex }) => ({ commandIndex, status: 'OBSERVED', confidence: 1, matchedRuleIds: [], warnings: [] })), nestedObservations: [{ parentCommandIndex: 0, sectionIndex: 1, nestingDepth: 1, commandText: 'nested evidence', sourceLineStart: 10, sourceLineEnd: 20, status: 'NO_DATA', confidence: 1, matchedRuleIds: [], warnings: [] }], genericContent: { units: [] } }

async function reachable(locator: Locator) {
  // Collapse animates its clipping height. Wait for geometry, not an arbitrary sleep.
  await expect(async () => {
    // Verify full reachability, not nearest-edge rounding: Chromium can leave
    // 0.34375px below the viewport after scrollIntoViewIfNeeded at 1024×768.
    await locator.evaluate(el => el.scrollIntoView({ block: 'center', inline: 'center', behavior: 'instant' }))
    await expect(locator).toBeInViewport({ ratio: 1 })
    expect(await locator.evaluate(el => {
      const rect = el.getBoundingClientRect()
      for (let parent = el.parentElement; parent; parent = parent.parentElement) {
        // Body overflow propagates to the viewport; its document rect is not a clip.
        if (parent === document.body || parent === document.documentElement) continue
        const style = getComputedStyle(parent)
        const box = parent.getBoundingClientRect()
        if (/(auto|scroll|hidden|clip)/.test(style.overflowY) && (rect.top < box.top - 1 || rect.bottom > box.bottom + 1)) return false
      }
      return rect.height > 0 && rect.width > 0
    }), 'actual rect must fit every clipping/scroll ancestor').toBe(true)
  }).toPass({ timeout: 10_000 })
}

for (const viewport of [{ width: 1280, height: 720 }, { width: 1440, height: 900 }, { width: 1024, height: 768 }]) {
  test(`historical output tabs reachable ${viewport.width}x${viewport.height}`, async ({ page, context }) => {
    await page.setViewportSize(viewport)
    const forbidden: string[] = []
    await context.addInitScript(({ authority }) => {
      sessionStorage.setItem(`oidc.user:${authority}:device-ops-web`, JSON.stringify({ access_token: 'mock-history', token_type: 'Bearer', scope: 'openid device-ops:collections:read', profile: { sub: 'reader' }, expires_at: Math.floor(Date.now() / 1000) + 3600 }))
    }, { authority })
    await page.route('**/*', async route => {
      const url = new URL(route.request().url())
      if (url.port === '48181' || route.request().method() !== 'GET' || (!['127.0.0.1', 'localhost'].includes(url.hostname))) {
        forbidden.push(route.request().url()); await route.abort(); return
      }
      if (!url.pathname.startsWith('/api/')) { await route.continue(); return }
      if (url.pathname.endsWith('/runtime-config')) { await route.fulfill({ json: { oidcAuthority: authority, oidcClientId: 'device-ops-web', oidcScope: 'openid device-ops:collections:read', apiBaseUrl: '/api/v1', serialEnabled: false } }); return }
      if (url.pathname.endsWith('/semantic-results')) { await route.fulfill({ json: [{ targetId: 1, taskId: 'history-task', state: 'SUCCEEDED', coordinate, result: { semanticResult } }] }); return }
      if (url.pathname.endsWith('/collections/layout-history')) { await route.fulfill({ json: details }); return }
      if (url.pathname.endsWith('/evidence')) { await route.fulfill({ status: 404, json: {} }); return }
      if (url.pathname.includes('/output')) { await route.fulfill({ contentType: 'text/event-stream', body: '' }); return }
      await route.fulfill({ json: [] })
    })
    await page.goto('/connections?collectionId=layout-history&namespace=standalone&mode=generic')
    const panel = page.locator('.task-panel')
    await expect(panel.locator('.task-panel__state')).toContainText('采集成功')
    for (const name of ['标准输出', '错误输出', '请求快照', '解析事实']) {
      const tab = panel.getByRole('tab', { name, exact: true })
      await reachable(tab)
      await tab.click()
      await expect(tab).toHaveAttribute('aria-selected', 'true')
    }
    const facts = panel.locator('.facts')
    const inner = panel.locator('.semantic-result__tabs')
    await expect(panel.getByRole('button', { name: /命令与内嵌摘要/ })).toHaveAttribute('aria-expanded', 'false')
    // The tabs must precede the long evidence summaries, not require scrolling past 24 commands.
    expect(await inner.evaluate(el => el.getBoundingClientRect().top - el.closest('.facts')!.getBoundingClientRect().top)).toBeLessThan(220)
    for (const name of ['通用结构', '实体视图', '字段证据', '原始 JSON']) {
      const tab = inner.getByRole('tab', { name, exact: true })
      await reachable(tab); await tab.click(); await expect(tab).toHaveAttribute('aria-selected', 'true')
    }
    const header = panel.locator('.task-output-tabs > .el-tabs__header')
    const fixed = [header, panel.locator(':scope > .el-card__header'), panel.locator('.task-panel__dispatch')]
    const before = await Promise.all(fixed.map(locator => locator.boundingBox()))
    await facts.evaluate(el => { el.scrollTop = el.scrollHeight })
    expect(await facts.evaluate(el => el.scrollTop)).toBeGreaterThan(0)
    for (const [index, locator] of fixed.entries()) expect((await locator.boundingBox())?.y).toBe(before[index]?.y)
    const summary = panel.getByRole('button', { name: /命令与内嵌摘要/ })
    await reachable(summary); await summary.click()
    await reachable(panel.locator('.semantic-result__commands code').filter({ hasText: 'show evidence 23' }))
    const nested = panel.getByRole('button', { name: /内嵌命令块/ })
    await reachable(nested); await nested.click()
    await reachable(panel.getByText('nested evidence', { exact: true }))
    const leftLast = page.locator('.connection-workbench .el-card__body').locator('button:visible').last()
    await reachable(leftLast)
    if (viewport.height > 850) {
      const center = await page.locator('.workbench-center').boundingBox()
      const output = await panel.boundingBox()
      expect(output!.y + output!.height).toBeLessThanOrEqual(center!.y + center!.height + 1)
    }
    // Records reuse the same panel with its default standalone height and no execute action.
    await page.goto('/records/layout-history?collectionId=layout-history&namespace=standalone&mode=generic')
    await expect(panel).toHaveClass(/task-panel--standalone/)
    await expect(panel.getByRole('button', { name: '连接并执行采集' })).toHaveCount(0)
    for (const name of ['标准输出', '错误输出', '请求快照', '解析事实']) {
      const tab = panel.getByRole('tab', { name, exact: true })
      await reachable(tab); await tab.click(); await expect(tab).toHaveAttribute('aria-selected', 'true')
    }
    expect(forbidden).toEqual([])
  })
}
