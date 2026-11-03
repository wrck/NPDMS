import { ed25519 } from '@noble/curves/ed25519'

export const LOCAL_DAC_ORIGINS = Array.from({ length: 10 }, (_, index) => `http://127.0.0.1:${8280 + index}`)
export const LOCAL_DAC_START_URI = 'sdp-dac://start/'

export class LocalDacError extends Error {
  constructor(readonly outcomeUnknown: boolean, readonly status?: number, reason?: string) {
    super(reason ?? (status === 401 ? '本机操作授权已过期或被拒绝，请继续原任务刷新授权'
      : status === 403 ? '本机操作授权与当前任务不匹配'
      : status === 404 ? '本机任务或保存的连接不存在，请核对原任务和连接'
      : status === 409 ? '本机任务或连接已变化，请刷新查看'
      : status === 400 ? '本机采集参数无效，请检查设备连接信息'
      : status === 503 ? '本机 DAC 无法完成服务端授权校验，请检查任务权限后继续原任务'
      : status ? `本机 DAC 处理请求失败（HTTP ${status}），请保留原任务后重试`
      : '未取得本机 DAC 响应，执行结果未知，请查询原任务状态'))
    this.name = 'LocalDacError'
  }
}

export async function localDacRequest<T>(options: {
  environment: Environment
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
    const discovery = await discoverLocalDac({ ...options.environment, signal: controller.signal })
    if (discovery.state !== 'ready') throw new LocalDacError(false, undefined,
      discovery.state === 'connection-required' ? '请先连接本机 DAC，本次操作尚未发送'
        : discovery.state === 'identity-mismatch' ? '本机 DAC 身份校验失败，本次操作尚未发送'
        : '本机 DAC 尚未就绪或环境不匹配，本次操作尚未发送，请重新检测')
    // Revalidate before sending a ticket or password. A failed POST is never retried automatically.
    const response = await fetch(`${discovery.origin}/api/v1/desktop${options.path}`, {
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
  product: 'sdp-dac'
  protocolVersion: 3
  status: 'READY' | 'STARTING'
  serverUrl: string
  webOrigin: string
  port: number
  publicKey: string
  instanceId: string
  signature: string
}
type Environment = { serverUrl: string; webOrigin: string }
export type LocalDacDiscovery =
  | { state: 'ready' | 'starting' | 'connection-required'; status: LocalDacStatus; origin: string }
  | { state: 'unavailable' | 'incompatible' | 'wrong-environment' | 'identity-mismatch' }
const normalize = (url: string) => new URL(url).href.replace(/\/+$/, '')
const pairingStorageKey = (environment: Environment) => `npdms-dac-public-key:${normalize(environment.serverUrl)}:${normalize(environment.webOrigin)}`
const hexBytes = (hex: string) => Uint8Array.from(hex.match(/../g) ?? [], part => parseInt(part, 16))
const previousOrigins = new Map<string, string>()

function trustedKey(environment: Environment): string | null {
  try { return window.localStorage.getItem(pairingStorageKey(environment)) } catch { return null }
}

/** Called only with the proof returned to the owner of a native-launch challenge. */
export async function confirmLocalDac(options: Environment & { nonce: string; status: LocalDacStatus; signal?: AbortSignal }): Promise<LocalDacDiscovery> {
  const data = options.status
  if (options.signal?.aborted) throw new Error('本机连接已取消')
  if (!/^[a-f0-9]{64}$/.test(options.nonce) || !data || data.product !== 'sdp-dac' || data.protocolVersion !== 3
    || data.status !== 'READY' || !Number.isInteger(data.port) || data.port < 8280 || data.port > 8289
    || !/^[a-f0-9]{64}$/.test(data.publicKey) || !/^[a-f0-9-]{36}$/.test(data.instanceId)
    || data.serverUrl !== normalize(options.serverUrl) || data.webOrigin !== normalize(options.webOrigin)) {
    throw new Error('本机连接确认与当前环境不匹配，请重新连接')
  }
  try {
    const message = `sdp-dac-discovery-v1\n${options.nonce}\n${data.port}\n${data.instanceId}\n3\n${data.status}\n${data.serverUrl}\n${data.webOrigin}`
    const signature = Uint8Array.from(atob(data.signature), char => char.charCodeAt(0))
    if (!ed25519.verify(signature, new TextEncoder().encode(message), hexBytes(data.publicKey), { zip215: false })) throw new Error()
  } catch { throw new Error('本机连接身份校验失败，请检查安装后重新连接') }
  const pinned = trustedKey(options)
  if (pinned && pinned !== data.publicKey) throw new Error('本机 DAC 安装身份已变化，请检查本机安装')
  try { window.localStorage.setItem(pairingStorageKey(options), data.publicKey) }
  catch { throw new Error('浏览器无法保存安装公钥，请检查站点存储权限') }
  return discoverLocalDac(options)
}

async function probe(origin: string, options: Environment & { signal?: AbortSignal }): Promise<LocalDacDiscovery> {
  const abort = new AbortController()
  const cancel = () => abort.abort()
  options.signal?.addEventListener('abort', cancel, { once: true })
  if (options.signal?.aborted) abort.abort()
  const timeout = setTimeout(cancel, 2000)
  try {
    const nonce = Array.from(crypto.getRandomValues(new Uint8Array(32)), byte => byte.toString(16).padStart(2, '0')).join('')
    const response = await fetch(`${origin}/api/v1/desktop/status?nonce=${nonce}`, {
      method: 'GET', credentials: 'omit', cache: 'no-store', redirect: 'error', signal: abort.signal
    })
    if (!response.ok) return { state: 'unavailable' }
    const data = await response.json() as LocalDacStatus
    if (data?.product !== 'sdp-dac' || data?.protocolVersion !== 3 || !['READY', 'STARTING'].includes(data?.status)
      || typeof data.serverUrl !== 'string' || typeof data.webOrigin !== 'string'
      || !/^[a-f0-9]{64}$/.test(data.publicKey) || !/^[a-f0-9-]{36}$/.test(data.instanceId)
      || data.port !== Number(new URL(origin).port) || typeof data.signature !== 'string') return { state: 'incompatible' }
    if (normalize(data.serverUrl) !== normalize(options.serverUrl) || normalize(data.webOrigin) !== normalize(options.webOrigin)) {
      return { state: 'wrong-environment' }
    }
    const message = `sdp-dac-discovery-v1\n${nonce}\n${data.port}\n${data.instanceId}\n3\n${data.status}\n${data.serverUrl}\n${data.webOrigin}`
    const signature = Uint8Array.from(atob(data.signature), char => char.charCodeAt(0))
    if (!ed25519.verify(signature, new TextEncoder().encode(message), hexBytes(data.publicKey), { zip215: false })) {
      return { state: 'identity-mismatch' }
    }
    const pinned = trustedKey(options)
    if (pinned && pinned !== data.publicKey) return { state: 'identity-mismatch' }
    return { state: !pinned ? 'connection-required' : data.status === 'READY' ? 'ready' : 'starting', status: data, origin }
  } catch { return { state: 'unavailable' } }
  finally {
    clearTimeout(timeout)
    options.signal?.removeEventListener('abort', cancel)
  }
}

export async function discoverLocalDac(options: Environment & { signal?: AbortSignal }): Promise<LocalDacDiscovery> {
  // Fixed, finite candidate set; probe messages never contain tickets, tokens, cookies or credentials.
  const key = pairingStorageKey(options)
  const previous = previousOrigins.get(key)
  const previousResult = previous ? await probe(previous, options) : undefined
  if (previousResult && 'status' in previousResult) return previousResult
  if (options.signal?.aborted) return { state: 'unavailable' }
  const origins = previous ? LOCAL_DAC_ORIGINS.filter(origin => origin !== previous) : LOCAL_DAC_ORIGINS
  return new Promise(resolve => {
    const controller = new AbortController()
    const results: LocalDacDiscovery[] = previousResult ? [previousResult] : []
    let remaining = origins.length
    let settled = false
    const finish = (result: LocalDacDiscovery) => {
      if (settled) return
      settled = true
      options.signal?.removeEventListener('abort', cancel)
      controller.abort()
      resolve(result)
    }
    const cancel = () => finish({ state: 'unavailable' })
    if (options.signal?.aborted) { cancel(); return }
    options.signal?.addEventListener('abort', cancel, { once: true })
    for (const origin of origins) {
      void probe(origin, { ...options, signal: controller.signal }).then(result => {
        if (settled) return
        // A candidate has already passed nonce signature, environment, port and
        // any saved public-key checks. Before first confirmation it remains untrusted.
        if ('status' in result) {
          if (result.state === 'ready') previousOrigins.set(key, result.origin)
          finish(result)
          return
        }
        results.push(result)
        if (--remaining !== 0) return
        for (const state of ['identity-mismatch', 'wrong-environment', 'incompatible'] as const) {
          if (results.some(result => result.state === state)) { finish({ state }); return }
        }
        finish({ state: 'unavailable' })
      })
    }
  })
}
