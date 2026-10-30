import type { RouteLocationNormalized } from 'vue-router'

const legacyPath = '/pms/engineering/execution/configuration-device-logs'
const retainedPath = '/pms/engineering/execution/imp-configuration'

/** Consolidate only routes already granted by the server; never add business permissions. */
export const configurationRoutes = (
  routes: AppRouteRecordRaw[], parentPath = ''
): AppRouteRecordRaw[] => {
  const fullPath = (path: string) => path.startsWith('/') ? path : `${parentPath}/${path}`.replace(/\/+/g, '/')
  const legacy = routes.find(route => fullPath(route.path) === legacyPath)
  const retained = routes.find(route => fullPath(route.path) === retainedPath)
  const result = routes.map(route => {
    const path = fullPath(route.path)
    if (path === legacyPath) {
      if (!retained) return { ...route, path: route.path.startsWith('/') ? retainedPath : 'imp-configuration' }
      return redirectRoute(route)
    }
    return route.children
      ? { ...route, children: configurationRoutes(route.children, path) }
      : route
  })
  if (legacy && !retained) result.push(redirectRoute(legacy))
  if (retained && !legacy) result.push(redirectRoute({ ...retained, path: retained.path.startsWith('/') ? legacyPath : 'configuration-device-logs' }))
  return result
}

const redirectRoute = (source: AppRouteRecordRaw): AppRouteRecordRaw => ({
  ...source,
  name: 'PmsConfigurationLegacyRedirect',
  component: undefined,
  redirect: (to: RouteLocationNormalized) => ({ path: retainedPath, query: to.query, hash: to.hash }),
  meta: { ...source.meta, hidden: true, breadcrumb: false }
})
