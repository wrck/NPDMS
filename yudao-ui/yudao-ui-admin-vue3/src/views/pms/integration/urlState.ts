type QueryPatch = Record<string, string | undefined>

/**
 * Persist integration workspace state for refresh without triggering a Vue Router navigation.
 * The host tags-view treats every distinct fullPath as a separate page tab.
 */
export const replaceIntegrationUrlState = (patch: QueryPatch) => {
  const url = new URL(window.location.href)
  Object.entries(patch).forEach(([key, value]) => {
    if (value === undefined) url.searchParams.delete(key)
    else url.searchParams.set(key, value)
  })
  const fullPath = `${url.pathname}${url.search}${url.hash}`
  window.history.replaceState({ ...window.history.state, current: fullPath }, '', fullPath)
}
