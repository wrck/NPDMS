import request from '@/config/axios'
import SHA256 from 'crypto-js/sha256'
import { confirmLocalDac } from './localDac'
import type { LocalDacStatus } from './localDac'

const root = '/api/v1/pms/integration/device-ops/desktop/connections'
const randomHex = (size: number) => Array.from(crypto.getRandomValues(new Uint8Array(size)), byte => byte.toString(16).padStart(2, '0')).join('')
export interface PreparedLocalConnection {
  href: string
  expiresAt: number
  finish(signal: AbortSignal): ReturnType<typeof confirmLocalDac>
  cancel(): Promise<void>
}

/** Prepare before the click, so the protocol anchor retains browser user activation. */
export async function prepareLocalDacConnection(environment: { serverUrl: string; webOrigin: string }, signal: AbortSignal): Promise<PreparedLocalConnection> {
  const id = randomHex(16)
  const nonce = randomHex(32)
  const before = Date.now()
  const created = await request.post<{ requestId: string; tenantId: number | string; expiresInSeconds: number }>({
    url: root, signal, silentError: true, timeout: 10000,
    data: { requestId: id, nonceHash: SHA256(nonce).toString(), serverUrl: new URL(environment.serverUrl).href.replace(/\/+$/, ''), webOrigin: environment.webOrigin }
  })
  let cancelled = false
  const cancel = async () => {
    if (cancelled) return
    cancelled = true
    try { await request.delete({ url: `${root}/${id}`, timeout: 5000, silentError: true }) } catch { /* TTL also invalidates abandoned challenges. */ }
  }
  if (signal.aborted) { await cancel(); throw new Error('本机连接已取消') }
  if (created.requestId !== id || !/^[1-9][0-9]{0,17}$/.test(String(created.tenantId)) || created.expiresInSeconds !== 90) {
    await cancel(); throw new Error('本机连接准备失败，请刷新页面后重试')
  }
  const expiresAt = before + created.expiresInSeconds * 1000
  return {
    href: `npdms-dac://connect/${id}?challenge=${nonce}&tenant=${created.tenantId}`, expiresAt, cancel,
    async finish(waitSignal) {
      while (!cancelled && !waitSignal.aborted && Date.now() < expiresAt) {
        let result: { state: string; status?: LocalDacStatus }
        try {
          result = await request.get({ url: `${root}/${id}`, signal: waitSignal, silentError: true,
            timeout: Math.max(1, Math.min(5000, expiresAt - Date.now())) })
        } catch {
          throw new Error(waitSignal.aborted || cancelled ? '本机连接已取消' : '本机连接确认未完成或已过期，请检查登录和网络后重新连接')
        }
        if (cancelled || waitSignal.aborted || Date.now() >= expiresAt) break
        if (result.state === 'confirmed' && result.status) return confirmLocalDac({ ...environment, nonce, status: result.status, signal: waitSignal })
        if (result.state !== 'pending') throw new Error('本机连接已失效，请重新连接')
        await new Promise<void>(resolve => {
          const done = () => { clearTimeout(timer); waitSignal.removeEventListener('abort', done); resolve() }
          const timer = setTimeout(done, 1000)
          waitSignal.addEventListener('abort', done, { once: true })
          if (waitSignal.aborted) done()
        })
      }
      if (cancelled || waitSignal.aborted) throw new Error('本机连接已取消')
      throw new Error('连接确认超时，请允许打开客户端；若仍未连接，请更新 DAC 后重试')
    }
  }
}
