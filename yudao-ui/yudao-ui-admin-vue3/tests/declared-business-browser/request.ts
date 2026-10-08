type Query = { url: string; params?: Record<string, unknown>; data?: unknown }
export async function send<T>(method: string, query: Query): Promise<T> {
  const url = new URL(query.url, location.origin)
  Object.entries(query.params || {}).forEach(([key, value]) => {
    if (value != null) url.searchParams.set(key, String(value))
  })
  const response = await fetch(url, { method, headers: { 'tenant-id': '7', 'Content-Type': 'application/json' },
    body: query.data === undefined ? undefined : JSON.stringify(query.data) })
  const body = await response.json()
  if (body.code !== 0) {
    const error = Object.assign(new Error(body.msg || String(body.code)), { response: { status: response.status, data: body } })
    throw error
  }
  return body.data as T
}
export default {
  get: <T>(query: Query) => send<T>('GET', query),
  post: <T>(query: Query) => send<T>('POST', query),
  put: <T>(query: Query) => send<T>('PUT', query),
  delete: <T>(query: Query) => send<T>('DELETE', query)
}
