import { afterEach, expect, it, vi } from 'vitest'
import { discoverLocalDac, LOCAL_DAC_ORIGIN, localDacRequest, LocalDacError } from './localDac'

const environment = { serverUrl: 'https://npdms.example:8443', webOrigin: 'https://web.example' }
const ready = { product: 'npdms-dac', protocolVersion: 2, status: 'READY', ...environment }
const respond = (body: unknown) => {
  const request = vi.fn().mockResolvedValue({ ok: true, json: async () => body })
  vi.stubGlobal('fetch', request)
  return request
}
afterEach(() => {
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

it('discovers the fixed local endpoint without sending login tokens or cookies', async () => {
  const request = respond(ready)
  expect(await discoverLocalDac(environment)).toEqual({ state: 'ready', status: ready })
  expect(request).toHaveBeenCalledWith(
    `${LOCAL_DAC_ORIGIN}/api/v1/desktop/status`,
    expect.objectContaining({ credentials: 'omit', cache: 'no-store', redirect: 'error' })
  )
  expect(request.mock.calls[0][1].headers).toBeUndefined()
})

it('rejects a different environment before any credential can be sent', async () => {
  respond({ ...ready, serverUrl: 'https://other.example:8443' })
  expect(await discoverLocalDac(environment)).toEqual({ state: 'wrong-environment' })
  respond({ ...ready, webOrigin: 'https://another-web.example' })
  expect(await discoverLocalDac(environment)).toEqual({ state: 'wrong-environment' })
})

it('distinguishes startup from readiness and rejects an unrelated or incompatible service', async () => {
  const starting = { ...ready, status: 'STARTING' }
  respond(starting)
  expect(await discoverLocalDac(environment)).toEqual({ state: 'starting', status: starting })
  for (const payload of [null, { status: 'UP' }, { ...ready, protocolVersion: 1 }]) {
    respond(payload)
    expect(await discoverLocalDac(environment)).toEqual({ state: 'incompatible' })
  }
})

it('bounds unreachable service probing and never labels a network failure as not installed', async () => {
  vi.useFakeTimers()
  const request = vi.fn((_url: RequestInfo | URL, init?: RequestInit) => new Promise((_resolve, reject) => {
    init?.signal?.addEventListener('abort', () => reject(new Error('aborted')))
  }))
  vi.stubGlobal('fetch', request)
  const pending = discoverLocalDac(environment)
  await vi.advanceTimersByTimeAsync(2000)
  expect(await pending).toEqual({ state: 'unavailable' })
  expect(request).toHaveBeenCalledTimes(1)
})

it('uses only the scoped ticket on local execution and never follows a redirect', async () => {
  const request = respond({ collectionId: 'collection-1' })
  await localDacRequest({ path: '/tasks/task-1/executions', ticket: { value: 'short-ticket', tenantId: 1 }, method: 'POST', body: { password: 'local-only' } })
  expect(request.mock.calls[0][0]).toBe(`${LOCAL_DAC_ORIGIN}/api/v1/desktop/tasks/task-1/executions`)
  expect(request.mock.calls[0][1]).toMatchObject({ credentials: 'omit', redirect: 'error', headers: { 'X-NPDMS-DAC-Ticket': 'short-ticket', 'tenant-id': '1' } })
  expect(request.mock.calls[0][1].headers).not.toHaveProperty('Authorization')
  await expect(localDacRequest({ path: '//other.example', ticket: { value: 'short-ticket', tenantId: 1 } })).rejects.toBeInstanceOf(LocalDacError)
  expect(request).toHaveBeenCalledTimes(1)
})

it('keeps uncertain errors free of password-bearing request arguments', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('transport failed')))
  const error = await localDacRequest({ path: '/tasks/task-1/executions', ticket: { value: 'short-ticket', tenantId: 1 }, method: 'POST', body: { password: 'local-only' } }).catch(error => error)
  expect(error).toBeInstanceOf(LocalDacError)
  if (!(error instanceof LocalDacError)) throw new Error('Expected a local DAC error')
  expect(error.outcomeUnknown).toBe(true)
  expect(JSON.stringify(error)).not.toContain('local-only')
})

it.each([[401,'授权已过期或被拒绝'],[403,'授权与当前任务不匹配'],[404,'连接不存在'],[409,'连接已变化'],[400,'采集参数无效'],[503,'服务端授权校验']])('explains explicit local failures without retaining response or credential data: %s', async (status,reason) => {
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue({ok:false,status,json:async()=>({password:'never-display'})}))
  const failure=await localDacRequest({path:'/tasks/task-1/executions',ticket:{value:'scoped-ticket',tenantId:1},method:'POST',body:{password:'local-only'}}).catch(error=>error)
  expect(failure).toBeInstanceOf(LocalDacError)
  if (!(failure instanceof LocalDacError)) throw new Error('Expected a local DAC error')
  expect(failure.message).toContain(reason)
  expect(JSON.stringify(failure)).not.toContain('local-only')
  expect(JSON.stringify(failure)).not.toContain('never-display')
})
