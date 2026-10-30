import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter, type RouteRecordRaw } from 'vue-router'
import { configurationRoutes } from './configurationRoutes'

const base = '/pms/engineering/execution'
const page = (path: string, name: string): AppRouteRecordRaw => ({
  path, name, component: { render: () => null }, meta: { title: name, hidden: false }
})
const tree = (children: AppRouteRecordRaw[]): AppRouteRecordRaw[] => [
  { ...page('/pms', 'Pms'), children: [{ ...page('engineering', 'Engineering'), children: [
    { ...page('execution', 'Execution'), children }
  ] }] }
]
const entries = (routes: AppRouteRecordRaw[]) => routes[0].children![0].children![0].children!

describe('configuration menu consolidation', () => {
  it.each(['both', 'legacy', 'retained'])('keeps access and redirects bookmarks with %s menu authorization', async kind => {
    const source = tree([
      ...(kind !== 'retained' ? [page('configuration-device-logs', 'OldConfiguration')] : []),
      ...(kind !== 'legacy' ? [page('imp-configuration', 'ConfigurationLogs')] : []),
      page('imp-joint-test', 'JointTest')
    ])
    const originalPaths = entries(source).map(route => route.path)
    const routes = configurationRoutes(source)
    const visible = entries(routes).filter(route => !route.meta.hidden)
    expect(visible.map(route => route.path)).toEqual(['imp-configuration', 'imp-joint-test'])
    expect(entries(source).map(route => route.path)).toEqual(originalPaths)
    const router = createRouter({ history: createMemoryHistory(), routes: routes as RouteRecordRaw[] })
    await router.push(`${base}/configuration-device-logs?projectId=9007199254740993#history`)
    expect(router.currentRoute.value.path).toBe(`${base}/imp-configuration`)
    expect(router.currentRoute.value.query.projectId).toBe('9007199254740993')
    expect(router.currentRoute.value.hash).toBe('#history')
    expect(router.currentRoute.value.meta.hidden).toBe(false)
  })

  it('does not create either entry without an authorized configuration menu', () => {
    const routes = tree([page('imp-joint-test', 'JointTest')])
    expect(configurationRoutes(routes)).toEqual(routes)
  })

  it('supports absolute route paths without modifying unrelated same-named pages', () => {
    const routes = configurationRoutes([
      page(`${base}/configuration-device-logs`, 'OldConfiguration'),
      page('/unrelated/configuration-device-logs', 'Unrelated')
    ])
    expect(routes[0].path).toBe(`${base}/imp-configuration`)
    expect(routes[1].path).toBe('/unrelated/configuration-device-logs')
    expect(routes[1].redirect).toBeUndefined()
  })
})
