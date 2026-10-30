import { getCurrentScope, onScopeDispose, ref, shallowRef } from 'vue'

export function safeError(error: unknown): string {
  const status = (error as { response?: { status?: number } })?.response?.status
  if (status === 403) return '403 · 无权访问此授权范围，请联系管理员。'
  if (status === 404) return '404 · 记录不存在或不在授权范围内。'
  if (status === 409) return '409 · 状态已变化，请刷新后重新确认操作。'
  return status ? `请求失败（${status}），请重试。` : '请求失败，请检查输入或网络后重试。'
}

export function createRequest<T>() {
  const data = shallowRef<T>()
  const loading = ref(false)
  const error = ref('')
  let generation = 0
  let controller: AbortController | undefined
  function clear() { generation++; controller?.abort(); data.value = undefined; error.value = ''; loading.value = false }
  async function run(load: (signal: AbortSignal) => Promise<T>) {
    clear()
    const current = generation
    controller = new AbortController()
    loading.value = true
    try { const value = await load(controller.signal); if (current === generation) data.value = value }
    catch (cause) { if (current === generation) error.value = safeError(cause) }
    finally { if (current === generation) loading.value = false }
  }
  if (getCurrentScope()) onScopeDispose(clear)
  return { data, loading, error, run, clear }
}
