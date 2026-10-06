type Query = { url: string; params?: Record<string, unknown>; data?: unknown }
const send = async <T>(method: string, query: Query): Promise<T> => {
  const url = new URL(query.url, location.origin)
  Object.entries(query.params || {}).forEach(([key, value]) => { if (value != null) url.searchParams.set(key, String(value)) })
  const multipart = query.data instanceof FormData
  const response = await fetch(url, { method, headers: multipart ? {} : { 'Content-Type': 'application/json' },
    body: query.data === undefined ? undefined : multipart ? query.data as FormData : JSON.stringify(query.data) })
  const result = await response.json()
  if (result.code !== 0) throw new Error(result.msg)
  return result.data
}
export default { get: <T>(query: Query) => send<T>('GET', query), post: <T>(query: Query) => send<T>('POST', query), delete: <T>(query: Query) => send<T>('DELETE', query) }
