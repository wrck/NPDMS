// 任务点击时刻的读请求预取：把原本等子面板挂载后才发出的业务读请求提前到点击时刻并行发出。
// 只做一次性 Promise 转交，不缓存业务数据本身；TTL 内无人消费（非业务绑定任务、切换过快）自动失效。
const TTL_MS = 4000

const store = new Map<string, { promise: Promise<unknown>; createdAt: number }>()

const prune = (now: number) => {
  for (const [key, entry] of store) if (now - entry.createdAt >= TTL_MS) store.delete(key)
}

export const prefetchTaskView = <T>(key: string, request: () => Promise<T>): void => {
  const now = Date.now()
  prune(now)
  const existing = store.get(key)
  if (existing && now - existing.createdAt < TTL_MS) return
  const promise = request()
  store.set(key, { promise, createdAt: now })
  // 未被消费时的静默副本，避免 unhandled rejection；消费方拿到的仍是原始 promise，可正常捕获失败。
  promise.catch(() => {})
}

export const takeTaskViewPrefetch = <T>(key: string): Promise<T> | undefined => {
  const entry = store.get(key)
  if (!entry) return undefined
  store.delete(key)
  if (Date.now() - entry.createdAt >= TTL_MS) return undefined
  return entry.promise as Promise<T>
}

export const taskBusinessContextKey = (taskId: number | string): string =>
  `task-business-context:${taskId}`

export const requirementWorkspaceKey = (
  projectId: number | string,
  stageId: number | string | undefined,
  taskId: number | string | undefined
): string => `requirement-workspace:${projectId}:${stageId ?? ''}:${taskId ?? ''}`
