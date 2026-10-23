export const LOCAL_DAC_ORIGIN = 'http://127.0.0.1:48182'
export const LOCAL_DAC_START_URI = 'npdms-dac://start/'

export class LocalDacError extends Error {
  constructor(readonly outcomeUnknown: boolean, readonly status?: number) {
    super(status === 401 ? '本机操作授权已过期或被拒绝，请继续原任务刷新授权'
      : status === 403 ? '本机操作授权与当前任务不匹配'
      : status === 404 ? '本机任务或保存的连接不存在，请核对原任务和连接'
      : status === 409 ? '本机任务或连接已变化，请刷新查看'
      : status === 400 ? '本机采集参数无效，请检查设备连接信息'
      : status === 503 ? '本机 DAC 无法完成服务端授权校验，请检查任务权限后继续原任务'
      : status ? `本机 DAC 处理请求失败（HTTP ${status}），请保留原任务后重试`
      : '未取得本机 DAC 响应，执行结果未知，请查询原任务状态')
    this.name = 'LocalDacError'
  }
}

export async function localDacRequest<T>(options: {
  path: string
  ticket: { value: string; tenantId: string | number }
  method?: 'GET' | 'POST'
  body?: unknown
  timeout?: number
}): Promise<T> {
  if (!/^\/(tasks\/[a-zA-Z0-9-]+(?:\/executions|\/semantic-results)?|saved-connections)$/.test(options.path)) {
    throw new LocalDacError(false)
  }
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), options.timeout ?? 30000)
  try {
    const response = await fetch(`${LOCAL_DAC_ORIGIN}/api/v1/desktop${options.path}`, {
      method: options.method ?? 'GET',
      headers: {
        'X-NPDMS-DAC-Ticket': options.ticket.value,
        'tenant-id': String(options.ticket.tenantId),
        ...(options.body === undefined ? {} : { 'Content-Type': 'application/json' })
      },
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      credentials: 'omit', cache: 'no-store', redirect: 'error', referrerPolicy: 'no-referrer',
      signal: controller.signal
    })
    if (!response.ok) throw new LocalDacError(response.status >= 500, response.status)
    return await response.json() as T
  } catch (failure) {
    if (failure instanceof LocalDacError) throw failure
    // Never retain the password-bearing fetch arguments in an exception or log.
    throw new LocalDacError(true)
  } finally { clearTimeout(timeout) }
}

export interface LocalSavedConnection {
  id: string
  displayName: string
  version: number
  connection: { host: string; port: number; username: string; protocol: 'SSH2' | 'TELNET'; authenticationType: string; executionMode: string }
}

export interface LocalCollection {
  collectionId: string
  externalRequestId: string
  status: string
  targets: { stdout?: string; stderr?: string; outcome?: string }[]
}

export interface LocalDacStatus {
  product: 'npdms-dac'
  protocolVersion: 2
  status: 'READY' | 'STARTING'
  serverUrl: string
  webOrigin: string
}

export type LocalDacDiscovery =
  | { state: 'ready' | 'starting'; status: LocalDacStatus }
  | { state: 'unavailable' | 'incompatible' | 'wrong-environment' }

export async function discoverLocalDac(options: {
  serverUrl: string
  webOrigin: string
  signal?: AbortSignal
}): Promise<LocalDacDiscovery> {
  const abort = new AbortController()
  const cancel = () => abort.abort()
  options.signal?.addEventListener('abort', cancel, { once: true })
  if (options.signal?.aborted) abort.abort()
  const timeout = setTimeout(cancel, 2000)
  try {
    // Discovery must never carry the NPDMS token, cookies or any device credential.
    const response = await fetch(`${LOCAL_DAC_ORIGIN}/api/v1/desktop/status`, {
      method: 'GET',
      credentials: 'omit',
      cache: 'no-store',
      redirect: 'error',
      signal: abort.signal
    })
    if (!response.ok) return { state: 'unavailable' }
    const data = await response.json()
    if (
      data?.product !== 'npdms-dac' ||
      data?.protocolVersion !== 2 ||
      !['READY', 'STARTING'].includes(data?.status) ||
      typeof data?.serverUrl !== 'string' ||
      typeof data?.webOrigin !== 'string'
    ) {
      return { state: 'incompatible' }
    }
    const normalize = (url: string) => new URL(url).href.replace(/\/+$/, '')
    if (
      normalize(data.serverUrl) !== normalize(options.serverUrl) ||
      normalize(data.webOrigin) !== normalize(options.webOrigin)
    ) {
      return { state: 'wrong-environment' }
    }
    return { state: data.status === 'READY' ? 'ready' : 'starting', status: data }
  } catch {
    return { state: 'unavailable' }
  } finally {
    clearTimeout(timeout)
    options.signal?.removeEventListener('abort', cancel)
  }
}
